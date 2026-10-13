
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMServer.java,v 1.54 2000/01/27 11:50:44 bdalton Exp $
// Time-stamp: "2000-02-05 17:18:06 barney"

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


//should this be in the server directory?
//the MOM server,
//listens for new connections 
//validates client login
//implements, share, join, leave for constituents
//allows managment of constituents
//routes messages on channels
//accepts changes to shared data
//notifys peers of changes to shared data
//handles token requests 


// move all of the stuff to a SessionRootMaster - just like client
// side stuff

// HORRIBLE MESS AT THE MOMENT

// the context stuff should be pluggable
// ought to be able operate the mom in a router only mode

package au.edu.uwa.mech.mom.server;

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.util.Factory;
import au.edu.uwa.mech.util.BuildException;
import au.edu.uwa.mech.util.Clock;
import au.edu.uwa.mech.util.Fifo;
import au.edu.uwa.mech.util.ConfigFile;
import au.edu.uwa.mech.util.XMLConfigFile;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.util.FileLog;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.*;//Enumeration;

import au.edu.uwa.mech.util.ConsoleInput;

// ******************************

//TO DO ................. implement MOMConnectionHandler interface

// ******************************

public class MOMServer implements EventRelay, Clock//, NewConnectionHandler
{
  
  //protected ServerSocket s;
  protected Vector         listeners    = new Vector();
  protected Vector         peers        = new Vector();
  //protected Hashtable    sessions     = new Hashtable();
  protected Manager        manager;
  //protected Hashtable    contexts     = new Hashtable();
  //protected Hashtable      brokers      = new Hashtable();
  //protected Vector         knownBrokers = new Vector();
  protected Hashtable      peerToConnection = new Hashtable();
  protected Hashtable      connectionToPeer = new Hashtable();
  protected Vector         peerlessConnections = new Vector();
  
  protected UserManager    userManager;
  protected SessionRootMaster   rootSession;
  //protected ContextURL        rootSessionURL = new ContextURL("");
  protected MOMPacketFactory    packetFactory;
  //protected QueuedTransceiver incomingQueue;
  protected Sender              sender;
  FileLog userLog;
  //protected Factory contextFactory=new Factory();
  
  
  
  public MOMServer (String configFile) throws IOException {

    userManager = new DefaultUserManager();
    
    try{
      ConfigFile config = new XMLConfigFile(configFile);  
      String dbFile=config.getString("usermanager", "file", "users.ini");
      userManager.setDatabaseFile(dbFile);
      
    } catch (Exception e){
      e.printStackTrace();
    }

    userLog = new FileLog("logs/users.log", true);
    
    Factory listenerFactory=new MOMListenerFactory();
    try{
      MOMListener
        sl=(MOMListener)listenerFactory.build("socket");

      //this should be part of the build string
      sl.configure("port", new Integer(4444));
      sl.configure("bulkport", new Integer(4445));
      //sl.setConnectionHandler(this);
      //quick hack
      sl.configure("mom", this);
      listeners.addElement(sl);
    } catch (BuildException be){
      be.printStackTrace();
    }

    //rmi not working at the moment
//      try{  
//        //MOMListener rmil=(MOMListener)listenerFactory.build("rmi");
//        //rmil.setConnectionHandler(this);
//        //listeners.addElement(rmil);
//      } catch (BuildException be){
//        be.printStackTrace();
//      }

    packetFactory=new MOMPacketFactory(this);
    
    
    rootSession = new SessionRootMaster();//rootSessionURL, null, null);
    rootSession.setEventRelay(this);
    rootSession.setPacketFactory(packetFactory);
    //contexts.put(rootSessionURL, rootSession); //root context
       
  
    //start a new thread to process messages
    //sender = new Sender();
    //sender.start();
    

  }

  public void listen() {
                                //loop though each listener and start
                                //them 
    for (Enumeration e=listeners.elements(); e.hasMoreElements();)
    {
      try{
        ((MOMListener)e.nextElement()).listen();
      }
      catch (MOMIOException ex){
        ex.printStackTrace();
      }
    }
  }

//    public void putqDownstream(Object source, Object arg) throws MOMIOException{
//                                  //use the send. sendX functions etc
//      throw new RuntimeException("Use send,sendX instead of putqDownstream");
//    }
  

  // would be nice to provide some way for registering classes with
  // the MOM, could throw MOMClassNotRegistered exceptions when
  // clients send args we don't know about. If possible we don't want
  // args being sent as binary attachments as it means more
  // implementation work at client/server end. We can still provide it
  // as an option, but at the moment, its the only option.
  
                                //called by MOMConnection for each new packet 
  public void receive(ConnectionManager cm, Object arg) throws MOMIOException
  {
    if (arg instanceof Packet)
    {
      //debug("MOMServer::received :"+arg);
      MOMPacket packet=packetFactory.wrap((Packet)arg);
      
                                // find out what kind of Packet it is
                                // login, logout etc. Should have a
                                // hashtable of packet names - vs -
                                // functions to process them
      try{
        
        if(packet.getName().equals(MOMPacket.Values.LOGIN)) {
          processLogin(packet, cm);
        }
        else if (packet.getName().equals(MOMPacket.Values.LOGOUT)) {
          processLogout(packet, cm);
        }
        else if (packet.getName().equals(MOMPacket.Values.EXISTS)) {
                                // do exists stuff
          processExists(packet, cm);
        }
        else
        {
          
                                //move this stuff to session root?
                                //if its for a context
          ContextURL curl=packet.getContextURL();
          Peer peer=packet.getSender();

          ContextMaster targetContext=rootSession;

          //System.out.println("putqUpstream "+curl+" "+rootSession.directory.lookup(curl));
          if(curl != null){
            targetContext=rootSession.directory.lookup(curl);
            if (targetContext==null)
              targetContext=rootSession;
          }
          
          targetContext.receive(packet, peer);
        }
      }
      catch (MOMException e) {
                                //need to return this to client
                                //somehow
        debug("context exception "+e);
        MOMPacket reply=getPacketFactory().replyError(packet);
        reply.setArg(e.getMessage().toCharArray());
      //System.err.println("sending login error "+ reply);
        sendTo(cm, reply);

        //e.printStackTrace();
      }
    }
    
    //debug("MOMServer::receive FINISHED");
  }
  
  
//    public void addDownstream(Transceiver transceiver)
//    {
//      throws new RuntimeException("Use newConnection()");
//      //create a supervisor
//    //  knownBrokers.addElement(transceiver);
//    }
  
//    public void removeDownstream(Transceiver transceiver)
//    {
//      knownBrokers.removeElement(transceiver);
//    }

//                                  //nothing above us is there?
//    public void addUpstream(Transceiver transceiver){;}
//    public void removeUpstream(Transceiver transceiver){;}
  

  //public Peer getPeer
  
  public void setConnectionFor(Peer p, ConnectionManager cm){
    peerToConnection.put(p, cm);
    connectionToPeer.put(cm, p);
    cm.setName(p.toString());
    peerlessConnections.removeElement(cm);
  }


  public void closeConnectionFor(Peer p)
  {
    debug("MOMServer::removeManagerFor removing manager for peer:"+p);
    ConnectionManager cm = (ConnectionManager)peerToConnection.remove(p);
    if(cm != null){
      connectionToPeer.remove(cm);
      cm.close();
    }
    
  }

       
  public void newConnection(ConnectionManager cm){
    //ConnectionManager cm = new ConnectionManager(this, transceiver);
    cm.setSendTimeout(1000);
    peerlessConnections.addElement(cm);
  }

  public void lostConnection(ConnectionManager cm)
  {
    TheLog.println("lostConnection "+cm);
    if(peerlessConnections.contains(cm)){
      cm.close();
      peerlessConnections.removeElement(cm);
    }
    else{
      Peer p = (Peer)connectionToPeer.get(cm);
      if(p !=null){
        closeConnectionFor(p);
        logout(p); //removes peer from all contexts
      }
    }
  }

                                //send to everyone other than "notThisOne"
  public void sendX(Peer notThisOne, Object args)
  {
    //debug("MOMServer::sendX  "+args);

    for (Enumeration e=peers.elements();e.hasMoreElements(); )
    {
      Peer p=(Peer)e.nextElement();
      if (!p.equals(notThisOne))
        sendTo(p, args); 
    } // end of for ()
    //debug("MOMServer::sendX FINISHED");
  } 
  
  public void send(Object args){
    sendTo(peers, args); 
  }

  public void sendTo(Vector peerList, Object arg){
    synchronized (peerList){
      for (Enumeration e=peerList.elements();e.hasMoreElements(); )
        sendTo((Peer)e.nextElement(), arg);
    }
  }
  
  public void sendTo(Peer p, Object arg){
    sendTo((ConnectionManager)peerToConnection.get(p), arg);
  }
  
  public void sendTo(ConnectionManager cm, Object arg){
    if(cm !=null)
      cm.send(arg);
  }


  
  public void processLogin(MOMPacket loginPacket, ConnectionManager cm)
  {
    Hashtable userDetails=(Hashtable)loginPacket.getArg();
    String name=(String)userDetails.get(MOMPacket.Keys.USERNAME);
    String password=(String)userDetails.get(MOMPacket.Keys.PASSWORD);
    String nickname=(String)userDetails.get(MOMPacket.Keys.NICKNAME);

    MOMPacket reply;
    
    try
    {
      Peer peer=login(name, password, nickname);
      System.out.println("logged in peer "+peer);
      setConnectionFor(peer, cm);
    
      reply=getPacketFactory().replyTo(loginPacket);
      reply.setArg(peer);
      sendTo(peer, reply);
    }
    catch (MOMLoginException e)
    {
      System.err.println("Login exception "+e.getMessage());
      
      reply=getPacketFactory().replyError(loginPacket);
      reply.setArg(e.toString().toCharArray());
      //System.err.println("sending login error "+ reply);
      sendTo(cm, reply);
    }
  }
  
  
  public Peer login(String userName, String password, String nickname) throws MOMLoginException
  {
    debug("need to finish MOMServer.login()"); // sd
    Peer peer;
    if((userName == null) || (userName.length() <=0)){
      throw new MOMLoginException("no username specified - try 'guest'");
    }
    
    
    if (userManager != null){
      peer = userManager.login(userName, password, nickname);
      //System.out.println("finished usermanager login ok");
    }    
    else
      peer = new Peer(userName, nickname);
    
    //temporary hack - need to resolve what happens when peers get
    //disconnected 
    
    if (!peers.contains(peer))
      peers.addElement(peer);
    else
      throw new MOMLoginException("Nickname "+nickname+" is already in use");

    Date now = new Date();
    try{
      userLog.println("login!!"+now.getTime()+"!!"+now+"!!"+userName+"!!"+password+"!!"+nickname+"!!"+peers.size());
    } catch(IOException e){
      TheLog.logException(e);
    }
    
    
    
    return peer;
  }


  public void processLogout(MOMPacket packet, ConnectionManager cm)
  {
    Peer peer = (Peer)packet.getArg();
    logout(peer);    
    MOMPacket reply=getPacketFactory().replyTo(packet);
    sendTo(cm, reply);
    
    try{Thread.currentThread().sleep(10000);}
    catch (InterruptedException e){;}
    
    closeConnectionFor(peer);
  }
  
  
  public void logout(Peer peer)
  {
    rootSession.remove(peer);
    peers.removeElement(peer);
    Date now = new Date();

    try{
      userLog.println("logout!!"+now.getTime()+"!!"+now+"!!"+peer+"!!"+peers.size());
    } catch(IOException e){
      TheLog.logException(e);
    }
    //remove all contexts peer created?
  }

  
  //this should be handled by the parent session, THIS IS A MESS
  public void processExists(MOMPacket packet, ConnectionManager cm)
    throws MOMPermissionException
  {
    ContextURL parentURL=packet.getContextURL();
    ContextURL childURL=(ContextURL)packet.getArg();
    Peer peer=packet.getSender();

    MOMPacket reply=getPacketFactory().replyTo(packet);
    reply.setContextURL(parentURL);
    
    if (checkExists(new ContextURL(parentURL, childURL), peer)){
      //if it exists tell client to create it
      //debug("Exists; Context found returning reply"+childURL);
      reply.setName(MOMPacket.Values.CHILD_CREATED);
      reply.setArg(childURL);
    }
    else{
      //debug("Exists: context not found"+childURL);
      reply.unsetArg();
    }      
    sendTo(peer, reply); 
  }
  
  
  public boolean checkExists(ContextURL curl, Peer p)
    throws MOMPermissionException
  {
    try {
      lookup(curl, p);
      return true;
    } catch (MOMNotFoundException e){
      return false;
    }
  }
  
  public ContextMaster lookup(ContextURL curl, Peer p)
    throws MOMNotFoundException, MOMPermissionException
  {
    try {
      //debug("MOMServer::lookup curl:"+curl+" peer:"+p);
      
      ContextMaster c=rootSession.directory.lookup(curl);
      if (c==null)
        throw new MOMNotFoundException("MOMServer::lookup Failed to find context "+curl);
      
      //debug("context is "+c.toString());
      return c;
      
    } catch (NullPointerException e) {
      debug("MOMServer::lookup throwing momnotfoundexception for "+curl);
      throw new MOMNotFoundException("Failed to find context "+curl);
    }
  }
  
  // implementation of au.edu.uwa.mech.mom.MOM interface

  /**
   *
   * @return <description>
   */
  public Enumeration getConnections() {
    // TODO: implement this au.edu.uwa.mech.mom.MOM method
    throw new RuntimeException("getConnection not implemented yet");
  }
  
  /**
   *
   * @return <description>
   */
  public Enumeration getPeers(String requestingPeer) {
    //check if the peer is allowed to get this info
    //if its allowed then return the list of peers in the root session
    // TODO: implement this au.edu.uwa.mech.mom.MOM method
    throw new RuntimeException("getPeers not implemented yet");
  }


  public long getTime()
  {
    return new Date().getTime();
  }

  public MOMPacketFactory getPacketFactory()
  {
    return packetFactory;
  }

  public String toString(){
    StringBuffer sb=new StringBuffer();
    sb.append("peers\t" + peers+"\n\n");
    sb.append("rootSession\t" + rootSession+"\n\n");
    sb.append("peerToConnection\n"+peerToConnection+"\n\n");
    sb.append("connectionToPeer\n"+connectionToPeer+"\n\n");
    sb.append("peerlessConnections\n"+peerlessConnections+"\n");
    return sb.toString();
  }
  
  
  public void talkToConsole(){

    ConsoleInput in=new ConsoleInput();
    char request;
    while ((request=in.readChar()) !='q')
    {
      if (request == 's')
        synchronized(System.out){
          synchronized(System.err){
            System.out.println("Server Status");
            System.out.println(this);
          }
        }
    }
      
  }

  
//    public void close(){
//      throws new RuntimeException("Yes I know this is a mess, but close() doesn't make sense for the MOMServer");
//    }
  

  // no status window on a headless host (java.awt.headless=true, as on AWS)
  GUI gui = java.awt.GraphicsEnvironment.isHeadless() ? null : new GUI();
  class GUI extends Frame
  {
    TextArea ta = new TextArea();

    GUI(){
      super("MOMServer status");
      add(ta);
      addWindowListener(new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            setVisible(false);
          }
        });
      pack();
      show();
    }

    public void update(){
      ta.setText(MOMServer.this.toString());
    }
    
  }
  
  
  
  public static void main (String args[]) throws Exception {

    // In 1999 this checked that the rmic-generated RMI stubs were on the
    // classpath; since Java 5 RMI makes stubs dynamically, so there are none.

    String configFile = "MOMconfig.xml";
    if(args.length == 1)
      configFile=args[0];

    MOMServer mom=new MOMServer(configFile);
    
    mom.listen();
    System.out.println("done mom listen, main");
 //     try{
//        mom.talkToConsole();
//      } catch (Exception e){
//        System.out.println("Exception talking ot console - exiting");
//      }
    while(true){
      // create new class to do this
      // for (each client) (peers and peerless connections
      // ping them, ping will be an Object of type Ping addressed to
      // sessionroot
      // - exception remove peer, close connection 
      // get reply - channel.sendAndReceive(timeout)
      // log round trip delay
      // sleep
      // end of for ()
      

      
      Thread.currentThread().sleep(5000);
      if (mom.gui != null)
        mom.gui.update();
    }
    
    
    
    //clean up here
  }

  static protected boolean debug=true;
  static protected void debug(String s)
  {
    if (debug)
      System.err.println(s);
  }

  
}

