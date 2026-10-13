
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: WindowInputStream.java,v 1.1 1999/08/27 22:30:13 bdalton Exp $
// Time-stamp: "1999-08-26 21:38:14 student"

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


// This InputStream allows you to look at the data you are about to
// receive, by specifying a window size. The InputStream will never buffer
// past the end of the window, so for good performance use a
// BufferedReaer as the source for this InputStream.

// read() always returns the next byteacters in the stream. if the

// VERY SLOW can it be optimized?

package au.edu.uwa.mech.io;

import java.io.InputStream;
import java.io.IOException;

public class WindowInputStream extends java.io.InputStream {


  int windowLength;
  int windowStart;
  int dataEnd;
  InputStream source;
  byte[] windowBuf;
  boolean windowFull;
  int inWindow;
  
  
  // sz is the size of the window
  public WindowInputStream(InputStream source, int windowLength) {

    if (windowLength <= 0)
      throw new IllegalArgumentException("window length <= 0");
    this.source = source;
    this.windowLength=windowLength;
    windowStart=dataEnd=0;
    windowBuf = new byte[goodBufferSizeFor(windowLength)];
    windowFull=false;
    inWindow=0;
  }


  public byte[] view() throws IOException {
    if(!windowFull)
      fillWindow();

    //debug("windowStart "+windowStart+"\t
    //return new String(windowBuf, windowStart, inWindow());
    
    byte[] view = new byte[inWindow];
    System.arraycopy(windowBuf, windowStart, view, 0, inWindow);
    //System.out.println("view "+new String(view));
    return view;
    
  }

  public int read() throws IOException
  {
    if (inWindow >=1)
    {
      byte cb=windowBuf[windowStart];
      slideWindow(1);
      return cb;
    }
    else
      return source.read();
    
  }
  
  
  public int read(byte cbuf[], int off, int len) throws IOException 
  {
    int readIn=0;

    //debug("read: len:"+len+" inWindow:"+inWindow()+" windowStart:"+windowStart);
    
    
    // if we want less than current window take it from there 
    if (len <= inWindow)
    {
      System.arraycopy(windowBuf, windowStart, cbuf, off, len);
      slideWindow(len);
      readIn=len;
    }
    else if (inWindow >0)// take what's in the window 
    {
      System.arraycopy(windowBuf, windowStart, cbuf, off, inWindow);
      readIn=inWindow;
      slideWindow(inWindow);
    
    }
    else // nothing in window so try a direct read from the source
    {
      readIn=source.read(cbuf, off, len);
    }
    
    return readIn;
  }
  
  
  
  public void close() throws IOException
  {
    source.close();
  }
  
  protected int goodBufferSizeFor(int windowLength) {
    return (windowLength < 50 ? 50 : (int)(windowLength*1.5));
    
  }
  
  
  //space filled in the window
  //protected int inWindow() {
  //  return dataEnd-windowStart;
  //}
  
  //protected int spaceLeftInWindow() {
  //  return windowStart+windowLength-dataEnd;
  //}

  //protected boolean windowFull() {
  //  return (spaceLeftInWindow() == 0 ? true : false);
  //}
  
  protected void fillWindow() throws IOException {
    int red=0;
    int n;
    int windowEnd=windowStart+windowLength;

  out: {
      
      do{
        //n=source.read(windowBuf, dataEnd, spaceLeftInWindow());
        //playing round with optimizing this
        n=source.read(windowBuf, dataEnd, windowEnd-dataEnd);
      
        if (n==-1){
          //System.out.println("End of file in WindowInputStream fillWindow");
          break out;
        }
        
      
        dataEnd+=n;
      }  while ((windowEnd-dataEnd) > 0);

      //if (windowEnd==dataEnd)
      windowFull=true;
      inWindow=windowLength;
    }
    
    // more arsing about with optimizing
    //} while (!windowFull());
    
  }

  
  protected void slideWindow(int distance)
  {
    //debug("sliding window by "+distance+" windowBuf:"+windowBuf);
    //what if distance is greater than window width?
    if(distance > windowLength)
      throw new IllegalArgumentException
        ("window distance"+ distance +
         " > than windowLength " + windowLength);


    int newWindowStart=windowStart+distance;
    int newWindowEnd=newWindowStart+windowLength;

    if (newWindowEnd > windowBuf.length)
    {
      //debug("sliding window back to 0");
      //set window back to beginning
      System.arraycopy(windowBuf, newWindowStart, windowBuf, 0,
                       dataEnd-newWindowStart);

      //set dataEnd to end of stuff we copied 
      dataEnd=dataEnd-newWindowStart;
      //we copied everything to start at the oth byte so move the
      //window there
      newWindowStart=0;
    }

    windowStart=newWindowStart;
    windowFull=false;
    inWindow-=distance;
    //debug("sliding window windowBuf now:"+windowBuf);
  }
  


  
  public static void main (String[] args)
  {
    String data="abcdefghijklmnopqrstuvwxyz";
    java.io.ByteArrayInputStream byteInputStream=new java.io.ByteArrayInputStream(data.getBytes());
    WindowInputStream InputStream=new WindowInputStream(byteInputStream, 6);

    try{
      
      while(true)
      {
        System.err.println("window -->"+new String(InputStream.view()));
        System.err.println("-->"+(byte)InputStream.read());
      }
    } catch(IOException e){
      e.printStackTrace();
    }
      
      
    
  } // end of main ()

  static protected boolean debug=false;
  static protected void debug(String s)
  {
    if (debug)
      System.err.println(s);
  }


}

// window has data in it then it reads from this. If the window is
// empty then the byteacters are taken straight from the source stream
// without filling the window.

// The window is filled whenever viewWindow() is called if the end of
// a stream is reached and the window is empty then viewWindow() will
// return a zero length string.

// NOT ANY MORE - make them crate a new one
// The window can be resized, larger or smaller. If it is being
// reduced then an Exception will be thrown if the amount of data in
// the window is greater than the new size - the window will not be
// resized. To make sure the window is empty perform call
// discardWindowContents() or perform a read


    
//    //discard any data in the window and leave it empty    
//    public void discardWindowContents()
//    {
//      windowStart=dataEnd;
//    }
  

//    public void resizeWindow(int newLength) throws WindowReaderException
//    {
//      if (newLength > windowLength) {
//        // make sure the buffer is big enough
//        if (windowBuf.length < newLength)
//        {
//          byte[] newBuf=new byte[goodBufferSizeFor(newLength)];
//          System.arraycopy(windowBuf, 0, newBuf, 0, windowBuf.length);
//          windowBuf=newBuf;
//        }
      
//      } else { //going smaller
//        if (newLength < inWindow())
//          throw new WindowReaderException("Requested size of window won't fit current data in");
//        //can still usse same buffer so carry on
//      }
    
    
//      windowLength=newLength;
//    }





