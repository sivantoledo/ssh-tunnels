# SSH Tunneling to Connect to Remote Computers

[Sivan Toledo](https://www.cs.tau.ac.il/~stoledo), Tel Aviv University, 2023

This software is designed to provide a reliable, secure, low-cost, and easy-to-set-up mechanism to connect to a large number of remote computers using SSH connections. In particular, it works even if the remote computers are behind firewalls or NAT routers or any other mechanism that does not allow incoming connections. It does not rely on a VPN or dynamic DNS.

The mechanism does rely on a proxy or jump host with a fixed IP address that is not behind a firewall or NAT, and optionally on an Amazon Web Services (AWS) IoT Core broker, which is essentially a secure MQTT server. I use a AWS Lightsail virtual server as a proxy host, at a cost of $5 per month, but any virtual or physical server that can be accessed through SSH from the Internet will work. The cost of the AWS IoT Core service is about $0.05 (5 cents) per remote computer per year.

The mechanism also relies on GitHub, to transfer certificates and public keys between computers. This service is essentially free.

A single instance of this mechanism, called a *realm*, can support many separate groups of remote computers called *systems* that are managed by separate people or organization, with very little exposure of computers in one system to computers in the other systems.

AWS offers a similar service, called
[IoT secure tunneling](https://docs.aws.amazon.com/iot/latest/developerguide/secure-tunneling.html),
but it is somewhat expensive to use, 
[costing 1 USD for each connection to a device](https://aws.amazon.com/iot-device-management/pricing/)
(in 2023; the connection is valid for 12 hours).

Using a VPN, such as OpenVPN, is a plausible alternative to this mechanism.

## System Architecture

We distinguish between three groups of computers in this system. *Remote computers* are the computers that we need to occasionally connect to using SSH. These computers are typically physically remote and connected to the Internet through firewalls or routers that do not allow incoming connections. This is very common. The current version of the software only supports remote computers that run Linux, but it should not be difficult to extend the software to support Windows or MacOS. *Controlling computers*  are Windows or Linux laptops or desktops from which we need to establish connections to remote computers. A *proxy host* is a single Linux computer that is directly accessible through SSH from both the remote and the controlling computers. It is usually convenient to use a virtual cloud computer as the proxy host, but you can also use a computer on a home or corporate or institutional network that is accessible from the Internet (e.g., though port forwarding) and a fixed IP address or DNS name.  

The software supports operations with or without an AWS IoT Core broker (Essentially a secure MQTT broker). Using the broker makes the overall system more scalable (can support many more remote computers with a given proxy). Not using the proxy eliminates the need for an AWS account and makes the system a little easier to deploy. It also simplifies a little bit the configuration of new remote and controlling computers. A configuration without a broker is fine for managing a few systems each with tens of remote computers. Beyond that, a broker becomes very useful. You can start without broker and add it later.

Remote computers need to run continuously a program that accepts incoming connections. When a broker is used, it connects to an AWS IoT Core (MQTT) broker. This program uses this connection to listens for commands from controlling computers and to report back to they its SSH connection state. When a remote computer receives a `connect` command from a controlling computer that wants to connect to it, it established a *reverse SSH tunnel* to the proxy host. It reports back through the MQTT connection the port number of the tunnel on the proxy host. The controlling computer receives this information and issues an SSH command that established an SSH connection with the remote computer. When the user terminates the connection, the controlling computer sends a `disconnect` command to the remote computer, which causes the remote computer to take down the reverse SSH tunnel, to reduce resource consumption on the proxy host and on the network.

When a broker is not used, the program on remote computers simply starts a reverse SSH tunnel to the proxy.

A single proxy host with a single IoT Core broker form a *realm* that can serve several separate *systems*, each with its set of remote and controlling computers. The systems are effectively separated, in the sense that a contolling computer of one system cannot connect to remote computers of another system. However, it is possible to set up a single computer as a remote computer or a controller in more than one system. But such setups must be created explicitly and intentionally. We refer to the entire installation, which includes an AWS IoT Core and a proxy server, as a *realm*. The realm can support multiple systems.

The SSH connection between a controlling computer and a remote computer uses two auxiliary SSH connections. One is established from the remote computer to the proxy; this is the so-called reverse SSH tunnel. The other is established from the controlling computer to the proxy; it serves a forward SSH tunnel. The user name on the proxy for both connection is identical to the system's name.

Within each system, each remote or controlling computer must have a unique name. The system distinguishes between devices and controllers by their names; the name of a controlling computer must start with the word *controller*. The names need not be identical to the host names; they are internal to this tunneling mechanism. The only difference between remote and controlling computers is that controlling computers can send `connect` and `disconnecct` commands to remote computers in the system; remote computers cannot. In a configuration without a broker, the name of remote computers is always the numeric part of their hostname. So a remote computer with a hostname `atlas-333` will be named simply `333`.

In the rest of this document, we use as an example a system called `hula` that is part of the realm `atlas`, a remote computer that we name `333`, and a controlling computer that we name `controller-sivanlap`.

We begin with instructions to system administrators; individuals who manage a group of remote and controlling computers, using a realm that is already functional. The instructions for creating and managing realms appear later.

## Instructions for System Administrators

Once you have set up the remote and controlling computers, you will have a directory on the controlling computer with both the software and the configuration files to connect to remote computers. If this directory is `~/tunnel`, say, you issue the command `./tunnel.sh connect atlas@333` to connect to the remote computer. When you exit from the shell on the remote computer, the software tells the remote connection to close the SSH tunnel.

    sivan@sivanlap:~/tunnel$ ./tunnel.sh connect atlas@333
    >>> atlas/hula/primary/tunnel/control: connect
        sent
    <<< atlas/hula/primary/tunnel/state: connected|port=63943
        connected
        port=63943
    ssh starting connection to remote target
    
    Welcome to Ubuntu 20.04.5 LTS (GNU/Linux 5.4.0-144-generic x86_64)    
    atlas@tau-333 $
    ...
    atlas@tau-333 $ exit
        
    Connection to loaclhost closed.
    >>> atlas/hula/primary/tunnel/control: disconnect
        sent
    <<< atlas/hula/primary/tunnel/state: disconnected
        disconnected

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

#### Configure and Create SSH Keys.

Next, set up the directory for a particular device or controller and create authentication keys . The commands are exactly under Linux and Windows (with the exception of the script name, of course).

    ./tunnel.sh prepare hula 333
    ./tunnel.sh ssh-keygen

The first command creates a file called `properties.txt` that specifies all the details of the tunnel. The second generates an SSH key pair and uploads the public key as an issue to GitHub. You can also check if the files have been uploaded to GitHub by issuing the command

    ./tunnel.sh list

You should see two new files in the repository, entitled `333.hula.atlas.sshkey.pub` and `333.hula.atlas.x509.csr`.

Run similar commands on the controlling computer, giving it a name that starts with the word `controller`. In our example, the name is `controller-sivanlap`.

    tunnel.bat prepare hula controller-laptop
    tunnel.bat ssh-keygen
    
#### Create x509 Keys (Only if Using a Broker).

Connecting to the broker requires an x509 secret key and a certificate that is signed by AWS. The next command generates the key and uploads a certificate signing request file to GitHub.

    ./tunnel.sh x509-keygen

or

    tunnel.bat x509-keygen
    
    
#### Request Proxy Access and Certificates.

Let the realm administrator know that you have uploaded your public keys to GitHub (and if a broker is used, also your certificate request-files). He or she will add your public keys to the SSH proxy server, to allow your computers to establish tunnels. If using a broker, he or she will also create signed certificates and upload them back to GitHub. 

Once you receive notification that these steps have been completed, continue to the next stage.

The realm administrator might run the relevant operations manually and report back, or he or she may have set up automatic processing every certain amount of time, say every hour.

#### Download Public Keys and Test Access.

Next, test that both computers can create an SSH tunnel to the proxy host by running the following command (this tests that their public keys are trusted by the proxy):

    ./tunnel.sh ssh-test
    
You should get back from the server the name of the ATLAS system, `hula` for this example. 

Now install the public keys of controlling computers on remote computers. Installing public keys is optional. If you do not install them, you will be asked for the password whenever you connect to that remote computer. The public keys that the next command installs eliminate the need to type the password every time. To install the keys, run 

    ./tunnel.sh ssh-getpub

This downloads the public keys for all the controlling computers in your system from GitHub and adds them to `~/.ssh/authorized_keys`. 

The software will verify that you trust the GitHub user who uploaded the public keys to GitHub. If it encounters a key uploaded by an unverified user, it will ask you whether to use keys uploaded by this user:

    Permit user sivantoledo (Sivan Toledo) access to hula?
    
This check ensures that you only install trusted keys. The software asks this question only once for a given user and stores your answer in `permissions.txt`. You can edit or delete this file. 

#### Download Certificates (If Using a Broker).

To download the certificates that enable communication with the broker, run on both computers the command `x509-getcert`:
    
    ./tunnel.sh x509-getcert
    
#### Listen for Connection Requests.

Remote computers listen for `connect` and `disconnect` commands by running `./tunnel.sh listen` in the `tunnel` directory. Run it once interactively on each remote computer for testing. 

But you also must ensure that it runs continuously. On Ubuntu Linux, you can do that using the following `/etc/rc.local` script (also make sure it has execute permissions):

    #/bin/bash
    
    /sbin/start-stop-daemon 
      --start --background \
      --startas /bin/bash \
      --pidfile /tmp/ssh-tunnel.pid --make-pidfile \
      --chuid atlas --chdir /home/atlas/tunnel \
      -- \
      /home/atlas/tunnel/tunnel.sh listen
      
Recent versions of Ubuntu do not run `/etc/rc.local` automatically. To activate this mechanism, create a file /etc/systemd/system/rc-local.service containing

    [Unit]
    Description=/etc/rc.local Compatibility
    ConditionPathExists=/etc/rc.local
    
    [Service]
    Type=oneshot
    ExecStart=/etc/rc.local start
    TimeoutSec=0
    StandardOutput=/tmp/rc.local.output
    RemainAfterExit=yes
    
    [Install]
    WantedBy=multi-user.target
    Wants=network-online.target
    After=network-online.target

and then run the command 

    sudo systemctl enable rc-local.service

That's it. You should be all set to use the tunneling mechanism.

#### A Security Recommendation

If an attacker gains access to a controlling computer (e.g., somebody steals your laptop and is able to use it, or somebody hacks remotely into your desktop), they can use the tunneling mechanism to try to connect to remote computers. If connecting to remote computers does not require a password in addition to the private key of the controlling computer, it will difficult or impossible for the attacker to connect to remote computers. Therefore, we recommend that you configure remote computers so that they require a password to connect in addition to the private key. 

To do that, add the line

    AuthenticationMethods publickey,password

to `/etc/sshd/sshd_config` on remote computers and restart the SSH sevice using the command

    sudo service sshd restart
    
If you also want to allow password-only access (without a private key), use

    AuthenticationMethods publickey,password password
   
But be aware that this is less secure. We recommend that once the key+password method works, you disallow password-only access.

## Instructions for Realm Administrators

#### Building and Packaging the Software

To build the software (on Linux), install a Java Development Kit (JDK) and Maven, a build tool. On Ubuntu, you can do so with the commands:

    sudo apt install default-jdk
    sudo apt install maven

Once these are installed, run the script `build.sh` which runs Maven and builds the Java program `targets/sivantoledo.iot-1.0-jar-with-dependencies.jar`.

You then need to pack this file along with 

    tunnel.sh
    tunnel.bat
    system.txt
    
in an archive that your users will open in their `tunnel` directory. The instructions for preparing your realm's `system.txt` file follow.

#### Preparing the Configuration File for the Realm
     `

The settings for a realm (proxy host and IoT Core broker that serve multiple systems) are stored in a file `system.txt` that you need to include in the `tunnels.tgz` or `tunnels.zip` files that you distribute to your users. We will assume in the instructions below that your file has the following contents:

    REALM=atlas
    PROXY=tunnels.yourdomain.com
    ISSUES=githubuser/githubrepo
    CLIENTID_SUFFIX=.youdomain.com
    BROKER=xyz1w2abcdefg-ats.iot.eu-central-1.amazonaws.com
    ALLOCATION_START_RANDOM=61000
    
The last line defines the range of random port numbers that the system will use. The largest number used is always 65535. If you expect to have many simultaneous connections (hundreds) through the proxy, it makes sense to reduce `ALLOCATION_START_RANDOM`. If you do that, verify that the range of random ports is disjoint from the range of ephemeral ports specified in `/proc/sys/net/ipv4/ip_local_port_range`. These range of ephemeral ports can be changed using the command

    sudo sysctl -w net.ipv4.ip_local_port_range="16000 31999"
    
or 

    echo 16000 31999 > /proc/sys/net/ipv4/ip_local_port_range

If you do not plan to use a broker, you do not need a value for `BROKER` and `ALLOCATION_START_RANDOM` in `system.txt`, but you do need to define a port allocation method and its parameters. For our example, these would be:

    ALLOCATOR=hostname
    ALLOCATION_START_hula=61000
    ALLOCATION_END_hula=61999

The first line specifies that port numbers for remote computers are based on the numeric part of their host name. This number is added to a starting port number, which in this case is 61000 for the `hula` system. So a remote computer named `atlas-333` in this system would use port number 61333 on the proxy.

#### Set Up a Proxy Host

Decide which computer, physical or virtual, will serve as the proxy host and edit `system.txt` so that `PROXY` is set to its DNS name or IP address. It is best to dedicate a computer for this purpose, not to use a server that also has other functions, for isolation. This computer needs to run Linux, and it should have a directory, say `tunnels`, with the contents of `tunnels.tgz` (that is, with the same software that controlling and remote computers have).

Create a user for each system that you want to support on this proxy. The user name is the name of the system. To set up the system `hula`, run the command

    ./tunnel.sh proxy-prepare-system hula
    
This creates a new user, sets up its `~/.ssh` directory, and within it a file `authorized_keys`, to allow the tunnels to be created.

Add GitHub authentication by running 

    ./tunnel.sh gh-auth ghp_xDgdOfeda9TrJxwg3EpHzhfRpNlfJX3sMVcQ

(of course with your own personal access token).

#### Set Up a GitHub Repository for Public Keys and Certificates

Create a GitHub repo that will be used to upload and download public SSH keys, certificate signing requests, and certificates. In our example this repository is owned by GitHub user `githubuser` and is named `githubrepo`. In practice I use a repo called `issues`. This repository should normally contain no files and there is no need to clone it.

This repository can be public on GitHub, since there is not need for these files to be kept secret, but there is also no reason not to use a private repository. If you use a public repository, I recommend that you limit interaction with it to collaborators (under settings -> moderation options). This will ensure that other people won't be able to add issues.

Add the GitHub accounts of administrators of each system in the realm as collaborators for this repository (under the settings menu for the repository), so that they can add issues that contain public keys and certificate signing requests.

#### Set up a IoT Core (If Using a Broker)

If you decided to use a broker, create an AWS account if you do not have one and create an instance of *IoT Core*.

Copy the URL of the broker, also called it *endpoint*, to the `BROKER` variable in `system.txt`. The URL is available in the AWS web console under IoT Core -> Settings. 

Under the *Security* panel of IoT Core in the console, create two policies called `tunnel.target` and `tunnel.controller`. These define the permissions of each MQTT client. Use the files under `policies` in this repository as templated, but edit them to reflect the correct AWS resource names (ARNs). The resource names include the name of the AWS region and a long number that specifies your AWS account. In the sample files, this part of the ARNs is `eu-central-1:123456789012`. You must replace this part by the corresponding one for your account.

#### Set Up A Certificate Signing Environment (If Using a Broker)

On some computer, say your laptop, set up the environment required to sign certificates for remote and controlling computers. Unpack `tunnels.tgz` or `tunnels.zip` into some directory, say `tunnels`.

Add GitHub authentication by running 

    ./tunnel.sh gh-auth ghp_xDgdOfeda9TrJxwg3EpHzhfRpNlfJX3sMVcQ
   
Test this using the `./tunnel.sh list` command.
   
You also need AWS authentication. This can be done either by installing the AWS command-line tool (CLI) and issuing the `aws configure` command, or by directly editing `~/.aws/config` and `~/.aws/credentials`. See the [AWS documentation](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-quickstart.html) for details on how to obtain and specify the required information.

Test that the credentials are valid using the `./tunnel.sh list-certificates` command. Initially, it will not show any certificates, but it will demonstrate the ability to perform AWS operations.

That's it; your setup is now complete.

#### Adding SSH Public Keys to the Proxy Host

Once system administrators add SSH public keys to the GitHub issues repository, run on the proxy host the command 

    ./tunnel.sh proxy-update-pubs
    
It will download all the public keys and will add them to the `~/.ssh/authorized_keys` of the different systems. It will ask for permission when it encounters a new GitHub user. Normally the keys of each system should be uploaded by GitHub users that only manage that system. The permissions are stored in `permissions.txt` and can be easily edited.

#### Signing Certificates

Once system administrators add certificate signing requests to the issues repository, run on the signing computer the command 

    ./tunnel.sh x509-ghsign atlas

(the argumnet is the name of the realm). This command will download all the new certificate signing requests, will ask AWS IoT Core to sign them, and will uploaded the signed certificates back to the GitHub repository. 

## Analysis of Costs

I currently use an AWS Lightsail virtual computer for the proxy host. I use the second-cheapest option, at 5 USD/month, which includes the computer itself running 24/7 (with 1GB of RAM and a 1TB data transfer allowance). Even the cheaper option is probably good enough. Data transfers beyond the allowance are charged at the normal AWS data transfer rates, which are about 0.09 USD/GB for outgoing data and no charge for incoming data. 

Other cloud providers might have better deals; for example, [vulr.com](vultr.com) charges about the same for the virtual host, but much less for additional bandwidth (0.01 GB/month).

AWS charges for multiple aspects of IoT Core, including both bandwidth and connection minutes, but for our use case, the dominant cost is connectivity, at 0.08 USD per million minutes, which is about 0.05 USD per year per remote computer.


