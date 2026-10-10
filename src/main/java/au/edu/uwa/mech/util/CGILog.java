//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: CGILog.java,v 1.2 1999/12/24 10:34:10 bdalton Exp $ 
// Time-stamp: "1999-12-17 09:03:47 barney"

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

public class CGILog implements Log {

  StringBuffer logBuffer = new StringBuffer();
  URL postURL;
  String logname;
  static final int pollTime = 5000;
  Poster poster;
  
  public CGILog(String logname, URL postURL){
    this.logname = logname;
    this.postURL = postURL;
    poster = new Poster();
    poster.start();
  }
  
  public void println(String Text){
    synchronized(logBuffer){
      logBuffer.append(Text);
      logBuffer.append("\r\n");
    }
  }

  class Poster extends Thread
  {
    public void run()
    {
      while(true)
      {
        try{sleep(pollTime);}
        catch(InterruptedException e){
          e.printStackTrace();
        }
        //System.out.println("Posting log back to server");
        
        String toPost=null;
        synchronized(logBuffer){
          toPost = logBuffer.toString();
          logBuffer.setLength(0);
        }
        //now post it
        if (toPost.length() >0){
          
          Hashtable postPairs = new Hashtable();
          postPairs.put("logname", logname);
          postPairs.put("text", toPost);
          //System.out.println("Posting to "+postURL+" stuff:"+postPairs);
          try{
            CGIPost.post(postURL, postPairs);
          } catch (IOException e){
            e.printStackTrace();
          }
        }
      }
    } 
  }
}
