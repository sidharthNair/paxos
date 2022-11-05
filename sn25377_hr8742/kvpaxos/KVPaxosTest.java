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

        Client ck = new Client(peers, ports);
        int low = 0;
        int high = 3;
        Random rand = new Random();
        System.out.println("Test: Basic put/get ...");
        for (int i = 0; i < 100; i++) {
            String key = "test" + rand.nextInt(high - low) + low;
            Integer value = rand.nextInt(high - low) + low;
            map.put(key, value);
            ck.Put(key, value);
            check(ck, key, value);

        }
        ck.Put("app", 6);
        check(ck, "app", 6);
        ck.Put("a", 70);
        check(ck, "a", 70);

        System.out.println("... Passed");

    }

}
