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

A single proxy host with a single IoT Core broker can serve several separate *systems*, each with its set of remote and controlling computers. The systems are effectively separated, in the sense that a contolling computer of one system cannot connect to remote computers of another system. However, it is possible to set up a single computer as a remote computer or a controller in more than one system. But such setups must be created explicitly and intentionally. We refer to the entire installation, which includes an AWS IoT Core and a proxy server, as a *realm*. The realm can support multiple systems.

The SSH connection between a controlling computer and a remote computer uses two auxiliary SSH connections. One is established from the remote computer to the proxy; this is the so-called reverse SSH tunnel. The other is established from the controlling computer to the proxy; it serves a forward SSH tunnel. The user name on the proxy for both connection is identical to the system's name.

Within each system, each remote or controlling computer must have a unique name. The system distinguishes between devices and controllers by their names; the name of a controlling computer must start with the word *controller*. The names need not be identical to the host names; they are internal to this tunneling mechanism. The only difference between remote and controlling computers is that controlling computers can send `connect` and `disconnecct` commands to remote computers in the system; remote computers cannot.

In the rest of this document, we use as an example a system called `hula` that is part of the realm `atlas`, a remote computer that we name `972002000333`, and a controlling computer that we name `controller-sivanlap`.

### Instructions for End Users

Once you have set up the remote and controlling computers, you will have a directory on the controlling computer with both the software and the configuration files to connect to remote computers. If this directory is `~/tunnel`, say, you issue the following command to set up a connection to the remote computer

    sivan@sivanlap:~/tunnel$ ./tunnel.sh connect atlas@972002000333
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

You should be getting the software along with a configuration file packed as a compressed archive file `tunnels.tgz` for Linux computers or `tunnels.zip` for Windows. The mechanism for distributing these files are not part of this software.

Create a directory called `tunnel` and unpack the contents of the archive in it. 

You also need to have certain auxiliary packages installed. Under Linux, these are `ssh-client` (almost always already installed), `ssh-server` (for remote computers only), and `openssl`. On Windows, the SSH client utilities are normally already installed and you do not normally need the SSH server. There are many binary distributions of `openssl` for windows; I use the one from [https://www.firedaemon.com/get-openssl](https://www.firedaemon.com/get-openssl).

#### Login to GitHub

The software uses a GitHub repository to store public authentication keys, certificates, and certificate signing requests (never private keys). These small files are not stored in the repository as normal files, but as GitHub issues, which are not versioned. You will need a GitHub account to upload and download these files from GitHub.

On [github.com](https://github.com), create a personal access token (a classic token) with scopes for `repo`, `workflow`, and `read:org`. This is done under Settings -> Developer Settings -> Personal Access Tokens. Copy the token (a long weird string such as `ghp_xDgdOfeda9TrJxwg3EpHzhfRpNlfJX3sMVcQor`) and give in the `tunnel` directory the command 

    ./tunnel.sh gh-auth ghp_xDgdOfeda9TrJxwg3EpHzhfRpNlfJX3sMVcQ

or 

    tunnel.bat gh-auth ghp_xDgdOfeda9TrJxwg3EpHzhfRpNlfJX3sMVcQ

Test that the token works by issuing the command 

    ./tunnel.sh list
    
It should respond with your GitHub user name and a list of files stored as issues. 

    GitHub login <sivantoledo> email <stoledo@secure-mail.com>

The personal access token is stored in the file `tunnel/gh-token.txt`. Once you set up the mechanism and it works, you can let the token expire or you can delete this file. If you later need to access the repository again from this computer, simply run the `gh-auth` command again with a new token.

The same `tunnel.sh` or `tunnel.bat` script runs almost all the commands that this software supports.

#### Create Keys.

Next, set up the directory for a particular device or controller and create authentication keys . The commands are exactly under Linux and Windows (with the exception of the script name, of course).

    ./tunnel.sh prepare hula 972001333
    ./tunnel.sh ssh-keygen
    ./tunnel.sh x509-keygen
    
The first command creates a file called `properties.txt` that specifies all the details of the tunnel. The second generates an SSH key pair and uploads the public key as an issue to GitHub. The last generates an x509 secret key and a certificate signing request file and uploads the request file to GitHub. You can inspect `properties.txt`. You can also check if the files have been uploaded to GitHub by issuing the command

    ./tunnel.sh list

You should see two new files in the repository, entitled `972001333.hula.atlas.sshkey.pub` and `972001333.hula.atlas.x509.csr`.

Run similar commands on the controlling computer, giving it a name that starts with the word `controller`. In our example, the name is `controller-sivanlap`.

    tunnel.bat prepare hula controller-laptop
    tunnel.bat ssh-keygen
    tunnel.bat x509-keygen
    
#### Request Certificates and Proxy Access.

Let the system administrator of the realm know that you have uploaded your certificate request-files and public keys to GitHub. He or she will create signed certificates and upload them back to GitHub, and they will also add your public keys to the SSH proxy server, to allow your computers to establish tunnels. Once this stage has been completed, continue to the next stage.

The administrator might run the relevant operations manually and report back, or he or she may have set up automatic processing every certain amount of time, say every hour.

#### Download Certificates and Public Keys, Test Access.

Now run on both computers the command `x509-getcert`, to download the signed certificate file:
    
    ./tunnel.sh x509-getcert
    
Next, install the public keys of controlling computers on remote computers. Installing public keys is optional. If you do not install them, you will be asked for the password whenever you connect to that remote computer. The public keys that the next command installs eliminate the need to type the password every time. To install the keys, run 

    ./tunnel.sh ssh-getpub

This downloads the public keys for all the controlling computers in your system from GitHub and will add them to `~/.ssh/authorized_keys`. 

XXX The software will verify that you trust the GitHub user who uploaded the public to GitHub:Permit user sivantoledo (Sivan Toledo) access to hula?This check ensures that you only install keys that you really trust. The software asks this question only once for a given user and stores your answer in permissions.txt. You can edit or delete this file. 

Finally, all the computers in which you set up the tunneling mechanism, test that their private key allows connection to the proxy server in the cloud,./tunnel.sh ssh-testYou should get back from the server the name of the ATLAS system, hula for this example. Running this command also ensures that the server's fingerprint is added to ~/.ssh/known_hosts.

#### Configure the Software for the Remote or Controlling Computer






### Instructions for System Administrators

### Analysis of Costs

