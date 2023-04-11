package sivantoledo.github;

import java.io.File;
//import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
//import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
//import org.kohsuke.github.GitHubBuilder;

import sivantoledo.aws.IotCertificate;

public class Issue {
  

  public static void signOpenRequests(String[] args) {
    
    if (args.length < 2) {
      System.err.printf("issues repository name is missing, exiting\n");
      System.exit(1);
    }
    String repo     = args[1];
    
    /*
    Properties props = new Properties();
    try {
      props.load(new FileInputStream("properties.txt"));
    } catch (IOException e) {
      System.err.printf("Properties.txt file missing, exiting\n");
      System.exit(1);
    }
*/
    
    try {
    
      GitHub github = (new Connection()).github;
      
      GHRepository issues = github.getRepository(repo);
    
      for (GHIssue issue: issues.queryIssues().state(GHIssueState.OPEN).list()) {
      
        String[] splits = issue.getTitle().split("\\.");
      
        if (splits.length<4) {
          System.err.printf("issue title cannot be parsed: %s\n", issue.getTitle());
          continue;
        }
      
        var device = splits[0];
        var system = splits[1];
        var realm  = splits[2];
        var type   = splits[3];
        var sub    = "";
        if (splits.length>=5) sub = splits[4];
      
        //System.out.printf("%s\n<<<%s>>>\n", issue.getTitle(), issue.getBody());
        
        System.out.printf("%s ==> %d %s.%s.%s.%s (%s)\n", issue.getTitle(),splits.length,device,system,realm,type,sub);
      
        if (type.equals("x509") && sub.equals("csr")) {
          var pem = IotCertificate.sign(issue.getBody(), device.contains("controller"));
          GHIssue cert = issues.createIssue(String.format("%s.%s.%s.%s.%s", device,system,realm,type,"cert")).body(pem).create();
          System.out.printf("added cert, closing the csr\n");
          issue.close();
        }
      }
    
      /*
      try {
        Issue issue = new Issue();
        issue.setTitle("Test issue"); // Title of the issue
        issue.setBody("Some stuff"); // Body of the issue
        // Other stuff can be included in the Issue as you'd expect .. like labels, authors, dates, etc. 
        // Check out the API per your needs
        
        // In my case, we utilize a private issue-only-repository "RepositoryIssueTracker" that is maintainted under our Organization "MyCompany"
        issueService.createIssue("MyCompany", "RepositoryIssueTracker", issue);
      } catch (Exception e) {
        System.out.println("Failed");
        e.printStackTrace();
      }
      */
    
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
  
  public static void download(String[] args) {
    
    if (args.length < 2) {
      System.err.printf("issues repository name is missing, exiting\n");
      System.exit(1);
    }
    String repo     = args[1];

    if (args.length < 3) {
      System.err.printf("file name is missing, exiting\n");
      System.exit(1);
    }
    String filename = args[2];
    //System.out.printf("trying to download %s\n",filename);
    
    /*
    Properties props = new Properties();
    try {
      props.load(new FileInputStream("properties.txt"));
    } catch (IOException e) {
      System.err.printf("Properties.txt file missing, exiting\n");
      System.exit(1);
    }
    */
    
    // we do not need found to be atomic, but we need it to be mutable, and AtomicBoolean is mutable.
    final java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean(false);
    
    forEach( repo, issue -> {
      String title = issue.getTitle();
      
      if (!title.contentEquals(filename)) return;
      
      try {
        Files.writeString( Paths.get(filename), issue.getBody(), StandardOpenOption.CREATE);
        found.set(true);
      } catch (IOException e) {
        System.err.printf("Exception: %s\n", e.getMessage());
      }    
    }, false );

    if (!found.get()) System.err.printf("File %s not found!\n",filename);
    else              System.out.printf("Downloaded %s\n",filename);

    /*
    boolean found=false;
    
    try {
    
      GitHub github = (new Connection()).github;
      
      GHRepository issues = github.getRepository(repo);
    
      for (GHIssue issue: issues.queryIssues().state(GHIssueState.OPEN).list()) {
      
        String title = issue.getTitle();
        
        if (!title.contentEquals(filename)) continue;
        
        Files.writeString( Paths.get(filename), issue.getBody(), StandardOpenOption.CREATE);    
        found=true;
      }
    
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    
    if (!found) System.err.printf("File %s not found!\n",filename);
    else        System.out.printf("Downloaded %s\n",filename);
    */
  }

  public static void upload(String[] args) {
    
    if (args.length < 2) {
      System.err.printf("issues repository name is missing, exiting\n");
      System.exit(1);
    }
    String repo = args[1];

    if (args.length < 3) {
      System.err.printf("file name is missing, exiting\n");
      System.exit(1);
    }
    String filename = args[2];
    System.out.printf("trying to download %s\n",filename);
    
    /*
    Properties props = new Properties();
    try {
      props.load(new FileInputStream("properties.txt"));
    } catch (IOException e) {
      System.err.printf("Properties.txt file missing, exiting\n");
      System.exit(1);
    }
    */
    
    String body=null;
    try {
      body = Files.readString(Paths.get(filename));
    } catch (IOException ioe) {
      System.err.printf("Failed to read file %s: %s\n", filename, ioe.getMessage());
      System.exit(1);
    }
    
    try {
    
      GitHub github = (new Connection()).github;
      
      GHRepository issues = github.getRepository(repo);

      GHIssue issue = issues.createIssue(filename).body(body).create();
      System.out.printf("Uploaded %s as an issue\n",filename);

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
  
  public static void list(String[] args) {
    
    if (args.length < 2) {
      System.err.printf("issues repository name is missing, exiting\n");
      System.exit(1);
    }
    String repo = args[1];
    
    final String suffix = args.length >= 3 ? args[2] : null;
        
    forEach( repo, issue -> {
      String title = issue.getTitle();
      
      if (suffix!=null && !title.endsWith(suffix)) return;
      
      try {
        System.out.printf("%d %s %s (%s) %s\n", issue.getNumber(), issue.getTitle(), issue.getUser().getLogin(), issue.getUser().getName()  , issue.getState().name());
      } catch (IOException e) {
        System.err.printf("Exception: %s\n", e.getMessage());
      }            
    }, false );
    
    /*
    if (1+1==2) return;
    
    try {
    
      GitHub github = (new Connection()).github;
      
      GHRepository issues = github.getRepository(repo);

      for (GHIssue issue: issues.queryIssues().state(GHIssueState.OPEN).list()) {
        
        String title = issue.getTitle();
        
        if (suffix!=null && !title.endsWith(suffix)) continue;
        
        System.out.printf("%d %s %s (%s) %s\n", issue.getNumber(), issue.getTitle(), issue.getUser().getLogin(), issue.getUser().getName()  , issue.getState().name());      
      }

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    */
  }
  
  public static void view(String[] args) {
    
    if (args.length < 2) {
      System.err.printf("issues repository name is missing, exiting\n");
      System.exit(1);
    }
    String repo = args[1];
    
    if (args.length < 3) {
      System.err.printf("specify filename\n");
      System.exit(1);
    }
    final String filename = args[2];
        
    forEach( repo, issue -> {
      String title = issue.getTitle();
      
      if (!title.contentEquals(filename)) return;
      
      try {
        System.out.printf("%d %s %s (%s) %s\n", issue.getNumber(), issue.getTitle(), issue.getUser().getLogin(), issue.getUser().getName()  , issue.getState().name());
        System.out.printf("%s\n", issue.getBody());
      } catch (IOException e) {
        System.err.printf("Exception: %s\n", e.getMessage());
      }            
    }, false );
  }
  

  public static Map<String,String> read(String[] args) {
    
    var map = new HashMap<String,String>();
    
    if (args.length < 2) {
      System.err.printf("issues repository name is missing, exiting\n");
      System.exit(1);
    }
    String repo     = args[1];

    if (args.length < 4) {
      System.err.printf("specify prefix and suffix (@ for any)\n");
      System.exit(1);
    }

    String prefix = args[2];
    String suffix = args[3];
    
    /*
    Properties props = new Properties();
    try {
      props.load(new FileInputStream("properties.txt"));
    } catch (IOException e) {
      System.err.printf("Properties.txt file missing, exiting\n");
      System.exit(1);
    }
    */
    
    try {
    
      GitHub github = (new Connection()).github;
      
      GHRepository issues = github.getRepository(repo);

      for (GHIssue issue: issues.queryIssues().state(GHIssueState.OPEN).list()) {
        
        String title = issue.getTitle();
        
        if (!prefix.equals("@") && !title.startsWith(prefix)) continue;
        if (!suffix.equals("@") && !title.endsWith(suffix))   continue;
        
        map.put(issue.getTitle(), issue.getBody());        
      }

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    
    return map;
  }
  
  public static Map<String,String> read(String repo, final Predicate<GHIssue> predicate) {
    
    final var map = new HashMap<String,String>();
    
    forEach( repo, issue -> {
      if (predicate.test(issue)) map.put(issue.getTitle(), issue.getBody());
    }, false );
    
    return map;
  }

  
  public static void forEach(String repo, Consumer<GHIssue> f, boolean onlyOpen) {
    
    //var map = new HashMap<String,String>();
      
    //System.out.printf("repo %s !\n", repo);

    try {
    
      GitHub github = (new Connection()).github;
      
      GHRepository issues = github.getRepository(repo);
      
      var list = onlyOpen ?
          issues.queryIssues().state(GHIssueState.OPEN).list()
          :
          issues.queryIssues().list();

      for (GHIssue issue: list) {
        
        //String title = issue.getTitle(); 
        //System.out.printf("repo %s issue %s\n", repo,title);
        
        f.accept(issue);        
      }

    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }    
  }



}
