# SSH Tunneling to Connect to Remote Computers

[Sivan Toledo](https://www.cs.tau.ac.il/~stoledo), Tel Aviv University, 2023

(Incomplete documentation)

This software is designed to provide a reliable, secure, low-cost, and easy-to-set-up mechanism to connect to a large number of remote computers using SSH connections. In particular, it works even if the remote computers are behind firewalls or NAT routers or any other mechanism that does not allow incoming connections. It does not rely on a VPN or dynamic DNS.

The mechanism does rely on a proxy or jump host with a fixed IP address that is not behind a firewall or NAT, and on an Amazon Web Services (AWS) IoT Core broker, which is essentially a secure MQTT server. I use a AWS Lightsail virtual server as a proxy host, at a cost of $5 per month, but any virtual or physical server that can be accessed through SSH from the Internet will work. The cost of the AWS IoT Core service is about $0.05 (5 cents) per remote computer per year.

The mechanism also relies on GitHub, to transfer certificates and public keys between computers. This service is essentially free.

AWS offers a similar service, called
[IoT secure tunneling](https://docs.aws.amazon.com/iot/latest/developerguide/secure-tunneling.html),
but it is somewhat expensive to use, 
[costing 1 USD for each connection to a device](https://aws.amazon.com/iot-device-management/pricing/)
(in 2023; the connection is valid for 12 hours).

Using a VPN, such as OpenVPN, is a plausible alternative to this mechanism.

### System Architecture

We distinguish between three groups of computers in this system. *Remote computers* are the computers that we need to occasionally connect to using SSH. These computers are typically physically remote and connected to the Internet through firewalls or routers that do not allow incoming connections. This is very common. The current version of the software only supports remote computers that run Linux, but it should not be difficult to extend the software to support Windows or MacOS. *Controlling computers*  are Windows or Linux laptops or desktops from which we need to establish connections to remote computers. A *proxy host* is a single Linux computer that is directly accessible through SSH from both the remote and the controlling computers. It is usually convenient to use a virtual cloud computer as the proxy host, but you can also use a computer on a home or corporate or institutional network that is accessible from the Internet (e.g., though port forwarding) and a fixed IP address or DNS name.  

Remote computers need to run continuously a program that connects to an AWS IoT Core (MQTT) broker. This program uses this connection to listens for commands from controlling computers and to report back to they its SSH connection state. When a remote computer receives a `connect` command from a controlling computer that wants to connect to it, it established a *reverse SSH tunnel* to the proxy host. It reports back through the MQTT connection the port number of the tunnel on the proxy host. The controlling computer receives this information and writes a script called `connect.sh` or `connect.bat` with an SSH command that established an SSH connection with the remote computer. When the tunnel is no longer needed, the controlling computer sends a `disconnect` command, which causes the remote computer to take down the reverse SSH tunnel, to reduce resource consumption on the proxy host and on the network.

A single proxy host with a single IoT Core broker can serve several separate *systems*, each with its set of remote and controlling computers. The systems are effectively separated, in the sense that a contolling computer of one system cannot connect to remote computers of another system. However, it is possible to set up a single computer as a remote computer or a controller in more than one system. But such setups must be created explicitly and intentionally.

The SSH connection between a controlling computer and a remote computer uses two auxiliary SSH connections. One is established from the remote computer to the proxy; this is the so-called reverse SSH tunnel. The other is established from the controlling computer to the proxy; it serves a forward SSH tunnel. The user name on the proxy for both connection is identical to the system's name.

Within each system, each remote or controlling computer must have a unique name. The system distinguishes between devices and controllers by their names; the name of a controlling computer must start with the word *controller*. The names need not be identical to the host names; they are internal to this tunneling mechanism. The only difference between remote and controlling computers is that controlling computers can send `connect` and `disconnecct` commands to remote computers in the system; remote computers cannot.

The user account on the remote computers is currently always the same and is known as the *realm*. (This might change into a more flexible configuration in the future.)

In the rest of this document, we use as an example a system called `tau` that is part of the realm `atlas`, a remote computer that we name `972002000333`, and a controlling computer that we name `controller-sivanlap`.


### Instructions for End Users

Once you have set up the remote and controlling computers, you will have a directory on the controlling computer with both the software and the configuration files to connect to remote computers. If this directory is `~/tunnel`, say, you issue the following command to set up a connection to the remote computer

    atlas@sivanlap:~/tunnel$ ./tunnel.sh connect 972002000333
    >>> atlas/hula/primary/tunnel/control: connect
        sent
    <<< atlas/hula/primary/tunnel/state: connected|port=63943
        connected
        port=63943
    atlas@sivanlap:~/tunnel$ ./connect.sh
    
    Welcome to Ubuntu 20.04.5 LTS (GNU/Linux 5.4.0-144-generic x86_64)
    
    atlas@tau-333 $

That's it. You are now connected through an SSH connection to the remote computer, whose host name appears to be `tau-333`. You can log out and reconnect by running `connect.sh` again. To take down the reverse SSH tunnel from the remote computer, run 

    atlas@sivanlap:~/tunnel$ ./tunnel.sh disconnect 972002000333
    >>> atlas/hula/primary/tunnel/control: disconnect
        sent
    <<< atlas/hula/primary/tunnel/state: disconnected
        disconnected

Running `connect.sh` now will fail to establish a connection to the remote computer. 

Now let's see how you set up the remote and controlling computers in the first place.

#### Get and Unpack the Software

#### Configure the Software for the Remote or Controlling Computer






### Instructions for System Administrators

### Analysis of Costs

