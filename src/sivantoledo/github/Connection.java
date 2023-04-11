package sivantoledo.github;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class Connection {
  
  public final GitHub github;
  
  public Connection() throws IOException {
    
    String token=null;
    try {
      token = Files.readString(Paths.get("gh-token.txt")).trim();
    } catch (IOException ioe) {
      System.err.printf("Failed to read gh-token.txt: %s\n", ioe.getMessage());
      System.exit(1);
    }
    
    github = new GitHubBuilder().withOAuthToken(token).build();
    
    String ghLogin = github.getMyself().getLogin();
    String ghEmail = github.getMyself().getEmail();
  
    System.out.printf("GitHub login <%s> email <%s>\n", ghLogin, ghEmail);
  }  
}
