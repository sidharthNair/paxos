package kvpaxos;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Random;

/**
 * This is a subset of entire test cases
 * For your reference only.
 */
public class KVPaxosTest {

    public void check(Client ck, String key, Integer value) {
        Integer v = ck.Get(key);
        assertTrue("Get(" + key + ")->" + v + ", expected " + value, v.equals(value));
    }

    @Test
    public void TestBasic() {
        final int npaxos = 5;
        String host = "127.0.0.1";
        String[] peers = new String[npaxos];
        int[] ports = new int[npaxos];

        Server[] kva = new Server[npaxos];
        for (int i = 0; i < npaxos; i++) {
            ports[i] = 1100 + i;
            peers[i] = host;
        }
        for (int i = 0; i < npaxos; i++) {
            kva[i] = new Server(peers, ports, i);
        }

        HashMap<String, Integer> map = new HashMap<String, Integer>();


        Client[] clients = new Client[5];
        for (int i = 0; i < clients.length; i++) {
            clients[i] = new Client(peers, ports);
        }

        System.out.println("Test: Basic put/get ...");

        Random rand = new Random();
        clients[rand.nextInt(clients.length)].Put("app", 6);
        clients[rand.nextInt(clients.length)].Put("a", 70);

        int low = 0;
        int high = 4;
        int iterations = 100;
        for (int i = 0; i < iterations; i++) {
            String key = "test" + (rand.nextInt(high - low) + low);
            Integer value = rand.nextInt(high - low) * 18 + low - 13;
            map.put(key, value);
            clients[rand.nextInt(clients.length)].Put(key, value);
            key = "test" + (rand.nextInt(high - low) + low);
            if (map.get(key) != null) {
                check(clients[rand.nextInt(clients.length)], key, map.get(key));
            }
            if (i % (iterations / ((ports.length / 2) + (ports.length % 2) - 1)) == 0) {
                // Kill a server
                int index = rand.nextInt(ports.length);
                // System.out.println("Killing " + index);
                ports[index] = 1;
            }

        }

        check(clients[rand.nextInt(clients.length)], "app", 6);
        check(clients[rand.nextInt(clients.length)], "a", 70);

        System.out.println("... Passed");

    }

}
