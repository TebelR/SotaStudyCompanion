import cv2
import torch
from pathlib import Path
import gdown

from gaze_interpreter import GazeInterpreter
# Import the L2CS-Net pipeline
try:
    from l2cs import Pipeline, render
except ImportError:
    raise ImportError("L2CS-Net not installed. Please run: pip install git+https://github.com/edavalosanaya/L2CS-Net.git@main")

class GazeTracker:

    # This will initialize the pipeline and download the model if it doesn't exist. The model is around 100MB, so it may take a moment to download the first time.
    # Feel free to download the model manually from the github repo of L2CS-Net. The model file needs to exist in a directory called "l2cs_models" in the same directory as this script
    def __init__(self, model_dir="./l2cs_models", device=None):
        self.calibrated = False
        self.center_x = 0.0
        self.center_y = 0.0
        self.max_x = 0.0  # Maximum horizontal deviation (looking far right)
        self.max_y = 0.0  # Maximum vertical deviation (looking far down)
        self.min_x = 0.0  # Minimum horizontal deviation (looking far left)
        self.min_y = 0.0  # Minimum vertical deviation (looking far up)
        
        # Screen boundaries (mapped from gaze space)
        self.screen_left = -1.0
        self.screen_right = 1.0
        self.screen_top = 1.0
        self.screen_bottom = -1.0

        self.model_dir = Path(model_dir)
        self.model_dir.mkdir(exist_ok=True)
        
        # Set device
        if device is None:
            self.device = 'cuda' if torch.cuda.is_available() else 'cpu'
        else:
            self.device = device
            
        print(f"GazeTracker (L2CS-Net) initializing on device: {self.device}")
        
        # Path to the pre-trained model (download if missing)
        self.model_path = self.model_dir / 'L2CSNet_gaze360.pkl'
        
        if not self.model_path.exists():
            self._download_model()
        
        # Initialize the L2CS pipeline
        self.gaze_pipeline = Pipeline(
            weights=str(self.model_path),
            arch='ResNet50',  # Using ResNet50 data for good accuracy/speed balance
            device=torch.device(self.device)
        )

        self.interpreter = GazeInterpreter()
        # Storage for the latest gaze result
        self.last_gaze = {"yaw": 0.0, "pitch": 0.0}
        self.initialized = True
        print("L2CS-Net pipeline initialized successfully!")


    def _download_model(self):
        print("Downloading L2CS-Net pre-trained model (L2CSNet_gaze360.pkl)...")
        # The below URL worked for me at the time of doing this assignment. If it fails, the repo has a link to a google drive that contains all the models (including this one)
        url = "https://drive.google.com/file/d/18S956r4jnHtSeT8z8t3z8AoJZjVnNqPJ/view?usp=drive_link"
        try:
            
            gdown.download(url, str(self.model_path), quiet=False)
        except ImportError:
            # Fallback to using requests if gdown is not installed
            import requests
            print("gdown not installed, downloading with requests...")
            response = requests.get(url, stream=True)
            with open(self.model_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            print("Download complete.")
        except Exception as e:
            print(f"Download failed: {e}. Please download the model manually from {url} and place it in {self.model_dir}")



    def process_frame(self, frame):
        if frame is None or not self.initialized:
            return frame, None
        
        # Convert to RGB for L2CS-Net
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        
        results = self.gaze_pipeline.step(frame_rgb)
        
        gaze_data = None
        if results and results.yaw is not None and results.pitch is not None:
            # L2CS-Net typically returns yaw, pitch as a tuple or two values
            yaw_value = float(results.yaw[0]) if len(results.yaw) > 0 else 0.0
            pitch_value = float(results.pitch[0]) if len(results.pitch) > 0 else 0.0
            
            self.interpreter.update_gaze(yaw_value, pitch_value)

            self.last_gaze = {"yaw": yaw_value, "pitch": pitch_value}
            gaze_data = self.last_gaze
            
            try:
                processed_frame = render(frame, results)
            except Exception as e:
                print(f"Failed to use the 'render' function from L2CS-Net: {e}")
                # Fallback to the old frame if rendering fails
                processed_frame = frame.copy()
        else:
            processed_frame = frame
            gaze_data = None
            
        # If no face is detected, the pipeline still returns a frame, but gaze_data is None
        return processed_frame, gaze_data

    
    
    def is_distracted(self):
        return self.interpreter.is_distracted()
    
    def get_distraction_level(self):
        return self.interpreter.get_distraction_level()
    
    def get_screen_region(self):
        return self.interpreter.get_screen_region()