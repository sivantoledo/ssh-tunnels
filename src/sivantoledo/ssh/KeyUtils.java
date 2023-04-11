package sivantoledo.ssh;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class KeyUtils {
   
  public static void authorize(Map<String,String> map) {
    authorize(map,System.getProperty("user.home")+File.separator+".ssh"+File.separator);
  }

  public static void authorize(Map<String,String> map, String sshDir) {
    Map<String,String> newAuthsMap = new HashMap<String,String>();
    
    for (String key: map.keySet()) {
      String line = map.get(key);
      String[] splits = line.split("[ \r\n]");
      if (splits.length < 3) {
        System.err.printf("cannot parse public key %s\n", key);
        continue;
      }
      String comment = splits[2];
      System.out.printf("%s (%s): %s\n", key, comment, map.get(key));
      newAuthsMap.put(comment, map.get(key));
    }
    
    String existing  = sshDir+"authorized_keys";
    String temporary = sshDir+"authorized_keys_temp";
    
    BufferedWriter newAuths = null;
    try {
      newAuths = new BufferedWriter(new FileWriter(temporary));
    } catch (IOException ioe) {
      ioe.printStackTrace();
      System.exit(1);;
    }

    System.out.printf("current authorized keys in %s:\n",existing);
    try {
      BufferedReader br = new BufferedReader(new FileReader(existing));
      String line;
      while ((line = br.readLine()) != null) {
        String[] splits = line.split("[ \r\n]");
        if (splits.length>=3) {
          System.out.printf("  comment=<%s>\n",splits[2]);
          if (newAuthsMap.containsKey(splits[2])) {
            System.out.printf("    authorized, deleting current authorization\n");
          } else {
            System.out.printf("    unrelated authorization, maintaining\n");       
            newAuths.append(line);
            newAuths.append("\n");
          }
        }
      }
      br.close();
    } catch (FileNotFoundException fnfe) {
      System.out.printf("  (none)\n");
    }  catch (IOException ioe) {
      ioe.printStackTrace();
      System.exit(1);
    }
    
    System.out.printf("adding new authorized keys to %s:\n",temporary);
    try {
     for (String key: newAuthsMap.keySet()) {
        System.out.printf("    adding authorization for %s\n", key);
        newAuths.append(newAuthsMap.get(key));
      }

      newAuths.close();
      
      System.out.printf("deleting existing authorizations file and renaming new one\n");
      (new File(existing)).delete();
      (new File(temporary)).renameTo(new File(existing));
    } catch (FileNotFoundException fnfe) {
      System.out.printf("  (none)\n");
    }  catch (IOException ioe) {
      ioe.printStackTrace();
    }


  }
}
