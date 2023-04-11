package sivantoledo.iot;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import sivantoledo.permissions.Permissions;
import sivantoledo.ssh.KeyUtils;

public class Dispatcher {

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.printf("You must provide a command argument: connect, disconnect, listen\n");
      System.err.printf("                                     list-certificates, delete-certificate, sign-from-issues\n");
      return;
    }
    if (args[0].contentEquals("connect") || args[0].contentEquals("disconnect")) TunnelController.main(args);
    else if (args[0].contentEquals("listen")) TunnelExecutor.main(args);
    
    if (args[0].contentEquals("delete-certificate")) sivantoledo.aws.IotCertificate.delete(args);
    else if (args[0].contentEquals("list-certificates"))  sivantoledo.aws.IotCertificate.list(args);
    //else if (args[0].contentEquals("sign-certificate"))   SignCertificate. main(args);
    //else if (args[0].contentEquals("create-zip"))    CreateZip.main(args);
    //else if (args[0].contentEquals("gh-auth"))             sivantoledo.github.Connection.auth(args);
    else if (args[0].contentEquals("gh-get-issue"))        sivantoledo.github.Issue.download(args);
    else if (args[0].contentEquals("gh-put-issue"))        sivantoledo.github.Issue.upload(args);
    else if (args[0].contentEquals("gh-view-issue"))        sivantoledo.github.Issue.view(args);
    else if (args[0].contentEquals("gh-list-issues"))      sivantoledo.github.Issue.list(args);
    else if (args[0].contentEquals("sign-from-issues"))    sivantoledo.github.Issue.signOpenRequests(args);
    
    else if (args[0].contentEquals("gh-ssh-authorize"))    {
      if (args.length<4) {
        System.err.printf("This method requires three arguments, a repository name, a system name, and a realm name\n");
        System.exit(1);
      }
      final String repo   = args[1];
      final String system = args[2];
      final String realm  = args[3];
      
      final var prefix = "controller";
      final var suffix = "."+system+"."+realm+".sshkey.pub";
      
      sivantoledo.ssh.KeyUtils.authorize(sivantoledo.github.Issue.read(repo, issue -> {
        String title = issue.getTitle();
        if (!title.startsWith(prefix)) return false;
        if (!title.endsWith  (suffix)) return false;
        var splits = title.split("\\."); // split on periods
        if (splits.length<5) return false; // must be of the form device.system.realm.sshkey.pub
        try {
          if (!Permissions.check(issue.getUser().getLogin(),issue.getUser().getName(),splits[1])) return false;
        } catch (IOException ioe) {
          System.err.printf("Failed to read issue details: %s\n", ioe.getMessage());
          System.exit(1);
          return false;
        }
        return true;
      }));
    }

    else if (args[0].contentEquals("gh-ssh-authorize-proxy"))    {
      if (args.length<3) {
        System.err.printf("This method requires two arguments, a repository name and a realm name\n");
        System.exit(1);
      }
      final String repo   = args[1];
      final String realm  = args[2];
      
      //final var prefix = "controller";
      final var suffix = "."+realm+".sshkey.pub";
      
      var all = sivantoledo.github.Issue.read(repo, issue -> {
        String title = issue.getTitle();
        if (!title.endsWith  (suffix)) return false;
        var splits = title.split("\\."); // split on periods
        if (splits.length<5) return false; // must be of the form device.system.realm.sshkey.pub
        try {
          if (!Permissions.check(issue.getUser().getLogin(),issue.getUser().getName(),splits[1])) return false;
        } catch (IOException ioe) {
          System.err.printf("Failed to read issue details: %s\n", ioe.getMessage());
          System.exit(1);
          return false;
        }
        return true;
      });
      
      //ivantoledo.ssh.KeyUtils.authorize(s);
      
      Map<String,Map<String,String>> bySystem = new HashMap<>();
      
      for (Entry<String, String> e: all.entrySet()) {
        var splits = e.getKey().split("\\.");
        if (splits.length<3) continue; // should not happen
        var system = splits[1];
        if (!bySystem.containsKey(system)) bySystem.put(system, new HashMap<String,String>());
        (bySystem.get(system)).put(e.getKey(), e.getValue());
      }
      
      for (Entry<String, Map<String,String>> e: bySystem.entrySet()) {
        var system = e.getKey();
        var map    = e.getValue();
        KeyUtils.authorize(map,"/home/"+system+"/.ssh/");
      }
    }

    
    //else if (args[0].contentEquals("install-keys"))   sivantoledo.ssh.KeyUtils.main(args);
    else System.err.printf("command %s is invalid\n", args[0]);
  }
}
