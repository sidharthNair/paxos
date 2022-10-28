#!/bin/bash

javac -cp junit-4.12.jar:hamcrest-core-1.3.jar paxos/*.java kvpaxos/*.java
java -cp junit-4.12.jar:hamcrest-core-1.3.jar:paxos:. org.junit.runner.JUnitCore kvpaxos.KVPaxosTest