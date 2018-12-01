package snob.simulation.observers.program;

import snob.simulation.observers.DictGraph;
import snob.simulation.observers.ObserverProgram;

/**
 * Created by julian on 4/29/15.
 */
public class DebugProgram implements ObserverProgram {

    public void tick(long currentTick, DictGraph observer) {
        if (currentTick > 0) {
            System.err.println(observer.countArcs());
        }
    }

    public void onLastTick(DictGraph observer) {

    }
}
