package a4;

import com.badlogic.gdx.ai.btree.*;
import com.badlogic.gdx.ai.btree.branch.*;
import com.badlogic.gdx.ai.btree.leaf.Failure;

public class SotaBehaviorTree {
    
    // ============================================================
    // DISTRACTION HANDLING
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createCheckAccusation() {
        return new Sequence<>(
            new IsGazeDistracted(),
            new HasBeenDistractedForLong(),
            new IncrementDistractionCount(),
            new SetVoiceToneByCount(),
            new PoliceLights(),
            new PointAtUser(),
            new Speak("Hey! Focus!"),
            new PointAtScreen(),
            new ResetDistractionTimer()
        );
    }
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createWorkCycle() {
        return new Sequence<>(
            new IsWorkSessionActive(),
            
            new Selector<>(
                new Sequence<>(createCheckAccusation()),
                new Sequence<>(
                    new IsGazeFocused(),
                    new ResetDistractionTimer()
                )
            ),
            
            new WaitSeconds(2)
        );
    }
    
    // ============================================================
    // HOUR COMPLETE HANDLER
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createHourCompleteHandler() {
        return new Sequence<>(
            new IsWorkSessionExpired(),
            new IsNotInBreak(),
            new SetWorkSessionActive(false),
            new Speak("You've worked for an hour! May I suggest taking a break?"),
            new WaitSeconds(5),
            new Selector<>(
                new Sequence<>(
                    new HeardKeyword("yes"),
                    createBreakSubtree()
                ),
                new Sequence<>(  // Timeout if nothing heard or anything else is said
                    new IsWorkSessionNotActive(),// Mimicking the other pathway here to prevent this from triggering immediately after a break ends - user will silently return to work
                    new Speak("It seems you no longer want a break. Let me know if you change your mind."),
                    new ResetWorkSession(),
                    new Failure<>()// This is needed so that Sota does not shutdown
                )
            )
        );
    }
    
    // ============================================================
    // CORE WORK SESSION
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createCoreWorkSession() {
        RepeatForever workLoop = new RepeatForever();
        workLoop.addChild(new Selector<>(
            createHourCompleteHandler(),
            createWorkCycle()
        ));

        return workLoop;
    }
    
    // ============================================================
    // BREAK SUBTREE
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createActiveBreak() {
        // Create the LED update loop
        RepeatUntilSuccess ledLoop = new RepeatUntilSuccess();
        ledLoop.addChild(new Sequence<>(
            new UpdateBreakLEDs(),
            new WaitSeconds(1),
            new IsBreakComplete()
        ));
        
        return new Sequence<>(
            new LowerHead(),
            new StartBreakTimer(),
            ledLoop,  // This will repeat until IsBreakComplete succeeds
            new RaiseHead(),
            new Selector<>(
                new Sequence<>(
                    new IsNoFaceDetected(),
                    new Speak("Hmm seems like you're not around. I'm shutting down."),
                    new ShutdownRobot()
                ),
                new Sequence<>(
                    new IsFaceDetected(),
                    new Speak("Break is over! Time for more work. Please focus on your screen once again."),
                    new ResetWorkSession(),
                    new Failure<>()// This is needed so that Sota does not shutdown

                )
            )
        );
    }
    


    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createBreakSubtree() {
        return new Sequence<>(
            new NodHead(),
            new Speak("Of course, would you like to take a 15 minute break?"),
            new TiltHead(),
            new WaitSeconds(5),
            
            new Selector<>(
                new Sequence<>(
                    new HeardKeyword("yes"),
                    new Speak("Sounds good, see you in 15 minutes then."),
                    createActiveBreak()
                ),
                new Sequence<>(
                    new IsWorkSessionNotActive(), // This prevents this sequence from being played if the user has just finished a break - it just fails the selector silently
                    new Speak("It seems you no longer want a break. Let me know if you change your mind."),
                    new ResetWorkSession(),
                    new Failure<>()// This is needed so that Sota does not shutdown
                )
            )
        );
    }
    
    // ============================================================
    // WORK SESSION START
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createWorkStartSequence() {
        return new Sequence<>(
            new HeardKeyword("work"),
            new Speak("Sounds good, initiating work session, please focus on your screen now. Best of luck!"),
            new WaitSeconds(5),
            new ResetWorkSession(),
            new Speak("Work session started! I'll help you stay focused."),
            createCoreWorkSession()
        );
    }
    
    // ============================================================
    // SHUTDOWN PATH
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createShutdownPath() {
        return new Sequence<>(
            new IsWorkSessionActive(),
            new HeardKeyword("end"),
            new Speak("Good job, you've worked hard. Let me know when you need help again!"),
            new ShutdownRobot()
        );
    }
    
    // ============================================================
    // BREAK DURING WORK PATH
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createBreakDuringWorkPath() {
        return new Sequence<>(
            new IsWorkSessionActive(),
            new HeardKeyword("break"),
            new SetWorkSessionActive(false),
            createBreakSubtree()
        );
    }
    
    // ============================================================
    // TOP-LEVEL SELECTOR
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createBehaviorSelector() {
        return new Parallel<>(Parallel.Policy.Selector,
            createShutdownPath(),
            createBreakDuringWorkPath(),
            createWorkStartSequence()
        );
    }
    
    // ============================================================
    // CONTINUOUS MONITORING (runs in parallel)
    // ============================================================
    
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createContinuousMonitoring() {
        RepeatForever monitoringLoop = new RepeatForever();
        monitoringLoop.addChild(new Sequence<>(
            new CaptureImage(),
            new UpdateGazeState()
        ));
        
        return monitoringLoop;
    }
    
    // ============================================================
    // MAIN TREE
    // ============================================================
    
    @SuppressWarnings("unchecked")
    public static BehaviorTree<Blackboard> createTree(Blackboard bb) {
        Task<Blackboard> root = new Sequence<>(
            new ContinuousVoiceListener(),
            new Parallel<>(Parallel.Policy.Selector,
                createBehaviorSelector(),
                createContinuousMonitoring()
            )
            
        );
        
        BehaviorTree<Blackboard> tree = new BehaviorTree<>(root);
        tree.setObject(bb);
        return tree;
    }
}