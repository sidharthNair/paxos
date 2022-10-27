# Paxos
The goal of this programming assignment is to learn the knowledge about the distributed consensus and fault-tolerance of servers that is based upon active replications. Specifically, you have to implement a fault-tolerant key-value store which stores same copy of key-value pairs in multiple servers by using Paxos to achieve concensus between servers. Assume that there are n servers that keep the same key-value pairs. After completing parts A and B, your final system will accept the following requests from a client:

- Put ⟨key⟩ ⟨value⟩ adds the key value pair into this store. key is type of String, value is type of Integer. Whenever a server receives a Put request from client, the server has to use Paxos to agree on the request to deliver.
- Get ⟨key⟩ returns the value associated with the specific key. If no such key exists, return null.

You should consult the paper, Paxos Made Simple by Leslie Lamport. You can also consult MIT Distributed System lab3 http://nil.csail.mit.edu/6.824/2015/labs/lab-3.html. Finally, starter code and a JUnit test suite is provided for you. Please be sure to download these resources from canvas before beginning - they contain functionality needed for the grader.
