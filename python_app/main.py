import cv2
import numpy as np
from flask import Flask, request, jsonify
import threading
import time
from PIL import Image, ImageTk
import tkinter as tk
from tkinter import ttk
from gaze_tracker import GazeTracker

app = Flask(__name__)

class SotaGazeServer:
    def __init__(self, host="0.0.0.0", port=8080):
        self.host = host
        self.port = port
        self.frame_width = 320# This is the smallest resolution I could get from Sota's camera so it is hardcoded here. Need to change this if Sota sends frames with a different resolution
        self.frame_height = 240
        self.frame_channels = 3
        
        # Initialize Gaze Tracker (shared between Flask and GUI)
        print("Initializing Gaze Tracker")
        self.gaze_tracker = GazeTracker()
        
        # Statistics for GUI
        self.last_frame = None
        self.last_processed_frame = None
        self.last_gaze_data = None
        self.request_count = 0
        self.last_request_time = time.time()
        self.fps = 0
        
        # Setup GUI
        self.root = tk.Tk()
        self.root.title("Sota Gaze Server")
        self.root.geometry("1000x750")
        self.root.configure(bg="#1c1c1c")
        
        self.setup_gui()
        print("GUI initialized")

        self.setup_endpoint()
        print("Endpoint initialized")
        
        # Start Flask server in background thread
        self.start_flask_server()
    

    def setup_endpoint(self):
        # Main entry point - receives BGR data, does inference through the L2CS pipeline, and returns a JSON response with distraction status
        @app.route('/detect', methods=['POST'])
        def detect():
            try:
                # My original implementation for Sota's client sends over raw BGR bytes - no compression
                frame_bytes = request.get_data()
                
                if len(frame_bytes) != self.frame_width * self.frame_height * self.frame_channels:
                    return jsonify({'error': f'Invalid frame size: {len(frame_bytes)}'}), 400
            
                frame_array = np.frombuffer(frame_bytes, dtype=np.uint8)
                frame = frame_array.reshape((self.frame_height, self.frame_width, self.frame_channels))
                
                # Stats for getting FPS in the GUI
                self.request_count += 1
                current_time = time.time()
                if current_time - self.last_request_time >= 1.0:
                    self.fps = self.request_count
                    self.request_count = 0
                    self.last_request_time = current_time
                    self.update_stats()
                
                self.last_frame = frame.copy()
                
                # Inference through L2CS pipeline - this should not affect the frame, but the gaze data now contains yaw, pitch and face bounding box
                processed_frame, gaze_data = self.gaze_tracker.process_frame(self.last_frame)
                
                if processed_frame is not None:
                    self.last_processed_frame = processed_frame
                
                if gaze_data:
                    self.last_gaze_data = gaze_data
                    # Update GUI display
                    self.update_gaze_display(gaze_data)
                    self.update_display(processed_frame)
                
                # Get distraction status - this will reach into the gaze interpreter to determine if the user is distracted
                is_distracted, direction = self.gaze_tracker.is_distracted()
                
                # Return JSON response
                return jsonify({
                    'distracted': is_distracted,
                })
                
            except Exception as e:
                print(f"Detection error: {e}")
                return jsonify({'error': str(e)}), 500
        

    
    # Starts the flask server in a separate thread
    def start_flask_server(self):
        def run_flask():
            # Suppress flask's default logging
            import logging
            log = logging.getLogger('werkzeug')
            log.setLevel(logging.ERROR)
            
            app.run(host=self.host, port=self.port, debug=False, threaded=True)
        
        flask_thread = threading.Thread(target=run_flask, daemon=True)
        flask_thread.start()
        print(f"Flask server started on {self.host}:{self.port}")
    


    # I used tkinter for the GUI as it is native to Python.
    def setup_gui(self):
        # Main container
        main_container = ttk.Frame(self.root, padding="10")
        main_container.pack(fill=tk.BOTH, expand=True)
        
        # Left panel for stats
        left_panel = ttk.Frame(main_container, width=280, relief=tk.RAISED, padding="10")
        left_panel.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))
        left_panel.pack_propagate(False)
        
        # Server Info
        ttk.Label(left_panel, text="Server Status", font=('Arial', 10, 'bold')).pack(pady=(0, 5))
        
        self.status_label = ttk.Label(left_panel, text="Status: Running", foreground='green')
        self.status_label.pack(pady=5)
        
        self.port_label = ttk.Label(left_panel, text=f"Port: {self.port}")
        self.port_label.pack(pady=2)
        
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
        
        # Screen Region
        ttk.Label(gaze_frame, text="Screen Region:", font=('Arial', 9)).grid(row=4, column=0, sticky=tk.W, pady=2)
        self.screen_region_label = ttk.Label(gaze_frame, text="N/A", foreground='gray')
        self.screen_region_label.grid(row=4, column=1, padx=10, sticky=tk.W)
        
        ttk.Separator(left_panel, orient='horizontal').pack(fill=tk.X, pady=10)
        
        # Performance stats
        ttk.Label(left_panel, text="Performance", font=('Arial', 10, 'bold')).pack(pady=(0, 5))
        self.fps_label = ttk.Label(left_panel, text="Requests/sec: 0")
        self.fps_label.pack()
        self.requests_label = ttk.Label(left_panel, text="Total requests: 0")
        self.requests_label.pack()
        
        ttk.Separator(left_panel, orient='horizontal').pack(fill=tk.X, pady=10)

        # Right panel - Video display
        right_panel = ttk.Frame(main_container)
        right_panel.pack(side=tk.RIGHT, fill=tk.BOTH, expand=True)
        
        self.video_canvas = tk.Canvas(right_panel, bg='black', highlightthickness=0)
        self.video_canvas.pack(fill=tk.BOTH, expand=True)
        
        # Statistics tracking
        self.total_requests = 0
        self.request_counter = 0
    
    

    # Update tick for the GUI
    def update_gaze_display(self, gaze_data):
        if not gaze_data:
            return
        
        yaw = gaze_data.get('yaw', 0)
        pitch = gaze_data.get('pitch', 0)
        
        # Get distraction status
        is_distracted, distraction_direction = self.gaze_tracker.is_distracted()
        distraction_level = self.gaze_tracker.get_distraction_level()
        screen_region = self.gaze_tracker.get_screen_region()
        
        # Update labels (using after for thread safety)
        self.root.after(0, lambda: self.gaze_yaw_label.config(text=f"{yaw:.2f}"))
        self.root.after(0, lambda: self.gaze_pitch_label.config(text=f"{pitch:.2f}"))
        
        # Update distraction status
        if is_distracted:
            status_text = f"DISTRACTED - {distraction_direction.upper()}"
            status_color = 'red'
        else:
            status_text = "FOCUSED"
            status_color = 'green'
        
        self.root.after(0, lambda: self.distraction_status_label.config(text=status_text, foreground=status_color))
        
        # Update progress bar
        progress_value = int(distraction_level * 100)
        self.root.after(0, lambda: self.distraction_progress.config(value=progress_value))
        
        # Update screen region
        region_display = screen_region.replace('_', ' ').title()
        self.root.after(0, lambda: self.screen_region_label.config(text=region_display))
    


    # Update tick for the screen portion of the GUI - this will display the latest processed frame with gaze annotations from the L2CS-Net pipeline
    def update_display(self, frame):
        if frame is None:
            return
        
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
    


    # Update fps and other stats
    def update_stats(self):
        self.root.after(0, lambda: self.fps_label.config(text=f"Requests/sec: {self.fps}"))
        self.root.after(0, lambda: self.requests_label.config(text=f"Total requests: {self.request_counter}"))
    


# Main method - can pass in a different port as an argument, but defaults to 8080 - need to configure MobaXTerm to forward Sota's requests to this port
if __name__ == "__main__":
    import sys
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    
    server = SotaGazeServer(port=port)
    server.root.mainloop()