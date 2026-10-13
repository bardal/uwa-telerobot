
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: DelimiterInputStream.java,v 1.1 1999/08/27 22:30:13 bdalton Exp $
// Time-stamp: "1999-08-26 21:42:06 student"

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


// This Reader is used to read up to a given Delimiter in a
// stream. The Delimiter can be multiple byteacters. A read is
// performed by calling nextToken(), the String upto the delimiter is
// returned, and the delimiter is discarded. This behaviour can be
// changed by settgin discardDelimiters to false. The delimiter can be
// changed after each read. Each call to nextToken will only read to
// the end of the delimiter. If you want better performance, chanin
// this with a BufferedInputStream first

package au.edu.uwa.mech.io;


import au.edu.uwa.mech.util.Array;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

public class DelimiterInputStream 
{
  
  InputStream source;
  WindowInputStream windowInputStream;
  byte[] delimiter;
  boolean eof;
  byte firstByteOfDelimiter;
  boolean multiByteDelimiter;
  int delimiterLength;
  
  
  public DelimiterInputStream(InputStream source, byte[] delimiter) {
    this.source=source;
    eof=false;
    setDelimiter(delimiter);
    //System.out.println("delimiter is "+new String(delimiter));
    
  }

  public void setDelimiter(byte[] delimiter) 
  {
    this.delimiter       = delimiter;
    firstByteOfDelimiter = delimiter[0];
    multiByteDelimiter   = (delimiter.length >1 ? true : false);
    delimiterLength      = delimiter.length;
    windowInputStream         = new WindowInputStream(source, delimiter.length);
  }

  public boolean hasMoreTokens() {return !eof;}

  
  // need a more effeicient algorithm for multibyte delimiters
//    int firstDelim=delimiter.byteAt(0);

//    while(noMatch)
//    {
      
//      //find match for first byteacter of delimiter int window
//      String window=windowInputStream.view();
//      int index=window.indexOf(firstDelim);
//      int doRead=0;
//      //if index <0 no match read whole window
//      //if index ==0 potenetial match at 0 do a string equals
//      //if index >0 then we found a potential match read up to that
//      //point in the window first 

//      if (index < 0)
//      {
//        doRead=delimiter.length();
//      }
//      else
//        doRead = index;
   

  public byte[] nextToken() throws java.io.IOException
  {
    if (multiByteDelimiter == true)
      return nextTokenMultiByte();
    else
      return nextTokenSingleByte();
  }


  protected byte[] nextTokenMultiByte() throws java.io.IOException
  {
    ByteArrayOutputStream token=new ByteArrayOutputStream();
    byte[] buf=new byte[delimiter.length];
    //String windowContents;
    
    
    top_loop : while (!Array.equal(windowInputStream.view(), delimiter))
    {
      //read a single byte first
      int c=windowInputStream.read();
      if (c != -1)
        token.write((byte)c);
      else {
        eof =true; 
        break top_loop;
      }

      
      int readTo;
      //readToNextPossibleMatch     
      while ((readTo=Array.indexOf(windowInputStream.view(), firstByteOfDelimiter)) != 0)
      {
        if (readTo == -1)
          readTo = delimiterLength;

        int readIn=windowInputStream.read(buf, 0, readTo);
        if (readIn == -1)
        {
          eof = true;
          break top_loop;
        }
        token.write(buf,0,readIn);
        //System.err.println("readTo:"+readTo+" readIn:"+readIn+" token "+token);
      }
    }
    //discard/keep token here
    if(!eof)
      windowInputStream.read(buf);
    
    return token.toByteArray();
  }


  protected byte[] nextTokenSingleByte() throws java.io.IOException
  {
    ByteArrayOutputStream token=new ByteArrayOutputStream();
    byte[] buf=new byte[delimiter.length];

    //use this if delemiter is a single byte
    while (!windowInputStream.view().equals(delimiter))
    {
      int c=windowInputStream.read();
      if (c != -1)
        token.write((byte)c);
      else {
        eof =true; 
        break;
      }
      
    }
    //discard/keep token here
    //byte[] temp = new byte[delimiter.length()];
    if (!eof)
     windowInputStream.read(buf);
    
    return token.toByteArray();
  }

  public static void main (String[] args)
  {
    String data="ab|cde|||fghi|||jklmnopq|||rs||||||tuvwxyz|||";
    java.io.ByteArrayInputStream byteInputStream=new
      java.io.ByteArrayInputStream(data.getBytes());
    DelimiterInputStream r = new DelimiterInputStream(byteInputStream, (new String("|||")).getBytes());
    try{
      
      while (r.hasMoreTokens())
      {
        System.err.println("Token -->"+new String(r.nextToken())+"<--");
      }
      
    } catch (java.io.IOException e) {
      e.printStackTrace();  
    }
    
    
  } // end of main ()
  
  
}




