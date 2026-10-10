
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Token.java,v 1.14 2000/01/23 13:02:32 bdalton Exp $
// Time-stamp: "2000-01-23 10:29:35 barney"

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
import  au.edu.uwa.mech.mom.Packet;
import  au.edu.uwa.mech.mom.event.TokenEventListener;
import  java.util.Enumeration;
import  java.util.Vector;

public class Token extends SimpleContext
{
  public Token()
  {
    packetProcessors.put(MOMPacket.Values.GRABBED, new GrabbedProcessor());
    packetProcessors.put(MOMPacket.Values.RELEASED, new ReleasedProcessor());
  }
  

  //how to we send errors back to the user?
  public void grab()  throws MOMIOException, MOMPermissionException, MOMNotFoundException
  {
    send(getPacketFactory().grabRequest(getContextURL()));
  }

  public void release() throws MOMIOException, MOMPermissionException, MOMNotFoundException
  {
    send(getPacketFactory().releaseRequest(getContextURL()));
  }

  public void giveTo(Peer p) throws MOMIOException, MOMPermissionException, MOMNotFoundException
  {
    send(getPacketFactory().giveRequest(getContextURL(), p));
  }  

  public Peer getOwner()
  {
    return owner;
  }
  
  public void addTokenEventListener(TokenEventListener eh)
  {
    tokenEventListeners.addElement(eh);
  }

  public void removeTokenEventListener(TokenEventListener eh)
  {
    tokenEventListeners.removeElement(eh);
  }
  
  public static String TYPE_NAME="token";
  int    state;
  Peer   owner;
  Vector tokenEventListeners=new Vector();
  
                                // called for GRABBED events
  class GrabbedProcessor implements PacketProcessor 
  {

    public void process(MOMPacket packet)
    {
      owner=(Peer)packet.getArg();
      for (Enumeration en=tokenEventListeners.elements(); en.hasMoreElements();)
        ((TokenEventListener)en.nextElement()).tokenGrabbed(Token.this, owner);
    }
    
  }

                                // called for RELEASED events
  class ReleasedProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      owner=null;
      for (Enumeration en=tokenEventListeners.elements(); en.hasMoreElements();)
        ((TokenEventListener)en.nextElement()).tokenReleased(Token.this);

    }
    
  }
  
}

 
//    public void receive(Object arg)
//    {
//      Packet e=(Packet)arg;
//      try{
//        TokenEvent te=(TokenEvent)e;
//        //debug("Token "+getContextURL()+" received event "+e+","+arg);
//        switch (te.getEventType())
//        {
//          case TokenEvent.REQUEST:
//            debug("token request for "+getContextURL()+" by "+te.getSubject());
//            break;
//          case TokenEvent.NEWOWNER:
//            debug(getContextURL().toString()+" now owned by "+te.getSubject().getNickname());
//            break;
//          default:
//            debug("token "+getContextURL()+" received uncoded event "+e+","+arg);
//            break;
//        } // end of switch (te.getEventType())

//        //debug("event is from "+te.getProperty(Packet.TIMESTAMP_KEY)+" MOM time now "+root.getTime());
      
//      } catch (ClassCastException ex)
//      {
//        debug("Token "+getContextURL()+" received unexpected packet type "+e+","+arg);
//        debug(ex.toString());
//      }
    
 


