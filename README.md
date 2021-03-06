---
layout: page
---

# What is Bitcoin-Akka ?

Bitcoin-akka is a minimalistic but nearly fully compliant implementation of the Bitcoin protocol in Scala using the Akka library.
It runs as a server node, playing a similar role to the reference implementation `Bitcoin Core`.

Before going further, here's the standard disclaimer:

> I claim no responsibility if you lose money by using this software. It is provided for educational purposes only.

Especially true since Bitcoin has real money equivalence, though the risk is limited by the absence
of wallet functionality.

That being said, care has been taken in replicating the same behavior as of Bitcoin Core. Bitcoin-akka passes the same [regression tests][1]
and leverage the consensus library from [Bitcoin Core][2] for transaction verification. 

Nonetheless, it remains an accademic/educational project aiming to demonstrate the principles of functional programming and how to apply them
in a concrete environment without a large amount of code (BTC-akka has below 1700 LOC) and run on Windows, Linux and Mac OS.

As for the features, here's a short list:

- Automatically synchronizes with the blockchain using headers first and parellel block download
- Maintain database of unspent outputs: verifies and relays unconfirmed transactions
- Import/Export blockchain
- Serves headers and blocks - other nodes can synchronize the blockchain from Bitcoin-Akka

The project is structured as a tutorial with each step associated with a page and a commit. There are 4 milestones before
the end:

- "handshake": It connects to a peer and completes the handshake phase. The two nodes are ready to communicate,
- "download": It requests the blockchain and can download headers/blocks,
- "persist": It keeps the data on disk and can be restarted without repeating the same tasks,
- "validate": It checks that the data is correct per protocol rules,
- "final": It is finished!

At the bottom of every page, you have a commit hash. I recommend that you sync the project to it because I won't
necessarily cover every aspect of the commit. The project should always build but it may not do anything useful yet.

[1]: https://github.com/TheBlueMatt/test-scripts
[2]: https://github.com/bitcoin/bitcoin
