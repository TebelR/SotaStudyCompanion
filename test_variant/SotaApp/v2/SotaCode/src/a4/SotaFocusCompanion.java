package a4;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task.Status;

public class SotaFocusCompanion {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");// using headless mode to avoid library issues on Sota
        Blackboard bb = new Blackboard();
        BehaviorTree<Blackboard> tree = SotaBehaviorTree.createTree(bb);
        
        final int MAX_TICKS = 100000;
        int ticks = 0;
        
        System.out.println("\n=== Sota Focus Companion Started ===\n");
        
        while (ticks < MAX_TICKS && tree.getStatus() != Status.SUCCEEDED) {
            tree.step();
            
            try {
                Thread.sleep(100); // 10 Hz tick rate
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ticks++;
        }
        
        bb.cleanup();
        System.out.println("\n=== Sota Focus Companion Finished ===\n");
    }
}
