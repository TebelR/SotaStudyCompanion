package a4;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task.Status;

public class SotaFocusCompanion {

    private static final boolean DEBUG = true; // Set to true to enable debug prints

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");// Using headless mode to avoid library issues on Sota
        Blackboard bb = new Blackboard(DEBUG);
        BehaviorTree<Blackboard> tree = SotaBehaviorTree.createTree(bb);
        
        final int MAX_TICKS = 100000;
        int ticks = 0;
        
        System.out.println("\n=== Sota Focus Companion Started ===\n");
        
        while (ticks < MAX_TICKS && tree.getStatus() != Status.SUCCEEDED) {
            tree.step();
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ticks++;
        }
        
        bb.cleanup();
        System.out.println("\n=== Sota Focus Companion Finished ===\n");
    }
}
