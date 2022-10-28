package paxos;

import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import java.util.Arrays;
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

    // Helper class to represent the peer's knowledge of the paxos instance
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

    // Maps sequence no. -> paxos instance
    HashMap<Integer, PaxosInstance> instances;

    // For transmitting values to the thread
    int seq;
    Object value;

    // For keeping track of the instances that are not needed any more
    // The lock is required since the array may be access and modified
    // by multiple threads at the same time. We could use this.mutex but
    // that is being used for the instances HashMap and we can get finer
    // locking granularity by keeping the accesses separate.
    ReentrantLock highestDoneMutex;
    int[] highestDone;

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

        this.highestDoneMutex = new ReentrantLock();
        this.highestDone = new int[this.peers.length];
        Arrays.fill(highestDone, -1);

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
        if (seq < this.Min()) {
            // Ignore paxos instances with seq < Min()
            return;
        }

        // HashMaps are not thread safe, so we need to use a lock when accessing
        this.mutex.lock();
        instances.put(seq, new PaxosInstance());
        this.seq = seq;
        this.value = value;
        this.mutex.unlock();

        Thread thread = new Thread(this);
        thread.start();
    }

    /**
     * Paxos initiator loop (pseudo-code)
     *
     *  proposer(v):
     *  while not decided: do
     *      choose n, unique and higher than any n seen so far
     *      send prepare(n) to all servers including self
     *      if prepare_ok(n, n_a, v_a) from majority then
     *          v' = v_a with highest n_a; choose own v otherwise
     *          send accept(n, v') to all
     *          if accept_ok(n) from majority then
     *              send deicded(v') to all
     *          end if
     *      end if
     *  end while
     */
    @Override
    public void run() {
        this.mutex.lock();
        int seq = this.seq;
        Object value = this.value;
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        int highestProposalSeen = -1;
        int numPeers = this.peers.length;

        while ((instance.status.state == State.Pending) && !this.isDead()) {
            // Compute next proposal; this formula computes proposals as follows:
            // P0: 0 3 6 ...
            // P1: 1 4 7 ...
            // P2: 2 5 8 ...
            int proposal = (highestProposalSeen % numPeers >= this.me)
                    ? (1 + highestProposalSeen / numPeers) * numPeers + this.me
                    : (highestProposalSeen / numPeers) * numPeers + this.me;

            int acks = 0;
            int largestAcceptedProposal = -1;
            Object valueToPropose = value;
            // Every request and response piggybacks the peer's highestDone value
            Request req = new Request(seq, proposal, value, this.highestDone[this.me]);
            Response resp;
            for (int i = 0; i < numPeers; i++) {
                resp = (i == this.me) ? this.Prepare(req) : this.Call("Prepare", req, i);
                if (resp == null) {
                    // Peer has "crashed"
                    continue;
                }
                // Update highestDone array with the response's piggybacked value
                this.updateHighestDone(i, resp.highestDone);
                if (!resp.ack) {
                    // Response was ignored, update highest seen proposal number
                    highestProposalSeen = Math.max(highestProposalSeen, resp.proposal);
                    continue;
                }
                acks++;
                if (resp.proposal > largestAcceptedProposal) {
                    // The peer has already accepted a value, and its larger than
                    // any accepted value we have seen so far. So we need to propose
                    // the accompanying value.
                    largestAcceptedProposal = resp.proposal;
                    valueToPropose = resp.value;
                }
            }

            if (!(acks > (numPeers / 2))) {
                // We did not get a majority of acks
                continue;
            }

            acks = 0;
            req = new Request(seq, proposal, valueToPropose, this.highestDone[this.me]);
            for (int i = 0; i < numPeers; i++) {
                resp = (i == this.me) ? this.Accept(req) : this.Call("Accept", req, i);
                if (resp == null) {
                    // Peer has "crashed"
                    continue;
                }
                // Update highestDone array with the response's piggybacked value
                this.updateHighestDone(i, resp.highestDone);
                if (!resp.ack) {
                    // Response was ignored, update highest seen proposal number
                    highestProposalSeen = Math.max(highestProposalSeen, resp.proposal);
                    continue;
                }
                acks++;
            }

            if (!(acks > (numPeers / 2))) {
                // We did not get a majority of acks
                continue;
            }

            for (int i = 0; i < numPeers; i++) {
                resp = (i == this.me) ? this.Decide(req) : this.Call("Decide", req, i);
                if (resp == null) {
                    // Peer has "crashed"
                    continue;
                }
                this.updateHighestDone(i, resp.highestDone);
            }
        }
    }

    /**
     * RMI Handler for prepare requests (pseudo-code)
     *
     *  acceptor's state:
     *  n_p (highest prepare seen)
     *  n_a, v_a (highest accept seen)
     *
     *  acceptor's prepare(n) handler:
     *  if n > n_p then
     *      n_p = n
     *      reply prepare_ok(n, n_a, v_a)
     *  else
     *      prepare_reject
     *  end if
     */
    public Response Prepare(Request req) {
        int seq = req.seq;
        // Update highestDone array with piggybacked value. Instead of piggybacking
        // the PID with the message we can compute it from the proposal number:
        // Request PID = Proposal Number % Number of Peers
        this.updateHighestDone(req.proposal % this.peers.length, req.highestDone);

        this.mutex.lock();
        if (!instances.containsKey(seq)) {
            instances.put(seq, new PaxosInstance());
        }
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        if (req.proposal > instance.highestPrepare) {
            // This is the highest proposal number we have seen, so we return a promise
            // to not acknowledge any proposals < this one
            instance.highestPrepare = req.proposal;
            return new Response(true, instance.highestAcceptedProposal, instance.highestAcceptedValue, this.highestDone[this.me]);
        }
        // Reject the proposal since it is <= the highest we have seen. The highest
        // proposal is piggybacked so the proposing peer can update its proposal number
        return new Response(false, instance.highestPrepare, null, this.highestDone[this.me]);
    }

    /**
     * RMI Handler for accept requests (pseudo-code)
     *
     *  acceptor's state:
     *  n_p (highest prepare seen)
     *  n_a, v_a (highest accept seen)
     *
     *  acceptor's accept(n, v) handler:
     *  if n >= n_p then
     *      n_p = n
     *      n_a = n
     *      v_a = v
     *      reply accept_ok(n)
     *  else
     *      accept_reject
     *  end if
     */
    public Response Accept(Request req) {
        int seq = req.seq;
        this.updateHighestDone(req.proposal % this.peers.length, req.highestDone);

        this.mutex.lock();
        if (!instances.containsKey(seq)) {
            instances.put(seq, new PaxosInstance());
        }
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        if (req.proposal >= instance.highestPrepare) {
            // The proposal is >= the highest prepare proposal we have seen. So we update
            // our paxos instance and return an accept response
            instance.highestPrepare = req.proposal;
            instance.highestAcceptedProposal = req.proposal;
            instance.highestAcceptedValue = req.value;
            return new Response(true, instance.highestAcceptedProposal, instance.highestAcceptedValue, this.highestDone[this.me]);
        }
        // Reject the accept request since it is less than the highest we have seen
        return new Response(false, instance.highestPrepare, null, this.highestDone[this.me]);
    }

    /**
     * RMI Handler for decide requests
     *
     * Here we simply update the peer's decided value and state for this paxos instance
     */
    public Response Decide(Request req) {
        int seq = req.seq;
        this.updateHighestDone(req.proposal % this.peers.length, req.highestDone);

        this.mutex.lock();
        if (!instances.containsKey(seq)) {
            instances.put(seq, new PaxosInstance());
        }
        PaxosInstance instance = instances.get(seq);
        this.mutex.unlock();

        // We will only receive a decide request if the sender has received a majority
        // of acknowledged accept requests. So it is safe to update our status to decided.
        instance.status.v = req.value;
        instance.status.state = State.Decided;

        return new Response(true, req.proposal, req.value, this.highestDone[this.me]);
    }

    /**
     * Helper function to update highestDone array and remove
     * any instances that should be forgotten after the update.
     */
    public void updateHighestDone(int index, int highestDone) {
        if (this.highestDone[index] == highestDone) {
            // No need to update the array
            return;
        }

        // Update array index
        this.highestDoneMutex.lock();
        this.highestDone[index] = highestDone;
        this.highestDoneMutex.unlock();

        // Get the minimum in the array
        int min = this.Min();

        // Discard all instances with seq < min
        this.mutex.lock();
        this.instances.entrySet().removeIf(entry -> (entry.getKey() < min));
        this.mutex.unlock();
    }

    /**
     * The application on this machine is done with
     * all instances <= seq.
     *
     * see the comments for Min() for more explanation.
     */
    public void Done(int seq) {
        // Update this peer's highestDone element
        this.highestDoneMutex.lock();
        this.highestDone[this.me] = Math.max(this.highestDone[this.me], seq);
        this.highestDoneMutex.unlock();
    }

    /**
     * The application wants to know the
     * highest instance sequence known to
     * this peer.
     */
    public int Max() {
        // Compute the max as the largest instance in instances
        int max = -1;

        this.mutex.lock();
        for (int seq : instances.keySet()) {
            max = Math.max(max, seq);
        }
        this.mutex.unlock();

        return max;
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
        // Compute the min as the smallest value in highestDone
        int min = Integer.MAX_VALUE;
        this.highestDoneMutex.lock();
        for (int i = 0; i < this.highestDone.length; i++) {
            min = Math.min(min, highestDone[i]);
        }
        this.highestDoneMutex.unlock();
        return min + 1;
    }

    /**
     * the application wants to know whether this
     * peer thinks an instance has been decided,
     * and if so what the agreed value is. Status()
     * should just inspect the local peer state;
     * it should not contact other Paxos peers.
     */
    public retStatus Status(int seq) {
        if (seq < this.Min()) {
            // Return forggoten for instances with seq < Min()
            return new retStatus(State.Forgotten, null);
        }

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
