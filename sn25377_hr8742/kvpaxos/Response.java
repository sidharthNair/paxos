package kvpaxos;

import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the response message for each RMI call.
 * Hint: Make it more generic such that you can use it for each RMI call.
 */
public class Response implements Serializable {
    static final long serialVersionUID = 22L;
    // your data here
    boolean ack;
    Integer value;

    // Your constructor and methods here

    // Get request response
    public Response(Integer value) {
        this.ack = true;
        this.value = value;
    }

    // Put request response
    public Response(boolean ack) {
        this.ack = ack;
        this.value = -1;
    }
}
