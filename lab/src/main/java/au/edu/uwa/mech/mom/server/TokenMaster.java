
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TokenMaster.java,v 1.14 2000/01/23 13:02:33 bdalton Exp $
// Time-stamp: "2000-01-23 10:11:50 barney"

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

package au.edu.uwa.mech.mom.server; //inserted by perl script 'checkPackages'

import au.edu.uwa.mech.mom.*;


public class TokenMaster extends ContextMaster
{
  public TokenMaster() 
  {
    packetProcessors.put(MOMPacket.Values.JOIN,    new JoinProcessor());
    packetProcessors.put(MOMPacket.Values.GRAB,    new GrabProcessor());
    packetProcessors.put(MOMPacket.Values.GIVE,    new GiveProcessor());
    packetProcessors.put(MOMPacket.Values.RELEASE, new ReleaseProcessor());
  }
  
  public static String TYPE_NAME = "token";
  protected Peer       owner;

  public Peer getOwner() {
    return owner;
  }
  
  
  class JoinProcessor implements PacketProcessor
  {
    // let the newly joined peer know who owns the token
    public void process(MOMPacket packet) 
    {
      if (owner != null){  
        MOMPacket event = getPacketFactory().event(MOMPacket.Values.GRABBED);
        event.setArg(owner);    
        sendTo(packet.getSender(), event);
      }
      
    }
  }  
  
  
  
                                // called for a grab request - if the
                                // manager appoves, so we just give
                                // token to requestor.
  class GrabProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      //makes more snese to ask the manager here, not int contextmaster
      // what about a reply?
      owner = packet.getSender();
      debug("New owner "+owner+" of "+getContextURL());
      MOMPacket event = getPacketFactory().event(MOMPacket.Values.GRABBED);
      event.setArg(owner);
      send(event);
    }
  }

                                 // peer released token - manager has
                                 // already checked this is ok 
  class ReleaseProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      // what about a reply?
      owner = null;
      MOMPacket event = getPacketFactory().event(MOMPacket.Values.RELEASED);
      send(event);
    }
  }
                                // called when a peer has sent a GIVE
                                // token message
  class GiveProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      // what about a reply?
      owner = (Peer)packet.getArg(); 
      MOMPacket event = getPacketFactory().event(MOMPacket.Values.GRABBED);
      event.setArg(owner);
      send(event);
    }
  }

}


  
  
//    public void receive(Object arg, Peer p) throws MOMPermissionException
//    {
//      try{
//        MOMPacket te=(MOMPacket)arg;
     //   te.setSubject(p);
//        send(te); //let everyonr know about the request
//        switch (te.getEventType()) {
        
//          case TokenEvent.REQUEST:
//            owner=p;
//            te=new TokenEvent();
//            te.setSubject(p);
//            te.setEventType(te.NEWOWNER);

//            try{te.setPropertyReadOnly(te.TIMESTAMP_KEY, new java.util.Date());}
//            catch (MOMReadOnlyException ex){;}
          
//            send(te);
//            break;
//          case TokenEvent.RELEASE:
//            break;
//          case TokenEvent.GIVETO:
//            break;
//          case TokenEvent.LEND:
//            break;
          
//        //      }
      
//      } catch (ClassCastException ex)
//      {
//        debug("TokenMaster "+getContextURL()+" received unexpected event type "+arg);
//        debug(ex.toString());
//      }

//    }


