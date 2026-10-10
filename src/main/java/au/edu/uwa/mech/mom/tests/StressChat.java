
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: StressChat.java,v 1.3 2000/01/19 11:43:23 bdalton Exp $
// Time-stamp: "2000-01-19 15:53:35 barney"

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

package au.edu.uwa.mech.mom.tests;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;


import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.LoginEventListener;
import au.edu.uwa.mech.mom.event.PeerEventListener;
import au.edu.uwa.mech.mom.event.MessageEventListener;
//import au.edu.uwa.mech.mom.event.MessageEvent;

import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.mom.impl.*;
import au.edu.uwa.mech.mom.gui.*;
import au.edu.uwa.mech.gui.Console;
import java.util.Observer;
import java.util.Observable;

//import netscape.security.*;

public class StressChat  implements MessageEventListener, PeerEventListener
{
  
  Channel chatChannel;
  SessionRoot sr;
  Peer us;

  public StressChat(String momurl)
  {
    try
    {    
      sr = new SessionRoot(momurl);
      Peer us = sr.login("guest", "", "");
      chatChannel = (Channel)sr.lookupOrCreate(new ContextURL("chat.channel"));
      
      chatChannel.addMessageEventListener(this);
      chatChannel.addPeerEventListener(this);
      chatChannel.join();
      
    } catch (MOMException e) {
      e.printStackTrace();
    }     
  }

  public void chat() throws InterruptedException, MOMIOException
  {
    String text = "stressbot ...............";
    while(true){
      MOMPacket message = chatChannel.getPacketFactory().message(text.toCharArray());
      message.setUnimportant(true);
      message.setBulk(true);
      chatChannel.sendMessage(message);
      Thread.currentThread().sleep(100);
    }
  }
  
  public void message(Context c, MOMPacket m)
  {
    TheLog.println(m.getSender()+": "+new String((char[])m.getArg()));
  }

  public void peerJoined(Context c, Peer p){
    TheLog.println("Peer joined "+p.toString());
  }
  
  public void peerLeft(Context c, Peer p){
    TheLog.println("Peer left "+p.toString());
  }

  public void joined(Context c){
  }

  public void left(Context c){
  }
  
  public static void main (String[] args)
  {
    try{
      StressChat chat = new StressChat(args[0]);
      chat.chat();
    } catch (Exception e){
      e.printStackTrace();
    }
    
  } // end of main ()
  
}



