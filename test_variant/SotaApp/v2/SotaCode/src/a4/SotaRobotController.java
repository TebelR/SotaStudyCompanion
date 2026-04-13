package a4;

import jp.vstone.RobotLib.*;
import jp.vstone.camera.CRoboCamera;
import jp.vstone.camera.CameraCapture;

import java.awt.Color;
import java.io.*;

public class SotaRobotController {
    private CSotaMotion motion;
    private CRobotMem mem;
    private CRoboCamera camera;
    private boolean isConnected = false;
    private CameraCapture capture = null;
    private PiperTTS piper;

    private VoiceKeywordDetector voiceDetector;
    private Thread voicePollingThread;
    private String lastVoiceCommand = "";
    public boolean voiceKeywordDetected = false;
    
    // Servo IDs for Sota (from documentation)
    private final Byte[] SERVO_IDS = {1, 2, 3, 4, 5, 6, 7, 8};
    
    // Neutral pose (all servos at center/rest position)
    private final Short[] NEUTRAL_POSE = {0, 0, 0, 0, 0, 0, 0, 0};
    
    // Nod pose: pitch head down then back to neutral
    private final Short[] NOD_DOWN_POSE = {0, 0, 0, 0, 0, 0, -300, 0};  // Head pitch down
    private final int NOD_DURATION_MS = 200;
    
    // Tilt head (listening posture) - roll the head
    private final Short[] TILT_POSE = {0, 0, 0, 0, 0, 0, 0, 200};  // Head roll
    
    // Lower head (break posture) - look down
    private final Short[] LOWER_HEAD_POSE = {0, 0, 0, 0, 0, 0, -450, 0};  // Look down more
    
    // Raise head (return to neutral)
    private final Short[] RAISE_HEAD_POSE = NEUTRAL_POSE;
    
    // Point gestures
    // Right arm point (shoulder, elbow, wrist)
    private final Short[] POINT_RIGHT_POSE = {0, 0, 0, 0, 500, 0, 0, 0};  // Extend right arm
    
    // Look directions (head yaw only)
    private final Short[] LOOK_LEFT_POSE = {0, 0, 0, 0, 0, -450, 0, 0};
    private final Short[] LOOK_RIGHT_POSE = {0, 0, 0, 0, 0, 450, 0, 0};
    private final Short[] LOOK_CENTER_POSE = NEUTRAL_POSE;
    
    // Police lights colors (LED control) - used on procrasitnation alert
    private final int[][] POLICE_COLORS = {
        {255, 0, 0},   // Red
        {0, 0, 255},   // Blue
        {255, 0, 0},   // Red
        {0, 0, 255}    // Blue
    };
    
    public SotaRobotController() {
        connect();
        piper = new PiperTTS("http://10.186.226.230:5000");
        voiceDetector = new VoiceKeywordDetector();
        startVoicePolling();
    }
    
    private void connect() {
        mem = new CRobotMem();
        motion = new CSotaMotion(mem);
        
        if(mem.Connect()) {
            motion.InitRobot_Sota();
            isConnected = true;
            System.out.println("[SOTA] Robot connected");
            
            // Initialize camera
            camera = new CRoboCamera("/dev/video0", motion);
            
            // Set neutral pose on startup
            goToNeutral();
        } else {
            System.err.println("[SOTA] Failed to connect to robot");
        }
    }



    private void startVoicePolling() {
        voicePollingThread = new Thread(() -> {
            while (true) {
                if (voiceDetector.hasKeyword()) {
                    String keyword = voiceDetector.getNextKeyword();
                    if (keyword != null) {
                        // This will be read by behavior tree nodes
                        // Need to update blackboard here------------------------------------------------------
                        // For now store it in a field that the behavior tree can access
                        lastVoiceCommand = keyword;
                        voiceKeywordDetected = true;
                    }
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        voicePollingThread.setDaemon(true);
        voicePollingThread.start();
    }



    public String getLastVoiceCommand() {
        String cmd = lastVoiceCommand;
        lastVoiceCommand = "";
        voiceKeywordDetected = false;
        return cmd;
    }
    


    public void startListening() {
        System.out.println("[SOTA] Listening for voice commands...");
        voiceDetector.startListening();
    }
    


    public void stopListening() {
        voiceDetector.stopListening();
    }


    
    private void goToNeutral() {
        if (!isConnected) return;
        CRobotPose pose = new CRobotPose();
        pose.SetPose(SERVO_IDS, NEUTRAL_POSE);
        motion.play(pose, 500);
        motion.waitEndinterpAll();
    }
    
    private void playPose(Short[] targetAngles, int durationMs) {
        if (!isConnected) return;
        CRobotPose pose = new CRobotPose();
        pose.SetPose(SERVO_IDS, targetAngles);
        motion.play(pose, durationMs);
    }
    
    private void playPoseAndWait(Short[] targetAngles, int durationMs) {
        playPose(targetAngles, durationMs);
        motion.waitEndinterpAll();
    }
    
    private void waitMs(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    public void speak(String text) {
        System.out.println("[SOTA SPEAK] " + text);
        if (piper != null) {
            piper.speak(text);
        }
    }
    
    public void setVoiceTone(String tone) {
        System.out.println("[SOTA TONE] " + tone);
        piper.setTone(tone);
    }
    
    public void policeLights() {
        System.out.println("[SOTA LIGHTS] Police mode (red/blue alternating)");
        new Thread(() -> {
            for (int i = 0; i < 6; i++) {  // 3 cycles of red/blue
                int[] color = POLICE_COLORS[i % POLICE_COLORS.length];
                setLEDColor(color[0], color[1], color[2]);
                waitMs(200);
            }
            setLEDColor(0, 0, 0);  // Turn off
        }).start();
    }
    
    public void setLEDColor(int r, int g, int b) {
        System.out.println("[SOTA LED] RGB(" + r + "," + g + "," + b + ")");
        if (motion != null) {
            CRobotPose nextPose = new CRobotPose();
            nextPose.setLED_Sota(new Color(r, g, b), new Color(r, g, b), 0, new Color(0, 0, 0));
            motion.play(nextPose, 10);
        }
    }
    
    public void pointAtUserThenScreen() {
        System.out.println("[SOTA GESTURE] Point at user, then at screen");
        
        // First point at user (extend right arm)
        playPoseAndWait(POINT_RIGHT_POSE, 400);
        waitMs(500);
        
        // Return to neutral
        playPoseAndWait(NEUTRAL_POSE, 400);
        waitMs(300);
        
        // Then point at screen (extend left arm - mirror of right)
        Short[] pointLeftPose = {100, 0, 0, 0, -500, 0, 0, 0};
        playPoseAndWait(pointLeftPose, 400);
        waitMs(500);
        
        // Return to neutral
        playPoseAndWait(NEUTRAL_POSE, 400);
    }
    
    public void nod() {
        System.out.println("[SOTA GESTURE] Nodding");
        // Nod down and back up
        playPoseAndWait(NOD_DOWN_POSE, NOD_DURATION_MS);
        playPoseAndWait(NEUTRAL_POSE, NOD_DURATION_MS);
    }
    
    public void tiltHead() {
        System.out.println("[SOTA GESTURE] Tilting head (listening posture)");
        playPoseAndWait(TILT_POSE, 300);
        waitMs(1000);  // Stay tilted while listening
        playPoseAndWait(NEUTRAL_POSE, 300);
    }
    
    public void lowerHead() {
        System.out.println("[SOTA GESTURE] Lowering head (break posture)");
        playPoseAndWait(LOWER_HEAD_POSE, 500);
    }
    
    public void raiseHead() {
        System.out.println("[SOTA GESTURE] Raising head");
        playPoseAndWait(RAISE_HEAD_POSE, 500);
    }
    
    public void lookAt(String position) {
        System.out.println("[SOTA] Looking " + position);
        Short[] targetPose;
        
        switch(position.toLowerCase()) {
            case "left":
                targetPose = LOOK_LEFT_POSE;
                break;
            case "right":
                targetPose = LOOK_RIGHT_POSE;
                break;
            default:
                targetPose = LOOK_CENTER_POSE;
                break;
        }
        
        playPoseAndWait(targetPose, 300);
    }


    
    public void shutdown() {
        System.out.println("[SOTA] Shutting down");
        if (camera != null) {
            camera.closeCapture();
        }
        if (voiceDetector != null) {
            voiceDetector.cleanup();
        }
        if (motion != null) {
            motion.ServoOff();
        }
        System.exit(0);
    }
    
    public byte[] captureFrame() {
        if (capture == null) {
            capture = new CameraCapture(CameraCapture.CAP_IMAGE_SIZE_QVGA, 
                                        CameraCapture.CAP_FORMAT_3BYTE_BGR);
            try {
                capture.openDevice("/dev/video0");
                System.out.println("[SOTA] Camera initialized for frame capture");
            } catch (IOException e) {
                System.err.println("[SOTA] Camera open failed: " + e.getMessage());
                return null;
            }
        }
        
        try {
            capture.snap();
            byte[] frameData = capture.getImageRawData();
            if (frameData != null && frameData.length == 320 * 240 * 3) {
                return frameData;
            } else {
                System.err.println("[SOTA] Invalid frame size: " + 
                    (frameData != null ? frameData.length : "null"));
                return null;
            }
        } catch (Exception e) {
            System.err.println("[SOTA] Frame capture failed: " + e.getMessage());
            return null;
        }
    }
}