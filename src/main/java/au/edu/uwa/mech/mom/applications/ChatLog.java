
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ChatLog.java,v 1.4 2000/01/28 11:20:54 bdalton Exp $
// Time-stamp: "2000-01-28 15:04:48 barney"

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

package au.edu.uwa.mech.mom.applications;


import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.*;
import java.io.*;
import java.util.*;

public class ChatLog implements MessageEventListener 
{
  
  Channel chatChannel;
  SessionRoot sr;
  Peer us;
  BufferedWriter writer;
  
  public ChatLog(BufferedWriter writer, String momurl)
  {
    this.writer = writer;
    try
    {    
      sr = new SessionRoot(momurl);
      Peer us = sr.login("guest", "", "observer");
      chatChannel = (Channel)sr.lookupOrCreate(new ContextURL("chat.channel"));
      chatChannel.join();
      chatChannel.addMessageEventListener(this);
    } catch (MOMException e) {
      e.printStackTrace();
    }     
  }

  public void message(Context source, MOMPacket m){
    try{
      Date now = new Date();
      writer.write(now.getTime()+"!!"+now+"!!"+m.getSender()+"!!"+new String((char[])m.getArg())+"!!");
      writer.newLine();
      writer.flush();
    }
    catch (IOException e){
      e.printStackTrace();
    }
  }
  
  public static void main (String[] args)
  {

    try{
      BufferedWriter logfile = new BufferedWriter(new FileWriter(args[0], true));
      ChatLog hint = new ChatLog(logfile, args[1]);
      Integer i = new Integer(1);
      synchronized(i){i.wait();}
      logfile.close();
    } catch (Exception e){
      e.printStackTrace();
    } finally {

    }
    
  } // end of main ()
  
}



