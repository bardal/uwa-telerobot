
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: PRInputStream.java,v 1.1.1.1 1999/04/15 05:23:33 bdalton Exp $
// Time-stamp: "1999-03-22 19:10:45 barney"

// {{{ GNU General Public License
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as
// published by the Free Software Foundation; either version 2 of the
// License, or (at your option) any later version. This program is
// distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
// License for more details.  You should have received a copy of the
// GNU General Public License along with this program; if not, write
// to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
// Boston, MA 02111-1307, USA.
// }}}

package au.edu.uwa.mech.io;
import java.io.*;

public class PRInputStream extends FilterInputStream
{
  private long nread = 0;
  private long size = 0;

  
  public long getProgress() {return nread;}
  public long getSize() {return size;} 

  public PRInputStream(InputStream in, long size) {
      super(in);
        this.size=size;
  }
        
  public int read() throws IOException
  {
    int c = in.read();
    if (c>=0) nread++;

    return c;
  }

  public int read(byte b[]) throws IOException
  {
    int nr = in.read(b);
    if (nr>0) nread+=nr;
    return nr;
  }
  
  public int read(byte b[], int off, int len)
    throws IOException
  {
    int nr = in.read(b, off, len);
    if (nr>0) nread+=nr;
    return nr;
  }

  public long skip(long n) throws IOException {
    long nr = in.skip(n);
    if (nr>0) nread+=nr;
    return nr;
  }

  /**
   * Overrides <code>FilterInputStream.reset</code> 
   * to reset the P monitor as well as the stream.
   */
  
  public synchronized void reset() throws IOException {
    throw new IOException("Not tested yet");
    //int old_count_to_end=in.available();
    //in.reset();
    //nread = size-(in.available()-old_count_to_end);
    
  }

  


}



