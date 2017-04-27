# **Chord P2P Protocol**

The project is an implementation of a Peer-to-peer protocol Chord. In this protocol a distributed hash table stores key-value pairs by assigning keys to different computers (known as "nodes"); a node will store the values for all the keys for which it is responsible. Chord specifies how keys are assigned to nodes, and how a node can discover the value for a given key by first locating the node responsible for that key. You can read more about the protocl [here](https://en.wikipedia.org/wiki/Chord_(peer-to-peer)).
The implementation of Chord protocol can be found in chord directory. The implementation of chord protocol with failure tolerance can be found in the chord-stable directory.

	To run the project type the following command in terminal:
	sbt "run <number of  atcive nodes> <number of messages>"

Number of active Nodes can be any non negative Integer.
Number of messages also can be any non neagative Integer.

The otput of the network is the average Hops required per message passed in the network.
