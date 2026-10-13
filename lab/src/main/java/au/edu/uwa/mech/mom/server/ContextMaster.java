
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ContextMaster.java,v 1.27 2000/01/23 13:02:32 bdalton Exp $
// Time-stamp: "2000-01-23 10:13:31 barney"

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
//import au.edu.uwa.mech.mom.event.PeerEvent;
//import au.edu.uwa.mech.mom.Packet;
import au.edu.uwa.mech.util.HashVector;
import au.edu.uwa.mech.util.TheLog;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;

public class ContextMaster
{

  public ContextMaster()
  {
    this.parent=null;
    this.creator=null;
    packetProcessors.put(MOMPacket.Values.JOIN, new JoinProcessor());
    packetProcessors.put(MOMPacket.Values.LEAVE, new LeaveProcessor());
  }
  

  //can we avod a reference to SessionMaster?
  public ContextMaster(ContextURL curl, Peer p, SessionMaster parent) throws MOMPermissionException
  {
    this.parent=parent;
    this.creator=p;
    this.contextURL=curl;
    packetProcessors.put(MOMPacket.Values.JOIN, new JoinProcessor());
    packetProcessors.put(MOMPacket.Values.LEAVE, new LeaveProcessor());
    //if (parent !=null)
    //  parent.addChild(this);
    //need to check for (permission)
    //need to notify interested parties that a new context is available 
    
  }


  
  public boolean joined(Peer p)
  {
    if (peers.contains(p)) return true;
    else return false;
  }

  public void remove(Peer p)
  {
    if (joined(p)){
      MOMPacket pe=getPacketFactory().event(MOMPacket.Values.PEER_LEFT);
      pe.setArg(p);
      send(pe);
      peers.remove(p);
    }
  }
  
  
                                //called by rootSession or by MOM?
  public final void receive(Object arg, Peer p) throws MOMPermissionException
  {
                                // need hash of functions to call for
                                // each Packet type, might need more
                                // than one function per message type
                                // so make it a hash of vectors
    MOMPacket packet=(MOMPacket)arg;
    //should leave this up to individual context managers
    manager.authorise(this, packet);
    Vector pps=packetProcessors.getVector(packet.getName());
    if (pps != null)
      for (Enumeration e=pps.elements();e.hasMoreElements();)
        ((PacketProcessor)e.nextElement()).process(packet);
    else
      TheLog.println("No packetprocessor for "+packet.getName());

    //put this in a nested class that implements the packet processor interface 
    //if(((MOMPacket)arg).getName().equals(MOMPacket.Values.JOIN))
    //   join(p);
  }

  public void send(Object arg)
  {
    ((MOMPacket)arg).setContextURL(contextURL);
    eventRelay.sendTo(peers, arg);
  }
  
  public void sendTo(Peer p, Object arg)
  {
    ((MOMPacket)arg).setContextURL(contextURL);
    eventRelay.sendTo(p,arg);
  }

  public void sendTo(Vector peers, Object arg)
  {
    ((MOMPacket)arg).setContextURL(contextURL);
    eventRelay.sendTo(peers,arg);
  }
  
  
  public void sendX(Peer p, Object arg)
  {
    ((MOMPacket)arg).setContextURL(contextURL);
    Vector peerList = (Vector)peers.clone();
    peerList.removeElement(p);
    eventRelay.sendTo(peerList,arg);
  }
  
  
  public String toString(){
    return "[ContextMaster contextURL="+contextURL.toString()+" peers="+peers;
  }
  
  
  MOMPacketFactory getPacketFactory()
  {
    return packetFactory;
  }

  void setPacketFactory(MOMPacketFactory packetFactory)
  {
    this.packetFactory=packetFactory;
  }

  
  ContextURL contextURL;
  
  /**
     * Get the value of contextURL.
     * @return Value of contextURL.
     */
  public ContextURL getContextURL() {return contextURL;}
  
  /**
     * Set the value of contextURL.
     * @param v  Value to assign to contextURL.
     */
  public void setContextURL(ContextURL  v) {this.contextURL = v;}
  
  
  /**
     * Get the value of parent.
     * @return Value of parent.
     */
  public SessionMaster getParent() {return parent;}
  
  /**
     * Set the value of parent.
     * @param v  Value to assign to parent.
     */
  public void setParent(SessionMaster  v) {this.parent = v;}
  
  
  
  /**
     * Get the value of creator.
     * @return Value of creator.
     */
  public Peer getCreator() {return creator;}
  
  /**
     * Set the value of creator.
     * @param v  Value to assign to creator.
     */
  public void setCreator(Peer  v) {this.creator = v;}
  
  /**
     * Get the value of eventRelay.
     * @return Value of eventRelay.
     */
  public EventRelay getEventRelay() {return eventRelay;}
  
  /**
     * Set the value of eventRelay.
     * @param v  Value to assign to eventRelay.
     */
  public void setEventRelay(EventRelay  v) {this.eventRelay = v;}

  
    
  /**
     * Get the value of manager.
     * @return Value of manager.
     */
  public Manager getManager() {return manager;}
  
  /**
     * Set the value of manager.
     * @param v  Value to assign to manager.
     */
  public void setManager(Manager  v) {this.manager = v;}
  

  protected SessionRootMaster sessionRoot;
  private  EventRelay eventRelay;
  protected Peer creator;
  protected Manager manager=new DefaultManager();
  protected SessionMaster parent;
  protected Vector peers=new Vector();
  //protected MOM mom;
  protected MOMPacketFactory packetFactory;
  protected HashVector packetProcessors = new HashVector();
  
  public static String TYPE_NAME="ContextMaster";


  class JoinProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      Peer newPeer=packet.getSender();
      //System.out.println("Processing join packet "+packet+" for "+ContextMaster.this);
                                // checking for joining parent should
                                // be done by the manager before this
                                // function is called
      //if (parent !=null)          //check we've joined parent first
      //  parent.join(newPeer);
      //manager.join(newPeer);      //throws MOMPermissionException if something is wrong

      //TEMPORARY HACK
      //need to think about what we do about peers that rejoin 

      if (!joined(newPeer)){
        peers.add(newPeer);
        //System.out.println("now joined "+joined(newPeer)+" "+newPeer);
                                //let all peers know about the new member
        MOMPacket pe=getPacketFactory().event(MOMPacket.Values.PEER_JOINED);
        pe.setArg(newPeer);
        sendX(newPeer, pe);

                                //send a list of joined peers to the new member
                                //need to exclude us 
        for (Enumeration e=peers.elements();e.hasMoreElements();)
        {
          pe=getPacketFactory().event(MOMPacket.Values.PEER_JOINED); //set creation time
          pe.setArg(e.nextElement()); //set arg to next peer
          sendTo(newPeer, pe);
        }
      }
      
      //System.out.println("finsished Processing join packet "+packet+" for "+ContextMaster.this);
    }
  }
  

  
  class LeaveProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      Peer peer=packet.getSender();
      //System.out.println("Processing leave packet "+packet+" for "+ContextMaster.this);

      if (joined(peer)){
                                //let all peers know about the new member
        MOMPacket pe=getPacketFactory().event(MOMPacket.Values.PEER_LEFT);
        pe.setArg(peer);
        send(pe);
        peers.remove(peer);
      }

      System.out.println("finsished Processing leave packet");
    }
  }
  

  
  static protected boolean debug=true;
  static protected void debug(String s)
  {
    if (debug)
      System.err.println(s);
  }
  
}


  
  
//    protected String name;
  
//    /**
//       * Get the value of name.
//       * @return Value of name.
//       */
//    public String getName() {return name;}
  
//    /**
//       * Set the value of name.
//       * @param v  Value to assign to name.
//       */
//    public void setName(String  v) {this.name = v;}





