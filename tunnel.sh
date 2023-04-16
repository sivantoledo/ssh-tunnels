#!/bin/bash

# Copyright Sivan Toledo 2023

DIR=`pwd`
source system.txt

#if ! command -v gh help &> /dev/null
#then
#    echo "gh (github) command is missing"
#    echo "install the deb package from https://github.com/cli/cli/releases"
#    echo "sudo dpkg -i gh_*.deb"
#    echo ""
#    echo "then log in with a github user with issues permissions for $ISSUES"
#    echo ""
#    echo "gh auth login"
#    exit
#fi

if [ "$#" -lt 1 ]; then
    echo "$0 command ..."
    echo ""
    echo "  commands:"
    echo "    gh-auth TOKEN"
    echo "    prepare SYSTEM DEVICE"
    echo "    ssh-keygen"
    echo "    ssh-upload"
    echo "    ssh-getpub"
    echo "    x509-keygen"
    echo "    x509-upload"
    echo "    x509-getcert"
    echo "    "
    echo "    connect"
    echo "    disconnect"
    echo "    "
    echo "    list"
    echo "    delete ISSUE-NUMBER"
    echo "    "
    echo "    ssh-test"
    echo "    "
    echo "    listen"
    echo "    "
    echo "    proxy-update-pubs    (on ssh jump host; not for end users)"
    echo "    proxy-prepare-system (on ssh jump host; not for end users)"
    echo "    "
    echo "    x509-ghsign          (on computer with AWS credentials; not for end users)"
    exit
fi

COMMAND=$1

if [ $COMMAND == "gh-auth" ]; then
    if [ "$#" -lt 2 ]; then
      echo "$0 $COMMAND TOKEN"
      exit
    fi
    echo $2>gh-token.txt
    chmod 600 gh-token.txt
    #java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-auth $2
    exit
fi

if [ $COMMAND == "list" ]; then
    java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-list-issues $ISSUES $2
    #gh issue list --repo $ISSUES
    exit
fi

if [ $COMMAND == "delete" ]; then
    if [ "$#" -lt 2 ]; then
	echo "$0 $COMMAND issue-number"
	exit
    fi
    echo deleting issue number $2
    gh issue delete --repo $ISSUES $2
    exit
fi


if [ $COMMAND == "proxy-prepare-system" ]; then
    if [ "$#" -lt 2 ]; then
	echo "$0 $COMMAND system"
	exit
    fi
    SYSTEM=$2
    sudo useradd --create-home --user-group $SYSTEM

    sudo chmod 710 /home/$SYSTEM
    sudo mkdir                 /home/$SYSTEM/.ssh
    sudo touch                 /home/$SYSTEM/.ssh/authorized_keys
    sudo chown $SYSTEM:$SYSTEM /home/$SYSTEM/.ssh
    sudo chown $SYSTEM:$SYSTEM /home/$SYSTEM/.ssh/authorized_keys
    sudo chmod 700             /home/$SYSTEM/.ssh
    sudo chown 600             /home/$SYSTEM/.ssh/authorized_keys
    exit
fi

if [ $COMMAND == "proxy-update-pubs" ]; then
    sudo java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-ssh-authorize-proxy $ISSUES $2
    exit
fi

if [ $COMMAND == "proxy-update-pubs-xxx" ]; then
    #if [ `whoami` != "root" ]; then
    #   echo "You must run this command under sudo"
    #   exit
    #fi
    FILES=`gh issue list --repo $ISSUES | grep .sshkey.pub | cut -f 3`
    echo $FILES
    for FILE in $FILES
    do
	echo $FILE
	DEVICE=`echo $FILE | cut -d. -f 1`
	SYSTEM=`echo $FILE | cut -d. -f 2`
	REALM=`echo $FILE | cut -d. -f 3`
	ID=$DEVICE.$SYSTEM.$REALM
	echo $DEVICE $SYSTEM $REALM
	echo $ID

	if sudo [ ! -d /home/$SYSTEM ]; then
	    echo "User <$SYSTEM> does not exist, create it first with prepare-system"
	    exit
	fi
	if sudo [ ! -d /home/$SYSTEM/.ssh ]; then
	    echo "Directory /home/$SYSTEM/.ssh is missing, create it first"
	    exit
	fi
	if sudo [ ! -f /home/$SYSTEM/.ssh/authorized_keys ]; then
	    echo "File /home/$SYSTEM/.ssh/authorized_keys does not exist, create it first"
	    exit
	fi

	AUTH=/home/$SYSTEM/.ssh/authorized_keys
		
	echo "Downloading and installing public key $FILE for $ID"
	NUMBER=`gh issue list --repo $ISSUES | grep $FILE | cut -f 1`
	echo "File is stored in issue $NUMBER"
	# gh issue view --repo $ISSUES $NUMBER | grep "ssh-" | grep $ID > $FILE
	PUB=`gh issue view --repo $ISSUES $NUMBER | grep "ssh-" | grep $ID`
	echo "Downloaded public key: $PUB"

	#touch                      $AUTH
	#chmod 600                  $AUTH
	sudo sed -i '/^[[:space:]]*$/d' $AUTH
	sudo sed -i "/$ID/d"            $AUTH
	echo "$PUB" | sudo tee --append $AUTH

	#touch ~/.ssh/authorized_keys
	#chmod 600 ~/.ssh/authorized_keys
	#echo "$(grep -v $ID ~/.ssh/authorized_keys)" > ~/.ssh/authorized_keys
	#cat $FILE >> ~/.ssh/authorized_keys
	#echo "Added public key for $ID to ~/.ssh/authorized_keys"
    done
    exit
fi

if [ $COMMAND == "prepare" ]; then
    SYSTEM=$2
    DEVICE=$3

    if [[ $ALLOCATOR == hostname ]] && [[ ! ${DEVICE}x =~ .*controller.* ]]; then
	if [ "$#" -lt 2 ]; then
	    echo "$0 $COMMAND system"
	    exit
	fi
    
	VSTART=ALLOCATION_START_${SYSTEM}
	VEND=ALLOCATION_END_${SYSTEM}
	START=${!VSTART}
	END=${!VEND}
	if [[ $START =~ ^[1-9][0-9]*$ ]]; then
	    echo Port allocation for $SYSTEM starts at $START
	else
	    echo Missing port allocation for system $SYSTEM
	    exit
	fi
	if [[ $END =~ ^[1-9][0-9]*$ ]]; then
	    echo Port allocation for $SYSTEM ends at $END
	else
	    echo Missing port allocation for system $SYSTEM
	    exit
	fi
	if [[ `hostname` =~ [1-9][0-9]* ]]; then
	    echo Numeric part of hostname is ${BASH_REMATCH[0]}
	    DEVICE=${BASH_REMATCH[0]}
	else
	    echo Port allocation using hostname, but hostname contains no number, exiting
	    exit
	fi
	let sshProxyPort=$START+$DEVICE
	if [[ $sshProxyPort -gt $END ]]; then
	    echo Resulting port number $sshProxyPort exceeds allocation limit
	    exit
	fi
    else
	if [ "$#" -lt 3 ]; then
	    echo "$0 $COMMAND system device"
	    if [[ `hostname` =~ [1-9][0-9]* ]]; then
		echo The number ${BASH_REMATCH[0]} makes a good device name, hostname is `hostname`
	    fi
	    exit
	fi
        if [[ `hostname` =~ [1-9][0-9]* ]]; then
            if [ ${BASH_REMATCH[0]} != $DEVICE ]; then
		echo WARNING: device name is $DEVICE but hostname contains a number ${BASH_REMATCH[0]} that makes a good name
	    fi
        fi
	sshProxyPort=-1
    fi

    echo device.system = $DEVICE.$SYSTEM
    echo Proxy port = $sshProxyPort

    rm properties.txt
    touch properties.txt
    echo "REALM=$REALM"                                            >> properties.txt
    echo "SYSTEM=$SYSTEM"                                          >> properties.txt
    echo "DEVICE=$DEVICE"                                          >> properties.txt
    echo "broker=$BROKER"                                          >> properties.txt
    echo "control=$REALM/$SYSTEM/$DEVICE/tunnel/control"           >> properties.txt
    echo "state=$REALM/$SYSTEM/$DEVICE/tunnel/state"               >> properties.txt
    echo "clientId=$DEVICE.$SYSTEM.$REALM$CLIENTID_SUFFIX"         >> properties.txt
    echo "shortId=$DEVICE.$SYSTEM.$REALM"                          >> properties.txt
    echo "certificate=$DEVICE.$SYSTEM.$REALM.x509.cert"            >> properties.txt
    echo "certificateRequest=$DEVICE.$SYSTEM.$REALM.x509.csr"      >> properties.txt
    echo "privateKey=$DEVICE.$SYSTEM.$REALM.x509.key"              >> properties.txt
    echo "sshPrivateKey=$DEVICE.$SYSTEM.$REALM.sshkey"             >> properties.txt
    echo "sshPublicKey=$DEVICE.$SYSTEM.$REALM.sshkey.pub"          >> properties.txt
    echo "sshProxyHost=$PROXY"                                     >> properties.txt
    echo "sshProxyUser=$SYSTEM"                                    >> properties.txt
    echo "sshProxyKey=$DEVICE.$SYSTEM.$REALM.sshkey"               >> properties.txt
    echo "sshProxyPort=$sshProxyPort"                              >> properties.txt

    #rm loop.sh
    #touch loop.sh
    #chmod 700 loop.sh
    #echo "#!/bin/bash"                                 >> loop.sh
    #echo "while :"                                     >> loop.sh
    #echo "do"                                          >> loop.sh
    #echo "  java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar listen" >> loop.sh
    #echo "  sleep 10"                                  >> loop.sh
    #echo "done"                                        >> loop.sh
    exit
fi

if [ ! -f properties.txt ]; then
    echo "The file properties.txt is missing, create one with the prepare command."
    exit
fi

source properties.txt

echo "Running $COMMAND for device $DEVICE.$SYSTEM.$REALM"

if [ $COMMAND == "ssh-test" ]; then
  echo ssh $SYSTEM@$PROXY -i $sshPrivateKey "whoami"
  ssh $SYSTEM@$PROXY -i $sshPrivateKey "whoami"
  exit
fi

if [ $COMMAND == "ssh-keygen" ]; then
  echo "creating $sshPrivateKey with comment $shortId"
  ssh-keygen -N "" -f $sshPrivateKey -C $shortId
  java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-put-issue $ISSUES $sshPublicKey
  #gh issue create --repo $ISSUES --body-file $sshPublicKey --title $sshPublicKey
  #echo "Uploaded $sshPublicKey"
  exit
fi

if [ $COMMAND == "ssh-upload" ]; then
    java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-put-issue $ISSUES $sshPublicKey
    #gh issue create --repo $ISSUES --body-file $sshPublicKey --title $sshPublicKey
    #echo "Uploaded $sshPublicKey"
    exit
fi

if [ $COMMAND == "ssh-getpub" ]; then
    java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-ssh-authorize $ISSUES $SYSTEM $REALM
    exit
fi

if [ $COMMAND == "connect" ]; then
    if [ "$#" -lt 2 ]; then
	echo "$0 $COMMAND user@remote-computer"
	exit
    fi
    TARGET=$2

    if [ $ALLOCATOR == hostname ]; then
	VSTART=ALLOCATION_START_${SYSTEM}
	VEND=ALLOCATION_END_${SYSTEM}
	START=${!VSTART}
	END=${!VEND}

	if [[ $TARGET =~ ^[^@]+ ]]; then
	    USER=${BASH_REMATCH[0]}
	else
	    echo Argument should have the form user@remote-computer
	    exit
	fi

	if [[ $TARGET =~ [^@]+$ ]]; then
	    DEVICE=${BASH_REMATCH[0]}
	else
	    echo Argument should have the form user@remote-computer
	    exit
	fi
	
	if [[ $DEVICE =~ [1-9][0-9]* ]]; then
	    # all good
	    echo 
	else
	    echo Remote computer name should be a number with no leading zeros
	    exit
	fi
	let targetPort=$START+$DEVICE
	if [[ $targetPort -gt $END ]]; then
	    echo Resulting port number $targetPort exceeds allocation limit
	    exit
	fi

	ssh \
	    -o ProxyCommand=\"ssh -W %h:%p $SYSTEM@$sshProxyHost -i $sshProxyKey\" \
            -o StrictHostKeyChecking=no \
            -o UserKnownHostsFile=/dev/null \
            -i $sshPrivateKey \
	    $USER@localhost \
            -p $targetPort
    else
	echo "Trying to create reverse-SSH tunnel to $TARGET.SYSTEM.REALM" 
	java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar connect $TARGET $sshPrivateKey
    fi
    exit
fi

if [ $COMMAND == "listen" ]; then
    while :
    do
      if [ $sshProxyPort -eq -1 ]
      then
        java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar listen
      else
        ssh -o ServerAliveInterval=60 -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
            -4 -N -x \
            -R $sshProxyPort:localhost:22 \
            -i $sshProxyKey $sshProxyUser@$sshProxyHost
      fi
      sleep 10
    done
fi

if [ $sshProxyPort -gt -1 ]; then
	echo "ERROR: Command $COMMAND not known (or is disconnect or an x509 command, which are not required in this realm)
fi

if [ $COMMAND == "x509-keygen" ]; then
  echo openssl req -subj /CN=$clientId/OU=$DEVICE/O=$SYSTEM -newkey rsa:4096 -keyout $privateKey -nodes -out $certificateRequest -verbose
  openssl req -subj /CN=$clientId/OU=$DEVICE/O=$SYSTEM -newkey rsa:4096 -keyout $privateKey -nodes -out $certificateRequest -verbose
  java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-put-issue $ISSUES $certificateRequest
  #gh issue create --repo $ISSUES --body-file $certificateRequest --title $certificateRequest
  #echo "Uploaded $certificateRequest"
  exit
fi

if [ $COMMAND == "x509-upload" ]; then
  java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-put-issue $ISSUES $certificateRequest
  #gh issue create --repo $ISSUES --body-file $certificateRequest --title $certificateRequest
  #echo "Uploaded $certificateRequest"
  exit
fi

if [ $COMMAND == "x509-getcert" ]; then
    java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar gh-get-issue $ISSUES $certificate
    #NUMBER=`gh issue list --repo $ISSUES | grep $certificate | cut -f 1`
    #gh issue view --repo $ISSUES $NUMBER | awk 'BEGIN{P=0}/BEGIN CERTIFICATE/{P=1}{if (P==1) print $0;}/END CERTIFICATE/{P=0}' > $certificate
    #echo "Downloaded $certificate"
    exit
fi

# This command does not require properties.txt, but is an x509 command, so we put it here
if [ $COMMAND == "x509-ghsign" ]; then
    java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar sign-from-issues $ISSUES
    #gh issue list --repo $ISSUES
    exit
fi


if [ $COMMAND == "disconnect" ]; then
    if [ "$#" -lt 2 ]; then
	echo "$0 $COMMAND remote-computer"
	exit
    fi
    TARGET=$2
    echo "Trying to disconnect reverse-SSH tunnel to $TARGET.$SYSTEM.$REALM" 
    java -jar sivantoledo.iot-1.0-jar-with-dependencies.jar disconnect $TARGET 
    exit
fi


echo "ERROR: Command $COMMAND not known 
exit







