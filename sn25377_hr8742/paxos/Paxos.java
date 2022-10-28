package paxos;

import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import java.util.HashMap;

/**
 * This class is the main class you need to implement paxos instances.
 */
public class Paxos implements PaxosRMI, Runnable {

    ReentrantLock mutex;
    String[] peers; // hostname
    int[] ports; // host port
    int me; // index into peers[]

    Registry registry;
    PaxosRMI stub;

    AtomicBoolean dead; // for testing
    AtomicBoolean unreliable; // for testing

    // Your data here

    public class PaxosInstance {

        int seq;
        retStatus status;
        int highestPrepare; // n_p
        int highestAcceptedProposal; // n_a
        Object highestAcceptedValue; // n_v

        public PaxosInstance() {
            this.status = new retStatus(State.Pending, null);
            this.highestPrepare = -1;
            this.highestAcceptedProposal = -1;
            this.highestAcceptedValue = null;
        }
    }

    HashMap<Integer, PaxosInstance> instances;

    int seq;
    Object value;

    /**
     * Call the constructor to create a Paxos peer.
     * The hostnames of all the Paxos peers (including this one)
     * are in peers[]. The ports are in ports[].
     */
    public Paxos(int me, String[] peers, int[] ports) {

        this.me = me;
        this.peers = peers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.dead = new AtomicBoolean(false);
        this.unreliable = new AtomicBoolean(false);

        // Your initialization code here
        this.instances = new HashMap<Integer, PaxosInstance>();
        this.seq = -1;
        this.value = null;

        // register peers, do not modify this part
        try {
            System.setProperty("java.rmi.server.hostname", this.peers[this.me]);
            registry = LocateRegistry.createRegistry(this.ports[this.me]);
            stub = (PaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("Paxos", stub);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Call() sends an RMI to the RMI handler on server with
     * arguments rmi name, request message, and server id. It
     * waits for the reply and return a response message if
     * the server responded, and return null if Call() was not
     * be able to contact the server.
     *
     * You should assume that Call() will time out and return
     * null after a while if it doesn't get a reply from the server.
     *
     * Please use Call() to send all RMIs and please don't change
     * this function.
     */
    public Response Call(String rmi, Request req, int id) {
        Response callReply = null;

        PaxosRMI stub;
        try {
            Registry registry = LocateRegistry.getRegistry(this.ports[id]);
            stub = (PaxosRMI) registry.lookup("Paxos");
            if (rmi.equals("Prepare"))
                callReply = stub.Prepare(req);
            else if (rmi.equals("Accept"))
                callReply = stub.Accept(req);
            else if (rmi.equals("Decide"))
                callReply = stub.Decide(req);
            else
                System.out.println("Wrong parameters!");
        } catch (Exception e) {
            return null;
        }
        return callReply;
    }

    /**
     * The application wants Paxos to start agreement on instance seq,
     * with proposed value v. Start() should start a new thread to run
     * Paxos on instance seq. Multiple instances can be run concurrently.
     *
     * Hint: You may start a thread using the runnable interface of
     * Paxos object. One Paxos object may have multiple instances, each
     * instance corresponds to one proposed value/command. Java does not
     * support passing arguments to a thread, so you may reset seq and v
     * in Paxos object before starting a new thread. There is one issue
     * that variable may change before the new thread actually reads it.
     * Test won't fail in this case.
     *
     * Start() just starts a new thread to initialize the agreement.
     * The application will call Status() to find out if/when agreement
     * is reached.
     */
    public void Start(int seq, Object value) {
        // Your code here
        System.out.println(this.me + " starting");
        this.mutex.lock();
        instances.put(seq, new PaxosInstance());
        this.seq = seq;
        this.value = value;
        this.mutex.unlock();

        Thread thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {
        this.mutex.lock();
        int seq = this.seq;
        Object value = this.value;
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        int highestProposalSeen = 0;
        int numPeers = this.peers.length;

        while ((instance.status.state == State.Pending) && !this.isDead()) {
            // Compute next proposal; this formula computes proposals as follows:
            // P0: 0 3 6 ...
            // P1: 1 4 7 ...
            // P2: 2 5 8 ...
            int proposal = highestProposalSeen % numPeers >= this.me ?
                (1 + highestProposalSeen / numPeers) * numPeers + this.me :
                (highestProposalSeen / numPeers) * numPeers + this.me;

            int acks = 0;
            int largestAcceptedProposal = -1;
            Object valueToPropose = value;
            Request req = new Request(seq, proposal, value);
            Response resp;
            for (int i = 0; i < numPeers; i++) {
                resp = (i == this.me) ?
                    this.Prepare(req) :
                    this.Call("Prepare", req, i);
                if (!resp.ack) {
                    // Response was ignored, update highest seen proposal number
                    highestProposalSeen = Math.max(highestProposalSeen, resp.proposal);
                    continue;
                }
                acks++;
                if (resp.proposal > largestAcceptedProposal) {
                    largestAcceptedProposal = resp.proposal;
                    valueToPropose = resp.value;
                }
            }

            if (!(acks > (numPeers / 2))) {
                continue;
            }

            acks = 0;
            req = new Request(seq, proposal, valueToPropose);
            for (int i = 0; i < numPeers; i++) {
                resp = (i == this.me) ?
                    this.Accept(req) :
                    this.Call("Accept", req, i);
                if (!resp.ack) {
                    // Response was ignored, update highest seen proposal number
                    highestProposalSeen = Math.max(highestProposalSeen, resp.proposal);
                    continue;
                }
                acks++;
            }

            if (!(acks > (numPeers / 2))) {
                continue;
            }

            for (int i = 0; i < numPeers; i++) {
                resp = (i == this.me) ?
                    this.Decide(req) :
                    this.Call("Decide", req, i);
            }
        }
    }

    // RMI handler
    public Response Prepare(Request req) {
        int seq = req.seq;

        this.mutex.lock();
        if (!instances.containsKey(seq)) {
            instances.put(seq, new PaxosInstance());
        }
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        if (req.proposal > instance.highestPrepare) {
            System.out.println(seq + ":" + this.me + " got higher " + req.proposal + " " + instance.highestPrepare);
            instance.highestPrepare = req.proposal;
            return new Response(true, instance.highestAcceptedProposal, instance.highestAcceptedValue);
        }
        System.out.println(seq + ":" + this.me + " rejecting " + req.proposal + " " + instance.highestPrepare);
        return new Response(false, instance.highestPrepare, null);
    }

    public Response Accept(Request req) {
        int seq = req.seq;

        this.mutex.lock();
        if (!instances.containsKey(seq)) {
            instances.put(seq, new PaxosInstance());
        }
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        if (req.proposal >= instance.highestPrepare) {
            instance.highestPrepare = req.proposal;
            instance.highestAcceptedProposal = req.proposal;
            instance.highestAcceptedValue = req.value;
            return new Response(true, instance.highestAcceptedProposal, instance.highestAcceptedValue);
        }
        return new Response(false, instance.highestPrepare, null);
    }

    public Response Decide(Request req) {
        int seq = req.seq;

        this.mutex.lock();
        if (!instances.containsKey(seq)) {
            instances.put(seq, new PaxosInstance());
        }
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        instance.status.v = req.value;
        instance.status.state = State.Decided;

        return new Response(true, req.proposal, req.value);
    }

    /**
     * The application on this machine is done with
     * all instances <= seq.
     *
     * see the comments for Min() for more explanation.
     */
    public void Done(int seq) {
        // Your code here
    }

    /**
     * The application wants to know the
     * highest instance sequence known to
     * this peer.
     */
    public int Max() {
        // Your code here
        return this.seq;
    }

    /**
     * Min() should return one more than the minimum among z_i,
     * where z_i is the highest number ever passed
     * to Done() on peer i. A peers z_i is -1 if it has
     * never called Done().

     * Paxos is required to have forgotten all information
     * about any instances it knows that are < Min().
     * The point is to free up memory in long-running
     * Paxos-based servers.

     * Paxos peers need to exchange their highest Done()
     * arguments in order to implement Min(). These
     * exchanges can be piggybacked on ordinary Paxos
     * agreement protocol messages, so it is OK if one
     * peers Min does not reflect another Peers Done()
     * until after the next instance is agreed to.

     * The fact that Min() is defined as a minimum over
     * all Paxos peers means that Min() cannot increase until
     * all peers have been heard from. So if a peer is dead
     * or unreachable, other peers Min()s will not increase
     * even if all reachable peers call Done. The reason for
     * this is that when the unreachable peer comes back to
     * life, it will need to catch up on instances that it
     * missed -- the other peers therefore cannot forget these
     * instances.
     */
    public int Min() {
        // Your code here
        return 0;
    }

    /**
     * the application wants to know whether this
     * peer thinks an instance has been decided,
     * and if so what the agreed value is. Status()
     * should just inspect the local peer state;
     * it should not contact other Paxos peers.
     */
    public retStatus Status(int seq) {
        this.mutex.lock();
        if (!instances.containsKey(seq)) {
            instances.put(seq, new PaxosInstance());
        }
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        return instance.status;
    }

    /**
     * helper class for Status() return
     */
    public class retStatus {
        public State state;
        public Object v;

        public retStatus(State state, Object v) {
            this.state = state;
            this.v = v;
        }
    }

    /**
     * Tell the peer to shut itself down.
     * For testing.
     * Please don't change these four functions.
     */
    public void Kill() {
        this.dead.getAndSet(true);
        if (this.registry != null) {
            try {
                UnicastRemoteObject.unexportObject(this.registry, true);
            } catch (Exception e) {
                System.out.println("None reference");
            }
        }
    }

    public boolean isDead() {
        return this.dead.get();
    }

    public void setUnreliable() {
        this.unreliable.getAndSet(true);
    }

    public boolean isunreliable() {
        return this.unreliable.get();
    }

}
