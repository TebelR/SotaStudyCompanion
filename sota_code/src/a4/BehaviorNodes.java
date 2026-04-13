package a4;

import com.badlogic.gdx.ai.btree.*;
import java.time.Instant;

// ============================================================
// CONDITION TASKS
// ============================================================

// Checks if a specific keyword was heard (consumes it)
class HeardKeyword extends LeafTask<Blackboard> {
    private final String keyword;
    
    public HeardKeyword(String keyword) {
        this.keyword = keyword;
    }
    
    @Override
    public Status execute() {
        boolean heard = getObject().consumeKeyword(keyword);
        if (getObject().debug) {
            System.out.println("[CONDITION] Heard '" + keyword + "'? " + heard);
        }
        
        return heard ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new HeardKeyword(keyword);
    }
}



// Checks work session active flag
class IsWorkSessionActive extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean active = getObject().workSessionActive;
        if (getObject().debug) {
            System.out.println("[CONDITION] Work session active? " + active);
        }
        return active ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsWorkSessionActive();
    }
}



class IsWorkSessionNotActive extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean notActive = !getObject().workSessionActive;
        if (getObject().debug) {
            System.out.println("[CONDITION] Work session not active? " + notActive);
        }
        return notActive ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsWorkSessionNotActive();
    }
}



// Checks if user is distracted (gazeState == 1)
class IsGazeDistracted extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean distracted = getObject().gazeState == 1;
        if (getObject().debug) {
            System.out.println("[CONDITION] Gaze distracted? " + distracted);
        }
        Blackboard bb = getObject();
        if (bb.previousDistracted == false && distracted == true) { // If user just became distracted, reset the point in time when user was last not distracted
            bb.timeWhenLastNotDistracted = Instant.now().toEpochMilli();
            bb.previousDistracted = true;
        }else if (bb.previousDistracted == true && distracted == false) { // If user just became focused, reset true SINCE timer
            bb.timeSinceNotDistracted = 0;
            bb.previousDistracted = false;
        } else{
            bb.timeSinceNotDistracted = Instant.now().toEpochMilli() - bb.timeWhenLastNotDistracted; // Increment the timer SINCE
        }
        return distracted ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsGazeDistracted();
    }
}


class HasBeenDistractedForLong extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        boolean distractedForLong = bb.timeSinceNotDistracted > 3000 && bb.isDistracted; // 3 seconds and is still distracted
        if (getObject().debug) {
            System.out.println("[CONDITION] Has been distracted for long? " + distractedForLong );
        }
        return distractedForLong ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new HasBeenDistractedForLong();
    }
}


// Checks if user is focused (gazeState == 0)
class IsGazeFocused extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean focused = getObject().gazeState == 0;
        if (getObject().debug) {
            System.out.println("[CONDITION] Gaze focused? " + focused);
        }
        return focused ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsGazeFocused();
    }
}


// Checks if no face detected (gazeState == -1)
class IsNoFaceDetected extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean noFace = getObject().gazeState == -1;
        if (getObject().debug) {
            System.out.println("[CONDITION] No face detected? " + noFace);
        }
        return noFace ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsNoFaceDetected();
    }
}


// Checks if any face is detected (gazeState != -1)
class IsFaceDetected extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean hasFace = getObject().gazeState != -1;
        if (getObject().debug) {
            System.out.println("[CONDITION] Face detected? " + hasFace);
        }
        return hasFace ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsFaceDetected();
    }
}

// Checks if work session has exceeded 1 hour
class IsWorkSessionExpired extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean expired = getObject().isWorkSessionExpired();
        if (getObject().debug) {
            System.out.println("[CONDITION] Work session expired? " + expired);
        }
        return expired ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsWorkSessionExpired();
    }
}

// ============================================================
// ACTION TASKS
// ============================================================

// Continuous voice listener (always running, non-blocking)
// This just ensures voice detection is active - actual detection is background thread
class ContinuousVoiceListener extends LeafTask<Blackboard> {
    private boolean started = false;
    
    @Override
    public Status execute() {
        if (!started) {
            System.out.println("[VOICE] Starting voice listener");
            getObject().robot.startListening();
            started = true;
        }
        // Voice detection runs in background thread - just return success
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ContinuousVoiceListener();
    }
}



// Capture image from camera
class CaptureImage extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        byte[] frame = getObject().robot.captureFrame();
        if (frame != null) {
            Blackboard bb = getObject();
            bb.lastFrame = frame;
            return Status.SUCCEEDED;
        }

        System.out.println("[IMAGE] Failed to capture frame");
        return Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new CaptureImage();
    }
}



// Send frame to Python server and update gaze state
class UpdateGazeState extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        byte[] frame = bb.lastFrame;
        if (frame == null) return Status.FAILED;
        
        int result = bb.gazeClient.updateAndGetDistracted(frame);
        bb.gazeState = result;
        bb.isDistracted = (result == 1);
        
        return result != -1 ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new UpdateGazeState();
    }
}



// Play speech using TTS
class Speak extends LeafTask<Blackboard> {
    private final String text;
    
    public Speak(String text) {
        this.text = text;
    }
    
    @Override
    public Status execute() {
        getObject().robot.speak(text);
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new Speak(text);
    }
}



// Set voice tone based on distraction count
class SetVoiceToneByCount extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        int count = bb.distractionCount;
        
        String tone;
        if (count < 2) tone = "mild";
        else if (count < 4) tone = "moderate";
        else tone = "angry";
        
        bb.robot.setVoiceTone(tone);
        System.out.println("[TONE] Set to " + tone);
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new SetVoiceToneByCount();
    }
}



// Wait specified seconds (non-blocking)
class WaitSeconds extends LeafTask<Blackboard> {
    private final int seconds;
    private long startTime = 0;
    
    public WaitSeconds(int seconds) {
        this.seconds = seconds;
    }
    
    @Override
    public void start() {
        super.start();
        startTime = System.currentTimeMillis();
    }
    
    @Override
    public Status execute() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= seconds * 1000L) {
            return Status.SUCCEEDED;
        }
        return Status.RUNNING;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new WaitSeconds(seconds);
    }
}



// Reset work session parameters
class ResetWorkSession extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().resetSession();
        System.out.println("[SESSION] Work session reset");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ResetWorkSession();
    }
}



// Set work session active flag
class SetWorkSessionActive extends LeafTask<Blackboard> {
    private final boolean active;
    
    public SetWorkSessionActive(boolean active) {
        this.active = active;
    }
    
    @Override
    public Status execute() {
        getObject().workSessionActive = active;
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new SetWorkSessionActive(active);
    }
}



// Increment distraction counter
class IncrementDistractionCount extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().distractionCount++;
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IncrementDistractionCount();
    }
}



// Reset distraction timer (clear state)
class ResetDistractionTimer extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        bb.timeWhenLastNotDistracted = Instant.now().toEpochMilli();
        bb.timeSinceNotDistracted = 0;
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ResetDistractionTimer();
    }
}



// Police lights effect
class PoliceLights extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.policeLights();
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new PoliceLights();
    }
}



// Point at user
class PointAtUser extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.pointAtUser();
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new PointAtUser();
    }
}



// Point at screen
class PointAtScreen extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.pointAtScreen();
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new PointAtScreen();
    }
}



// Shutdown robot
class ShutdownRobot extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.shutdown();
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new ShutdownRobot();
    }
}

// ============================================================
// BREAK-RELATED ACTIONS
// ============================================================

class NodHead extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().robot.nod();
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
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new RaiseHead();
    }
}



class StartBreakTimer extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        getObject().startBreak();
        System.out.println("[BREAK] Timer started (15 minutes)");
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new StartBreakTimer();
    }
}



// Update LED color based on break time remaining
class UpdateBreakLEDs extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        Blackboard bb = getObject();
        if (!bb.inBreak) return Status.SUCCEEDED;
        
        long remainingMs = bb.getBreakTimeRemainingMs();
        float percentRemaining = (float) remainingMs / (bb.breakMinutes * 60 * 1000f);
        
        if (percentRemaining > 0.66f) {
            bb.robot.setLEDColor(0, 255, 0);  // Green
        } else if (percentRemaining > 0.33f) {
            bb.robot.setLEDColor(255, 255, 0); // Yellow
        } else {
            bb.robot.setLEDColor(255, 0, 0);   // Red
        }
        
        return Status.SUCCEEDED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new UpdateBreakLEDs();
    }
}



class IsNotInBreak extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean notInBreak = !getObject().inBreak;
        if (getObject().debug) {
            System.out.println("[CONDITION] Not in break? " + notInBreak);
        }
        return notInBreak ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsNotInBreak();
    }
}



class IsBreakComplete extends LeafTask<Blackboard> {
    @Override
    public Status execute() {
        boolean complete = getObject().isBreakComplete();
        return complete ? Status.SUCCEEDED : Status.FAILED;
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new IsBreakComplete();
    }
}

// ============================================================
// DECORATORS
// ============================================================

// Repeat child forever (infinite loop)
class RepeatForever extends Decorator<Blackboard> {
    @Override
    public void childSuccess(Task<Blackboard> runningTask) {
        running(); // Restart
    }
    
    @Override
    public void childFail(Task<Blackboard> runningTask) {
        running(); // Restart on fail too
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new RepeatForever();
    }
}



class RepeatUntilSuccess extends Decorator<Blackboard> {
    @Override
    public void start() {
        super.start();
    }

    @Override
    public void childSuccess(Task<Blackboard> runningTask) {
        success();  // Child succeeded, so decorator succeeds
    }
    
    @Override
    public void childFail(Task<Blackboard> runningTask) {
        running();  // Child failed, try again
    }
    
    @Override
    protected Task<Blackboard> copyTo(Task<Blackboard> task) {
        return new RepeatUntilSuccess();
    }
}