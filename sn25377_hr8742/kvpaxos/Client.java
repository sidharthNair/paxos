package kvpaxos;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Client {
    String[] servers;
    int[] ports;

    // Your data here

    public Client(String[] servers, int[] ports) {
        this.servers = servers;
        this.ports = ports;
        // Your initialization code here
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
        KVPaxosRMI stub;
        try {
            Registry registry = LocateRegistry.getRegistry(this.ports[id]);
            stub = (KVPaxosRMI) registry.lookup("KVPaxos");
            if (rmi.equals("Get"))
                callReply = stub.Get(req);
            else if (rmi.equals("Put")) {
                callReply = stub.Put(req);
            } else
                System.out.println("Wrong parameters!");
        } catch (Exception e) {
            return null;
        }
        return callReply;
    }

    // RMI handlers
    public Integer Get(String key) {
        Integer value;
        Request req = new Request(key);
        // Infinite loop over servers until get request has been completed
        for (int i = 0;; i = (i + 1) % this.servers.length) {
            Response resp = this.Call("Get", req, i);
            if (resp == null || !resp.ack) {
                // Call timed out or request was rejected
                continue;
            }
            value = resp.value;
            break;
        }
        return value;
    }

    public boolean Put(String key, Integer value) {
        boolean put;
        Request req = new Request(key, value);
        // Infinite loop over servers until put request has been completed
        for (int i = 0;; i = (i + 1) % this.servers.length) {
            Response resp = this.Call("Put", req, i);
            if (resp == null || !resp.ack) {
                // Call timed out or request was rejected
                continue;
            }
            put = resp.ack;
            break;
        }
        return put;
    }
}
