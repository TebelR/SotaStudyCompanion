package a4;

import com.badlogic.gdx.ai.btree.*;
import com.badlogic.gdx.ai.btree.branch.*;

public class SotaBehaviorTree {
    
    // ============================================================
    // GAZE MONITOR - Continuous frame capture and distraction update
    // Runs as a separate sequence in parallel with behavior responses
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createGazeMonitor() {
        return new Sequence<>(
            new CaptureImage(),           // Capture image through camera
            new UpdateGazeStatus()        // Send BGR bytes to Python server, and update distracted status in blackboard based on response
        );
    }
    
    // ============================================================
    // SHUTDOWN SEQUENCE
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createShutdownSequence() {
        return new Sequence<>(
            new ShutdownKeywordDetected(),  // "Done" is heard
            new PlaySpeech("Good job, you've worked hard. Let me know when you need help again!"),
            new ShutdownRobot()
        );
    }
    
    // ============================================================
    // BREAK REQUEST SEQUENCE
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createBreakRequestSequence() {
        return new Sequence<>(
            new IsWorkSessionActive(),      // Work session is active
            new BreakKeywordDetected(),     // "Break" is heard
            new InterruptWork(),            // Interrupt current work
            createBreakSubroutine()         // Break Subtree
        );
    }
    
    // ============================================================
    // WORK SESSION SEQUENCE (Main workflow)
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createWorkSessionSequence() {
        return new Sequence<>(
            createInitializationSubtree(),
            createCoreLoopSubtree()
        );
    }
    
    // ============================================================
    // BEHAVIOR SELECTOR - Chooses between shutdown, break, or work
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createBehaviorSelector() {
        return new Selector<>(
            createShutdownSequence(),       // Priority: Shutdown first
            createBreakRequestSequence(),   // Then break requests
            createWorkSessionSequence()     // Then normal work session
        );
    }
    
    // ============================================================
    // INITIALIZATION SUBTREE
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createInitializationSubtree() {
        return new Sequence<>(
            new ListenForVoice(3000),
            new WorkKeywordDetected(), // "Work" is heard
            new PlaySpeech("Sounds good, initiating work session, please focus on your screen now. Best of luck!"),
            new WaitSeconds(5),
            new InitializeWorkSession(),
            new PlaySpeech("Work session started! I'll help you stay focused.")
        );
    }
    
    // ============================================================
    // FOCUS RESPONSE - handles distraction detection and response
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createFocusResponse() {
        return new Selector<>(
            // User is distracted (for 3+ seconds)
            new Sequence<>(
                new IsDistracted(),              // Gaze deviation detected
                new IncrementDistractionCount(),
                new SelectAngerLevel(),          // Based on distraction count
                new PoliceLights(),
                new PointAtUser(),
                new PlaySpeech("Hey! Focus!"),
                new PointAtScreen(),
                new ResetDistractionTimer()
            ),
            // User is not distracted
            new Sequence<>(
                new IsFocused(),
                new ResetDistractionTimer()
            )
        );
    }
    
    // ============================================================
    // CORE WORK CYCLE - runs every 2 seconds
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createWorkCycle() {
        return new Sequence<>(
            createFocusResponse(),
            new WaitSeconds(2)
        );
    }
    
    // ============================================================
    // CORE LOOP SUBTREE - 1 hour work session
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createCoreLoopSubtree() {
        return new Selector<>(
            // Timeout reached (1 hour)
            new Sequence<>(
                new WorkSessionTimeout(),
                new PlaySpeech("You've worked for an hour! May I suggest taking a break?"),
                new SureKeywordDetected(),
                new SetWorkSessionActive(false),
                createBreakSubroutine()
            ),

            // Still within work session
            new Sequence<>(
                new IsWorkSessionActive(),
                new Loop(1800),  // Max 1 hour at 2 seconds per tick
                createWorkCycle()
            )
        );
    }
    
    // ============================================================
    // BREAK SUBROUTINE - complete break negotiation and timer
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createBreakSubroutine() {
        return new Sequence<>(
            new NodHead(),
            new PlaySpeech("Of course, how long do you want your break to be?"),
            new TiltHead(),
            
            // Listen for duration (with 10 second timeout)
            new Selector<>(
                // Successful duration extraction
                new Sequence<>(
                    new ListenForVoice(),// This listens for 10 seconds
                    new ExtractBreakMinutes(),
                    
                    // Validate duration    
                    new Selector<>(
                        // Invalid: > 60 minutes
                        new Sequence<>(
                            new IsBreakMinutesGreaterThan(60),
                            new PlaySpeech("That's so long, you might as well turn me off. Would you like a 15 minute break instead?"),
                            new ListenForVoice(5000),  // 5 second timeout
                            new Selector<>(
                                new Sequence<>(
                                    new IsKeywordDetected("no"),
                                    new ShutdownRobot()
                                ),
                                new Sequence<>(
                                    new PlaySpeech("Alright, seems like you've come to your senses! See you in 15 then!"),
                                    createTakeBreak(15)
                                )
                            )
                        ),
                        // Invalid: 0 minutes (joke)
                        new Sequence<>(
                            new IsBreakMinutesEqual(0),
                            new PlaySpeech("Very funny, it seems you are not taking me seriously! Back to work then!"),
                            new ResetWorkSession()
                        ),
                        // Valid: 1-60 minutes
                        new Sequence<>(
                            new IsBreakMinutesValid(),
                            createTakeBreakFromExtracted()
                        )
                    )
                ),
                // Timeout - no voice input
                new Sequence<>(
                    new PlaySpeech("It seems you no longer want a break. Let me know if you change your mind."),
                    new ResetWorkSession()
                )
            )
        );
    }
    
    // Take break with extracted minutes
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createTakeBreakFromExtracted() {
        return new Sequence<>(
            new PlaySpeech("Sounds good, see you in X minutes then."),
            createTakeBreak(-1)  // Use extracted minutes from blackboard
        );
    }
    
    // Take break with specified minutes
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createTakeBreak(int fixedMinutes) {
        return new Sequence<>(
            new StartBreak(fixedMinutes),
            new LowerHead(),
            
            // Break countdown loop
            new Loop(3600),  // Max 60 minutes
            new Sequence<>(
                new UpdateBreakLEDs(),// This will change the colour of the LED based on how much time is left in the break - green to yellow to red
                new WaitSeconds(1),
                new IsBreakComplete()
            ),
            
            new RaiseHead(),
            createResumeAfterBreakSubtree()
        );
    }
    
    // ============================================================
    // RESUME AFTER BREAK SUBTREE
    // ============================================================
    @SuppressWarnings("unchecked")
    private static Task<Blackboard> createResumeAfterBreakSubtree() {
        return new Sequence<>(
            // Try for 5 seconds to find face
            new Selector<>(
                new Sequence<>(
                    new RaiseHead(),
                    new UpdateGazeStatus(),
                    new PlaySpeech("Break is over! Time for more work. Please focus on your screen once again."),
                    new ResetWorkSession()
                ),
                new Sequence<>(
                    new PlaySpeech("Hmm seems like you're not around. I'm shutting down."),
                    new ShutdownRobot()
                )
            )
        );
    }
    
    // ============================================================
    // MAIN TREE CONSTRUCTION
    // ============================================================
    @SuppressWarnings("unchecked")
    public static BehaviorTree<Blackboard> createTree(Blackboard bb) {
        // Parallel node with SUCCEED_ON_ANY policy
        Task<Blackboard> root = new Parallel<>(Parallel.Policy.Sequence,
            createBehaviorSelector(),    // Behavioral responses (shutdown, break, work)
            createGazeMonitor()          // Continuous gaze monitoring (runs independently)
        );
        
        BehaviorTree<Blackboard> tree = new BehaviorTree<>(root);
        tree.setObject(bb);
        return tree;
    }
}

