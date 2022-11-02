JAVAC = javac
JVM = java
TARGET = build/
MKTARGET = mkdir -p $(TARGET)

JAVAC_FLAGS = -g -d $(TARGET)
JAVAC_CP = -cp

ifdef OS
   	PATH_SEPARATOR = ;
else
	PATH_SEPARATOR = :
endif

JARS = junit-4.12.jar$(PATH_SEPARATOR)hamcrest-core-1.3.jar

PAXOS = paxos/*.java
KVPAXOS = kvpaxos/*.java

SRCS = $(PAXOS) $(KVPAXOS)

PAXOS_TEST = paxos.PaxosTest
KVPAXOS_TEST = kvpaxos.KVPaxosTest

MAIN = org.junit.runner.JUnitCore

.SUFFIXES : .class .java

all:
	$(MKTARGET) | $(JAVAC) $(JAVAC_FLAGS) $(JAVAC_CP) "$(JARS)" $(SRCS)

paxos:
	$(MKTARGET) | $(JAVAC) $(JAVAC_FLAGS) $(JAVAC_CP) "$(JARS)" $(PAXOS)

kvpaxos: all

testpaxos:
	$(JVM) $(JAVAC_CP) "$(JARS)$(PATH_SEPARATOR)$(TARGET)" $(MAIN) $(PAXOS_TEST)

testkvpaxos:
	$(JVM) $(JAVAC_CP) "$(JARS)$(PATH_SEPARATOR)$(TARGET)" $(MAIN) $(KVPAXOS_TEST)

test: testpaxos testkvpaxos

clean:
	rm -rf $(TARGET)

.PHONY: all paxos kvpaxos testpaxos testkvpaxos test clean