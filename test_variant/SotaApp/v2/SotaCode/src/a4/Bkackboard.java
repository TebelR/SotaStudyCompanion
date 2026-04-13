package a4;

import java.util.Map;
import java.time.Instant;
import java.util.HashMap;

class Blackboard {

    // ============================================================
    // GAZE TRACKING (from Python server)
    // ============================================================
    public boolean isDistracted = false;        // Only field needed from Python
    
    // ============================================================
    // SESSION STATE
    // ============================================================
    public boolean workSessionActive = false;   // Is work session currently running
    public Instant workStartTime = null;        // When current session started (for 1-hour timer)
    public int distractionCount = 0;            // Number of distractions in current session
    
    // ============================================================
    // BREAK STATE
    // ============================================================
    public int breakMinutes = 0;                // Duration of current break
    public Instant breakStartTime = null;       // When break started
    public boolean inBreak = false;             // Is break currently active
    
    // ============================================================
    // EXTERNAL CLIENTS
    // ============================================================
    public GazeTrackerClient gazeClient = null; // HTTP client for Python server
    public SotaRobotController robot = null;    // Hardware controller
    
    // ============================================================
    // GENERAL DATA STORAGE (for future expansion)
    // ============================================================
    public Map<String, Object> data = new HashMap<>();
    
    // ============================================================
    // CONSTRUCTOR
    // ============================================================
    public Blackboard() {
        gazeClient = new GazeTrackerClient("10.186.226.230", 8080);  // Same port as Python server
        this.robot = new SotaRobotController();
    }
    
    // ============================================================
    // HELPER METHODS
    // ============================================================
    public void cleanup() {
        // Any needed global cleanup
    }
    
    // Reset session state for new work session
    public void resetSession() {
        workSessionActive = true;
        workStartTime = Instant.now();
        distractionCount = 0;
        inBreak = false;
        breakStartTime = null;
    }
    
    // Start a break
    public void startBreak(int minutes) {
        breakMinutes = minutes;
        breakStartTime = Instant.now();
        inBreak = true;
        workSessionActive = false;
    }
    
    // End break and resume work
    public void endBreak() {
        inBreak = false;
        workSessionActive = true;
        workStartTime = Instant.now();
        distractionCount = 0;
        breakStartTime = null;
    }
    
    // Check if work session has exceeded 1 hour
    public boolean isWorkSessionExpired() {
        if (workStartTime == null) return false;
        long elapsed = System.currentTimeMillis() - workStartTime.toEpochMilli();
        return elapsed >= 60 * 60 * 1000;  // 1 hour
    }
    
    // Check if break is complete
    public boolean isBreakComplete() {
        if (breakStartTime == null) return false;
        long elapsed = System.currentTimeMillis() - breakStartTime.toEpochMilli();
        long totalMs = breakMinutes * 60 * 1000L;
        return elapsed >= totalMs;
    }
}