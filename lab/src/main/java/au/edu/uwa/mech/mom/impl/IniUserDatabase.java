package au.edu.uwa.mech.mom.impl;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import au.edu.uwa.mech.mom.UserDatabase;

/**
 * Users held in a Windows-style INI file, one section per username:
 * <pre>
 * [guest]
 * PIN=-1
 * UserLevel=Guest
 * </pre>
 * Section and key names are case-insensitive. Missing users or fields read
 * as the string "NULL", which DefaultUserManager treats as "no such user".
 * (The 1999 version was a JNI wrapper around GetPrivateProfileString.)
 */
public class IniUserDatabase implements UserDatabase
{
  private final Map<String, Map<String, String>> sections = new HashMap<String, Map<String, String>>();

  public synchronized void setDatabaseFile(String filename)
  {
    sections.clear();
    try (BufferedReader in = new BufferedReader(
             new InputStreamReader(new FileInputStream(filename), "ISO-8859-1"))) {
      Map<String, String> current = null;
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty() || line.startsWith(";"))
          continue;
        if (line.startsWith("[") && line.endsWith("]")) {
          String name = line.substring(1, line.length() - 1).trim().toLowerCase();
          current = sections.get(name);
          if (current == null)
            sections.put(name, current = new HashMap<String, String>());
        } else if (current != null) {
          int eq = line.indexOf('=');
          String key = eq > 0 ? line.substring(0, eq).trim().toLowerCase() : null;
          if (key != null && !current.containsKey(key)) // first occurrence wins
            current.put(key, line.substring(eq + 1).trim());
        }
      }
      System.out.println("IniUserDatabase: " + sections.size() + " users from " + filename);
    } catch (IOException e) {
      System.err.println("IniUserDatabase: could not read " + filename + ": " + e);
    }
  }

  public synchronized String getStringField(String username, String fieldname)
  {
    if (username == null || fieldname == null)
      return "NULL";
    Map<String, String> section = sections.get(username.toLowerCase());
    String value = section == null ? null : section.get(fieldname.toLowerCase());
    return value == null ? "NULL" : value;
  }

  public synchronized int getIntField(String username, String fieldname)
  {
    try {
      return Integer.parseInt(getStringField(username, fieldname));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
