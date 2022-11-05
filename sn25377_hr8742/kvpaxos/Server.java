package kvpaxos;

import paxos.Paxos;
import paxos.State;
// You are allowed to call Paxos.Status to check if agreement was made.

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

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
    ArrayList<Op> paxosLog;

    public Server(String[] servers, int[] ports, int me) {
        this.me = me;
        this.servers = servers;
        this.ports = ports;
        this.mutex = new ReentrantLock();
        this.px = new Paxos(me, servers, ports);

        // Your initialization code here
        this.currentSeq = new AtomicInteger(0);
        this.paxosLog = new ArrayList<Op>();

        try {
            System.setProperty("java.rmi.server.hostname", this.servers[this.me]);
            registry = LocateRegistry.getRegistry(this.ports[this.me]);
            stub = (KVPaxosRMI) UnicastRemoteObject.exportObject(this, this.ports[this.me]);
            registry.rebind("KVPaxos", stub);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // RMI handlers
    public Response Get(Request req) {
        Integer value = -1;
        while (true) {
            Op op = new Op(this.me, "Get", this.currentSeq.getAndIncrement(), req.key, -1);
            this.px.Start(op.ClientSeq, op);
            Op result = wait(op.ClientSeq);
            this.paxosLog.add(result);
            if (result.equals(op)) {
                this.px.Done(op.ClientSeq);
                break;
            }
        }
        for (int i = this.paxosLog.size() - 1; i >= 0; i--) {
            Op op = this.paxosLog.get(i);
            if (op.op.equals("Put") && op.key.equals(req.key)) {
                value = op.value;
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
            this.paxosLog.add(result);
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
