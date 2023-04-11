package sivantoledo.permissions;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;

public class Permissions {
  
  private static Properties props = null;
  
  private static void load() {
    props = new Properties();
    try {
      props.load(new FileInputStream("permissions.txt"));
    } catch (FileNotFoundException fnfe) {
      System.out.printf("File permissions.txt does not exist, creating an empty one.\n");
      try {
        props.store(new FileOutputStream("permissions.txt"), "created programmatically");
      } catch (IOException e1) {
        System.err.printf("Exception while creating permissions.txt: %s\n", e1.getMessage());
        System.exit(1);
      }
    } catch (IOException e) {
      System.err.printf("Exception while reading permissions.txt: %s\n", e.getMessage());
      System.exit(1);
    }
  }
  
  public static boolean check(String user, String fullname, String resource) {
    if (props==null) load();
    
    String value = props.getProperty(resource+"+"+user);
    if (value==null) {
      Scanner sc= new Scanner(System.in);
      value = "";
      System.out.printf("Permit user %s (%s) access to %s?\n(y/n): ",user,fullname,resource);
      value = sc.next().trim();
      while (!value.toLowerCase().startsWith("y") && !value.toLowerCase().startsWith("n")) {
        System.out.printf("(y/n): ");
        value = sc.next().trim();
      }     
      if (value.toLowerCase().startsWith("y")) value = "allow";
      else                                     value = "deny" ; 
      
      props.put(resource+"+"+user, value);
      
      try {
        props.store(new FileOutputStream("permissions.txt"), "modified programmatically");
      } catch (IOException e1) {
        System.err.printf("Exception while creating permissions.txt: %s\n", e1.getMessage());
        System.exit(1);
      }
    }
    
    if (value.contentEquals("allow")) return true;
    if (value.contentEquals("deny"))  return false;

    System.err.printf("Permission value must be \"allow or deny\", but it is \"%s\"\n", value);
    System.exit(1);

    return false; // dummmy
  }

  public static void main(String[] args) {
    check("sivantoledo", "Sivan Toledo", "xyz");
    check("sivantoledo", "Sivan Toledo", "xyz");
    check("sivantoledo", "Sivan Toledo", "xyz");
    check("sivantoledo", "Sivan Toledo", "xxx");
    check("sivantoledo", "Sivan Toledo", "xxx");
    check("sivantoledo", "Sivan Toledo", "xxx");
    check("sivantoledo", "Sivan Toledo", "123");
    check("sivantoledo", "Sivan Toledo", "123");
    check("sivantoledo", "Sivan Toledo", "123");
  }
}
