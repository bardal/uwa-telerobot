//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: FileLog.java,v 1.3 2000/01/28 11:20:54 bdalton Exp $ 
// Time-stamp: "2000-01-28 14:26:46 barney"

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


package au.edu.uwa.mech.util;

import au.edu.uwa.mech.net.CGIPost;

import java.net.*;
import java.io.*;
import java.util.*;

public class FileLog implements Log {

  FileWriter f;
  PrintWriter p;
  boolean open=false;
  
  public FileLog(String logname) throws IOException{
    this(logname, false);
  }

   public FileLog(String logname, boolean append) throws IOException{
    f = new FileWriter(logname, append);
    p = new PrintWriter(f);
    open=true;
  }
  
  public void println(String text) throws IOException{
    synchronized(p){
      p.println(text);
      p.flush();
    }
  }

  public void close() throws IOException{
    if (open){
      f.close();
      open = false;
    }
  }

  protected void finalize() throws IOException{
      close();
  }
  
  
}
