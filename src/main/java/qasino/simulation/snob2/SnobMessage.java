package qasino.simulation.snob2;

import peersim.core.Node;
import qasino.simulation.rps.IMessage;

import java.util.List;

/**
 * Message containing the sample to exchange in Snob
 */
public class SnobMessage implements IMessage {

    private List<Node> sample;

    public SnobMessage(List<Node> sample) {
        this.sample = sample;
    }

    public Object getPayload() {
        return this.sample;
    }
}