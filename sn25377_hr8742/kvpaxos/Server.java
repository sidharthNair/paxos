package kvpaxos;

import paxos.Paxos;
import paxos.State;
// You are allowed to call Paxos.Status to check if agreement was made.

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Server implements KVPaxosRMI {

    ReentrantLock mutex;
    Registry registry;
    Paxos px;
    int me;

    String[] servers;
    int[] ports;
    KVPaxosRMI stub;

    // Your definitions here
    AtomicInteger currentSeq;
    int processedSeq;
    HashMap<String, Integer> table;

    public Server(String[] servers, int[] ports, int me) {
        this.me = me;
        this.servers = servers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.px = new Paxos(me, servers, ports);

        // Your initialization code here
        this.currentSeq = new AtomicInteger(0);
        this.processedSeq = 0;
        this.table = new HashMap<String, Integer>();

        try {
            System.setProperty("java.rmi.server.hostname", this.servers[this.me]);
            registry = LocateRegistry.getRegistry(this.ports[this.me]);
            stub = (KVPaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("KVPaxos", stub);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized Integer processOp(Op op) {
        // Makes sure to process operations in order, just in case the
        // server has a get and put request made at the same time.
        while (this.processedSeq != op.ClientSeq) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                // This should never happen
                Thread.currentThread().interrupt();
                return null;
            }
        }

        Integer returnVal = null;
        if (op.op.equals("Get") && (this.me == op.me)) {
            // Skips overhead of looking up the key if the Get wasn't made by me
            returnVal = table.get(op.key);

            // System.out.println(this.me + " GET " + op.key + ":" + returnVal);
        } else if (op.op.equals("Put")) {
            returnVal = table.put(op.key, op.value);

            // System.out.println(this.me + " PUT " + op.key + ":" + op.value);
        }

        this.processedSeq++;
        this.notifyAll();
        return returnVal;
    }

    // RMI handlers
    public Response Get(Request req) {
        Integer value = -1;
        while (true) {
            Op op = new Op(this.me, "Get", this.currentSeq.getAndIncrement(), req.key, -1);
            this.px.Start(op.ClientSeq, op);
            Op result = wait(op.ClientSeq);
            // Process the operation
            value = this.processOp(result);
            if (result.equals(op)) {
                this.px.Done(op.ClientSeq);
                break;
            }
        }
        return new Response(value);
    }

    public Response Put(Request req) {
        while (true) {
            Op op = new Op(this.me, "Put", this.currentSeq.getAndIncrement(), req.key, req.value);
            this.px.Start(op.ClientSeq, op);
            Op result = wait(op.ClientSeq);
            // Process the operation
            this.processOp(result);
            if (result.equals(op)) {
                this.px.Done(op.ClientSeq);
                break;
            }
        }
        return new Response(true);
    }

    public Op wait(int seq) {
        int to = 10;
        while (true) {
            Paxos.retStatus ret = this.px.Status(seq);
            if (ret.state == State.Decided) {
                return Op.class.cast(ret.v);
            }
            try {
                Thread.sleep(to);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (to < 1000) {
                to = to * 2;
            }
        }
    }

}
