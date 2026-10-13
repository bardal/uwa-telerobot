
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Server.java,v 1.25 2000/01/28 11:20:53 bdalton Exp $
// Time-stamp: "2000-01-28 18:48:18 barney"

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


//robot server

package au.edu.uwa.mech.devices.robot; //inserted by perl script 'checkPackages'

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.MessageEventListener;
import au.edu.uwa.mech.mom.event.PeerEventListener;
import au.edu.uwa.mech.mom.event.TokenEventListener;
import au.edu.uwa.mech.mom.MOMConnectionBuildException;
import au.edu.uwa.mech.mom.impl.HUWManager;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.util.FileLog;
import au.edu.uwa.mech.util.StringUtil;
import au.edu.uwa.mech.util.DateUtil;

import au.edu.uwa.mech.shared.Result;

import java.net.Socket;
import java.net.UnknownHostException;
import java.io.*;
import java.util.*;


public class Server implements MessageEventListener , TokenEventListener, PeerEventListener
{

  private Server(String momURL) throws MOMConnectionBuildException,
  MOMIOException, MOMLoginException, MOMPermissionException,
  MOMCreateException, MOMNotFoundException, IOException
  {
    commandLog = new FileLog("logs/RobotCommands.log", true);

    root     = new SessionRoot(momURL);
    root.login();               // login with the username/password
                                // specified by the URL 
    rSession = (Session)root.lookupOrCreate(new ContextURL("robot"));
    rSession.join();

    try{rChannel = (Channel)rSession.create(new ContextURL("robot.channel"));}
    catch(MOMCreateException e){
      e.printStackTrace();
      rChannel = (Channel)rSession.lookup(new ContextURL("robot.channel"));
    }
    
    rChannel.addMessageEventListener(this);
    rChannel.addPeerEventListener(this);
    rChannel.join();
    
    Manager manager = new HUWManager(180);
    try{rToken
          = (Token)rSession.create(new ContextURL("robot.token"), manager); // how do we specify the manager?
    }
    catch(MOMCreateException e){
      e.printStackTrace();
      rToken = (Token)rSession.lookup(new ContextURL("robot.token"));
    }
    
    rToken.addTokenEventListener(this);
    rToken.join();

    RobotPositionRelay rp=new RobotPositionRelay();
    Thread rpThread = new Thread(rp);
    rpThread.start();
  }


  // bit of a horrible mess!!!!
  public void message(Context c, MOMPacket packet)
  { 
    if (packet.getSender().equals(controller))
    {
      try{
        try{
        
          connectToRobotServer();

          char[] commandChars=null;
          int commandID=-1;
          //accept commadns as straight text or as object attachments
          //new stuff here - starting to send/receive commands as objects
          try{
            Object arg = packet.getArgDeserialized();
            if (arg instanceof Command){
              System.out.println("Server received "+arg);
              commandChars = ((Command)arg).toRobotString().toCharArray();
              commandID = ((Command)arg).ID();
            }
            
            else
              return;
          }
          catch (MOMIOException e){
            //probably wasn't a serialized object
          }

          if (commandChars==null) //try reading as normal text the
                                  //original way it was done
            commandChars = (char[])packet.getArg();

          TheLog.println("Got command "+commandChars+" from "+packet.getSender());
          
          rsOut.println(commandChars);
          rsOut.flush();

          Date beforeMove = new Date();
          String reply=null;
          while ((reply = rsIn.readLine()).length() >0)
          {
            TheLog.println("Got reply from RobotServer :"+ reply);
            rChannel.sendReplyTo(packet, reply.toCharArray());
          }

          //log it
          Date afterMove = new Date();
          String commandString = StringUtil.removeChar(new String(commandChars),'\r');
          commandString = commandString.replace('\n',';');
          long sinceLast = DateUtil.sub(beforeMove, lastMove);
          long moveTime  = DateUtil.sub(afterMove, beforeMove);
          commandLog.println(beforeMove.getTime()+"!!"+beforeMove+"!!"+sinceLast+"!!"+moveTime+"!!"+
                          packet.getSender()+"!!"+commandID+"!!"+(noMoves++)+"!!"+commandString+"!!");
          lastMove = beforeMove;
        }
        catch (java.net.SocketException e)
        {
          disconnectRobotServer();
          TheLog.println(e.getMessage());
          //e.printStackTrace();
          rChannel.sendReplyTo(packet, Result.newFail(-1, "Failed to connect to RobotServer").toString().toCharArray());
        }
        catch (NullPointerException e){
          TheLog.println(e.getMessage());
          //e.printStackTrace();
          rChannel.sendReplyTo(packet, Result.newFail(-1, "Robotserver Protocol error, maybe due to a socket reset").toString().toCharArray());
          disconnectRobotServer();
        }
        catch (IOException e){
          TheLog.println(e.getMessage());
          //e.printStackTrace();
        }
      } 
      catch (MOMIOException e){
        e.printStackTrace();
      }
    }
    else {
      System.out.println("Not forwarding to robotserver "+ packet.getSender()+
                         " is not the controller");
    }
  }


  int  noMoves = 0;
  Date lastMove;
  
  public void tokenGrabbed(Context c, Peer peer)
  {
    noMoves    = 0;
    lastMove   = new Date();
    controller = peer;
    System.out.println("Token "+c.getContextURL()+" has been grabbed by "+peer);
  }
  
  public void tokenReleased(Context c)
  {
    controller = null;
    System.out.println("Token "+c.getContextURL()+" has been released");
  }

  public void peerJoined(Context c, Peer p){
    //send it to everyone for the moment
    if (lastStatePacket != null){
      try{
        lastStatePacket.setTO(p);
        rChannel.sendMessage(lastStatePacket);
      }
      catch (MOMIOException e){e.printStackTrace();}
    }
    
  }
  
  
  public void peerLeft(Context c, Peer p){
  }
  

  public void joined(Context c){
  }

  public void left(Context c){
  }
  
  //this is a disgusting mess
  protected void connectToRobotServer() throws IOException
  {
    final int maxTries=2;
    
    if (!connectedToRobot){
      int noTries = 0;
      
      while(!connectedToRobot && (noTries++ < maxTries))
      {
        try{
          System.out.println("Trying to connect to control relay :"+ Robotserver_Host +":"+ Robotserver_Port);
          rsSocket=new Socket(Robotserver_Host, Robotserver_Port);
          //java.net.ConnectException
      
          rsIn = new LineNumberReader(new InputStreamReader(rsSocket.getInputStream()));
          rsOut = new PrintWriter(new OutputStreamWriter(rsSocket.getOutputStream()));
          connectedToRobot=true;
          System.out.println("connected to control relay :"+ Robotserver_Host +":"+ Robotserver_Port);

        }  catch (IOException e) {
          if(noTries >= maxTries)
            throw e;
          else{
            TheLog.println(e.getMessage());
            //e.printStackTrace();
            System.out.println("Problem connecting to control relay trying again");
            connectedToRobot=false;
          }
        }
      }
    }
  }

  protected void disconnectRobotServer()
  {
    try{
      if (rsSocket != null)
        rsSocket.close();
    } catch(IOException e){
      System.err.println("Exception disconnecting fomr robotserver - exception fiollows");
      TheLog.println(e.getMessage());
      //e.printStackTrace();
    }
    connectedToRobot=false;
    rsOut = null;
    rsIn = null;
    rsSocket = null;
  }
  
  FileLog       commandLog;
  
  Peer          controller;
  MOMConnection connection;
  SessionRoot   root;
  Session       rSession;
  Channel       rChannel;
  Token         rToken;

  Socket           rsSocket;
  LineNumberReader rsIn;
  PrintWriter      rsOut;
  boolean          connectedToRobot;  
  MOMPacket        lastStatePacket;
  
  static String Robotserver_Host;
  static int    Robotserver_Port;
  static int    Position_Port;   
  
  class RobotPositionRelay implements Runnable 
  {

    public void run()
    {
      boolean connected=false;
      Socket rpSocket;
      LineNumberReader rpIn=null;
      
      try{
        while(true)
        {
          
          while (!connected)
          {
            try{
              System.out.println("Trying to connect to position relay :"+ Robotserver_Host +":"+ Position_Port);
              rpSocket=new Socket(Robotserver_Host, Position_Port);
              rpIn = new LineNumberReader(new InputStreamReader(rpSocket.getInputStream()));
              connected=true;
              System.out.println("Connected to position relay :"+ Robotserver_Host +":"+ Position_Port);
            } catch (IOException e) {
              TheLog.println(e.getMessage());
              //e.printStackTrace();
              Thread.currentThread().sleep(5000);
            }
          }

          String reply;
          try{
            while ((reply = rpIn.readLine()).length() >0)
            {
              System.out.println("Got position from RobotServer :"+ reply);
              MOMPacket packet=root.getPacketFactory().message(reply.toCharArray());
              lastStatePacket=packet;
              rChannel.sendXMessage(packet);
            }
          } catch (java.net.SocketException e){
            connected=false;
            e.printStackTrace();
          } catch (IOException e){
            e.printStackTrace();
          } catch (MOMIOException e){
            e.printStackTrace();
          } catch (NullPointerException e){
            e.printStackTrace();
            System.out.println("Assuming disconnection from robotserver");
            connected=false;
          }
        }
      }
      catch (InterruptedException e) {
        System.err.println("giving up connecting to robot pos port");
        e.printStackTrace();
      }
    }
  }
  

  
  public static void main (String[] args)
  {
    try{

      String configFile = "server.properties";
      if(args.length == 1)
        configFile=args[0];
      // create and load default properties
      Properties properties = new Properties();
      FileInputStream in = new FileInputStream(configFile);
      properties.load(in);
      in.close();

      properties.list(System.out);
      
      //properties.put("MOMurl", "rmi://robot:robot-local:robot@localhost/");
      //properties.put("RobotServerHost", "euler");
      //properties.put("PositionPort", "30563");
      //properties.put("ControlPort", "30562");

      //FileOutputStream out = new FileOutputStream("server.properties");
      //properties.save(out, "---Server propeties---");
      //out.close();
      
      String url=(String)properties.get("MOMurl");
      Server.Robotserver_Host = (String)properties.get("RobotServerHost");
      Server.Robotserver_Port= Integer.parseInt((String)properties.get("ControlPort"));
      Server.Position_Port    = Integer.parseInt((String)properties.get("PositionPort"));
      
      System.out.println("Starting server with url "+au.edu.uwa.mech.util.MomUrls.masked(url));
      Server server=new Server(url);
    }
    catch(Exception e){
      e.printStackTrace();
    }
    
  } // end of main ()
  
}



