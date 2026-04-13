package a4;

import com.badlogic.gdx.ai.btree.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

// ============================================================
// CONDITION TASKS
// ============================================================

class IsWorkSessionActive extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        System.out.println("[CONDITION] Is work session active? " + getObject().workSessionActive);
        return getObject().workSessionActive ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsWorkSessionActive();
    }
}



class IsDistracted extends LeafTask<Blackboard> {
    private long distractionStartTime = 0;
    private static final long REQUIRED_DURATION_MS = 3000;
    
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        
        if (bb.isDistracted) {
            if (distractionStartTime == 0) {
                distractionStartTime = System.currentTimeMillis();
            }
            long distractedDuration = System.currentTimeMillis() - distractionStartTime;
            System.out.println("[CONDITION] Is distracted for some time? " + distractedDuration);
            if (distractedDuration >= REQUIRED_DURATION_MS) {
                return Status.SUCCEEDED;
            }
            return Status.RUNNING;
        } else {
            distractionStartTime = 0;
            return Status.FAILED;
        }
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsDistracted();
    }
}



class IsNotDistracted extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        System.out.println("[CONDITION] Is distracted? " + bb.isDistracted);
        if (!bb.isDistracted) {
            return Status.SUCCEEDED;
        } else {
            return Status.FAILED;
        }
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsNotDistracted();
    }
}



class WorkSessionTimeout extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        System.out.println("[CONDITION] Is work session expired? " + getObject().isWorkSessionExpired());
        return getObject().isWorkSessionExpired() ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new WorkSessionTimeout();
    }
}



class SureKeywordDetected extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        System.out.println("[CONDITION] Is SURE keyword detected? " + bb.robot.voiceKeywordDetected);
        return (bb.robot.voiceKeywordDetected && "sure".equalsIgnoreCase(bb.robot.getLastVoiceCommand())) 
            ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new SureKeywordDetected();
    }
}



class ShutdownKeywordDetected extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        System.out.println("[CONDITION] Is SHUTDOWN keyword detected? " + bb.robot.voiceKeywordDetected);
        return (bb.robot.voiceKeywordDetected && "done".equalsIgnoreCase(bb.robot.getLastVoiceCommand())) 
            ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ShutdownKeywordDetected();
    }
}



class WorkKeywordDetected extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        System.out.println("[CONDITION] Is WORK keyword detected? " + bb.robot.voiceKeywordDetected);
        return (bb.robot.voiceKeywordDetected && "work".equalsIgnoreCase(bb.robot.getLastVoiceCommand())) 
            ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new WorkKeywordDetected();
    }
}



class BreakKeywordDetected extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        System.out.println("[CONDITION] Is BREAK keyword detected? " + bb.robot.voiceKeywordDetected);
        return (bb.robot.voiceKeywordDetected && "break".equalsIgnoreCase(bb.robot.getLastVoiceCommand())) 
            ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new BreakKeywordDetected();
    }
}



class IsBreakComplete extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        System.out.println("[CONDITION] Is break complete? " + getObject().isBreakComplete());
        return getObject().isBreakComplete() ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsBreakComplete();
    }
}
// ============================================================
// ACTION TASKS
// ============================================================

class PlaySpeech extends LeafTask<Blackboard> {
    private final String text;
    
    public PlaySpeech(String text) {
        this.text = text;
    }
    
    @Override
    public Status execute() {
        System.out.println("[SOTA SPEECH] " + text);
        getObject().robot.speak(text);
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new PlaySpeech(this.text);
    }
}


class CaptureImage extends LeafTask<Blackboard> {
        private byte[] lastFrame = null;
        
        @Override
        public Status execute() {
            Blackboard bb = getObject();
            lastFrame = bb.robot.captureFrame();
            if (lastFrame != null) {
                // Store in blackboard for subsequent nodes
                bb.data.put("lastFrame", lastFrame);
                return Status.SUCCEEDED;
            }
            return Status.FAILED;
        }
        
        @Override
        protected Task<Blackboard> copyTo(Task<Blackboard> task) {
            return new CaptureImage();
        }
    }


class IsFocused extends LeafTask<Blackboard> {
        @Override
        public Status execute() {
            System.out.println("[CONDITION] Is focused? " + getObject().isDistracted);
            return getObject().isDistracted ? Status.FAILED : Status.SUCCEEDED;
        }
        
        @Override
        protected Task<Blackboard> copyTo(Task<Blackboard> task) {
            return new IsFocused();
        }
    }




class InterruptWork extends LeafTask<Blackboard> {
        @Override
        public Status execute() {
            getObject().workSessionActive = false;
            System.out.println("[WORK] Interrupted by break request");
            return Status.SUCCEEDED;
        }
        
        @Override
        protected Task<Blackboard> copyTo(Task<Blackboard> task) {
            return new InterruptWork();
        }
    }



class UpdateGazeStatus extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
            Blackboard bb = getObject();
            byte[] frame = (byte[]) bb.data.get("lastFrame");
            // System.out.println("[SERVER] Updating gaze status - is frame null: " + (frame == null));
            if (frame == null) return Status.FAILED;
            
            // Send to Python and store response
            int distracted = bb.gazeClient.updateAndGetDistracted(frame);
            bb.data.put("lastResponse", distracted == 1 ? true : false);

            if (distracted >= 0) {// API can get -1 on failure, 0 for not distracted, 1 for distracted
                // System.out.println("[SERVER] Updating gaze status - got response that isnt -1: ");
                return Status.SUCCEEDED;
            } else {
                System.err.println("Failed to get response from Python server or no face detected");
                return Status.FAILED;
            }
        }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new UpdateGazeStatus();
    }
}


class IsBreakMinutesGreaterThan extends LeafTask<Blackboard> {
    private final int threshold;
    public IsBreakMinutesGreaterThan(int threshold) { this.threshold = threshold; }
    @Override
    public Status execute() {
        System.out.println("[CONDITION] Is break minutes greater than " + threshold + "? " + (getObject().breakMinutes > threshold));
        return getObject().breakMinutes > threshold ? Status.SUCCEEDED : Status.FAILED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsBreakMinutesGreaterThan(threshold);
    }
}



class IsBreakMinutesEqual extends LeafTask<Blackboard> {
    private final int value;
    public IsBreakMinutesEqual(int value) { this.value = value; }
    @Override
    public Status execute() {
        System.out.println("[CONDITION] Is break minutes equal to " + value + "? " + (getObject().breakMinutes == value));
        return getObject().breakMinutes == value ? Status.SUCCEEDED : Status.FAILED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsBreakMinutesEqual(value);
    }
}



class IsBreakMinutesValid extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        int minutes = getObject().breakMinutes;
        System.out.println("[CONDITION] Is break minutes valid? " + (minutes >= 1 && minutes <= 60));
        return (minutes >= 1 && minutes <= 60) ? Status.SUCCEEDED : Status.FAILED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsBreakMinutesValid();
    }
}



class IsKeywordDetected extends LeafTask<Blackboard> {
    private final String keyword;
    public IsKeywordDetected(String keyword) { this.keyword = keyword; }
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        System.out.println("[CONDITION] Is " + keyword + " keyword detected? " + bb.robot.voiceKeywordDetected);
        return (bb.robot.voiceKeywordDetected && keyword.equalsIgnoreCase(bb.robot.getLastVoiceCommand())) 
            ? Status.SUCCEEDED : Status.FAILED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsKeywordDetected(keyword);
    }
}


class ListenForVoice extends LeafTask<Blackboard> {
    private long startTime = 0;
    private int durationMs;
    
    public ListenForVoice() { this.durationMs = 10000; }
    public ListenForVoice(int durationMs) { this.durationMs = durationMs; }
    
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
            bb.robot.startListening();
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        if (bb.robot.voiceKeywordDetected) {
            startTime = 0;
            bb.robot.voiceKeywordDetected = false;
            System.out.println("[VOICE INPUT] Detected keyword: " + bb.robot.getLastVoiceCommand());
            return Status.SUCCEEDED;
        }
        
        if (elapsed >= durationMs) {
            startTime = 0;
            System.out.println("[VOICE INPUT] Timed out");
            return Status.FAILED;
        }
        
        return Status.RUNNING;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ListenForVoice(durationMs);
    }
}



// This will either use the provided minutes, or the minutes stored in the blackboard if they were properly extracted from speech in an earlier task
class StartBreak extends LeafTask<Blackboard> {
    private final int fixedMinutes;
    
    public StartBreak() { this.fixedMinutes = -1; }
    public StartBreak(int fixedMinutes) { this.fixedMinutes = fixedMinutes; }
    
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        int minutes = (fixedMinutes > 0) ? fixedMinutes : bb.breakMinutes;
        bb.startBreak(minutes);
        System.out.println("[BREAK] Starting " + minutes + " minute break");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new StartBreak(fixedMinutes);
    }
}



class FinalCleanup extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        System.out.println("[SESSION] Final cleanup");
        return Status.SUCCEEDED;
    }
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new FinalCleanup();
    }
}




class InitializeWorkSession extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        bb.resetSession();
        System.out.println("[SESSION] Work session started");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new InitializeWorkSession();
    }
}



class IncrementDistractionCount extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        bb.distractionCount++;
        System.out.println("[FOCUS] Distraction count: " + bb.distractionCount);
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IncrementDistractionCount();
    }
}



class SelectAngerLevel extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        int count = bb.distractionCount;
        
        String tone;
        if (count < 3) {
            tone = "mild";
        } else if (count < 6) {
            tone = "moderate";
        } else {
            tone = "angry";
        }
        System.out.println("[SOTA SPEECH] Using " + tone + " tone");
        bb.robot.setVoiceTone(tone);
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new SelectAngerLevel();
    }
}



class PoliceLights extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.policeLights();
        System.out.println("[GESTURE] Police Lights Activated");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new PoliceLights();
    }
}



class PointAtUser extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.pointAtUserThenScreen();
        System.out.println("[GESTURE] Pointing at user");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new PointAtUser();
    }
}



class PointAtScreen extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        // This could be a separate gesture or part of pointAtUserThenScreen
        // For now, reuse the combined gesture
        getObject().robot.pointAtUserThenScreen();
        System.out.println("[GESTURE] Pointing at screen");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new PointAtScreen();
    }
}



class AccuseProcrastination extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        int count = bb.distractionCount;
        String message;
        if (count < 3) {
            message = "Hey, let's stay focused on your work!";
        } else if (count < 6) {
            message = "You're getting distracted again! Please focus!";
        } else {
            message = "FOCUS! You keep getting distracted!";
        }
        bb.robot.speak(message);
        System.out.println("[SOTA SPEECH] " + message);
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new AccuseProcrastination();
    }
}



class WaitSeconds extends LeafTask<Blackboard> {
    private final int seconds;
    private long startTime = 0;
    
    public WaitSeconds(int seconds) {
        this.seconds = seconds;
    }
    
    @Override
    public Status execute() {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= seconds * 1000L) {
            startTime = 0;
            System.out.println("[WAIT] Waited " + seconds + " seconds");
            return Status.SUCCEEDED;
        }
        return Status.RUNNING;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new WaitSeconds(this.seconds);
    }
}



class ShutdownRobot extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        System.out.println("[SOTA] Shutting down...");
        getObject().robot.shutdown();
        System.out.println("[SOTA] Shutdown complete");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ShutdownRobot();
    }
}



class SetWorkSessionActive extends LeafTask<Blackboard> {
    private final boolean active;
    
    public SetWorkSessionActive(boolean active) {
        this.active = active;
    }
    
    @Override
    public Status execute() {
        getObject().workSessionActive = active;
        System.out.println("[WORK] Set work session active to " + active);
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new SetWorkSessionActive(this.active);
    }
}

// ============================================================
// BREAK-RELATED ACTIONS
// ============================================================

class NodHead extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.nod();
        System.out.println("[GESTURE] Nod");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new NodHead();
    }
}

class TiltHead extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.tiltHead();
        System.out.println("[GESTURE] Tilt head");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new TiltHead();
    }
}

class LowerHead extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.lowerHead();
        System.out.println("[GESTURE] Lower head");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new LowerHead();
    }
}

class RaiseHead extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.raiseHead();
        System.out.println("[GESTURE] Raise head");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new RaiseHead();
    }
}




class ExtractBreakMinutes extends LeafTask<Blackboard> {
    private static final Pattern MINUTE_PATTERN = Pattern.compile("(\\d+)\\s*(minute|min|m)");
    
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        String command = bb.robot.getLastVoiceCommand();
        
        Matcher matcher = MINUTE_PATTERN.matcher(command.toLowerCase());
        
        if (matcher.find()) {
            int minutes = Integer.parseInt(matcher.group(1));
            if (minutes >= 1 && minutes <= 60) {
                bb.breakMinutes = minutes;
                System.out.println("[BREAK] Extracted break minutes: " + minutes);
                return Status.SUCCEEDED;
            }
        }
        
        System.out.println("[BREAK] Failed to extract break minutes");
        return Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ExtractBreakMinutes();
    }
}



// Displaying a progression of colour might be better than just a blinking yellow light - need to update the diagram for this
class UpdateBreakLEDs extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        if (bb.breakStartTime == null) return Status.SUCCEEDED;
        
        long elapsed = System.currentTimeMillis() - bb.breakStartTime.toEpochMilli();
        long totalMs = bb.breakMinutes * 60 * 1000L;
        float percentRemaining = 1.0f - (float)elapsed / totalMs;
        
        if (percentRemaining > 0.66f) {
            bb.robot.setLEDColor(0, 255, 0); // Green
        } else if (percentRemaining > 0.33f) {
            bb.robot.setLEDColor(255, 255, 0); // Yellow
        } else {
            bb.robot.setLEDColor(255, 0, 0); // Red
        }
        
        System.out.println("[BREAK] Updated break LEDs");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new UpdateBreakLEDs();
    }
}



class ResetWorkSession extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        bb.endBreak();
        System.out.println("[SESSION] Work session resumed after break");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ResetWorkSession();
    }
}

class ResetDistractionTimer extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        // Timer is managed by IsDistracted condition's internal state
        // This node exists for clarity in the tree structure
        System.out.println("[DISTRACTION] Reset distraction timer");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ResetDistractionTimer();
    }
}



// Helper class for loops
class Loop extends Decorator<Blackboard> {
    private int maxIterations;
    private int currentIteration = 0;
    
    public Loop(int maxIterations) {
        this.maxIterations = maxIterations;
    }
    
    @Override
    public void start() {
        super.start();
        currentIteration = 0;
    }
    
    @Override
    public void childSuccess(Task<Blackboard> runningTask) {
        currentIteration++;
        if (currentIteration < maxIterations) {
            running();
        } else {
            super.childSuccess(runningTask);
        }
    }
    
    @Override
    public void childFail(Task<Blackboard> runningTask) {
        super.childFail(runningTask);
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new Loop(maxIterations);
    }
}