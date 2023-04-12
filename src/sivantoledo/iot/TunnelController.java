package sivantoledo.iot;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;

import com.amazonaws.services.iot.client.AWSIotMqttClient;
import com.amazonaws.services.iot.client.AWSIotException;
import com.amazonaws.services.iot.client.AWSIotMessage;
import com.amazonaws.services.iot.client.AWSIotQos;
import com.amazonaws.services.iot.client.AWSIotTimeoutException;
import com.amazonaws.services.iot.client.AWSIotTopic;
//import com.amazonaws.services.iot.client.sample.sampleUtil.CommandArguments;
//import com.amazonaws.services.iot.client.sample.sampleUtil.SampleUtil;
//import com.amazonaws.services.iot.client.sample.sampleUtil.SampleUtil.KeyStorePasswordPair;

import sivantoledo.ssh.Tunnel;

/**
 * This is an example that uses {@link AWSIotMqttClient} to subscribe to a topic and
 * publish messages to it. Both blocking and non-blocking publishing are
 * demonstrated in this example.
 */
public class TunnelController {
  
  private static final AWSIotQos QOS = AWSIotQos.QOS1;
  
  private static AtomicBoolean state = new AtomicBoolean();
  private static AtomicInteger port  = new AtomicInteger(-1);
  private static Thread mainThread = null;
  
  private static class TopicListener extends AWSIotTopic {

    public TopicListener(String topic, AWSIotQos qos) {
        super(topic, qos);
    }

    @Override
    public void onMessage(AWSIotMessage message) {
      String m = message.getStringPayload();
      System.out.printf("<<< %s: %s\n", this.topic, m);
      if (m.startsWith("connected"))       {
        port.set(-1);
        for (String s: m.split("\\Q|\\E")) {
          System.out.printf("  split %s\n", s);
          if (s.startsWith("port=")) {
            try { port.set(Integer.parseInt(s.substring(5))); } catch (NumberFormatException nfe) {}
          }
        }
        state.set(true);
      }
      if (m.contentEquals("disconnected")) state.set(false);
      
      mainThread.interrupt();
    }
  }



  //@AtlasCommand(command="cloud-tunnel-controller", help="tell a remote computer to establish an ssh tunnel to the cloud")
  public static void main(String[] args) {

    Properties props = new Properties();
    try {
      props.load(new FileInputStream("properties.txt"));
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return;
    }
    
    //String system       = props.getProperty("system");
    //String device     = props.getProperty("device");
    //String myClientId = "atlas."+system+"."+device;
    //String broker       = props.getProperty("broker");
    String broker       = props.getProperty("broker");
    String controlTopic = props.getProperty("control");
    String stateTopic   = props.getProperty("state");
    String myClientId   = props.getProperty("clientId");
    String certificate  = props.getProperty("certificate");
    String privateKey   = props.getProperty("privateKey");
    //String cert         = "cert.pem";
    //String key          = "private.key";

    String tunnelProxyHost   = props.getProperty("sshProxyHost");
    String tunnelProxyUser   = props.getProperty("sshProxyUser");
    String tunnelProxyKey    = props.getProperty("sshProxyKey");
    int    tunnelProxyPort   = Integer.parseInt( props.getProperty("sshProxyPort","-1") );
    
    //String sshUser           = props.getProperty("sshTargetUser");
    //String sshId             = props.getProperty("sshTargetKey");
    
    //if (args.length >= 3) sshUser = args[2];
    //if (args.length >= 4) sshId   = args[3];

    //Tunnel tunnel = new Tunnel(tunnelProxyHost, tunnelProxyUser, tunnelProxyPort /* -1 requests a random port */, tunnelProxyKey);
    
    //if (args.length < 1) {
    //  System.out.printf("you must supply at least one argument: connect/disconnect\n");
    //  return;
    //}

    if (args.length < 1) {
      System.out.printf("you must supply at least one argument: connect/disconnect\n");
      return;
    }
  
    boolean tunnelState;
    
    if (args[0].equalsIgnoreCase("connect")) tunnelState = true;
    else if (args[0].equalsIgnoreCase("disconnect")) tunnelState = false;
    else {
      System.out.printf("you must supply at least one argument: connect/disconnect\n");
      return;      
    }
    
    //if (tunnelState && args.length < 4) {
    //  System.out.printf("to connect supply more arguments: connect target ssh-user-name ssh-id-file\n");
    //  return;
    //}
    
    String sshUser = null;
    String sshId   = null;
    String remoteDevice = null;

    if (tunnelState) {
      if (args.length < 3) {
        System.out.printf("to connect specify user@target and key\n");
        return;
      } else {
        var splits = args[1].split("@");
        if (splits.length!=2) {
          System.out.printf("to connect specify user@target and key\n");
          return;
        }
        sshUser      = splits[0];
        remoteDevice = splits[1];
        sshId        = args[2];
      }
    }
    
    if (!tunnelState && args.length < 2) {
      System.out.printf("to disconnect specify target: disconnect target\n");
      return;
    } else {
      var splits = args[1].split("@");
      if (splits.length==2) {
        remoteDevice = splits[1]; // user@target
      } else {
      remoteDevice = splits[1];   // target without a user name and @
    }
  }

    //if (tunnelState && (sshUser==null || sshId==null)) {
    //  System.out.printf("to connect specify target and possibly user and key (or user and key via properties.txt): connect target ssh-user-name ssh-id-file\n");
    //  return;
    //}

    //String remoteDevice = args[1];

    //System.out.printf("system   = %s\n", system);
    System.out.printf("clientId = %s\n", myClientId);

    //String certificateFile = "sivantoledo-laptop.cert.pem";
    //String privateKeyFile  = "sivantoledo-laptop.private.key";
    
    controlTopic = controlTopic.replaceFirst("/[^/]*controller[^/]*/", "/"+remoteDevice+"/");
    stateTopic   = stateTopic.  replaceFirst("/[^/]*controller[^/]*/", "/"+remoteDevice+"/");
    System.out.printf("control = %s\n", controlTopic);
    System.out.printf("state   = %s\n", stateTopic);
    
    
   
    mainThread = Thread.currentThread();
    
    state.set(!tunnelState); // set to opposite value, to make sure we issue the command

    MQTTConnection connection;
    try {
      connection = new MQTTConnection(broker,myClientId,certificate,privateKey);
    } catch (GeneralSecurityException | IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return;
    }

    try {
      System.out.printf("MQTT connecting...\n");
      connection.awsIotClient.connect();
      System.out.printf("MQTT connected\n");
    } catch (AWSIotException ce) {
      System.err.printf("exception: %s\n", ce.getMessage());
    }

    AWSIotTopic topic = new TopicListener(stateTopic, QOS);
    try {
      System.out.printf("MQTT subscribing to %s\n",stateTopic);
      connection.awsIotClient.subscribe(topic, true);
      System.out.printf("MQTT subscribed to %s\n",stateTopic);
    } catch (AWSIotException ce) {
      System.err.printf("exception: %s\n", ce.getMessage());
    }

    try {      
      do {
        String payload =  tunnelState ? "connect" : "disconnect";
        System.out.printf(">>> %s: %s\n", controlTopic, payload);
        connection.awsIotClient.publish(controlTopic, payload);
        System.out.printf("... sent\n");
        
        try {
          Thread.sleep(5000);
        } catch (InterruptedException e) {
        }
      } while (state.get() != tunnelState);
      
      System.out.printf("!!! remote state consistent, exiting\n");
      connection.awsIotClient.disconnect();
      
      if (tunnelState && port.get()<0) {
        System.out.printf("connected but port number not provided\n");
      }
      if (tunnelState && port.get()>0) {

        //String sshUser      = args[2];
        //String sshId        = args[3];

        Tunnel tunnel = new Tunnel(tunnelProxyHost, tunnelProxyUser, port.get(), tunnelProxyKey);
        tunnel.writeScript(sshUser, sshId);
        //writeScript(system, sshUser);
      }
      
      } catch (AWSIotException ce) {
      System.err.printf("exception: %s\n", ce.getMessage());
    }
    
    System.exit(0); // not sure why threads remain
  }    
    
}