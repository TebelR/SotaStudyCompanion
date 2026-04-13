# sota_stream_viewer.py
import cv2
import numpy as np
import requests
import threading
import time
from datetime import datetime
from PIL import Image, ImageTk
import tkinter as tk
from tkinter import ttk
from gaze_tracker import GazeTracker

class SotaStreamViewer:
    def __init__(self, sota_ip="10.186.226.136", sota_port=8080):
        self.stream_url = f"http://{sota_ip}:{sota_port}/stream"
        self.frame_url = f"http://{sota_ip}:{sota_port}/frame"
        self.streaming = False
        self.current_frame = None
        self.processed_frame = None
        self.fps = 0
        self.frame_count = 0
        self.last_fps_update = time.time()
        self.last_good_frame = None
        self.consecutive_errors = 0
        
        # For raw BGR streaming - we know the exact frame size
        self.frame_width = 320
        self.frame_height = 240
        self.frame_channels = 3  # BGR
        self.frame_size = self.frame_width * self.frame_height * self.frame_channels
        
        # Initialize Gaze Tracker
        print("Initializing Gaze Tracker...")
        self.gaze_tracker = GazeTracker()
        
        # Setup GUI
        self.root = tk.Tk()
        self.root.title("Sota Camera Stream - Gaze Tracking")
        self.root.geometry("1000x750")
        self.root.configure(bg="#1c1c1c")
        
        self.setup_gui()


        
    def setup_gui(self):
        # Main container
        main_container = ttk.Frame(self.root, padding="10")
        main_container.pack(fill=tk.BOTH, expand=True)
        
        # Left panel for controls
        left_panel = ttk.Frame(main_container, width=280, relief=tk.RAISED, padding="10")
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))
        left_panel.pack_propagate(False)
        
        # Connection controls
        ttk.Label(left_panel, text="Connection", font=('Arial', 10, 'bold')).pack(pady=(0, 5))
        
        ip_frame = ttk.Frame(left_panel)
        ip_frame.pack(fill=tk.X, pady=2)
        ttk.Label(ip_frame, text="IP:").pack(side=tk.LEFT)
        self.ip_entry = ttk.Entry(ip_frame, width=15)
        self.ip_entry.insert(0, "10.186.226.136")
        self.ip_entry.pack(side=tk.RIGHT)
        
        port_frame = ttk.Frame(left_panel)
        port_frame.pack(fill=tk.X, pady=2)
        ttk.Label(port_frame, text="Port:").pack(side=tk.LEFT)
        self.port_entry = ttk.Entry(port_frame, width=6)
        self.port_entry.insert(0, "8080")
        self.port_entry.pack(side=tk.RIGHT)
        
        self.connect_btn = ttk.Button(left_panel, text="Connect", command=self.toggle_stream)
        self.connect_btn.pack(fill=tk.X, pady=10)
        
        # Status
        self.status_label = ttk.Label(left_panel, text="Status: Disconnected", foreground='red')
        self.status_label.pack(pady=5)
        
        ttk.Separator(left_panel, orient='horizontal').pack(fill=tk.X, pady=10)
        
        # Gaze Display Panel
        ttk.Label(left_panel, text="Gaze Tracking", font=('Arial', 10, 'bold')).pack(pady=(0, 5))
        
        gaze_frame = ttk.Frame(left_panel, relief=tk.SUNKEN, padding="10")
        gaze_frame.pack(fill=tk.X, pady=5)
        
        # Yaw (Horizontal)
        ttk.Label(gaze_frame, text="Yaw (Left/Right):", font=('Arial', 9)).grid(row=0, column=0, sticky=tk.W, pady=2)
        self.gaze_yaw_label = ttk.Label(gaze_frame, text="0.00", foreground='black', font=('Arial', 10, 'bold'))
        self.gaze_yaw_label.grid(row=0, column=1, padx=10, sticky=tk.W)
        
        # Pitch (Vertical)
        ttk.Label(gaze_frame, text="Pitch (Up/Down):", font=('Arial', 9)).grid(row=1, column=0, sticky=tk.W, pady=2)
        self.gaze_pitch_label = ttk.Label(gaze_frame, text="0.00", foreground='black', font=('Arial', 10, 'bold'))
        self.gaze_pitch_label.grid(row=1, column=1, padx=10, sticky=tk.W)
        
        # Distraction Status
        ttk.Label(gaze_frame, text="Distraction Status:", font=('Arial', 9)).grid(row=2, column=0, sticky=tk.W, pady=2)
        self.distraction_status_label = ttk.Label(gaze_frame, text="Unknown", foreground='yellow', font=('Arial', 10, 'bold'))
        self.distraction_status_label.grid(row=2, column=1, padx=10, sticky=tk.W)
        
        # Distraction Level (Progress Bar)
        ttk.Label(gaze_frame, text="Distraction Level:", font=('Arial', 9)).grid(row=3, column=0, sticky=tk.W, pady=(10, 2))
        self.distraction_progress = ttk.Progressbar(gaze_frame, length=150, mode='determinate', maximum=100)
        self.distraction_progress.grid(row=3, column=1, padx=10, pady=(10, 2))
        
        # Screen Region (optional)
        ttk.Label(gaze_frame, text="Screen Region:", font=('Arial', 9)).grid(row=4, column=0, sticky=tk.W, pady=2)
        self.screen_region_label = ttk.Label(gaze_frame, text="N/A", foreground='gray')
        self.screen_region_label.grid(row=4, column=1, padx=10, sticky=tk.W)
        
        ttk.Separator(left_panel, orient='horizontal').pack(fill=tk.X, pady=10)
        
        # Performance stats
        ttk.Label(left_panel, text="Performance", font=('Arial', 10, 'bold')).pack(pady=(0, 5))
        self.fps_label = ttk.Label(left_panel, text="FPS: 0")
        self.fps_label.pack()
        self.error_label = ttk.Label(left_panel, text="Errors: 0")
        self.error_label.pack()
        
        # Calibration button - for the center of the screen only
        self.calibrate_center_btn = ttk.Button(left_panel, text="Calibrate Center", command=self.calibrate_center, state='disabled')
        self.calibrate_center_btn.pack(pady=10)
        
        # Screenshot button
        self.screenshot_btn = ttk.Button(left_panel, text="Take Screenshot", command=self.take_screenshot, state='disabled')
        self.screenshot_btn.pack(pady=5)
        
        # Right panel - Video display
        right_panel = ttk.Frame(main_container)
        right_panel.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True)
        
        self.video_canvas = tk.Canvas(right_panel, bg='black', highlightthickness=0)
        self.video_canvas.pack(fill=tk.BOTH, expand=True)

    def calibrate_center(self):
        """Run center calibration"""
        def calibration_thread():
            self.status_label.config(text="Status: Calibrating Center...", foreground='orange')
            self.calibrate_center_btn.config(state='disabled')
            
            self.gaze_tracker.calibrate_center(duration=3)
            
            self.status_label.config(text="Status: Streaming", foreground='green')
            self.calibrate_center_btn.config(state='normal')
        
        threading.Thread(target=calibration_thread, daemon=True).start()

    def toggle_stream(self):
        if not self.streaming:
            self.start_stream()
        else:
            self.stop_stream()

    def start_stream(self):
        ip = self.ip_entry.get()
        port = self.port_entry.get()
        self.stream_url = f"http://{ip}:{port}/stream"
        self.frame_url = f"http://{ip}:{port}/frame"
        
        self.streaming = True
        self.consecutive_errors = 0
        
        self.stream_thread = threading.Thread(target=self.stream_loop_raw, daemon=True)
        self.stream_thread.start()
        
        self.connect_btn.config(text="Disconnect")
        self.screenshot_btn.config(state='normal')
        self.status_label.config(text="Status: Streaming", foreground='green')
        self.calibrate_center_btn.config(state='normal')
        
    def stop_stream(self):
        self.streaming = False
        self.connect_btn.config(text="Connect")
        self.screenshot_btn.config(state='disabled')
        self.status_label.config(text="Status: Disconnected", foreground='red')
        self.calibrate_center_btn.config(state='disabled')

    def stream_loop_raw(self):
        """Raw BGR stream handler with frame delimiters"""
        buffer = bytearray()
        MAGIC_NUMBER = 0xDEADBEEF
        
        while self.streaming:
            try:
                response = requests.get(self.stream_url, stream=True, timeout=5)
                if response.status_code == 200:
                    buffer.clear()
                    
                    for chunk in response.iter_content(chunk_size=8192):
                        if not self.streaming:
                            break
                        
                        if chunk:
                            buffer.extend(chunk)
                            
                            # Look for complete frames
                            while len(buffer) >= 8:  # Need at least magic + size
                                # Check for magic number
                                magic = int.from_bytes(buffer[:4], byteorder='big')
                                
                                if magic == MAGIC_NUMBER:
                                    # Get frame size
                                    frame_len = int.from_bytes(buffer[4:8], byteorder='big')
                                    
                                    # Check if we have complete frame
                                    if len(buffer) >= 8 + frame_len:
                                        # Extract frame data
                                        frame_data = buffer[8:8 + frame_len]
                                        buffer = buffer[8 + frame_len:]
                                        
                                        # Convert to image
                                        frame_array = np.frombuffer(frame_data, dtype=np.uint8)
                                        
                                        try:
                                            frame = frame_array.reshape((self.frame_height, self.frame_width, self.frame_channels))
                                            
                                            if frame is not None and frame.size > 0:
                                                # Process the frame
                                                processed_frame, gaze_data = self.gaze_tracker.process_frame(frame)
                                                self.processed_frame = processed_frame
                                                self.update_display(processed_frame)

                                                if gaze_data:
                                                    self.update_gaze_display(gaze_data)

                                                self.frame_count += 1
                                                
                                                # Update FPS
                                                current_time = time.time()
                                                if current_time - self.last_fps_update >= 1.0:
                                                    self.fps = self.frame_count
                                                    self.frame_count = 0
                                                    self.last_fps_update = current_time
                                                    self.update_stats()
                                                
                                        except Exception as e:
                                            print(f"Reshape error: {e}")
                                    else:
                                        # Wait for more data
                                        break
                                else:
                                    # Magic number mismatch - search for next magic
                                    # Find the next occurrence of DE AD BE EF
                                    found = buffer.find(b'\xDE\xAD\xBE\xEF')
                                    if found > 0:
                                        buffer = buffer[found:]
                                    else:
                                        buffer.clear()
                                    break
                    
                    if self.streaming:
                        time.sleep(0.1)
                else:
                    print(f"HTTP error: {response.status_code}")
                    time.sleep(1)
                    
            except Exception as e:
                print(f"Stream error: {e}")
                time.sleep(2)
    
    def update_gaze_display(self, gaze_data):
        """Update GUI with latest gaze values and distraction status"""
        yaw = gaze_data.get('yaw', 0)
        pitch = gaze_data.get('pitch', 0)
        
        # Get distraction status from gaze tracker
        is_distracted, distraction_direction = self.gaze_tracker.is_distracted()
        distraction_level = self.gaze_tracker.get_distraction_level()
        screen_region = self.gaze_tracker.get_screen_region()
        
        # Update yaw/pitch labels
        self.root.after(0, lambda: self.gaze_yaw_label.config(text=f"{yaw:.2f}"))
        self.root.after(0, lambda: self.gaze_pitch_label.config(text=f"{pitch:.2f}"))
        
        # Update distraction status with color coding
        if is_distracted:
            status_text = f"DISTRACTED - {distraction_direction.upper()}"
            status_color = 'red'
        else:
            status_text = "FOCUSED"
            status_color = 'green'
        
        self.root.after(0, lambda: self.distraction_status_label.config(text=status_text, foreground=status_color))
        
        # Update distraction progress bar (0-100)
        progress_value = int(distraction_level * 100)
        self.root.after(0, lambda: self.distraction_progress.config(value=progress_value))
        
        # Change progress bar color based on level (requires ttk.Style)
        if progress_value < 30:
            color = 'green'
        elif progress_value < 70:
            color = 'orange'
        else:
            color = 'red'
        
        # Update screen region label
        region_display = screen_region.replace('_', ' ').title()
        self.root.after(0, lambda: self.screen_region_label.config(text=region_display))
        
        # Optional: Also update the console for debugging
        if self.frame_count % 30 == 0:  # Print every 30 frames
            print(f"Yaw: {yaw:.2f}, Pitch: {pitch:.2f}, Distracted: {is_distracted}, Level: {distraction_level:.2f}")
    
    def update_display(self, frame):
        """Update the video canvas with processed frame"""
        if frame is not None:
            canvas_width = self.video_canvas.winfo_width()
            canvas_height = self.video_canvas.winfo_height()
            
            if canvas_width > 10 and canvas_height > 10:
                frame_height, frame_width = frame.shape[:2]
                ratio = min(canvas_width / frame_width, canvas_height / frame_height)
                new_width = int(frame_width * ratio)
                new_height = int(frame_height * ratio)
                
                resized = cv2.resize(frame, (new_width, new_height))
                frame_rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)
                
                img = Image.fromarray(frame_rgb)
                imgtk = ImageTk.PhotoImage(image=img)
                
                x_offset = (canvas_width - new_width) // 2
                y_offset = (canvas_height - new_height) // 2
                
                self.video_canvas.delete("all")
                self.video_canvas.create_image(x_offset, y_offset, anchor=tk.NW, image=imgtk)
                self.video_canvas.image = imgtk
    
    def update_stats(self):
        self.fps_label.config(text=f"FPS: {self.fps}")
    
    def update_error_count(self):
        self.error_label.config(text=f"Errors: {self.consecutive_errors}")
    
    def take_screenshot(self):
        if self.processed_frame is not None:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            filename = f"sota_gaze_{timestamp}.jpg"
            cv2.imwrite(filename, self.processed_frame)
            print(f"Screenshot saved: {filename}")
    
    def run(self):
        self.root.mainloop()

if __name__ == "__main__":
    import sys
    ip = sys.argv[1] if len(sys.argv) > 1 else "10.186.226.136"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 8080
    
    viewer = SotaStreamViewer(ip, port)
    viewer.run()