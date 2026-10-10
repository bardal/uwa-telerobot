package au.edu.uwa.mech.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/** Line-oriented reading from standard input for the interactive test mains. */
public class ConsoleInput
{
  private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

  /** The next line, or "" at end of input. */
  public String readString()
  {
    try {
      String line = in.readLine();
      return line == null ? "" : line;
    } catch (IOException e) {
      return "";
    }
  }

  /** The first character of the next line, or '\0' if it is empty. */
  public char readChar()
  {
    String s = readString();
    return s.length() > 0 ? s.charAt(0) : '\0';
  }
}
