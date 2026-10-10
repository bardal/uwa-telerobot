
package au.edu.uwa.mech.devices.robot;


import java.io.IOException;
import java.io.CharArrayReader;
import java.util.*;
import au.edu.uwa.mech.util.ObservableVector;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.util.DateUtil;
//import au.edu.uwa.mech.util.Active;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.shared.Result;
import au.edu.uwa.mech.io.ASCIIReader;
import au.edu.uwa.mech.util.Debug;

import au.edu.uwa.mech.mom.Channel;
import au.edu.uwa.mech.mom.Token;
import au.edu.uwa.mech.mom.Peer;
import au.edu.uwa.mech.mom.Session;
import au.edu.uwa.mech.mom.SessionRoot;
import au.edu.uwa.mech.mom.Context;
import au.edu.uwa.mech.mom.ContextURL;
import au.edu.uwa.mech.mom.MOMPacket;
import au.edu.uwa.mech.mom.MOMException;
import au.edu.uwa.mech.mom.MOMIOException;
import au.edu.uwa.mech.mom.MOMPermissionException;
import au.edu.uwa.mech.mom.MOMNotFoundException;
import au.edu.uwa.mech.mom.event.LoginEventListener;
import au.edu.uwa.mech.mom.event.TokenEventListener;
import au.edu.uwa.mech.mom.event.ChildEventListener;
import au.edu.uwa.mech.mom.event.PeerEventListener;
import au.edu.uwa.mech.mom.event.MessageEventListener;
import au.edu.uwa.mech.mom.event.ReplyListener;



// need different types of client.
// fully fledged listen for  everything, or just interested in new
// states.
// would be best to have two channels, one for control, one for state.
// Control Client
// State Client - get images working first

public class Client
  implements TokenEventListener,
             PeerEventListener,
             ChildEventListener,
             MessageEventListener,
             //             LoginEventListener,
             RobotHandler
{
  public CommandList      commands;
  public ControlState     controlstate;
  protected RobotState    robotState;
  protected SessionRoot   root;
  protected Session       robotSession;
  protected Channel       robotChannel;
  protected Token         robotToken;
  protected Vector        robotListeners;
  protected Vector        stateListeners;

  
  protected static ContextURL robotSessionURL = new ContextURL("robot");
  protected static ContextURL robotTokenURL = new ContextURL(robotSessionURL.toString(), "robot", Token.TYPE_NAME);
  protected static ContextURL robotChannelURL = new ContextURL(robotSessionURL.toString(), "robot", Channel.TYPE_NAME);
  
  protected boolean contextsSetup=false;
  
  public Client(){
    construct();
  }

  public Client(SessionRoot root){
    construct();
    setRoot(root);
  }
  
  
  protected void construct()
  {
    commands       = new CommandList();
    robotListeners = new Vector();
    stateListeners = new Vector(); 
    controlstate   = new ControlState();
    robotState     = new RobotState();
  }

  public RobotState getState(){
    //TheLog.println("getState returning"+ robotState);
    return  new RobotState(robotState);
    
  }

  public Peer getController() throws RobotException {
    try{
      return getRobotToken().getOwner();
    } catch (MOMException e){
      throw new RobotException(e.toString());
    }
  }

  public boolean hasController(){
   return robotToken!=null && robotToken.getOwner()!=null;
  }
  
  
  public void gainControl()
    throws RobotException
  {
    try{
      getRobotToken().grab();
    } catch (MOMNotFoundException e){
      throw new RobotException("The robot doesn't seem to be logged in with MOM, is it running?");
    } catch (MOMException e){
      TheLog.logException(e);
      throw new RobotException(e.getMessage());
    }
  }

  protected Session getRobotSession()  throws MOMException{
    if(robotSession ==null){
      Session rs =(Session)root.lookup(robotSessionURL);
      setupRobotSession(rs);
    }
    return robotSession;
  }
  
  protected Channel getRobotChannel() throws MOMException{
    if(robotChannel ==null){
      Channel rc =(Channel)getRobotSession().lookup(robotChannelURL.getRelativeContextURL());
      setupRobotChannel(rc);
    }
    return robotChannel;
  }
  
  protected Token getRobotToken() throws MOMException{
    if(robotToken ==null){
      Token rt =(Token)getRobotSession().lookup(robotTokenURL.getRelativeContextURL());
      setupRobotToken(rt);
    }
    return robotToken;
  }
  
  public void releaseControl() throws RobotException
  {
    try{
      getRobotToken().release();
    } catch (MOMException e){
      throw new RobotException(e.toString());
    }
  }
  
  public ControlState controlState(){return controlstate;}  
  
  public void tokenGrabbed(Context c, Peer peer)
  {
    boolean inControl                                          = false;
    //TheLog.println("tokenGrabbed "+peer+" "+root.getUs()+" = "+peer.equals(root.getUs()));
    
    if (peer.equals(root.getUs())){
      controlstate.set(ControlState.INCONTROL);
      inControl = true;
    }
    else
       controlstate.set(ControlState.OBSERVER);
    
    TheLog.println("Token "+c.getContextURL()+" has been grabbed by "+peer);

    
    for (Enumeration en=robotListeners.elements(); en.hasMoreElements();)
      ((RobotListener)en.nextElement()).newController(this, peer.getNickname(), inControl);
  }
  
  public void tokenReleased(Context c)
  {
    controlstate.set(ControlState.OBSERVER);
    TheLog.println("Token "+c.getContextURL()+" has been released");

    for (Enumeration en=robotListeners.elements(); en.hasMoreElements();)
      ((RobotListener)en.nextElement()).noController(this);
  }

  public void peerJoined(Context c, Peer p){
    TheLog.println(p+" has joined "+c.getContextURL());
  }
  
  public void peerLeft(Context c, Peer p){
    TheLog.println(p+" has left "+c.getContextURL());
  }

  public void joined(Context c){
  }
  
  public void left(Context c){
  }
  
  
  public void childCreated(Context c, Context child){
    TheLog.println("child created"+c+child);
    try{
      if (!contextsSetup)
      {
     
        if (child.getContextURL().equals(robotSessionURL))
          setupRobotSession((Session)child);
        else if((robotSession != null) && (c == robotSession))
          if(child.getContextURL().equals(robotChannelURL))
            setupRobotChannel((Channel) child);
          else if(child.getContextURL().equals(robotTokenURL))
            setupRobotToken((Token)child);
      
        if ((robotSession != null) &&
            (robotToken != null)   &&
            (robotChannel != null))
        {
          contextsSetup = true;
          for (Enumeration en=stateListeners.elements(); en.hasMoreElements();)
            ((StateListener)en.nextElement()).setConnected(this, true);
        }
      }
    } catch (MOMException e){
      TheLog.logException(e);
    }
    
    
  }

  protected void setupRobotSession(Session rs) throws MOMException {
    if(robotSession == null){
      robotSession = rs;
      robotSession.addChildEventListener(this);
      robotSession.join();
    }
  }
  
  
  protected void setupRobotToken(Token rt) throws MOMException {
    if (robotToken == null){  
      robotToken=rt;
      robotToken.addTokenEventListener(this);
      robotToken.join();
    }
  }
  
  protected void setupRobotChannel(Channel rc) throws MOMException {
    if (robotChannel == null){  
      robotChannel = rc;
      robotChannel.addMessageEventListener(this);
      robotChannel.join();
    }
  }
  
  
  
  public void childDeleted(Context c, ContextURL childURL){
    //TheLog.println("child created"+c+childURL);
    //if (childURL.equals(robotSessionURL))
      //releaseContexts();
  }
  
  
  
  public void message (Context c, MOMPacket packet) 
  {
    //TheLog.println("Client received "+packet+"\n"+(char[])packet.getArg());
    //TheLog.println("TO DO... make robot Object attachments");
    //robot pose info will come here

    //new stuff here - starting to send/receive commands as objects
    try{
      Object arg = packet.getArgDeserialized();
      Peer sender = packet.getSender();

      if (arg instanceof Command){ //echo all commands to everyone
        if (sender != null && !sender.equals(root.getUs()))
          TheConsole.println(packet.getSender()+" sent "+((Command)arg).toEditableString());
        return;
      }  
    }
    catch (MOMIOException e){
    }
      
      
    
    try{
      
      ASCIIReader aReader = new ASCIIReader(new CharArrayReader((char[])packet.getArg()));
      //read in position
      RobotState tempState = new RobotState();
      tempState.readDeg(aReader);

      //no exceptions
      robotState = tempState;
      
      RobotState sendState = new RobotState(robotState);
      //TheLog.println("Got State"+ robotState);

      //coulsn't we do this in one?
      for (Enumeration en=robotListeners.elements(); en.hasMoreElements();)
        ((RobotListener)en.nextElement()).newRobotState(this, sendState);      

      
      for (Enumeration en=stateListeners.elements(); en.hasMoreElements();)
        ((StateListener)en.nextElement()).newRobotState(this, sendState);      

      //TheLog.println("After listeners State"+ robotState);
      
      
    } catch (IOException ex) {
      TheLog.logException(ex);
    } catch (NumberFormatException ex){
      //TheLog.println("Ignoring Number Format exception in message");
      //ex.printStackTrace();
    }


  }

  public synchronized void sendCommands() throws RobotException
  {
    try{
      for (int i=commands.lastSent(); i<=commands.getMax() ; i++) 
      {
        Command c = commands.getCommand(i);
        if (!c.sent())
          sendCommand(c);
      }
    }catch (MOMException e){
      throw new RobotException("Error sending commands :"+e);
    }
  }

  protected  synchronized void sendCommand(Command c) throws MOMException
  {
    TheConsole.println("Sending command "+c.ID()+" "+c.toEditableString());
    TheLog.println("Sending command"+c.toEditableString());
    //old way
    //char [] commandAsChars = c.toRobotString().toCharArray();
    //MOMPacket message      = getRobotChannel().getPacketFactory().message(commandAsChars);

    //new way - need ot sort out commands should be erialized - all
    //that observable crap needs to be dealt 
    //made it transient 
    MOMPacket message      = getRobotChannel().getPacketFactory().message();
    message.setArgSerialized(c);
    
    //set priority etc here

    getRobotChannel().sendMessage(message, new MoveReplyListener(c));
    c.state(Command.SENDING);
  }
  
  
  public RobotState executeCommand(Command command) throws RobotException{
    try{
      sendCommand(command);
      synchronized(command){
        while (!command.finished())
          command.wait();
      }
      //TheLog.println("returning from executeCommand");
      return getState();
    }
    catch(MOMException e){
      TheLog.logException(e);
      throw new RobotException(e.toString());
    }
    catch(InterruptedException e){
      TheLog.logException(e);
      throw new RobotException(e.toString());
    }
    
  }
  
  
  
  public void setRoot(SessionRoot root) 
  {
    this.root = root;
    // why? - maybe this isn't so bad, just not the right name. We
    // need to know if we are in contact with the outside world, whch
    // the logineventlistener interface does. Altenative names?
    // connected - hmm well you could be connected but not logged in,
    // which isn't really what we're interested in.
    //if(root.loggedIn())
    //  setupContexts();
    //root.addLoginEventListener(this);
    root.addChildEventListener(this);
  }

  public void addRobotListener(RobotListener eh) {
    robotListeners.addElement(eh);
    stateListeners.addElement(eh);
  }

  public void removeRobotListener(RobotListener eh) {
    robotListeners.removeElement(eh);
    stateListeners.removeElement(eh);
  }

  public void addStateListener(StateListener eh) {
    stateListeners.addElement(eh);
  }

  public void removeStateListener(StateListener eh) {
    stateListeners.removeElement(eh);
  }

  public CommandList getCommands() {
    return commands;
  }
  
  
                                // class to receive information about
                                // the execution for command it was
                                // created with
  class MoveReplyListener extends Thread implements ReplyListener  
  {
    Command command;
    Date start;
    
    
    protected MoveReplyListener(Command c) {
      this.command = c;
      start = new Date();
      this.start();
    }
    

    public void run()
    {
      //System.out.println("sleeping.....");
      //sleep for 5 minutes
      try {sleep(5*60000);}
      catch (InterruptedException e){}
      //System.out.println("slept");
      if (!command.received()){
        command.state(Command.ERROR);
        TheConsole.println("ERROR: Timeout sending command");
        //need ot now the channel and packet ID fir this
        //channel.removeListenerFor(packet.getID());
        //remove ourselves from the listener list?
      }
      
    }
    
                                // return true if we have finished
                                // receiving replies for this
                                // command. If we are still expecting
                                // more then we return false
    public boolean receiveReply(MOMPacket reply)
    {
      //decode reply from server
      //update the state of 'command' according to reply
      char[] messageChars = (char[])reply.getArg();
      TheLog.println("MoveReplyListener for id"+command.ID()+" "+messageChars);
      try
      {
        Result res = new Result();
        res.read(new String(messageChars));

        //TheLog.println(res);
        
        switch (res.MajorCode())
        {
          case Result.scOK:
            command.state(Command.COMPLETED);
            Date now = new Date();
            TheLog.println("COMMAND DELAY!!"+root.getUs()+"!!"+command.ID()+"!!"+start.getTime()+"!!"
                           +DateUtil.sub(now,start)+"!!"+command.toRobotString()+"!!");
            TheConsole.println("Command "+command.ID()+" completed successfully");
            return true;
          case Result.scIntermediate:
            command.state(Command.EXECUTING);
            //some added info about where we've got to?
            return false;
          case Result.scWarning:
            return false;
          case Result.scError:
            command.state(Command.ERROR);
            TheConsole.println("Command "+command.ID()+" had a problem :"+res.getText());
            //and some helpful info???
            return true;
          default:
            return false;
        }
      }
      catch (IOException e)
      {
        command.state(Command.ERROR);
        TheLog.logException(e);
        return true;
      }  
    }
  }
  



};







  //AO.writeString("x"+x+",y"+y+"z100;");
 
//   protected void startPath() {
//     try {
//       AO.writeString("gbh move ");
//     } catch (IOException ex) {
//       ex.printStackTrace ();
//     }
//   }


  // this isn't part of the login process, its something we do
  // afterwards this should be in the setRoot function - no it
  // shouldn't what happens if we're not logged in when setRoot is
  // called? Are but happens if we're already logged in when setRoot
  // gets called, then we'll never get this event!! Fixed this by
  // checking if we'return already logged in in setRoot there should
  // be no 'logged in function' - yes there should!
  //public void loggedIn(Peer p){
    // really we're interested in the existence of the robot session
    // and its children whether we've logged into the whole system or not
    // setupContexts();
  //}

//    protected void setupContexts()
//    {
//      //lookup robot root first, if that exists then we can continue,
//      //otherwise we have to wait for it to be created by the robotserver
//      robotSession  = (Session)root.lookup(robotSessionURL);
    
//      if (robotSession == null){
//        System.err.println("Help no robot session");
//        return;
//      }
    
    
//      try{
//        robotChannel = (Channel)root.lookup(new ContextURL("robot", "robot", Channel.TYPE_NAME));
//        TheLog.println("done channel lookup");
//        robotChannel.addMessageEventListener(this);
//        robotChannel.join();
//      } catch (MOMException e) {
//        e.printStackTrace();
//      }
    
//      try {
//        robotToken = (Token)root.lookup(new ContextURL("robot/robot.token")); 
//        robotToken.addTokenEventListener(this);
//        robotToken.join();
//      } catch (MOMException e) {
//        e.printStackTrace();
//      }

//      contextsSetup=true;
//      System.err.println("finished context setup");
    
//      for (Enumeration en=stateListeners.elements(); en.hasMoreElements();)
//        ((StateListener)en.nextElement()).setConnected(this, true);
//    //  }

//    protected void releaseContexts()
//    {
//      robotSession=null;
//      robotChannel=null;
//      robotToken=null;
//      contextsSetup=false;
//    }
  
  
//    public void loggedOut(Peer p)
//    {
//      for (Enumeration en=stateListeners.elements(); en.hasMoreElements();)
//        ((StateListener)en.nextElement()).setConnected(this, false);
//    }

