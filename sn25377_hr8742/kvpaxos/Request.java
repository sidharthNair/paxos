package kvpaxos;

import java.io.Serializable;

/**
 * Please fill in the data structure you use to represent the request message for each RMI call.
 * Hint: Make it more generic such that you can use it for each RMI call.
 * Hint: Easier to make each variable public
 */
public class Request implements Serializable {
    static final long serialVersionUID = 11L;
    // Your data here
    String key;
    Integer value;

    // Your constructor and methods here

    // Get Request
    public Request(String key) {
        this.key = key;
        this.value = -1;
    }

    // Put Request
    public Request(String key, Integer value) {
        this.key = key;
        this.value = value;
    }
}
