
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Chat.java,v 1.15 2000/01/16 10:01:51 bdalton Exp $
// Time-stamp: "2000-01-15 17:55:14 barney"

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

public class Chat extends java.applet.Applet
                            implements LoginEventListener //PeerEventListener,
                          //MessageEventListener, ActionListener
{
  ChatPanel cp;
  LoginPanel lp;
  Frame mainFrame;
  
  Channel chatChannel;
  SessionRoot sr;


  public Chat()
  {
    
  }
  
  
  public void init()
  {
    try{
      
      setupConnection(getParameter("momurl"));
      
      mainFrame=new Frame();
      mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
          public void windowClosing(java.awt.event.WindowEvent e) {e.getWindow().setVisible(false);}
        });
      setupGUI(mainFrame);

      Button launch=new Button("Launch Chat Applet");
      launch.addActionListener(new java.awt.event.ActionListener() {
          public void actionPerformed(ActionEvent e)
          {mainFrame.setVisible(true);} 
        });
          
                               
      add(launch);
      //fr.pack();
      //fr.show();

      
    } catch(Throwable e){
      TheLog.println("Major error in init :"+e.getMessage());
      e.printStackTrace(System.err);
       
    }
    
  }

  protected void setupConnection(String momurl)
  {
    try
    {    
      //MOMConnection c=MOMConnectionFactory.build("rmi");
      //c.setURL("moms://"+momlocation+":"+4444);      
      sr = new SessionRoot(momurl);
      sr.addLoginEventListener(this); 
      
    } catch (MOMException e) {
      e.printStackTrace();
    }     
  }
  
  
  
  public void setupGUI(Frame mainFrame)
  {
    this.mainFrame=mainFrame;
    mainFrame.setLayout(new BorderLayout());
    lp=new LoginPanel();
    cp=new ChatPanel();
    lp.setLoginHandler(sr);
    mainFrame.add("North", lp);
    mainFrame.add("South", cp);
    mainFrame.pack();
    //frame.pack();
    //frame.show();
  }
  

  public void loggedIn(Peer p)
  {
    try{
      
    chatChannel = (Channel)sr.lookupOrCreate(new ContextURL("chat.channel"));
    cp.setChannel(chatChannel);
    
    } catch (MOMException e) {
      TheLog.println("Failed to get chat channel "+e.getMessage());
    }  
  }
  
  public void loggedOut(Peer p)
  {
    TheLog.println(p+" logged out");
  }

  
  
  public static void main (String[] args)
  {
    Frame frame = new Frame("MOM Chat Applet");
    Chat chat = new Chat();
    //try {
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {System.exit(0);}
      });

    chat.setupConnection(args[0]);
      
      //} catch (MOMConnectionBuildException e) {
      //System.err.println(e.toString());
      //}

    chat.setupGUI(frame);

    frame.pack();
    frame.show();
      
  
    
  } // end of main ()
  
}



