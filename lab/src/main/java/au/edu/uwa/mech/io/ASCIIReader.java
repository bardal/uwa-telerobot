package au.edu.uwa.mech.io;

import java.io.EOFException;
import java.io.FilterReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

/**
 * Reads whitespace-separated values from text: numbers, words and
 * double-quoted strings. This is the text format the RobotServer and
 * camera server use for results and poses.
 */
public class ASCIIReader extends FilterReader
{
  public ASCIIReader(Reader in)
  {
    super(new PushbackReader(in, 1));
  }

  private PushbackReader pin() { return (PushbackReader) in; }

  /** Skip whitespace; returns false at end of input. */
  protected boolean skipWhitespace() throws IOException
  {
    int c;
    while ((c = pin().read()) != -1) {
      if (!Character.isWhitespace((char) c)) {
        pin().unread(c);
        return true;
      }
    }
    return false;
  }

  /** The next run of non-whitespace characters. */
  public String readWord() throws IOException
  {
    if (!skipWhitespace())
      throw new EOFException();
    StringBuilder sb = new StringBuilder();
    int c;
    while ((c = pin().read()) != -1 && !Character.isWhitespace((char) c))
      sb.append((char) c);
    return sb.toString();
  }

  /** A double-quoted string (quotes removed); an unquoted word is accepted too. */
  public String readQuotedString() throws IOException
  {
    if (!skipWhitespace())
      throw new EOFException();
    int c = pin().read();
    if (c != '"') {
      pin().unread(c);
      return readWord();
    }
    StringBuilder sb = new StringBuilder();
    while ((c = pin().read()) != -1 && c != '"')
      sb.append((char) c);
    return sb.toString();
  }

  public int readInt() throws IOException { return (int) Math.round(readDouble()); }

  public long readLong() throws IOException
  {
    String w = readWord();
    try {
      return Long.parseLong(w);
    } catch (NumberFormatException e) {
      return Math.round(parse(w));
    }
  }

  public float readFloat() throws IOException { return (float) readDouble(); }

  public double readDouble() throws IOException { return parse(readWord()); }

  /** Booleans are written as integers: 1 is true, anything else false. */
  public boolean readBool() throws IOException { return readInt() == 1; }

  /**
   * A word that is not a number throws NumberFormatException, not
   * IOException: callers such as the robot Client try to read every message
   * as a pose and quietly ignore the ones that are not.
   */
  private static double parse(String w)
  {
    try {
      return Double.parseDouble(w);
    } catch (NumberFormatException e) {
      throw new NumberFormatException("expected a number but got '" + w + "'");
    }
  }
}
