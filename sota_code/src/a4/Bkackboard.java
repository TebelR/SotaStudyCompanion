package a4;


import java.time.Instant;

class Blackboard {

    public boolean debug;

    // ============================================================
    // GAZE TRACKING (from Python server)
    // ============================================================
    public int gazeState = 0;           // -1 = no face, 0 = not distracted, 1 = distracted, note that even with a 500 http error gazeState is set to -1
    public boolean isDistracted = false; // Convenience: gazeState == 1
    public boolean previousDistracted = false; // is used to determine when to start the distraction timer
    public long timeSinceNotDistracted = 0; // in seconds, also is used to track distraction time
    public long timeWhenLastNotDistracted = 0; // in seconds, this is used to determine how long a user has been distracted for

    // ============================================================
    // IMAGE CAPTURE
    // ============================================================
    public byte[] lastFrame = null;
    
    // ============================================================
    // SESSION STATE
    // ============================================================
    public boolean workSessionActive = false;
    public Instant workStartTime = null;
    public int distractionCount = 0;
    public static final int WORK_SESSION_DURATION = 1; // in minutes, this is the duration of a work session before a break is suggested
    
    // ============================================================
    // BREAK STATE
    // ============================================================
    public static final int DEFAULT_BREAK_MINUTES = 1; // Default break duration in minutes
    public int breakMinutes = 1; // This value gets reset with every new break, the static final is the one that is used to reset it for new breaks
    public Instant breakStartTime = null;
    public boolean inBreak = false;
    
    // ============================================================
    // VOICE COMMAND STATE (non-blocking)
    // ============================================================
    // Store last heard keyword and timestamp (expires after 4 seconds)
    public volatile String lastKeyword = null;
    public volatile long lastKeywordTime = 0;
    private static final long KEYWORD_EXPIRY_MS = 4000;

    // ============================================================
    // EXTERNAL CLIENTS
    // ============================================================
    public GazeTrackerClient gazeClient = null;
    public SotaRobotController robot = null;
    
    // ============================================================
    // CONSTRUCTOR
    // ============================================================
    public Blackboard(boolean debug) {
        gazeClient = new GazeTrackerClient("10.186.226.230", 8080); // ============= Add your python server address and port here =============
        this.robot = new SotaRobotController(this);
        this.debug = debug;
    }
    
    // ============================================================
    // VOICE HELPER METHODS
    // ============================================================
    
    // Called by the listening thread when a keyword is detected - updates the last heard keyword and timestamp
    public void setKeyword(String keyword) {
        this.lastKeyword = keyword;
        this.lastKeywordTime = System.currentTimeMillis();
    }
    


    // Check if a specific keyword was heard within expiry window
    public boolean heardKeyword(String keyword) {
        if (lastKeyword == null) return false;
        if (System.currentTimeMillis() - lastKeywordTime > KEYWORD_EXPIRY_MS) {
            lastKeyword = null;
            return false;
        }
        return lastKeyword.equalsIgnoreCase(keyword);
    }
    


    // Consume a keyword (for conditions that should only trigger once)
    public boolean consumeKeyword(String keyword) {
        if (heardKeyword(keyword)) {
            lastKeyword = null;
            return true;
        }
        return false;
    }
    
    // ============================================================
    // SESSION HELPER METHODS
    // ============================================================
    
    public void resetSession() {
        workSessionActive = true;
        workStartTime = Instant.now();
        distractionCount = 0;
        inBreak = false;
        breakStartTime = null;
        gazeState = 0;
        isDistracted = false;
    }
    

    
    public void startBreak() {
        breakMinutes = DEFAULT_BREAK_MINUTES;
        breakStartTime = Instant.now();
        inBreak = true;
        workSessionActive = false;
    }
    


    public void endBreak() {
        inBreak = false;
        workSessionActive = true;
        workStartTime = Instant.now();
        distractionCount = 0;
        breakStartTime = null;
    }
    


    public boolean isWorkSessionExpired() {
        if (workStartTime == null) return false;
        long elapsed = System.currentTimeMillis() - workStartTime.toEpochMilli();
        return elapsed >= WORK_SESSION_DURATION * 60 * 1000;
    }
    


    public boolean isBreakComplete() {
        if (breakStartTime == null) return false;
        long elapsed = System.currentTimeMillis() - breakStartTime.toEpochMilli();
        System.out.println("[CHECK] Break time elapsed: " + (elapsed / 1000) + " seconds.");
        return elapsed/1000 >= breakMinutes * 60;
    }
    

    
    public long getBreakTimeRemainingMs() {
        if (breakStartTime == null) return 0;
        long elapsed = System.currentTimeMillis() - breakStartTime.toEpochMilli();
        long remaining = breakMinutes * 60 * 1000L - elapsed;
        return Math.max(0, remaining);
    }
    
    // ============================================================
    // CLEANUP
    // ============================================================
    public void cleanup() {
        if (robot != null) {
            robot.shutdown();
        }
    }
}