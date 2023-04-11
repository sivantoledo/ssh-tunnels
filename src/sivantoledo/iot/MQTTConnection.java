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
//import java.util.concurrent.atomic.AtomicBoolean;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
//import java.io.InputStream;
import java.math.BigInteger;

import com.amazonaws.services.iot.client.AWSIotMqttClient;
//import com.amazonaws.services.iot.client.AWSIotException;
//import com.amazonaws.services.iot.client.AWSIotMessage;
//import com.amazonaws.services.iot.client.AWSIotQos;
//import com.amazonaws.services.iot.client.AWSIotTimeoutException;
//import com.amazonaws.services.iot.client.AWSIotTopic;

/**
 * This is an example that uses {@link AWSIotMqttClient} to subscribe to a topic and
 * publish messages to it. Both blocking and non-blocking publishing are
 * demonstrated in this example.
 */
public class MQTTConnection {
  
  protected AWSIotMqttClient awsIotClient;
  
  /*
   * Utilities to read certificates and keys.
   */

  private static List<Certificate> loadCertificatesFromFile(final String filename) {
    File file = new File(filename);
    if (!file.exists()) {
      System.out.println("Certificate file: " + filename + " is not found.");
      return null;
    }

    try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
      final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      return (List<Certificate>) certFactory.generateCertificates(stream);
    } catch (IOException | CertificateException e) {
      System.out.println("Failed to load certificate file " + filename);
    }
    return null;
  }

  private static PrivateKey loadPrivateKeyFromFile(final String filename, final String algorithm) {
    PrivateKey privateKey = null;

    File file = new File(filename);
    if (!file.exists()) {
      System.out.println("Private key file not found: " + filename);
      return null;
    }
    try (DataInputStream stream = new DataInputStream(new FileInputStream(file))) {
      privateKey = PrivateKeyReader.getPrivateKey(stream, algorithm);
    } catch (IOException | GeneralSecurityException e) {
      System.out.println("Failed to load private key from file " + filename);
    }

    return privateKey;
  }
  
  public MQTTConnection(final String clientEndpoint, 
                        final String clientId,
                        final String certificateFile,
                        final String privateKeyFile) throws GeneralSecurityException, IOException {

    if (awsIotClient == null && certificateFile != null && privateKeyFile != null) {
      String algorithm = null; // keyAlgorithm

      final PrivateKey privateKey = loadPrivateKeyFromFile(privateKeyFile, algorithm);

      final List<Certificate> certificates = loadCertificatesFromFile(certificateFile);

      if (certificates == null || privateKey == null) 
        throw new IllegalArgumentException("Failed to construct client due to missing certificate or credentials.");

      KeyStore keyStore;
      String keyPassword;
      try {
          keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
          keyStore.load(null);

          // randomly generated key password for the key in the KeyStore
          keyPassword = new BigInteger(128, new SecureRandom()).toString(32);

          Certificate[] certChain = new Certificate[certificates.size()];
          certChain = certificates.toArray(certChain);
          keyStore.setKeyEntry("alias", privateKey, keyPassword.toCharArray(), certChain);
      } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
          throw e;
      }
      
      awsIotClient = new AWSIotMqttClient(clientEndpoint, clientId, keyStore, keyPassword);
    }

    if (awsIotClient == null) {
      String awsAccessKeyId = null;
      String awsSecretAccessKey = null;
      String sessionToken = null;

      if (awsAccessKeyId != null && awsSecretAccessKey != null) {
        awsIotClient = new AWSIotMqttClient(clientEndpoint, clientId, awsAccessKeyId, awsSecretAccessKey,
            sessionToken);
      }
    }

    if (awsIotClient == null) {
      throw new IllegalArgumentException("Failed to construct client due to missing certificate or credentials.");
    }
    
  }

}