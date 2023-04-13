package sivantoledo.ssh;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;

public class Tunnel {
  
  private Process process = null;
  private final String host;
  private final String user;
  private final String keyFile;
  private final int    defaultPort;
  private       int    port = -1;
  
  private String ext     = null;
  private String devNull = null;
  private String percent = null;
  private String os      = null;

  /*
   * host and user are at the proxy computer, so is the keyFile.
   * The port is the port that will be used on the proxy.
   */
  public Tunnel(String host, String user, int defaultPort, String keyFile) {
    this.host        = host;
    this.user        = user;
    this.defaultPort = defaultPort;
    this.keyFile     = keyFile;    
    
    os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
    if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0) || os.indexOf("nux") >= 0) {
      ext = ".sh";
      devNull = "/dev/null";
      percent = "%";
    } else if (os.indexOf("win") >= 0) {
      ext = ".bat";
      devNull = "NUL";
      percent = "%%";
    } else {
      System.err.printf("operating system not known, can't build tunnel script\n");
      return;
    }
  }
  
  public int port() { return port; }
  
  public void stop() {
    if (process!=null) process.destroyForcibly();
    process = null;
  }

  public void start() {
    if (process!=null) {
      if (process.isAlive()) {
        System.out.println("process exists and is alive, doing nothing");
        //process.destroyForcibly();
      }
      else System.out.println("process exists, but is not alive");
      process = null;
    }
    
    if (defaultPort > 0) port = defaultPort;
    else                 port = 32768 + (new Random()).nextInt(32767);
    
    ProcessBuilder builder = 
         new ProcessBuilder("ssh",
                           "-o","ServerAliveInterval=60",
                           "-4",
                           "-N", // no remote command
                           "-x", // no X11
                           "-R",String.format("%d:localhost:22",port),
                           "-i",keyFile,
                           "-o","StrictHostKeyChecking=no",
                           "-o","UserKnownHostsFile="+devNull,
                           user+"@"+host);
    //System.out.printf("ssh built 1: id %s\n",sshId);
    //builder.redirectErrorStream(true);
    //System.out.printf("ssh built 2\n");
    //builder.inheritIO();
    builder.redirectOutput(Redirect.INHERIT);
    builder.redirectError(Redirect.INHERIT);
    //System.out.printf("ssh built 3\n");
    try {
      System.out.printf("ssh starting connection to proxy\n");
      process = builder.start();
      System.out.printf("ssh to proxy started\n");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  public void connect(String remoteUser, String remoteKeyFile) {
    String proxyCommand = String.format("ProxyCommand=ssh -W %%h:%%p %s@%s -i %s",user,host,keyFile);
    String knownHostsFile="UserKnownHostsFile="+devNull;
    ProcessBuilder builder = 
        new ProcessBuilder("ssh",
            "-o",proxyCommand,
            "-o","StrictHostKeyChecking=no",
            "-o",knownHostsFile,
            "-o",proxyCommand,
            remoteUser+"@localhost",
            "-p",Integer.toString(defaultPort), // weird; should be port I think
            "-i",remoteKeyFile);
    
    System.out.printf("ssh command: ");
    for (String p: builder.command()) System.out.print(p+" "); System.out.println();
    //builder.inheritIO();
    builder.redirectInput(Redirect.INHERIT);
    builder.redirectOutput(Redirect.INHERIT);
    builder.redirectError(Redirect.INHERIT);
    try {
      System.out.printf("ssh starting connection to remote target\n");
      process = builder.start();
      System.out.printf("ssh to remote target started\n");
      
      while (process.isAlive()) {
        try {
          process.waitFor();
        } catch (InterruptedException e) {}
      }
   } catch (IOException e) {
     e.printStackTrace();
   }

  }
  
  public void writeScript(String remoteUser, String remoteKeyFile) {
 
    String cmd = String.format("ssh "
        +"-o ProxyCommand=\"ssh -W %sh:%sp %s@%s -i %s\" "
        +"-o StrictHostKeyChecking=no "
        +"-o UserKnownHostsFile="+devNull+" "
        +remoteUser+"@localhost "
        +"-p %d "
        +"-i %s "
        +"\n"
        ,percent
        ,percent
        ,user
        ,host
        ,keyFile
        ,defaultPort
        ,remoteKeyFile
        );
    
    String scriptName = "connect";
    
    try {
      OutputStream script = new FileOutputStream(scriptName+ext);
      script.write(cmd.getBytes());
      script.close();

      if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0) || os.indexOf("nux") >= 0) {
        java.nio.file.Files.setPosixFilePermissions(java.nio.file.Paths.get(scriptName+ext), new HashSet<PosixFilePermission>(Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)));
      }
    } catch (IOException e) {
      System.err.printf("failed to write commend to output file %s%s\n",scriptName,ext);
      System.err.printf(cmd);
    }
  }


}
