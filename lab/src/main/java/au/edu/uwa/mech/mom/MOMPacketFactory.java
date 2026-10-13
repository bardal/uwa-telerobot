
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMPacketFactory.java,v 1.14 2000/01/18 17:48:03 bdalton Exp $
// Time-stamp: "2000-01-18 18:08:06 barney"

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

import java.util.Hashtable;

import au.edu.uwa.mech.util.Clock;
import au.edu.uwa.mech.util.TheLog;
import java.util.Date;

public class MOMPacketFactory implements Clock
{

  public MOMPacketFactory() {
    this.clock = this;
  }
  
  
  public MOMPacketFactory(Clock clock)
  {
    this.clock = clock;
  }

  public MOMPacket wrap(Packet p){
    return new MOMPacket(p);
  }
  
  
                                // should this be subclassed somehow?
  public MOMPacket loginRequest(String userName, String password, String nickname)
  {
    MOMPacket packet=basicPacket(MOMPacket.Values.LOGIN);
    packet.setFamily(MOMPacket.Values.REQUEST);
    Hashtable args=new Hashtable();
    args.put(MOMPacket.Keys.USERNAME, userName);
    args.put(MOMPacket.Keys.PASSWORD, password);
    args.put(MOMPacket.Keys.NICKNAME, nickname);
    packet.setArg(args);
    return packet;
  }

  public MOMPacket logoutRequest(Peer p)
  {
    MOMPacket packet=request(MOMPacket.Values.LOGOUT);
    packet.setArg(p);
    return packet;
  }
                                // form a request to create a context
                                // with a manager
  public MOMPacket createRequest(ContextURL context, Manager manager)
  {
    MOMPacket packet=request(MOMPacket.Values.CREATE);
    Hashtable args=new Hashtable();
    args.put(MOMPacket.Keys.CONTEXTURL, context);
    args.put(MOMPacket.Keys.MANAGER, manager);
    packet.setArg(args);
    return packet;
  }
  
                                //form a request to create a context
                                //no manager
  public MOMPacket createRequest(ContextURL context) {
    MOMPacket packet=request(MOMPacket.Values.CREATE);
    Hashtable args=new Hashtable();
    args.put(MOMPacket.Keys.CONTEXTURL, context);
    packet.setArg(args);
    return packet;
  }

                                //form request to check if a context
                                //exists 
  public MOMPacket existsRequest(ContextURL context) {
    return contextRequest(context, MOMPacket.Values.EXISTS);
  }

                                //form request to join context
  public MOMPacket peerRequest(ContextURL context) {
    return contextRequest(context, MOMPacket.Values.JOIN);
  }

  public MOMPacket leaveRequest(ContextURL context) {
    return contextRequest(context, MOMPacket.Values.LEAVE);
  }
  
  public MOMPacket grabRequest(ContextURL context) {
    return contextRequest(context, MOMPacket.Values.GRAB);
  }

  public MOMPacket releaseRequest(ContextURL context) {
    return contextRequest(context, MOMPacket.Values.RELEASE);
  }
                                // give packet to p
  public MOMPacket giveRequest(ContextURL context, Peer p) {
    MOMPacket packet=contextRequest(context, MOMPacket.Values.GIVE);
    packet.setArg(p);
    return packet;
  }

  
                                //general purpose request with context
                                //as an arg 
  public MOMPacket contextRequest(ContextURL context, String name)
  {
    MOMPacket packet = basicPacket(name);
    packet.setFamily(MOMPacket.Values.REQUEST);
    packet.setArg(context);
    return packet;
  }

  
  
  public MOMPacket message(Object arg)
  {
    MOMPacket packet = message();
    packet.setArg(arg);
    return packet;
  }

  public MOMPacket message()
  {
    MOMPacket packet = basicPacket(MOMPacket.Values.MESSAGE);
    packet.setFamily(MOMPacket.Values.EVENT);
    return packet;
  }

  
  
                                //peer event
  public MOMPacket event(String name)
  {
    MOMPacket packet = basicPacket(name);
    packet.setFamily(MOMPacket.Values.EVENT);
    return packet;
  }

  public MOMPacket request(String name)
  {
    MOMPacket packet = basicPacket(name);
    packet.setFamily(MOMPacket.Values.REQUEST);
    return packet;
  }


  
  public MOMPacket replyTo(MOMPacket packet)
  {
    MOMPacket reply = basicPacket(packet.getName());
    if (packet.getSender() != null)
      reply.setTO(packet.getSender());
    reply.setFamily(MOMPacket.Values.REPLY);
    reply.setRef(packet.getID());
    reply.setContextURL(packet.getContextURL());
    return reply;
  }

  public MOMPacket replyError(MOMPacket packet)
  {
    MOMPacket reply = basicPacket(MOMPacket.Values.ERROR);
    reply.setFamily(MOMPacket.Values.REPLY);
    reply.setRef(packet.getID());
    return reply;
  }

  
  
                                //basic packet that contains time/id info
  public MOMPacket basicPacket(String name)
  {
    Packet _p=new Packet();
    MOMPacket p=new MOMPacket(_p);
    p.setName(name);
    p.setTimestamp(new Long(clock.getTime()));
    p.setID(new Integer(++lastID));
    return p;
  }

  public long getTime() {
    return new Date().getTime();
  }
  
  
  static int lastID=0;  
  
  public static void main (String[] args)
  {
    MOMPacketFactory mpf=new MOMPacketFactory();
    MOMPacket p=mpf.loginRequest("asdfasd", "asdfad", "asdfasf");
    MOMPacket r=mpf.replyError(p);
    TheLog.println(p);
    TheLog.println(r);
    
    //Packet lp=loginRequest("barney", "3058", "errrrrrrrrrrrrrrrr");
    //System.err.println(lp);
  } // end of main ()

  Clock clock;
  
}




