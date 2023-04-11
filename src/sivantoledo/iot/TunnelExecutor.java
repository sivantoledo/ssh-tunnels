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

import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.math.BigInteger;

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

public class TunnelExecutor {
  
  //private static final AWSIotQos QOS = AWSIotQos.QOS1;
  
  private static boolean state = false;
  private static Lock lock = new ReentrantLock();
  private static Condition cond;
  
  private static Tunnel tunnel;
  
  private static class TopicListener extends AWSIotTopic {

    public TopicListener(MQTTConnection connection, String controlTopic, String stateTopic, AWSIotQos qos) {
        super(controlTopic, qos);
    }

    @Override
    public void onMessage(AWSIotMessage message) {
      String m = message.getStringPayload();
        System.out.printf("<<< %s: %s\n", this.topic, m);
        if (m.contentEquals("connect") && !state) {
          System.out.printf("connecting...\n");
          tunnel.start();
          state = true;
        }
        if (m.contentEquals("disconnect") && state) {
          System.out.printf("disconnecting...\n");
          tunnel.stop();
          state = false;
        }

        lock.lock();
        cond.signalAll();
        lock.unlock();
    }
  }
  
  public static void main(String[] args) {

    
    Properties props = new Properties();
    try {
      props.load(new FileInputStream("properties.txt"));
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    
    String broker       = props.getProperty("broker");
    String certificate  = props.getProperty("certificate");
    String privateKey   = props.getProperty("privateKey");
    String controlTopic = props.getProperty("control");
    String stateTopic   = props.getProperty("state");
    String myClientId   = props.getProperty("clientId");

    String tunnelProxyHost   = props.getProperty("sshProxyHost");
    String tunnelProxyUser   = props.getProperty("sshProxyUser");
    String tunnelProxyKey    = props.getProperty("sshProxyKey");
    int    tunnelProxyPort   = Integer.parseInt( props.getProperty("sshProxyPort","-1") );

    tunnel = new Tunnel(tunnelProxyHost, tunnelProxyUser, tunnelProxyPort /* -1 requests a random port */, tunnelProxyKey);
    
    //System.out.printf("system   = %s\n", system);
    //System.out.printf("clientId = %s\n", myClientId);
    //System.out.printf("sshId = %s\n", sshId);
    //boolean tunnel   = options.getBoolean ("tunnel");

    //String certificateFile = "sivantoledo-laptop.cert.pem";
    //String privateKeyFile  = "sivantoledo-laptop.private.key";
    
    //String controlTopic =  "atlas/"+system+'/'+device+'/'+"tunnel/control";
    //String stateTopic = "atlas/"+system+'/'+device+'/'+"tunnel/state";
    
    cond = lock.newCondition();
    
    MQTTConnection connection;
    try {
      connection = new MQTTConnection(broker,myClientId,certificate,privateKey);
    } catch (GeneralSecurityException | IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return;
    }

    System.out.printf("MQTT starting...\n");

    try {
      System.out.printf("MQTT connecting...\n");
      connection.awsIotClient.connect();
      System.out.printf("MQTT connected\n");

      System.out.printf("MQTT subscribing to %s\n",controlTopic);    
      AWSIotTopic topic = new TopicListener(connection, controlTopic, stateTopic, AWSIotQos.QOS1);
      connection.awsIotClient.subscribe(topic, true);
      System.out.printf("MQTT subscribed to %s\n",controlTopic);    
    } catch (AWSIotException ce) {
      System.err.printf("exception: %s\n", ce.getMessage());
      return; 
    }

    while (true) {
      lock.lock();
      try {
        if (cond.await(60, TimeUnit.MINUTES))
          System.out.printf(">>> timeout, sending a keepalive message\n");
      } catch (InterruptedException e1) {        
      } finally {
        lock.unlock();
      }

      String payload =  state? String.format("connected|port=%d",tunnel.port()) : "disconnected";
      try {
        System.out.printf(">>> %s: %s\n", stateTopic, payload);
        connection.awsIotClient.publish(stateTopic, payload);
        System.out.printf("... sent\n");
      } catch (AWSIotException e) {
        e.printStackTrace();
      }

    }
  }    
    
  

  // continue listening
}