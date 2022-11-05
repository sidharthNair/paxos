package kvpaxos;

import java.io.Serializable;

/**
 * You may find this class useful, free to use it.
 */
public class Op implements Serializable {
    static final long serialVersionUID = 33L;
    int me;
    String op;
    int ClientSeq;
    String key;
    Integer value;

    public Op(int me, String op, int ClientSeq, String key, Integer value) {
        this.me = me;
        this.op = op;
        this.ClientSeq = ClientSeq;
        this.key = key;
        this.value = value;
    }

    public boolean equals(Op other) {
        return (this.me == other.me && this.op.equals(other.op) && this.ClientSeq == other.ClientSeq
                && this.key.equals(other.key) && this.value.equals(other.value));
    }
}
