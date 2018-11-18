package snob.simulation.cyclon;

import peersim.core.Node;
import snob.simulation.rps.IMessage;

import java.util.List;

/**
 * Message containing the sample to exchange in Snob
 */
public class CyclonMessage implements IMessage {

    private List<Node> sample;

    public CyclonMessage(List<Node> sample) {
        this.sample = sample;
    }

    public Object getPayload() {
        return this.sample;
    }
}
