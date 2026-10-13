
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SessionRoot.java,v 1.38 2000/01/27 11:50:44 bdalton Exp $
// Time-stamp: "2000-01-27 15:44:45 barney"

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


//import au.edu.uwa.mech.mom.event.EventHandler;
import au.edu.uwa.mech.mom.event.LoginEventListener;
import au.edu.uwa.mech.mom.impl.MOMConnectionFactory;


//import au.edu.uwa.mech.util.Hook;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Date;

import au.edu.uwa.mech.util.Factory;
import au.edu.uwa.mech.util.Clock;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.net.URLHelper;


public class SessionRoot extends Session
  implements LoginHandler, Transceiver, Clock
{


  public SessionRoot(String url)
    throws MOMConnectionBuildException,
           MOMLoginException, MOMIOException
  {
    this.momURL = new URLHelper(url);
    connection = MOMConnectionFactory.build(momURL.getProtocol());
    //TheLog.println("methods "+connection.getClass().getMethods());
    madeConnection = true;
    
    setup();
    connection.setURL(url);
    //login();
  }
  
  
  public SessionRoot(MOMConnection connection)
  {
    madeConnection = false;
    this.connection=connection;
    setup();
  }
  
  
  public SessionRoot(MOMConnection connection, Factory contextFactory)
  {
    madeConnection = false;
    this.connection=connection;
    this.contextFactory=contextFactory;
    setup();
  }



  
  protected void setup()
  {
    setContextURL(new ContextURL(""));
    directory.put(getContextURL(), this);
    packetFactory = new MOMPacketFactory(this);

    if (contextFactory == null){
      contextFactory = new Factory();
      contextFactory.register(Session.TYPE_NAME, "au.edu.uwa.mech.mom.Session");
      contextFactory.register(Channel.TYPE_NAME, "au.edu.uwa.mech.mom.Channel");
      contextFactory.register(Token.TYPE_NAME  , "au.edu.uwa.mech.mom.Token");
    }
    //System.out.println("registered contexts");
    //TheLog.println("Connection is "+connection+" this is "+this);
    //TheLog.println("methods "+connection.getClass().getMethods());
    //TheLog.println("done methods");
    //try{Thread.sleep(10000);}
    //catch(InterruptedException e){;}

    //TheLog.println(connection.getClass().getMethod());
    //System.out.println("addingupstream");
    connection.addUpstream(this);
    //TheLog.println("added upstream");
    this.root=this;
    //System.out.println("finished sessionroot setup()");
    //cp=new ConnectionParameters("localhost", -1);
  }
  
  //public void setConnectionParameters(ConnectionParameters cp) {this.cp=cp;}
  
  //ConnectionParameters cp;
  public Peer login(UserCredentials credentials)  throws MOMLoginException, MOMIOException
  {
    return login(credentials.name, credentials.password, credentials.nickname);
  }
  

  public Peer login() throws MOMLoginException, MOMIOException
  {
    String [] details = new String[momURL.getUserInfo().size()];
    for (int i=0; i<momURL.getUserInfo().size(); i++)
      details[i]=(String)momURL.getUserInfo().elementAt(i);
    return login(details[0], details[1], details[2]);
  }
  
  //need an aynchronous version of this
  public Peer login(String userName, String password, String nickname) throws MOMLoginException, MOMIOException
  {
    if ((userName == null) ||
        (password == null) ||
        (nickname == null))
      throw new MOMLoginException("one of the login parameters, username, password, nickname was null");

    
    connection.connect();
                                // make login packet
    MOMPacket loginPacket=packetFactory.loginRequest(userName, password, nickname);
    //peer=connection.login(userName, password, nickname);
                                // send it and wait for reply
    TheLog.println("Logging in "+userName+":"+password+":"+nickname);
    MOMPacket reply=(MOMPacket)sendAndReceive(loginPacket,10000);

    if (!reply.isError()){
      //Peer peer=reply.getProperty("DO THISSSSSSSSSSSSSSSSSSSSSSSS");
      us = (Peer)reply.getArg();
      loggedIn = true;
      TheLog.println("logged in as "+us);
    
      for (Enumeration en=loginEventListeners.elements(); en.hasMoreElements();)
        ((LoginEventListener)en.nextElement()).loggedIn(getUs());
    }
    else
      throw new MOMLoginException(new String((char[])reply.getArg()));

    try{join();}
    catch (MOMException e){throw new MOMLoginException(e.toString());}
    debug("finished SessionRoot::login");


    
    return us;
  }
    
  public void logout() throws MOMLoginException, MOMIOException
  {
    if(loggedIn){
      MOMPacket packet=packetFactory.logoutRequest(us);
      
      try{
        MOMPacket reply=(MOMPacket)sendAndReceive(packet);
      } catch (MOMException e){
        TheLog.logException(e);
      }
                                // test for result
    
      debug("TO DO : test for result of logout");
      loggedIn=false;
    }
    
    connection.disconnect();

    //if (madeConnection){
    //  connection=null;
    //  System.gc();
    //  System.runFinalization();
    //}
    
    for (Enumeration en=loginEventListeners.elements(); en.hasMoreElements();)
      ((LoginEventListener)en.nextElement()).loggedOut(getUs());
  }

  public boolean loggedIn() {return loggedIn;}


  // MAYBE WE SHOULD GET RID OF THESE TWO FUNCTIONS!!!
  // Client should rely on callbacks, but we may need a way of looking
  // up too

  //moved to Session
//    public Context lookup(ContextURL curl)
//    {
//      try{
//        Context c=(Context)directory.get(curl);
//        if (c != null){
//          debug("Found local copy "+c+" of "+curl);
//          return c;
//        }
      
//        else{
//          debug("NO local copy "+curl+" seding exists request");
//          MOMPacket reply=(MOMPacket)sendAndReceive(getPacketFactory().existsRequest(curl));
//          return (Context)reply.getArg();
//        }
//      } catch (MOMException ex){
//        return null;
//      }
//    }
  
  public void sendDownstream(Object arg)
    throws MOMIOException
  {
    if (getUs() != null)
      ((MOMPacket)arg).setSender(getUs());
    putqDownstream(this, arg);
  }
  
  public void putqDownstream(Object source, Object arg) throws MOMIOException
  {
    //implement a queue here?
    //System.out.println("SessionRoot - calling connection.putqDownstream "+connection);
    connection.putqDownstream(source, arg);
  }

                                //only know how to deal with MOMPackets
                                //at the moment 
  public void putqUpstream(Object source, Object arg) throws MOMIOException
  {
    //TheLog.println("SessionRoot received "+arg);
    try
    {
                                // for create we have to make a local
                                // copy of the context then pass this
                                // to the parent context. The server
                                // only gives us the context url
      MOMPacket packet=new MOMPacket((Packet)arg);

                                // if its a child created packet
      if (packet.getName().equals(MOMPacket.Values.CHILD_CREATED))
        packet.setArg(createLocalCopyOf(new ContextURL(packet.getContextURL(), (ContextURL)packet.getArg())));

      Receiver c;
      
      if (packet.getContextURL() != null)
        c=(Receiver)directory.get(packet.getContextURL());
      else                      //context specified so assume root
        c=this;
      
      if (c!=null){
        //TheLog.println("Forwarding packet to "+c);
        c.receive(source, packet);
      }
      else
        debug("SessionRoot::receive didn't know with :"+packet+"\n so Discarded it");
      
    }catch (MOMCreateException ex){
      debug("SessionRoot::receive ignoring create exception");
      TheLog.logException(ex);
    }catch (Throwable ex){
      debug("Error in processing packet - carrying on anyway");
      TheLog.logException(ex);
    }
    
  }

  public void addDownstream(Downstream downstream)
  {}
  public void removeDownstream(Downstream downstream)
  {}

  public void addUpstream(Upstream upstream)
  {}
  public void removeUpstream(Upstream upstream)
  {}

  public void close()
  {}
  
  
  public Peer getUs(){
    return us;
  }

  public long getTime()
  {
    Date date=new Date();
    return date.getTime();
  }
  
  
  public void addLoginEventListener(LoginEventListener eh)
  {
    loginEventListeners.addElement(eh);
  }

  public void removeLoginEventListener(LoginEventListener eh)
  {
    loginEventListeners.removeElement(eh);
  }

  public MOMPacketFactory getPacketFactory() {return packetFactory;}

  
  protected synchronized Context createLocalCopyOf(ContextURL curl) throws MOMCreateException 
  {
    try{
      //debug("SessionRoot::createLocalCopyOf Setting up local copy of context:"+curl);
      if (directory.containsKey(curl)){
        TheLog.println("We already have one "+curl+ "so we'll use that");
        return (Context)directory.get(curl);
      }
      
      Context newContext = (Context)contextFactory.build(curl.getContextType());
      newContext.setContextURL(curl);
      newContext.setRoot(this);
      directory.put(curl, newContext);

      //debug("SessionRoot::createLocalCopyOf  finished");
      return newContext;
      
    } catch (au.edu.uwa.mech.util.BuildException e) {
      debug("SessionRoot::createLocalCopyOf FAILED for "+curl);
      throw new MOMCreateException("SessionRoot::createLocalCopyOf FAILED for "+curl);     
    }
  }

  //form upstream not really relevant
  public void setName(String name){
    this.name=name;
  }
  String name="";
  

  protected MOMPacketFactory packetFactory;
  //protected Hook packetCreateHook;
  protected Vector loginEventListeners=new Vector();

  protected MOMConnection connection;
  protected Peer us;          // the oeer we are logged in as
  protected Hashtable directory=new Hashtable();
  protected URLHelper momURL;
  boolean loggedIn;
  boolean madeConnection=false;
  
  public static void main (String[] args)
  {
    try{

      MOMConnection connection=MOMConnectionFactory.build("socket");
      connection.setURL("moms://localhost:4444");
      SessionRoot sr=new SessionRoot(MOMConnectionFactory.build("socket"));
      sr.login("barney", "dog", "cherry");
    }
    catch (Exception e){
      e.printStackTrace();
    }
                                // need to handle disconnect/shutdown properly
    // sr.logout(); 
  } // end of main ()
  


  
}

// TIME DIFFERENCE STUFF HERE

//      //calculate time difference to MOM clock
//      //might want to do this periodically in a new thread
//      int noTries=10;
//      float averageDelay=0; //for one way
//      float averageTimeDifference=0;

//      //put this in the session class
//      for (int i=1;i<=noTries;i++)
//      {
//        long timeBeforeSend=(new Date()).getTime();
//        long timeForMOM=cb.getDate().getTime();
//        long timeAfterSend=(new Date()).getTime();
//        long roundTripDelay=timeAfterSend-timeBeforeSend;
//        long timeDifference=timeForMOM-((timeBeforeSend+timeAfterSend)/2);      

//        debug("before:"+timeBeforeSend+" after:"+timeAfterSend+" delay:"+roundTripDelay);
//        debug("momtime:"+timeForMOM);
//        averageDelay=(averageDelay*(i-1)+roundTripDelay/2)/i;

//        averageTimeDifference=(averageTimeDifference*(i-1)+timeDifference)/i;
//        debug("Average Delay "+averageDelay+"Time difference "+
//              timeDifference+" Average Time Difference "+averageTimeDifference);
//      } // end of for (int i=0;i<5;i++)
    
//      momTimeDifference=(long)averageTimeDifference;


//make a PacketFactory
//  class PacketCreateHook implements Hook
//  {
//    public PacketCreateHook(SessionRoot root)
//    {
//      this.root=root;    
//    }

//    SessionRoot root;
//    static int lastID=0;
  
  
//    public Object doYourStuffOn(Object arg)
//    {
//      Packet p=(Packet) arg;
//      try{
//        p.setPropertyReadOnly(MOMPacketHelper.Keys.TIMESTAMP, new Long(root.getSynchronizedTime()));
//        p.setPropertyReadOnly(MOMPacketHelper.Keys.ID, newID());
//      } catch (MOMReadOnlyException e){
//        e.printStackTrace();
//      }
    
//        return p;
//    }

//    protected Integer newID()
//    {
//      TheLog.println("generating new ID "+lastID);
//      return new Integer(++lastID);
//    }
  
//  }
  


//    public void receive(Object source, Object arg)
//    {
//      super.receive(source, arg);
//      debug("SessionRoot::receive arg:"+arg);
//    }
  

