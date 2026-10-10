
// Copyright (C) 1999, 2000 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Mon Sep 13 21:19:07 1999
// Version       : $Id: ChatPanel.java,v 1.6 2000/01/21 15:24:29 bdalton Exp $ 
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


package au.edu.uwa.mech.mom.gui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.Observer;
import java.util.Observable;


import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.PeerEventListener;
import au.edu.uwa.mech.mom.event.MessageEventListener;
//import au.edu.uwa.mech.mom.event.MessageEvent;

import au.edu.uwa.mech.mom.impl.*;
import au.edu.uwa.mech.mom.gui.*;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.util.TheLog;

//import netscape.security.*;

public class ChatPanel extends Panel
  implements MessageEventListener, PeerEventListener, ActionListener
{
  TextArea receiveArea;
  TextField sendField=new TextField();
  List    userList=new List();
  Panel panel = new Panel();

  
  Channel channel;

  public ChatPanel(int rows, int cols)
  {
    receiveArea=new TextArea(rows, cols);
    setup();
  }
  
  public ChatPanel(){
    receiveArea=new TextArea();
    setup();
  }
  
    
  public void setup()
  {
    //setLayout(new BorderLayout());

    setLayout(new BorderLayout(1,1));

    
    Panel p1 = new Panel(new BorderLayout());
    p1.add(new Label("Users", Label.CENTER), "North");
    p1.add(userList=new List(), "Center");

    Panel p2=new Panel(new BorderLayout());
    Label message=new Label("Message ",Label.RIGHT);
    p2.add(message,"West");
    p2.add(sendField, "Center");
    sendField.setEditable(false);

    Panel p3=new Panel(new BorderLayout(0,2));
    p3.add(p2, "North");
    p3.add(receiveArea, "Center");
    receiveArea.setEditable(false);
    
    add(p3, "Center");
    add(p1, "East");
    
    sendField.addActionListener(this);
  }
  

  public void setChannel(Channel c)
  {
    try{

      this.channel=c;
      channel.addMessageEventListener(this);
      channel.addPeerEventListener(this);
      channel.join();
      sendField.setEditable(true);
    
    
    } catch (MOMException e) {
      TheLog.println("channel problem"+e.getMessage());
    }  
  }


  public void message(Context c, MOMPacket m)
  {
    //TheLog.println("got message "+c+","+m);
    receiveArea.appendText(m.getSender()+": "+new String((char[])m.getArg())+"\n");
  }

  public void peerJoined(Context c, Peer p){
    userList.add(p.toString());
  }
  
  public void peerLeft(Context c, Peer p){
    userList.remove(p.toString());
  }
  
  public void joined(Context c){
  }
  
  public void left(Context c){
  }

  
  public void actionPerformed(ActionEvent evt) {
    if(channel == null)
      return;
    
    String text = sendField.getText();
    sendField.selectAll();
    TheLog.println(text);
    try{
      channel.sendMessage(channel.getPacketFactory().message(text.toCharArray()));
    } catch (MOMException e) {
      TheConsole.println("Failed to to send message : "+e.getMessage());
    } 
  }

  public static void main (String[] args)
  {
    Frame f= new Frame();
    f.add(new ChatPanel(10,40));
    f.addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent e) {System.exit(0);}
      });
    f.pack();
    f.show();
  } // end of main ()
  
  
}
