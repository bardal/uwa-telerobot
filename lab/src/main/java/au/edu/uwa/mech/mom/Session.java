
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Session.java,v 1.31 1999/09/16 14:49:55 bdalton Exp $
// Time-stamp: "1999-09-16 12:58:42 student"

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

import au.edu.uwa.mech.mom.event.*;
import java.util.*;
import au.edu.uwa.mech.util.Factory;

public  class Session extends SimpleContext //make this observable
{

  public Session()
  {
    peers=new Vector();
    children=new Hashtable();
    packetProcessors.put(MOMPacket.Values.CHILD_CREATED, new ChildCreatedProcessor());
    packetProcessors.put(MOMPacket.Values.CHILD_DELETED, new ChildDeletedProcessor());
  }

  public void addChildEventListener(ChildEventListener eh)
  {
    childEventListeners.addElement(eh);
    //let new listener know about current children
    for (Enumeration e=children.elements();e.hasMoreElements();)
      eh.childCreated(this, (Context)e.nextElement());
      
  }

  
  public Context create(ContextURL newContextURL, Manager manager)
    throws MOMIOException, MOMPermissionException, MOMCreateException{
    return processCreate(getPacketFactory().createRequest(newContextURL, manager));
  }

  
  public Context create(ContextURL newContextURL)
    throws MOMIOException, MOMPermissionException, MOMCreateException{
    return processCreate(getPacketFactory().createRequest(newContextURL));
  }

  protected Context processCreate(MOMPacket createRequest)
    throws MOMIOException, MOMPermissionException, MOMCreateException
  {
    MOMPacket reply = (MOMPacket)sendAndReceive(createRequest);
    if (reply.getName().equals(MOMPacket.Values.CHILD_CREATED))
      return (Context)reply.getArg();
    else
      throw new MOMCreateException(reply.getArg().toString());
  }
  

  
  public void removeChildEventListener(ChildEventListener eh)
  {
    childEventListeners.removeElement(eh);
  }

  public Context lookup(ContextURL curl) throws MOMIOException, MOMPermissionException, MOMNotFoundException
  {
    if(curl.hasParent())
      throw new MOMPermissionException("you can only lookup immediate children of a context "+curl);
    

      Context c=(Context)children.get(curl);
      if (c != null){
        debug("Found local copy "+c+" of "+curl);
        return c;
      }
      
      else{
        debug("NO local copy "+curl+" sending exists request");
        MOMPacket reply=(MOMPacket)sendAndReceive(getPacketFactory().existsRequest(curl));


        //DON'T change this, throwing a notfoind exception makes life
        //easier for clients
        
        if (reply.getArg() != null)
          return (Context)reply.getArg();
        else
          throw new MOMNotFoundException("Context "+curl+" does not exist in session "+getContextURL());
      }
      //} catch (MOMException ex){
      //return null;
      //}
  }
  
  
  public Context lookupOrCreate(ContextURL curl) throws MOMIOException, MOMPermissionException, MOMCreateException
  {
    debug("Session::lookupOrCreate \t"+curl);
    if(curl.hasParent())
      throw new MOMCreateException("you can only create immediate children");

    try{
      return lookup(curl);
    } catch (MOMNotFoundException e) {
      return create(curl);
    }
  }


  
  public Hashtable getChildren() throws MOMPermissionException
  {
    if (!joined)
      throw new MOMPermissionException("You must join a Session before calling getChildren");
    
    return children;
  }
  
  public static final String TYPE_NAME="session"; 

  protected Factory contextFactory;
  protected Hashtable children;
  protected java.util.Vector childEventListeners=new java.util.Vector();

                                // called for CHILD_CREATED packets
  class ChildCreatedProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      //debug("Session::receive event our curl:"+getContextURL()+" arg:"+arg);
      Context newChild = (Context)packet.getArg();
                                // yucky line!
      ContextURL newChildURL = newChild.getContextURL().getRelativeContextURL();
          
      children.put(newChildURL, newChild);

      //debug("Session::receive notifying childEventListeners:"+newChildURL);
      for (Enumeration en=childEventListeners.elements(); en.hasMoreElements();)
        ((ChildEventListener)en.nextElement()).childCreated(Session.this, newChild);
            
      //debug("Session::receive CHILD_CREATED FINISHED");
    }
  }

  
  class ChildDeletedProcessor implements PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      ContextURL curl=(ContextURL)packet.getArg();
      children.remove(curl);
      for (Enumeration en=childEventListeners.elements(); en.hasMoreElements();)
          ((ChildEventListener)en.nextElement()).childDeleted(Session.this, curl);
    }  
  }
  


  
}


//    /**
//       * Set the value of children.
//       * @param v  Value to assign to children.
//       */
//    public void setChildren(Hashtable  v) {this.children = v;}
  
  
  


//    protected void construct()
//    {
//    }
  

  //public void create(ContextURL newContextURL) throws MOMIOException, MOMPermissionException, MOMNotFoundException
  //{
    //do this here
    //need a setContextFactory method
    //send message to MOM
  // connection.send(getContextURL(), ChildEvent.CHILD_CREATE_EVENT, newContextURL);
    
    //return connection.create(new ContextURL(contextURL.toString()+"/"+newContextURL.toString()));
  //}
  
  
//    public Session (String name)
//    {
//      construct();
//      this.name=name;
//    }

  //public Session (String name, Vector peers, Vector children)
  //{
  //  this.name=name;
  //  this.peers=peers;
  //  this.children=children;
  //}

    
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


//    public void join() throws MOMIOException, MOMPermissionException, MOMNotFoundException
//    {
//      super.join();//connection.join(this.getContextURL());
//      Vector childContexts=connection.getChildrenContextURLs(this.getContextURL());
    
//      for (Enumeration e=childContexts.elements();e.hasMoreElements();)
//      {
//        ContextURL c=(ContextURL)e.nextElement();
//        children.put(c, connection.lookup(c));
//      } 
    
//      //get list of children
//    }
  
  
//    public Channel lookupChannel(String name)
//    {
//      return (Channel)lookup(new ContextURL(contextURL.toString(), name,
//                                            Channel.TYPE_NAME));
    
//    }  
  
  //public void joinContext(Context mc) throws MOMIOException, MOMPermissionException
  //{
  //  connection.joinContext(mc, this); 
  //}

 
  //public Context lookup(String type, String name)
  //{
  //  return (Context)contexts.get(type+name);
  //}
  

  
  
  //public void addPeer(Peer p)
  //{
  //  peers.add(p);
  //}



  
  //public  void share(Context m){;}
  //public  void join(context m);
  //public context join(String contextType, String name){
  //  throw new UnsupportedOperationException("join not implemented yet");
  //}
  
  //public  void leave(Context m){;}
  //public 

  
  
  //public final String CHANNEL="channel";
  //public final String TOKEN="token";
  //public final String DATA="data";
  

  
  /**
   * Get the value of channels.
   * @return Value of channels.
   */
  //public Enumeration getChannels() {return channels.elements();}
  
  /**
     * Set the value of channels.
     * @param v  Value to assign to channels.
     */
  //protected void setChannels(Hashtable  v);// {this.channels = v;}
  
  
    /**
     * Get the value of peers.
     * @return Value of peers.
     */
  //public Enumeration getPeers(){return peers.elements();}
  
  /**
     * Set the value of peers.
     * @param v  Value to assign to peers.
     */
  //public void setPeers(Hashtable  v);// {this.peers = v;}

  
  
  
  /**
     * Get the value of tokens.
     * @return Value of tokens.
     */
  //public Enumeration getTokens() {return tokens.elements();}
  
  /**
     * Set the value of tokens.
     * @param v  Value to assign to tokens.
     */
  //public void setTokens(Hashtable  v);// {this.tokens = v;}
  
  //Hashtable sharedObjects;
  
  /**
     * Get the value of sharedObjects.
     * @return Value of sharedObjects.
     */
  //public Hashtable getSharedObjects() {return sharedObjects;}
  
  /**
     * Set the value of sharedObjects.
     * @param v  Value to assign to sharedObjects.
     */
  //public void setSharedObjects(Hashtable  v) {this.sharedObjects = v;}
  




