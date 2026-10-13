package au.edu.uwa.mech.io;

import java.io.*;

public class PInputStream extends FilterInputStream
{
//    private long nread = 0;
//    private long size = 0;

  
//    public long bytesRead() {return nread;}
//    public long getSize() {return size;} 

  public PInputStream(InputStream in, long size) {
    super(in);
//          this.size=size;
  }
        
//    public int read() throws IOException
//    {
//      int c = in.read();
//      if (c>=0) nread++;

//      return c;
//    }

//    public int read(byte b[]) throws IOException
//    {
//      int nr = in.read(b);
//      if (nr>0) nread+=nr;
//      return nr;
//    }
  
//    public int read(byte b[], int off, int len)
//      throws IOException
//    {
//      int nr = in.read(b, off, len);
//      if (nr>0) nread+=nr;
//      return nr;
//    }

//    public long skip(long n) throws IOException {
//      long nr = in.skip(n);
//      if (nr>0) nread+=nr;
//      return nr;
//    }

//    /**
//     * Overrides <code>FilterInputStream.reset</code> 
//     * to reset the P monitor as well as the stream.
//     */
  
//    public synchronized void reset() throws IOException {
//      throw new IOException("Not tested yet");
//      //int old_count_to_end=in.available();
//      //in.reset();
//      //nread = size-(in.available()-old_count_to_end);
    
//    }
}
