
// Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Sun Sep 12 12:41:23 1999
// Version       : $Id: CopyStream.java,v 1.2 1999/09/14 20:46:15 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

// {{{ GNU General Public License
//  This program is free software; you can redistribute it and/or
//  modify it under the terms of the GNU General Public License
//  as published by the Free Software Foundation; either version 2
//  of the License, or (at your option) any later version.

//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
// }}}

package au.edu.uwa.mech.io;

import java.io.*;

public class CopyStream
{
  //use buffered streams if you want to make this run fast
  static public void copy(java.io.InputStream from, java.io.OutputStream to)
    throws java.io.IOException
  {
    for (int b = from.read();b != -1; b=from.read())
      to.write(b);
    to.flush();
  }

  static public void copy(String fromFile, java.io.OutputStream to)
    throws java.io.IOException
  {
    FileInputStream fromF= new FileInputStream(fromFile);
    copy(new BufferedInputStream(fromF), to);
    fromF.close();
  }

  //use buffered io if you want to make this run fast
  static public void copy(java.io.Reader from, java.io.Writer to)
    throws java.io.IOException
  {
    for (int b = from.read();b != -1; b=from.read())
      to.write(b);
    to.flush();
  }

  static public void copy(String fromFile, java.io.Writer to)
    throws java.io.IOException
  {
    FileReader fromF= new FileReader(fromFile);
    copy(new BufferedReader(fromF), to);
    fromF.close();
  } 
  
}



