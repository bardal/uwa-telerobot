
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ioUtil.java,v 1.2 1999/08/27 22:30:13 bdalton Exp $
// Time-stamp: "1999-08-26 21:34:17 student"

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


import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.InputStreamReader;


import java.io.IOException;
import java.io.UnsupportedEncodingException;


public class ioUtil
{
  //returns the character after whitespace 
  public static void eatWhitespace(java.io.PushbackReader reader) throws java.io.IOException
  {
    int i;
    do {
      i=reader.read();
      if (i==-1)
        break;
    } while(Character.isWhitespace((char)i));

    if (i!=-1)
      reader.unread(i);    
  }

    //returns the character after whitespace 
  public static void eatWhitespace(java.io.PushbackInputStream is) throws java.io.IOException
  {
    int i;
    do {
      i=is.read();
      if (i==-1)
        break;
      //nasty cast here but I'm in a hurry 
    } while(Character.isWhitespace((char)i));

    if (i!=-1)
      is.unread(i);    
  }
  
  public static byte[] CharToByte(char[] chars, String encoding)
    throws UnsupportedEncodingException
  {
    String s = new String(chars);
    return s.getBytes(encoding);
  }

  public static char[] ByteToChar(byte[] bytes, String encoding)
    throws UnsupportedEncodingException
  {
    String s = new String(bytes, encoding);
    return s.toCharArray();
  }
  
  
}



