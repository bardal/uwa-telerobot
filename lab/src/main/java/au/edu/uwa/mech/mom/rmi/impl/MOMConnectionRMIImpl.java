
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMConnectionRMIImpl.java,v 1.40 2000/01/14 10:42:29 bdalton Exp $
// Time-stamp: "2000-01-14 10:46:31 barney"

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

package au.edu.uwa.mech.mom.rmi.impl;

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.rmi.*;
import au.edu.uwa.mech.mom.Packet;
import au.edu.uwa.mech.mom.Receiver;
//import au.edu.uwa.mech.util.ContainedObservable;
//import au.edu.uwa.mech.util.Factory;
import au.edu.uwa.mech.net.URLHelper;
import au.edu.uwa.mech.util.Next;
import au.edu.uwa.mech.util.ActiveFifo;

import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import java.rmi.ConnectException;
import java.net.MalformedURLException;
import java.util.*;


//this sits on the client side of the RMI connection, extends
//UnicastRemoteObject so thaat

/*the non RMI stuff should go into a  MOMClient class that contains a
 * reference to the underlying connection */ 

//get rid of upstream FIFO, use an ActiveTransceiver between the MOM
//and the transceiver

public class MOMConnectionRMIImpl extends java.rmi.server.UnicastRemoteObject
  implements MOMConnection, MOMTransceiverRMI, Next
{
  
  protected MOMTransceiverRMI otherSide;
  protected Upstream       upstream;
  protected String            host;
  protected boolean           connected = false;  
  protected boolean           usingFifo = false;
  protected ActiveFifo        upstreamFifo; // threaded fifo so that
                                // we return immediately when called
                                // by the remote side - use a
                                // ActiveTransceiver int next layer instead!!!
  
  public MOMConnectionRMIImpl() throws RemoteException
  {
    super();
  
    try{
      System.getSecurityManager().checkListen(4000);
      debug("Security manager is letting us listen ");
    }
    catch (SecurityException e){
      debug("Security manager won't let us listen "+e.getMessage());
    }
    catch (NullPointerException e){
      debug("No security Manager");
    }
  }
  
  public void setURL(String url)
  {
    URLHelper urlh=new URLHelper(url);
    host=urlh.getHost();
  }
  
  public void connect()
    throws MOMIOException
  {

    if (connected)
      throw new MOMIOException("Already connected");


    usingFifo = true;
    upstreamFifo = new ActiveFifo();
    upstreamFifo.setNext(this);
    upstreamFifo.setActive(true);
    
    
    //need to include names of remote machine in lookup
    try {
      MOMListenerRMI lr = (MOMListenerRMI)
        Naming.lookup("//"+host+"/"+MOMListenerRMI.name);
      debug("looked up MOMListenerRMI");
      otherSide = lr.connect();
      debug("connected");
      otherSide.addDownstreamRMI(this);
      debug("called otherside addDownStream");
      this.addDownstreamRMI(otherSide);
      debug("added otherside to DownStream");
      connected=true;

    } catch (NotBoundException e) {
      throw new MOMIOException("not bound"+e.getMessage());
    } catch (MalformedURLException e) {
      throw new MOMIOException("malformed URL"+e.getMessage());
    } catch (ConnectException e){
      throw new MOMConnectException("Failed to connect "+e.getMessage());
    } catch (RemoteException e) {
      throw new MOMIOException("remote exception "+e.getMessage());
    }
  }
  
  public boolean connected(){return connected;}

  public void disconnect() {
    upstreamFifo.setActive(false);
    upstreamFifo.setNext(null);
    upstreamFifo=null;
    try {
      otherSide.removeDownstreamRMI(this);
      unexportObject(this, true);
      //unexportObject(otherSide, true);
    } catch (RemoteException e){
      System.out.println(e);
    }
    
    otherSide = null;
    connected = false;    

    
    
    //try and get the remote references cleared up
    System.gc();
    System.runFinalization();

  }
  
  public void close(){
    disconnect();
    
  }
  

  // send arg to object on other side
  public void putqDownstream(Object source, Object arg) throws MOMIOException
  {
    try {
      System.out.println("MOMConnectionRMIImpl putqdownstream "+arg);
      otherSide.putqUpstreamRMI(null, arg);
    } catch (java.rmi.ConnectException e) {
      //e.printStackTrace();
      throw new MOMConnectionLostException("RMI connection to peer lost");
    } catch (RemoteException e){
      throw new MOMIOException(e.toString());
    }
    
  }

                                // called by other side
  public void putqUpstreamRMI(Object source, Object arg)
    throws RemoteException, MOMIOException
  {
    try{
      putqUpstream(source, arg);
    } catch (RuntimeException e){
      e.printStackTrace();
      throw e;
    } catch (Error e){
      e.printStackTrace();
      throw e;
    }
    
  }

                                // called by other side
  public void addDownstreamRMI(MOMTransceiverRMI transceiver) throws RemoteException
  {
    System.out.println("MOMConnectionRMIImpl received addDownstreamRMI "+transceiver);
    otherSide=transceiver;
  }

  public void removeDownstreamRMI(MOMTransceiverRMI transceiver) throws RemoteException
  {
    otherSide=null;
  }
  
  public void putqUpstream(Object source, Object arg) throws MOMIOException
  {
    try{
    if (usingFifo)
      upstreamFifo.put(arg);
    else
      upstream.putqUpstream(this, arg);
    } catch (InterruptedException e){
      throw new MOMIOException("MOMConnectionRMIImpl putqUpstream interrupted");
    }
    
    
  }

  //the Next interface called by upstreamFifo
  public void put(Object source, Object arg) 
  {
    try {
      if (arg != null)
        upstream.putqUpstream(this, arg);
    } catch (MOMIOException e) {
      e.printStackTrace();
    } catch (Throwable e){
      e.printStackTrace();
      System.out.println("Continuing anyway");
    }
    
  }
  
  
                                
  public void addDownstream(Downstream downstream){;} 
  public void removeDownstream(Downstream downstream){;}
  
  public void addUpstream(Upstream upstream)
  {
    this.upstream=upstream;
  }
  
  public void removeUpstream(Upstream upstream){
    upstream =null;
  }

  // required by Upstream since the socket transport was added; only used for logging
  public void setName(String name){
    this.name = name;
  }

  String name = "";

  
  static protected boolean debug=true;
  static protected void debug(String s)
  {
    if (debug)
      System.err.println(s);
  }

  static final long serialVersionUID = 7272192495737797752L;
  
  public static void main (String[] args) throws IOException 
  {
    if (args.length != 1)
      throw new IOException ("Syntax:  <host>");
    //ConnectionParameters p=new ConnectionParameters(args[0], -1);
    MOMConnectionRMIImpl c = new MOMConnectionRMIImpl();
    try {
      c.connect();
      //c.login("user", "pin", "nickname");
    } catch (MOMIOException e) {
      debug(e.toString());
    }
    
    //} catch (MOMLoginException e) {
    //debug(e.toString());
    //}
  } // end of main ()
  
}

  
  
//    public void send(Object arg)
//      throws MOMIOException, MOMPermissionException, MOMNotFoundException
//    {
//      //try {cb.receive(curl, e, args);} //call the connection brokers receive method
//      try {cb.receive(arg);} //call the connection brokers receive method
//      catch (RemoteException ex) {
//         throw new MOMIOException("remote exception: "+ex.toString());
//       }
//    }
  
  
//    //callbacks from server
//    public void receive(Object arg) 
//    {

//      debug("MOMConnectionRMIImpl::receive  arg:"+arg);

//      receiver.receive(arg);
    //forward it to the correct context
   //   Context c=lookupLocal(curl);
//      if (c != null)
//      {
//        debug("MOMConnectionRMIImpl::receive forwarding event to "+c);
//        c.receive(e, args);
//      }
//      EventHandler eh=(EventHandler)eventHandlers.get(curl);
//      if (eh!=null)
//        eh.receive(curl, e, arg);
//      else
//         debug("MOMConnectionRMIImpl::receive No local copy of curl:" +
//               curl+" for event:"+e+","+arg);
    
//    }
  
//    public void setReceiver(Receiver handler)
//    {
//      receiver=handler;//.put(curl, handler);
//    }

//    Receiver receiver;
//    //Hashtable eventHandlers=new Hashtable();
  
  
  
//    public Peer getPeer() {return peer;}
  
    
//    /**
//       * Get the value of loggedIn.
//       * @return Value of loggedIn.
//       */
//    public boolean getLoggedIn() {return loggedIn;}
  
//    /**
//       * Set the value of loggedIn.
//       * @param v  Value to assign to loggedIn.
//       */
//    public void setLoggedIn(boolean  v)
//    {
//      this.loggedIn = v;
//      observable.setChanged();
//      observable.notifyObservers();
//    }
  
  
//    //public Peer    login() throws MOMLoginException, MOMIOException
//    //{return login("guest", null, null);}//return "guest";}
//    public Peer   login(String userName, String password, String nickname)
//      throws MOMLoginException, MOMIOException
//    {

//      if (loggedIn)
//        throw new MOMLoginException("Already logged in");

//      try {
//        peer=cb.login(userName, password, nickname);
//        directory=new Hashtable();
//        //for (Enumeration e=cb.getChildren();e.hasMoreElements();)
//        //{
        
//        //  directory.put( 
//        //}


//        //
//        //rootSession.children=cb.getChildren();
//        //rootSession.peers=cb.getPeers();
      
      
//        //cb.getChildrenContextURLs();
//        //do this in an applet - applet must be signed first
//        //netscape.security.PrivilegeManager.enablePrivilege("UniversalConnect");
      
//        cb.registerCallback(this);
      
//        //todo rad int list of peers
//        //peers=cb.getPeers();
//        //get a list of sessions?
//        //get a list of peers?
//        setLoggedIn(true);
//        return peer;
//      }
//      catch (RemoteException e) {
//        throw new MOMIOException("MOMConnectionRMIImpl::login remote exception: "+e.getMessage());
//      }
//  //      catch (MOMPermissionException e) {
//  //        throw new MOMLoginException(e.toString());
//  //      }
//  //      catch (MOMNotFoundException e) {
//  //        throw new MOMLoginException("Failed to find root session "+e.getMessage());
//  //      }
    
//    }
  
//    public void logout() throws MOMIOException, MOMLoginException
//    {
//      try {  
//        cb.logout();
//      }
//      catch (RemoteException e) {
//        throw new MOMIOException("remote exception"+e.getMessage());
//      }
    
//      setLoggedIn(false);
//    }


//    //protected Hashtable sessions;//new Hashtable();
//    protected Hashtable peers;

//    protected Hashtable directory;//directory of all MOM contexts
//    //(sessions,channels, tokens etc)                      
  
  
//    //public void share(MOMConstituent c){;}

//    public void create(ContextURL curl) throws MOMIOException, MOMPermissionException, MOMCreateException
//    {
//      try {
//        debug("creating context on MOMside");
//        cb.create(curl); //create the Context on the MOMside
//        debug("created context on MOMside");
//        //make a local copy and add it to the directory
//        //Context c= setupContext(curl);
//        //return c;
      
//      } catch (RemoteException e) {
//        throw new MOMIOException (e.toString());
//      } 
//    }

//    public boolean exists(ContextURL curl) throws MOMIOException, MOMPermissionException, MOMNotFoundException
//    {
//        try {
//          return cb.checkExists(curl);
//        } catch (RemoteException e) {
//          throw new MOMIOException (e.toString());
//        } 
//      //throw new UnsupportedOperationException("MOMConnectionRMIImpl::exists not implemented yet");
    
//    }
  
  
  //get rid of thjs lookup should be part of session
 //   public Context lookup(ContextURL curl)
//      throws MOMIOException, MOMPermissionException, MOMNotFoundException
//    {
//      debug("MOMConnectionRMIImpl::lookup curl:"+curl);
//      if (!loggedIn)
//        throw new MOMPermissionException("You must log in first");

//      try
//      {
      
//        Context c=lookupLocal(curl);
//        if (c==null)
//        {
//          //if (cb.checkExists(curl)) 
//          //  c=setupContext(curl);
//          //else
//          //NOW NOTHING GET S CREATED AT ALL
//          debug("MOMConnectionRMIImpl::lookup didn't find it, throwing an exception");
        
//          throw new MOMNotFoundException("context URL "+curl+" not known");
//        }
//        debug("MOMConnectionRMIImpl::lookup FINISHED returning:"+c);
//        return c;
      
//      } catch (RemoteException e) {
//        throw new MOMIOException(e.toString());
//      }  catch (MOMCreateException e) {
//        debug("SORT OUT HANDLING OF CREATE EXCEPTION IN LOOKUP");
//        throw new MOMNotFoundException("really a create exception "+e.getMessage());
//      }

//    }

  //contexts that we already have a local copy of 
//    protected Context lookupLocal(ContextURL curl)
//    {
//      return (Context)directory.get(curl);
//    }
  
  
//    protected Context setupContext(ContextURL curl) throws MOMCreateException
//    {
//      try{
//        debug("MOMConnectionRMIImpl::setupContext Setting up local copy of context:"+curl);
//        Context c = (Context)contextFactory.build(curl.getContextType());
//        c.setContextURL(curl);
//        c.setConnection(this);
//        directory.put(curl,c);
//        debug("MOMConnectionRMIImpl::setupContext finished");
//        return c;
//      } catch (au.edu.uwa.mech.util.BuildException e) {
//        throw new MOMCreateException("Setup Error in MOMConnectionRMIImpl::setupcontext"+e.getMessage());
//      }
//    }
  
  
//    public void join(ContextURL curl) throws MOMIOException, MOMPermissionException, MOMNotFoundException
//    {
//      try {
//        debug("MOMConnectionRMIImpl:join "+curl);
//        cb.join(curl);
//        //if (c instanceof Session)
//        //  ((Session)c).setChildren(cb.getChildren(c.getName()));
//        //get list of all consituents + peers etc 
//      }
//      catch (RemoteException e) {
//        throw new MOMIOException("remote exception"+e.getMessage());
//      }
//    }

//    public void leave(ContextURL curl) throws MOMIOException, MOMPermissionException
//    {
//      throw new UnsupportedOperationException("leave not implemented yet");
//    }

//    public Vector  getChildrenContextURLs(ContextURL curl)
//      throws MOMIOException, MOMPermissionException, MOMNotFoundException
//    {
//      try {return cb.getChildrenContextURLs(curl);}
//      catch (RemoteException e) {
//        throw new MOMIOException("remote exception in MOMConnectionRMIImpl::getChildren "+e.getMessage());
//      }
//    }
  


  
//    public Date getMOMTime()
//    {
//      return new Date((new Date()).getTime()+momTimeDifference);
//    }



//    public java.util.Observable observable()
//    {
//      return observable;
//    }
  
  

  //public void leave(MOMConstituent c){;}
  //public java.util.Enumeration getSessions()
  //{
  // return sessions.elements();
  //}


//    public void join(Session s) throws MOMIOException, MOMPermissionException
//    {
//      try {
//        cb.joinSession(s.getName());
//        s.setConstituents(cb.getConstituents(s.getName()));
//        //get list of all consituents + peers etc 
//      }
//      catch (RemoteException e) {
//        throw new MOMIOException("remote exception"+e.getMessage());
//      }

//    }


//    public void joinConstituent(MOMConstituent mc, Session s) throws MOMIOException, MOMPermissionException
//    {
//      try {
//        debug("joinConstituent "+mc.TYPE+" "+mc.getName()+" "+s.getName());
//        cb.joinConstituent(mc.TYPE, mc.getName(), s.getName());

//      }
//      catch (RemoteException e) {
//        throw new MOMIOException("remote exception"+e.getMessage());
//      }

//    }
  

  
  //public Session lookupSession(String name) throws MOMIOException, MOMPermissionException
  //{
  //  return (Session)sessions.get(name);
  //}
  

  
//    public void joinSession(Session s) throws MOMIOException, MOMPermissionException
//    {
//      try {
//        cb.joinSession(s.getName());
//        s.setConstituents(cb.getConstituents(s.getName()));
//        //get list of all consituents + peers etc 
//      }
//      catch (RemoteException e) {
//        throw new MOMIOException("remote exception"+e.getMessage());
//      }

//    }
  



