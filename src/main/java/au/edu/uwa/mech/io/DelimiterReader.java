
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: DelimiterReader.java,v 1.2 1999/06/28 12:47:21 bdalton Exp $
// Time-stamp: "1999-06-28 18:12:08 barney"

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
// stream. The Delimiter can be multiple characters. A read is
// performed by calling nextToken(), the String upto the delimiter is
// returned, and the delimiter is discarded. This behaviour can be
// changed by settgin discardDelimiters to false. The delimiter can be
// changed after each read. Each call to nextToken will only read to
// the end of the delimiter. If you want better performance, chanin
// this with a BufferedReader first

package au.edu.uwa.mech.io;

import java.io.Reader;

public class DelimiterReader 
{
  
  Reader source;
  WindowReader windowReader;
  String delimiter;
  boolean eof;
  char firstCharOfDelimiter;
  boolean multiCharDelimiter;
  int delimiterLength;
  
  
  public DelimiterReader(Reader source, String delimiter) {
    this.source=source;
    eof=false;
    setDelimiter(delimiter);
  }

  public void setDelimiter(String delimiter) 
  {
    this.delimiter       = delimiter;
    firstCharOfDelimiter = delimiter.charAt(0);
    multiCharDelimiter   = (delimiter.length() >1 ? true : false);
    delimiterLength      = delimiter.length();
    windowReader         = new WindowReader(source, delimiter.length());
  }

  public boolean hasMoreTokens() {return !eof;}

  
  // need a more effeicient algorithm for multichar delimiters
//    int firstDelim=delimiter.charAt(0);

//    while(noMatch)
//    {
      
//      //find match for first character of delimiter int window
//      String window=windowReader.view();
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
   

  public String nextToken() throws java.io.IOException
  {
    if (multiCharDelimiter == true)
      return nextTokenMultiChar();
    else
      return nextTokenSingleChar();
  }


  protected  String nextTokenMultiChar() throws java.io.IOException
  {
    StringBuffer token=new StringBuffer();
    char[] buf=new char[delimiter.length()];
    //String windowContents;
    
    
    top_loop : while (! windowReader.view().equals(delimiter))
    {
      //read a single character first
      int c=windowReader.read();
      if (c != -1)
        token.append((char)c);
      else {
        eof =true; 
        break top_loop;
      }

      
      int readTo;
      //readToNextPossibleMatch     
      while ((readTo=windowReader.view().indexOf(firstCharOfDelimiter)) != 0)
      {
        if (readTo == -1)
          readTo = delimiterLength;

        int readIn=windowReader.read(buf, 0, readTo);
        if (readIn == -1)
        {
          eof = true;
          break top_loop;
        }
        token.append(buf,0,readIn);
        //System.err.println("readTo:"+readTo+" readIn:"+readIn+" token "+token);
      }
    }
    //discard/keep token here
    if(!eof)
      windowReader.read(buf);
    
    return token.toString();
  }


  protected  String nextTokenSingleChar() throws java.io.IOException
  {
    StringBuffer token=new StringBuffer();
    char[] buf=new char[delimiter.length()];

    //use this if delemiter is a single char
    while (!windowReader.view().equals(delimiter))
    {
      int c=windowReader.read();
      if (c != -1)
        token.append((char)c);
      else {
        eof =true; 
        break;
      }
      
    }
    //discard/keep token here
    //char[] temp = new char[delimiter.length()];
    if (!eof)
     windowReader.read(buf);
    
    return token.toString();
  }

  public static void main (String[] args)
  {
    String data="ab|cde|||fghi|||jklmnopq|||rs||||||tuvwxyz|||";
    java.io.CharArrayReader charReader=new
      java.io.CharArrayReader(data.toCharArray());
    DelimiterReader r = new DelimiterReader(charReader, "|||");
    try{
      
      while (r.hasMoreTokens())
      {
        System.err.println("Token -->"+r.nextToken()+"<--");
      }
      
    } catch (java.io.IOException e) {
      e.printStackTrace();  
    }
    
    
  } // end of main ()
  
  
}




