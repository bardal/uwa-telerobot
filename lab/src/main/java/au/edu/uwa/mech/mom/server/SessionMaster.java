
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SessionMaster.java,v 1.25 1999/11/16 16:01:16 bdalton Exp $
// Time-stamp: "1999-11-15 17:14:36 barney"

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

import java.util.*;
import au.edu.uwa.mech.mom.*;
//import au.edu.uwa.mech.mom.event.ChildEvent;
//import au.edu.uwa.mech.mom.Packet;
import au.edu.uwa.mech.util.Factory;
import au.edu.uwa.mech.util.BuildException;




public class SessionMaster extends ContextMaster
{
  public SessionMaster()
  {
    super();
    packetProcessors.put(MOMPacket.Values.JOIN, new SMJoinProcessor());
    packetProcessors.put(MOMPacket.Values.CREATE, new SMCreateProcessor());
  }
  
  
  public SessionMaster(ContextURL curl, Peer p, SessionMaster parent) throws MOMPermissionException
  {
    super(curl, p, parent);
    packetProcessors.put(MOMPacket.Values.JOIN, new SMJoinProcessor());
    packetProcessors.put(MOMPacket.Values.CREATE, new SMCreateProcessor());
  }
  
  //protected SessionManager manager = null;
  protected Vector peers = new Vector();

  protected Hashtable children=new Hashtable();
  protected Vector childrenContextURLs=new Vector();

  public void remove(Peer p)
  {
    if (joined(p)){
      for (Enumeration e=children.elements();e.hasMoreElements();)
        ((ContextMaster)e.nextElement()).remove(p);
      super.remove(p);
    }
  }

  class SMJoinProcessor implements PacketProcessor
  {
    
    public void process(MOMPacket packet) 
    {
      Peer peer=packet.getSender();
      debug("SessionMaster join "+peer);
      //super.join(peer);
      //send alist of children contexts to the newly joined peer
      for (Enumeration e=childrenContextURLs.elements();e.hasMoreElements();)
      {
        MOMPacket ce=getPacketFactory().event(MOMPacket.Values.CHILD_CREATED);
        ce.setArg(((ContextURL)e.nextElement()).getRelativeContextURL());
        sendTo(peer, ce);
      } 
      
    }
  }
  
  class SMCreateProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet) 
    {
      MOMPacket reply=getPacketFactory().replyTo(packet);
      
      try{
                                // need to find out about the manager
                                // that's being requested
                                // CAN we avoid all these casts? very ugly
        Manager manager = (Manager)
          ((Hashtable)packet.getArg()).get(MOMPacket.Keys.MANAGER);

        ContextURL curl = (ContextURL)
          ((Hashtable)packet.getArg()).get(MOMPacket.Keys.CONTEXTURL);
        
        create(curl, packet.getSender(), manager);

                                 // let peer know the result

        reply.setArg(curl);
        reply.setName(MOMPacket.Values.CHILD_CREATED);
        debug("Created context "+curl);
        
      } catch (MOMCreateException ex){
        //debug("SMCreateProcessor::process need to handle MOMCreateException");
        //ex.printStackTrace();
        reply.setArg(ex.toString());
        reply.setName(MOMPacket.Values.ERROR);
      }

      sendTo(packet.getSender(), reply);
     
    }

    
    private ContextMaster create(ContextURL relativeurl, Peer p, Manager manager)
      throws MOMCreateException
    {
      System.out.println("Context "+ getContextURL() + " received create "+ relativeurl);
      
      ContextURL fullURL=new ContextURL(getContextURL(), relativeurl);
      if (sessionRoot.directory.contains(fullURL))
        throw new MOMCreateException("Context "+fullURL+" already exists");

      if (relativeurl.hasParent())
        throw new MOMCreateException("Creation in subsessions not allowed "+relativeurl);
      
      try{
                     
        ContextMaster c =
          (ContextMaster)contextFactory.build(relativeurl.getContextType());
        
        c.setContextURL(fullURL);
        c.setCreator(p);        //c's creator is us
        c.setEventRelay(getEventRelay()); //c uses same event relay as us
        c.setPacketFactory(getPacketFactory());
        c.sessionRoot=sessionRoot;

        if (manager != null)
          c.setManager(manager);
        
        sessionRoot.directory.put(fullURL, c);

        addChild(c);            //add c to our list of children

                                //let everyone else know 
        MOMPacket ce=getPacketFactory().event(MOMPacket.Values.CHILD_CREATED);
        ce.setContextURL(getContextURL());
        ce.setArg(relativeurl);
        sendX(p, ce);
        return c;
      
      } catch (BuildException e) {
        throw new MOMCreateException(e.toString());
      }
    }

    
    private void addChild(ContextMaster child) 
    {
      // should check if child already exists in manager
      // manager.checkAddChild(child, p);
      // if (children.get(child.getContextURL()) != null)
      //    throw new MOMPermissionException("Context "+child.getContextURL().toString()+" already exists");
      children.put(child.getContextURL(), child);
      childrenContextURLs.add(child.getContextURL());
      child.setParent(SessionMaster.this);

            
    }
  }
  

  public static void main (String[] args)
  {
    SessionMaster sm=new SessionMaster();
    
  } // end of main ()


  protected static Factory contextFactory; 
  public static String TYPE_NAME="session";
  
  static {
    
    contextFactory=new Factory();
    contextFactory.register(SessionMaster.TYPE_NAME, "au.edu.uwa.mech.mom.server.SessionMaster");
    contextFactory.register(ChannelMaster.TYPE_NAME, "au.edu.uwa.mech.mom.server.ChannelMaster");
    contextFactory.register(TokenMaster.TYPE_NAME, "au.edu.uwa.mech.mom.server.TokenMaster");
   
  }

 
}




//    public void join(Peer p) throws MOMPermissionException
//    {

//      //debug("need to finish SessionMaster.join()");
//      //if we have a session manager check with it
//      //SessionMaster s=(Session)sessions.get(name);
//      // if (s==null)
//      //  s=createSession(p, name);
//      //else
//      //  s.addPeer(p); //this needs to fire an event that notifys everyone
//      peers.add(p);

    
//      //return s;
//    }




  //public SessionMaster(String name, Peer p) throws MOMPermissionException
  //{
  //  super(name, p, root);
  //}
  
  
//    public void receive(Object arg, Peer p) throws MOMPermissionException
//    {
//      MOMPacket packet = (MOMPacket)arg;
    
//      if (packet.getName().equals(MOMPacket.Values.CREATE))
//        try{
//          create((ContextURL)packet.getArg(), p);
//        } catch (MOMCreateException ex){
//          debug("SessionMaster::receive need to handle MOMCreateException"+ex);
//        }
//      else
//        super.receive(arg,p);
    
    
//    }



  
  //public Vector getChildrenContextURLs()
  //{
  //  return childrenContextURLs;
    
  //}
  
  /**
     * Get the value of children.
     * @return Value of children.
     */
  //public Hashtable getChildren() {return children;}
  
  /**
     * Set the value of children.
     * @param v  Value to assign to children.
     */
  //public void setChildren(Hashtable  v) {this.children = v;}


  

  
  //public Session toSession()
  //{
  //  throw new UnsupportedOperationException("toSession not implemented yet");
    //Session s=new Session(name, peers, constituents);
    //return s;
    
  //}
  
  //protected static SessionMaster root;
