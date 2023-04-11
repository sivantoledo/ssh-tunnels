package sivantoledo.aws;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.services.iot.*;
import software.amazon.awssdk.services.iot.model.*;

public class IotCertificate {
    public static void list( String[] args ) {
        System.out.println( "Listing AWS IoT Certificates" );
        
        CertificateFactory certFactory = null;
        try {
          certFactory = CertificateFactory.getInstance("X.509");
        } catch (CertificateException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
        
        IotClient iot = IotClient.builder().build();
        
        var certResponse = iot.listCertificates();
        
        System.out.printf("has certificates? %b\n",certResponse.hasCertificates());
        
        for (Certificate cert: certResponse.certificates()) {
          System.out.printf("%s\n", cert.certificateId());
          System.out.printf("  %s\n", cert.statusAsString());
          System.out.printf("  %s\n", cert.creationDate().toString());
          
          var certDesc = iot.describeCertificate(DescribeCertificateRequest.builder().certificateId(cert.certificateId()).build()).certificateDescription();
          
          String pem = certDesc.certificatePem();
          
          if (certFactory!=null) try {
            X509Certificate cer = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            System.out.printf("  %s\n", cer.getSubjectX500Principal().getName());
          } catch (CertificateException e) {
            e.printStackTrace();
          }
          System.out.println();         
        }        
    }

    public static void delete( String[] args ) {
      System.out.println( "Deleting AWS IoT Certificate " + args[1] );
      
      CertificateFactory certFactory = null;
      try {
        certFactory = CertificateFactory.getInstance("X.509");
      } catch (CertificateException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      
      IotClient iot = IotClient.builder().build();
      
      String certificateId = null;
      
      //var updateReq = iot.updateCertificate(UpdateCertificateRequest.builder().certificateId(certificateId).newStatus(CertificateStatus.ACTIVE).build());
      
      var certResponse = iot.listCertificates();
      System.out.printf("has certificates? %b\n",certResponse.hasCertificates());
      
      for (Certificate cert: certResponse.certificates()) {
        //System.out.printf("%s\n", cert.certificateId());
        //System.out.printf("  %s\n", cert.statusAsString());
        //System.out.printf("  %s\n", cert.creationDate().toString());
        
        var certDesc = iot.describeCertificate(DescribeCertificateRequest.builder().certificateId(cert.certificateId()).build()).certificateDescription();
        
        String pem = certDesc.certificatePem();
        
        if (certFactory!=null) try {
          X509Certificate cer = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
          String[] splits = cer.getSubjectX500Principal().getName().split(",");
          String cn = null;
          for (String split: splits) {
            if (split.startsWith("CN=")) cn = split.substring(3); 
          }
          if (cn!=null) System.out.printf("  %s\n",cn);
          if (cn!=null && cn.contentEquals(args[1])) {
            System.out.printf("   deleting...\n");
            var dacReq = iot.updateCertificate(UpdateCertificateRequest.builder()
                                               .certificateId(certDesc.certificateId())
                                               .newStatus(CertificateStatus.INACTIVE)
                                               .build());
            var detReq = iot.detachPolicy(DetachPolicyRequest.builder()
                                          .target(certDesc.certificateArn())
                                          .policyName(cn.startsWith("controller") ? "tunnel.controller" : "tunnel.target")
                                          .build());
            var delReq = iot.deleteCertificate(DeleteCertificateRequest.builder().certificateId(certDesc.certificateId()).build());
          }
        } catch (CertificateException e) {
           e.printStackTrace();
        }
        
      }      
  }

public static String sign(String csr, boolean controller) {
      IotClient iot = IotClient.builder().build();
      System.out.printf("CSR:\n%s\n", csr);
      var certDesc = iot.createCertificateFromCsr(CreateCertificateFromCsrRequest.builder().certificateSigningRequest(csr).setAsActive(true).build());
      String pem = certDesc.certificatePem();
      String arn = certDesc.certificateArn();
      String id  = certDesc.certificateId();
      
      var attachPolicyResp = iot.attachPolicy(AttachPolicyRequest.builder().target(arn).policyName(controller ? "tunnel.controller" : "tunnel.target").build());
      
      return pem;
    }
}
