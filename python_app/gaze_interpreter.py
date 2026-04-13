import numpy as np

class GazeInterpreter:
    def __init__(self):
        # Calibration points: (gaze_yaw, gaze_pitch) -> (screen_x, screen_y)
        # screen_x: 0=left, 0.5=center, 1=right
        # screen_y: 0=top, 0.5=center, 1=bottom
        self.calibration_points = []
        
        # For smoothing
        self.gaze_history = []
        self.history_size = 5
        self.last_gaze = {"yaw": 0.0, "pitch": 0.0}
        self.last_screen_pos = (0.5, 0.5)  # Default to center
        
        # Distraction thresholds (in screen coordinates)
        self.distraction_threshold = 0.4  # 40% from center = distracted - around 30 % becomes too strict, specially at 1 fps
        self.screen_bounds = {
            'left': 0.33,
            'center_min': 0.33,
            'center_max': 0.67,
            'right': 0.67,
            'top': 0.33,
            'bottom': 0.67
        }
        
        # Load your calibration data
        self.load_calibration_data()
    


    # All the yaw and pitch values are hard coded for simplicity - I ran out of time to implement a proper calibration routine :(
    def load_calibration_data(self):
        # (gaze_yaw, gaze_pitch) gets mapped to (screen_x, screen_y)
        # based on my own calibration data. I just looked at 9 points around the screen and recorded average values - these vary heavily based on where you're positioned w.r.t. the screen and Sota.
        calibration_data = [
            ((0.3, -0.2), (0.0, 0.0)),    # top-left
            ((0.3, 0.8), (0.5, 0.0)),     # top-center
            ((0.3, 0.92), (1.0, 0.0)),    # top-right

            ((-0.05, -0.2), (0.0, 0.5)),    # middle-left
            ((-0.05, 0.8), (0.5, 0.5)),    # center
            ((-0.05, 0.92), (1.0, 0.5)),    # middle-right

            ((-0.46, -0.2), (0.0, 1.0)),    # bottom-left
            ((-0.46, 0.8), (0.5, 1.0)),    # bottom-center
            ((-0.46, 0.92), (1.0, 1.0)),    # bottom-right
        ]
        
        for (gaze_yaw, gaze_pitch), (screen_x, screen_y) in calibration_data:
            self.calibration_points.append({
                'gaze': (gaze_yaw, gaze_pitch),
                'screen': (screen_x, screen_y)
            })
        
        self.gaze_yaw_min = min(p['gaze'][0] for p in self.calibration_points)
        self.gaze_yaw_max = max(p['gaze'][0] for p in self.calibration_points)
        self.gaze_pitch_min = min(p['gaze'][1] for p in self.calibration_points)
        self.gaze_pitch_max = max(p['gaze'][1] for p in self.calibration_points)
    


    # Bilinear interpolation to map gaze angles to screen coordinates
    def gaze_to_screen_position(self, yaw, pitch):
        # Find the 4 nearest calibration points for interpolation
        # calculate distances to all calibration points
        distances = []
        for i, point in enumerate(self.calibration_points):
            g_yaw, g_pitch = point['gaze']
            distance = np.sqrt((yaw - g_yaw)**2 + (pitch - g_pitch)**2)
            distances.append((distance, i))
        
        # Sort by distance and get the 4 closest points
        distances.sort(key=lambda x: x[0])
        nearest_indices = [idx for _, idx in distances[:4]]
        
        # Get the screen positions of nearest points
        screen_points = []
        gaze_points = []
        for idx in nearest_indices:
            screen_points.append(self.calibration_points[idx]['screen'])
            gaze_points.append(self.calibration_points[idx]['gaze'])
        
        # Weighted average
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
    


    # Update gaze position
    def update_gaze(self, yaw, pitch):
        # Update history for smoothing - there's lots of noise otherwise
        self.gaze_history.append((yaw, pitch))
        if len(self.gaze_history) > self.history_size:
            self.gaze_history.pop(0)
        
        # Apply smoothing
        smoothed_yaw = np.mean([g[0] for g in self.gaze_history])
        smoothed_pitch = np.mean([g[1] for g in self.gaze_history])
        self.last_gaze = {"yaw": smoothed_yaw, "pitch": smoothed_pitch}
        
        # Convert to screen position
        screen_x, screen_y = self.gaze_to_screen_position(smoothed_yaw, smoothed_pitch)
        self.last_screen_pos = (screen_x, screen_y)
        
        return screen_x, screen_y
    
        

    # Returns True if the user is looking away from the screen beyond a certain threshold
    def is_distracted(self):
        screen_x, screen_y = self.last_screen_pos
        
        # Check if looking outside screen bounds
        if screen_x < 0 or screen_x > 1 or screen_y < 0 or screen_y > 1:
            # Determine which side
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
    


    # Get continuous distraction level (0-1)
    def get_distraction_level(self):
        screen_x, screen_y = self.last_screen_pos
        
        # Calculate distance from center in screen coordinates
        distance = np.sqrt((screen_x - 0.5)**2 + (screen_y - 0.5)**2)
        
        # Maximum possible distance (corner to center = ~0.707)
        max_distance = np.sqrt(0.5**2 + 0.5**2)
        
        level = min(1.0, distance / max_distance)
        
        if screen_x < 0 or screen_x > 1 or screen_y < 0 or screen_y > 1:
            level = 1.0
        
        return level
    


    # Returns the screen region that the user is looking at
    def get_screen_region(self):
        screen_x, screen_y = self.last_screen_pos
        
        # Define regions
        left = screen_x < 0.33
        center_x = 0.33 <= screen_x <= 0.67
        right = screen_x > 0.67
        
        top = screen_y < 0.33
        center_y = 0.33 <= screen_y <= 0.67
        bottom = screen_y > 0.67
        
        # Determine region
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
    