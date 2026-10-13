package au.edu.uwa.mech.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Writes values in the text format {@link ASCIIReader} reads. */
public class ASCIIOutputStream extends FilterOutputStream
{
  public ASCIIOutputStream(OutputStream out) { super(out); }

  protected void writeText(String s) throws IOException
  {
    for (int i = 0; i < s.length(); i++)
      out.write(s.charAt(i) & 0xff);
  }

  public void writeSpace() throws IOException { out.write(' '); }

  public void writeInt(int i) throws IOException { writeText(Integer.toString(i)); }

  public void writeLong(long l) throws IOException { writeText(Long.toString(l)); }

  public void writeFloat(float f) throws IOException { writeText(Float.toString(f)); }

  public void writeDouble(double d) throws IOException { writeText(Double.toString(d)); }

  public void writeBool(boolean b) throws IOException { writeInt(b ? 1 : 0); }

  public void writeWord(String s) throws IOException { writeText(s); }

  public void writeQuotedString(String s) throws IOException { writeText("\"" + s + "\""); }
}
