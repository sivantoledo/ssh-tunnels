# SSH Tunneling to Connect to Remote Computers

[Sivan Toledo](https://www.cs.tau.ac.il/~stoledo), Tel Aviv University, 2023

(Incomplete documentation)

This software is designed to provide a reliable, secure, low-cost, and easy-to-set-up mechanism to connect to a large number of remote computers using SSH connections. In particular, it works even if the remote computers are behind firewalls or NAT routers or any other mechanism that does not allow incoming connections. It does not rely on a VPN or dynamic DNS.

The mechanism does rely on a proxy or jump host with a fixed IP address that is not behind a firewall or NAT, and on an Amazon Web Services (AWS) IoT Core broker, which is essentially a secure MQTT server. I use a AWS Lightsail virtual server as a proxy host, at a cost of $5 per month, but any virtual or physical server that can be accessed through SSH from the Internet will work. The cost of the AWS IoT Core service is about $0.05 (5 cents) per remote computer per year.

The mechanism also relies on GitHub, to transfer certificates and public keys between computers. This service is essentially free.

### System Architecture

We distinguish between three groups of computers in this system. *Remote computers* are the computers that we need to occasionally connect to using SSH. These computers are typically physically remote and connected to the Internet through firewalls or routers that do not allow incoming connections. This is very common. The current version of the software only supports remote computers that run Linux, but it should not be difficult to extend the software to support Windows or MacOS. *Controlling computers*  are Windows or Linux laptops or desktops from which we need to establish connections to remote computers. A *proxy host* is a single Linux computer that is directly accessible through SSH from both the remote and the controlling computers. It is usually convenient to use a virtual cloud computer as the proxy host, but you can also use a computer on a home or corporate or institutional network that is accessible from the Internet (e.g., though port forwarding) and a fixed IP address or DNS name.  

Remote computers need to run continuously a program that connects to an AWS IoT Core (MQTT) broker. This program uses this connection to listens for commands from controlling computers and to report back to they its SSH connection state. When a remote computer receives a `connect` command from a controlling computer that wants to connect to it, it established a *reverse SSH tunnel* to the proxy host. It reports back through the MQTT connection the port number of the tunnel on the proxy host. The controlling computer receives this information and writes a script called `connect.sh` or `connect.bat` with an SSH command that established an SSH connection with the remote computer. When the tunnel is no longer needed, the controlling computer sends a `disconnect` command, which causes the remote computer to take down the reverse SSH tunnel, to reduce resource consumption on the proxy host and on the network.



### Instructions for End Users



### Instructions for System Administrators

### Analysis of Costs

