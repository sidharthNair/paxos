package paxos;

import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the response message for each RMI call.
 * Hint: You may need a boolean variable to indicate ack of acceptors and also you may need proposal number and value.
 * Hint: Make it more generic such that you can use it for each RMI call.
 */
public class Response implements Serializable {
    static final long serialVersionUID = 2L;
    // your data here
    boolean ack;
    int proposal;
    Object value;
    int highestDone;

    // Your constructor and methods here
    public Response(boolean ack, int proposal, Object value, int highestDone) {
        this.ack = ack;
        this.proposal = proposal;
        this.value = value;
        this.highestDone = highestDone;
    }
}
