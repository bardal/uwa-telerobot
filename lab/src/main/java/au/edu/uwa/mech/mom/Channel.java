
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Channel.java,v 1.27 2000/01/18 17:48:03 bdalton Exp $
// Time-stamp: "2000-01-19 01:23:29 barney"

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

package au.edu.uwa.mech.mom;

import java.util.Vector;
import java.util.Enumeration;

import au.edu.uwa.mech.mom.Packet;
import au.edu.uwa.mech.util.TheLog;
//import au.edu.uwa.mech.mom.event.MessageEvent;
import au.edu.uwa.mech.mom.event.MessageEventListener;
import au.edu.uwa.mech.mom.event.ReplyListener;

public class Channel extends SimpleContext
{
  public Channel() {
    packetProcessors.put(MOMPacket.Values.MESSAGE, new MessageProcessor());
  }
  

  //why not use the default send?
  //let listener know when we get a reply to this message
  public void sendMessage(MOMPacket packet, ReplyListener rl)
    throws MOMIOException
  {
    //MOMPacket packet=getPacketFactory().event(MOMPacket.Values.MESSAGE);
    //packet.setArg(obj);
    // setNAme?
    packet.setName(MOMPacket.Values.MESSAGE);
    send(packet, rl);
  }

  public void sendMessage(MOMPacket packet)
    throws MOMIOException
  {
    packet.setName(MOMPacket.Values.MESSAGE);
    send(packet);
  }

  //would be nice to have a sendXMessage or something 
  public void sendXMessage(MOMPacket packet)
    throws MOMIOException
  {
    packet.setExclude(root.getUs());
    packet.setName(MOMPacket.Values.MESSAGE);
    send(packet);
  }
  
  public void sendReplyTo(MOMPacket packet, Object arg) throws MOMIOException
  {
    MOMPacket replyPacket=getPacketFactory().replyTo(packet);
    replyPacket.setArg(arg);
    sendMessage(replyPacket);
  }
  
  
  public void addMessageEventListener(MessageEventListener eh)
  {
    messageEventListeners.addElement(eh);
  }

  public void removeMessageEventListener(MessageEventListener eh)
  {
    messageEventListeners.removeElement(eh);
  }

  protected Vector messageEventListeners = new Vector();
  public static String TYPE_NAME="channel";

  class MessageProcessor implements PacketProcessor
  {
    
    public void process(MOMPacket packet)
    {
      //debug("Channel::receive event our curl"+getContextURL()+" arg:"+arg);
      //MOMPacket packet=(MOMPacket)arg;
      //TheLog.println("channel forwarding message to "+ messageEventListeners);
      
      
      for (Enumeration en=messageEventListeners.elements(); en.hasMoreElements();)
        ((MessageEventListener)en.nextElement()).message(Channel.this, packet);
    }
  }
}


                                // need to do some work on this
                                // function probably sgould take an
                                // Object as an argument, not MOMPacket

  //public void subscribe();
  //public void unsubscribe();


  
  //default implementation will do fine
  //public void receive(Event e, EventArgs ea)
  //{
  //  debug("channel "+getContextURL()+" received event "+e+","+ea);
    // send message to messageconsumers
    
  //}
  

  //public void sendTo(Message m);
  //public void receive(Message m);
  //public void addConsumer(MessageConsumer c);
