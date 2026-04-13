from flask import Flask, jsonify
import threading

class GazeStatusServer:
    def __init__(self, gaze_tracker, port=8081):
        self.gaze_tracker = gaze_tracker
        self.port = port
        self.app = Flask(__name__)
        self.setup_routes()
    
    def setup_routes(self):
        @self.app.route('/gaze_status', methods=['GET'])
        def get_gaze_status():
            is_distracted, direction = self.gaze_tracker.is_distracted()
            return jsonify({
                'distracted': is_distracted,
                'direction': direction,
                'distraction_level': self.gaze_tracker.get_distraction_level(),
                'screen_region': self.gaze_tracker.get_screen_region(),
                'yaw': self.gaze_tracker.last_gaze.get('yaw', 0),
                'pitch': self.gaze_tracker.last_gaze.get('pitch', 0)
            })
    
    def start(self):
        threading.Thread(target=lambda: self.app.run(host='0.0.0.0', port=self.port, debug=False), daemon=True).start()