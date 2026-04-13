import numpy as np
import time

class GazeInterpreter:
    def __init__(self):
        self.calibrated = False
        
        # calibration points: (gaze_yaw, gaze_pitch) -> (screen_x, screen_y)
        # screen_x: 0=left, 0.5=center, 1=right
        # screen_y: 0=top, 0.5=center, 1=bottom
        self.calibration_points = []
        
        # for smoothing
        self.gaze_history = []
        self.history_size = 5
        self.last_gaze = {"yaw": 0.0, "pitch": 0.0}
        self.last_screen_pos = (0.5, 0.5)  # default to center
        
        # distraction thresholds (in screen coordinates)
        self.distraction_threshold = 0.3  # 30% from center = distracted
        self.screen_bounds = {
            'left': 0.33,
            'center_min': 0.33,
            'center_max': 0.67,
            'right': 0.67,
            'top': 0.33,
            'bottom': 0.67
        }
        
        self.load_calibration_data()
    



    def load_calibration_data(self):
        
        # (gaze_yaw, gaze_pitch) -> (screen_x, screen_y)
        # based on my own calibration data collected from the 3x3 grid test
        calibration_data = [
            # Top row (y=0)
            ((0.3, -0.2), (0.0, 0.0)),    # top-left
            ((0.3, 0.8), (0.5, 0.0)),     # top-center
            ((0.3, 0.92), (1.0, 0.0)),     # top-right
            
            # middle row (y=1)
            ((-0.05, -0.2), (0.0, 0.5)),    # middle-left
            ((-0.05, 0.8), (0.5, 0.5)),    # middle-center
            ((-0.05, 0.92), (1.0, 0.5)),    # middle-right
            
            # bottom row (y=2)
            ((-0.46, -0.2), (0.0, 1.0)),    # bottom-left
            ((-0.46, 0.8), (0.5, 1.0)),    # bottom-center
            ((-0.46, 0.92), (1.0, 1.0)),    # bottom-right
        ]
        
        for (gaze_yaw, gaze_pitch), (screen_x, screen_y) in calibration_data:
            self.calibration_points.append({
                'gaze': (gaze_yaw, gaze_pitch),
                'screen': (screen_x, screen_y)
            })
        
        # ranges for interpolation
        self.gaze_yaw_min = min(p['gaze'][0] for p in self.calibration_points)
        self.gaze_yaw_max = max(p['gaze'][0] for p in self.calibration_points)
        self.gaze_pitch_min = min(p['gaze'][1] for p in self.calibration_points)
        self.gaze_pitch_max = max(p['gaze'][1] for p in self.calibration_points)
        
    

    def gaze_to_screen_position(self, yaw, pitch):

        # find the 4 nearest calibration points for interpolation
        # calculate distances to all calibration points
        distances = []
        for i, point in enumerate(self.calibration_points):
            g_yaw, g_pitch = point['gaze']
            distance = np.sqrt((yaw - g_yaw)**2 + (pitch - g_pitch)**2)
            distances.append((distance, i))
        
        distances.sort(key=lambda x: x[0])
        nearest_indices = [idx for _, idx in distances[:4]]
        
        screen_points = []
        gaze_points = []
        for idx in nearest_indices:
            screen_points.append(self.calibration_points[idx]['screen'])
            gaze_points.append(self.calibration_points[idx]['gaze'])
        
        total_weight = 0
        weighted_x = 0
        weighted_y = 0
        
        for i, idx in enumerate(nearest_indices):
            distance = distances[i][0]
            if distance < 0.001:
                return screen_points[i]
            
            weight = 1.0 / distance
            weighted_x += screen_points[i][0] * weight
            weighted_y += screen_points[i][1] * weight
            total_weight += weight
        
        screen_x = weighted_x / total_weight
        screen_y = weighted_y / total_weight

        screen_x = max(0.0, min(1.0, screen_x))
        screen_y = max(0.0, min(1.0, screen_y))
        
        return screen_x, screen_y
    


    def update_gaze(self, yaw, pitch):
        self.gaze_history.append((yaw, pitch))
        if len(self.gaze_history) > self.history_size:
            self.gaze_history.pop(0)
        
        # apply smoothing
        smoothed_yaw = np.mean([g[0] for g in self.gaze_history])
        smoothed_pitch = np.mean([g[1] for g in self.gaze_history])
        
        self.last_gaze = {"yaw": smoothed_yaw, "pitch": smoothed_pitch}
        
        # convert to screen position
        screen_x, screen_y = self.gaze_to_screen_position(smoothed_yaw, smoothed_pitch)
        self.last_screen_pos = (screen_x, screen_y)
        
        return screen_x, screen_y
    


    def get_gaze_direction(self):
        screen_x, screen_y = self.last_screen_pos
        
        # Define region boundaries
        LEFT_BOUND = 0.33
        RIGHT_BOUND = 0.67
        TOP_BOUND = 0.33
        BOTTOM_BOUND = 0.67
    
        if screen_x < LEFT_BOUND:
            h_dir = "left"
        elif screen_x > RIGHT_BOUND:
            h_dir = "right"
        else:
            h_dir = "center_h"
        
        if screen_y < TOP_BOUND:
            v_dir = "up"
        elif screen_y > BOTTOM_BOUND:
            v_dir = "down"
        else:
            v_dir = "center_v"
        
        if h_dir == "center_h" and v_dir == "center_v":
            return "looking_forward"
        elif h_dir == "center_h":
            return f"looking_{v_dir}"
        elif v_dir == "center_v":
            return f"looking_{h_dir}"
        else:
            return f"looking_{h_dir}_{v_dir}"
    



    def is_looking_at_screen(self, threshold=0.15):
        screen_x, screen_y = self.last_screen_pos
        distance_from_center = np.sqrt((screen_x - 0.5)**2 + (screen_y - 0.5)**2)
        return distance_from_center < threshold
    



    def is_distracted(self):
        screen_x, screen_y = self.last_screen_pos
        
        # check if looking outside screen bounds
        if screen_x < 0 or screen_x > 1 or screen_y < 0 or screen_y > 1:
            # determine which side
            if screen_x < 0:
                direction = "far_left"
            elif screen_x > 1:
                direction = "far_right"
            elif screen_y < 0:
                direction = "far_top"
            else:
                direction = "far_bottom"
            return True, direction
        
        distance_from_center = np.sqrt((screen_x - 0.5)**2 + (screen_y - 0.5)**2)
        
        if distance_from_center > self.distraction_threshold:
            dx = screen_x - 0.5
            dy = screen_y - 0.5
            
            if abs(dx) > abs(dy):
                direction = "far_left" if dx < 0 else "far_right"
            else:
                direction = "far_top" if dy < 0 else "far_bottom"
            return True, direction
        
        return False, "focused"
    


    def get_distraction_level(self):
        screen_x, screen_y = self.last_screen_pos
        distance = np.sqrt((screen_x - 0.5)**2 + (screen_y - 0.5)**2)
        max_distance = np.sqrt(0.5**2 + 0.5**2)
        
        level = min(1.0, distance / max_distance)
        
        if screen_x < 0 or screen_x > 1 or screen_y < 0 or screen_y > 1:
            level = 1.0
        
        return level
    


    def get_screen_region(self):
        screen_x, screen_y = self.last_screen_pos
        
        # define regions
        left = screen_x < 0.33
        center_x = 0.33 <= screen_x <= 0.67
        right = screen_x > 0.67
        
        top = screen_y < 0.33
        center_y = 0.33 <= screen_y <= 0.67
        bottom = screen_y > 0.67
        
        # determine region
        if center_x and center_y:
            return "screen_center"
        elif center_x and top:
            return "screen_top"
        elif center_x and bottom:
            return "screen_bottom"
        elif left and center_y:
            return "screen_left"
        elif right and center_y:
            return "screen_right"
        elif left and top:
            return "screen_top_left"
        elif left and bottom:
            return "screen_bottom_left"
        elif right and top:
            return "screen_top_right"
        elif right and bottom:
            return "screen_bottom_right"
        else:
            return "screen_unknown"
    

    
    def calibrate_center(self, gaze_tracker, duration=3):        
        samples = []
        for _ in range(duration * 10):
            gaze = gaze_tracker.get_gaze()
            if gaze:
                samples.append((gaze.get('yaw', 0), gaze.get('pitch', 0)))
            time.sleep(0.1)
        
        if samples:
            center_yaw = np.mean([s[0] for s in samples])
            center_pitch = np.mean([s[1] for s in samples])
            
            # adjust calibration points based on new center - offset from original center point
            offset_yaw = center_yaw - (-0.32)
            offset_pitch = center_pitch - 0.84 
            
            for point in self.calibration_points:
                original_yaw, original_pitch = point['gaze']
                point['gaze'] = (original_yaw + offset_yaw, original_pitch + offset_pitch)

            self.gaze_yaw_min += offset_yaw
            self.gaze_yaw_max += offset_yaw
            self.gaze_pitch_min += offset_pitch
            self.gaze_pitch_max += offset_pitch
            
            self.calibrated = True
            return True
        
        return False
