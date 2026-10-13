
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SimpleContext.java,v 1.34 2000/01/28 11:20:54 bdalton Exp $
// Time-stamp: "2000-01-28 18:35:22 barney"

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


package au.edu.uwa.mech.mom; //inserted by perl script 'checkPackages'

import au.edu.uwa.mech.mom.event.PeerEventListener;
import au.edu.uwa.mech.mom.event.ReplyListener;
import au.edu.uwa.mech.util.HashVector;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.util.TimedWait;
import au.edu.uwa.mech.util.TimeoutException;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Hashtable;

//import au.edu.uwa.mech.util.ContainedObservable;

public class SimpleContext implements Context, Receiver
{
  protected SessionRoot root;
  protected Vector      peerEventListeners = new Vector();
  protected boolean     joined             = false;
  protected Vector      peers              = new Vector();

                                // has of vectors of functions to call
                                // for each packet name
  protected HashVector packetProcessors = new HashVector();
                                // A hashtable of objects wainting for
                                // replies from other parts of the MOM system
  protected Hashtable  replyListeners   = new Hashtable();
                                // A hashtable that contains ids of
                                // messages we are waiting for replies
                                // from. Once the values is changed
                                // from an id to the returned
                                // packet.
  protected Hashtable  waitingList      = new Hashtable();
  ContextURL           contextURL;

  public SimpleContext() 
  {                             // would be nice to be able to specify
                                // handling of replies here too
    packetProcessors.put(MOMPacket.Values.PEER_JOINED, new PeerJoinedProcessor());
    packetProcessors.put(MOMPacket.Values.PEER_LEFT, new PeerLeftProcessor());
  }
  
  
  public void join() throws MOMIOException, MOMPermissionException, MOMNotFoundException
  {
    if (!joined)
    {
      MOMPacket packet=getPacketFactory().peerRequest(getContextURL());      
      send(packet);             // mom knows who we are from the connection
                                // wait for peerJoined event to let us
                                // know we joined. What about if
                                // there's an error?
    }
    
  }


  public String toString(){
    return "{context "+contextURL.toString()+peers+"}";
  }
  
  
  /**
     * Get the value of peers.
     * @return Value of peers.
     */
  public Vector getPeers() {return peers;}
  

  
  public void leave() throws MOMIOException, MOMPermissionException
  {
    if (joined)
    {
      MOMPacket packet=getPacketFactory().leaveRequest(getContextURL());      
      send(packet);
    }
  }

                                //should inherit these functions
  public void setRoot(SessionRoot root)
  {
    this.root=root;
    //root knows this is an eventhandler
    //root.addEventHandler(getContextURL(), this);
  }
  

  
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


  public MOMPacketFactory getPacketFactory()
  {
    return root.getPacketFactory();
  }
  
  
                                // should be of type MOMPacket only
  public final void receive(Object source, Object arg)
  { 
    MOMPacket packet=(MOMPacket)arg;

    //look up a reply listener for this object if we have one call its
    //receiveReply method if it returns true take it off the list

    boolean processed=false;
    
    Integer ref = packet.getRef();
    if (ref != null)
    {    
      ReplyListener replyListener = (ReplyListener) replyListeners.get(packet.getRef());
      if (replyListener != null)
      {
        processed=true;
        boolean result = replyListener.receiveReply(packet);
        if (result == true)
          replyListeners.remove(packet.getRef());
      }
    }
    
                                // also send packet onto any objects that
                                // have specified an interest in
                                // events of this name
    Vector pps=packetProcessors.getVector(packet.getName());
    if (pps != null){
      for (Enumeration e=pps.elements();e.hasMoreElements();)
        ((PacketProcessor)e.nextElement()).process(packet);
      processed=true;
    }
    
    if(!processed)
      debug("Packet "+packet+" wasn't of interest to anyone");
  }
  

  final void send(MOMPacket packet, ReplyListener rl) throws MOMIOException
  {
    replyListeners.put(packet.getID(), rl);
    send(packet);
  }
  

  
  final void send(Object obj)
    throws MOMIOException
  {
    MOMPacket packet=(MOMPacket)obj;
    packet.setContextURL(getContextURL());
    root.sendDownstream(packet);
  }

  public void removeListenerFor(Integer ID){
    replyListeners.remove(ID);
  }
  

  public final Object sendAndReceive(Object arg) throws MOMIOException
  {
    return sendAndReceive(arg, 0);
  }
  
  
  public final Object sendAndReceive(Object arg, long waitTime) throws MOMIOException
  {
                                // only know how to deal with mom
                                // packets at the moment 
    try{
      MOMPacket packet = (MOMPacket) arg;
      Integer   id     = packet.getID();
      synchronized (id){
        //waitingList.put(id, id); //provides an easy way to get the
                                // exact id reference back again so
                                // that we can synchronize on it. This
                                // will get replaced by the packet
                                // returned by the mom
        send(packet, new SynchronousListener(id));
        TimedWait.wait(id, waitTime);
        return waitingList.remove(id);
      }   
    } catch (InterruptedException e){
      e.printStackTrace();
      throw new MOMIOException ("sendAndReceive Interrupted "+e.getMessage());
    } catch (TimeoutException e){
      e.printStackTrace();
      throw new MOMIOException ("Timeout of "+(waitTime/1000)+" seconds expired while waiting for reply.");
    } 
  }
  
                                // PeerEventHandler a consumer for
                                // events
  public void addPeerEventListener(PeerEventListener eh)
  {
    peerEventListeners.addElement(eh);
  }

  public void removePeerEventListener(PeerEventListener eh)
  {
    peerEventListeners.removeElement(eh);
  }
  
  
                                // called for PEER_JOINED events
  class PeerJoinedProcessor implements PacketProcessor 
  {
                                //what about error messages when we
                                //fail to join?
    
    public void process(MOMPacket packet)
    {
    
      //debug("SimpleContext::receive "+packet.getPacket());
                                //if (packet instanceof PeerEvent)

      
      Object eventArg=packet.getArg();
      if (root.getUs().equals(eventArg)){
        joined=true;            // if peer is us then joined = true
        for (Enumeration en=peerEventListeners.elements(); en.hasMoreElements();)
          ((PeerEventListener)en.nextElement()).joined(SimpleContext.this);
      }
      
                                
      peers.addElement(eventArg);// arg should be a peer (ought to check)
      for (Enumeration en=peerEventListeners.elements(); en.hasMoreElements();)
        ((PeerEventListener)en.nextElement()).peerJoined(SimpleContext.this, (Peer)eventArg);
    }
  }

                                // called for PEER_LEFT packets
  class PeerLeftProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      Object eventArg=packet.getArg();
      if (root.getUs().equals(eventArg)){
        for (Enumeration en=peerEventListeners.elements(); en.hasMoreElements();)
          ((PeerEventListener)en.nextElement()).left(SimpleContext.this);
        joined=false;
      }
      
      
      peers.removeElement(eventArg);
      for (Enumeration en=peerEventListeners.elements(); en.hasMoreElements();)
        ((PeerEventListener)en.nextElement()).peerLeft(SimpleContext.this, (Peer)eventArg);
        //observable.setChanged();
    }
  }


  class SynchronousListener implements ReplyListener 
  {
    Integer mutex;
    
    SynchronousListener(Integer mutex) {
      this.mutex = mutex;
    }
    

    public boolean receiveReply(MOMPacket packet)
    {
      synchronized(mutex){
        //debug("got reply to "+mutex);
        waitingList.put(mutex, packet);
        mutex.notifyAll();      // the sender will be waiting on this 
      }
      return true;
      //}
    }
  }
  
  
  
  static protected boolean debug=true;
  static protected void debug(String s)
  {
    if (debug)
      TheLog.println(s);
  }


}

  
//    Context parent;
  
//    /**
//       * Get the value of parent.
//       * @return Value of parent.
//       */
//    public Context getParent() {return parent;}
  
//    /**
//       * Set the value of parent.
//       * @param v  Value to assign to parent.
//       */
//    public void setParent(Context  v) {this.parent = v;}
  





  /**
     * Set the value of peers.
     * @param v  Value to assign to peers.
     */
  //  public void setPeers(Vector  v) {this.peers = v;}
  
  
//    //public ContainedObservable observable() {return observable;}
//    public String getName(){return name;} 
//    public void setName(String  v){this.name=v;} 
//    String name;

  
  
//    public void receive(Event e, Object ea)
//    {
//      debug("context "+getContextURL()+" received event "+e+","+ea);

//      //do local housekeeping first
//      localEventHandler(e, ea);
//      // send message to messageconsumers
//      callRegisteredEventHandlers(e, ea);
    
//    }

//    protected void callRegisteredEventHandlers(Event e, Object ea)
//    {
//      if (e instanceof PeerEvent)
//      {
//        switch (
//        for (Enumeration en=peerEventHandlers.elements(); en.hasMoreElements();)
//          ((PeerEventHandler)en.nextElement()).receive(this, e, ea);    
//      } // end of if (e instanceof PeerEventHandler)
    
//    }

  
  //register a consumer for events
  //public void register(EventConsumer ec)
 //   //{
//      consumers.add(ec);
//    }

//    public void deregister(EventConsumer ec)
//    {
//      consumers.remove(ec);
//    }
  
//    RootSession rootSession;
  
//    /**
//       * Get the value of rootSession.
//       * @return Value of rootSession.
//       */
//    public RootSession getRootSession() {return rootSession;}
  
//    /**
//       * Set the value of rootSession.
//       * @param v  Value to assign to rootSession.
//       */
//    public void setRootSession(RootSession  v) {this.rootSession = v;}
  



