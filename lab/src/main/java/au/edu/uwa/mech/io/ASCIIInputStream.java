package au.edu.uwa.mech.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Byte-stream front end to {@link ASCIIReader}, reading ISO-8859-1 text. */
public class ASCIIInputStream extends FilterInputStream
{
  private final ASCIIReader reader;

  public ASCIIInputStream(InputStream in)
  {
    super(in);
    try {
      reader = new ASCIIReader(new InputStreamReader(in, "ISO-8859-1"));
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  public String readWord() throws IOException { return reader.readWord(); }

  public String readQuotedString() throws IOException { return reader.readQuotedString(); }

  public int readInt() throws IOException { return reader.readInt(); }

  public long readLong() throws IOException { return reader.readLong(); }

  public float readFloat() throws IOException { return reader.readFloat(); }

  public double readDouble() throws IOException { return reader.readDouble(); }

  public boolean readBool() throws IOException { return reader.readBool(); }
}
