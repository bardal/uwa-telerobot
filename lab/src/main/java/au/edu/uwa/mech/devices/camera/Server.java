
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Server.java,v 1.15 2000/01/22 09:30:57 bdalton Exp $
// Time-stamp: "2000-01-22 13:03:15 barney"

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

package au.edu.uwa.mech.devices.camera; 

import java.awt.Frame;
import java.awt.Window;
import java.io.*;

import au.edu.uwa.mech.util.ConsoleInput;

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.MessageEventListener;
import au.edu.uwa.mech.mom.event.LoginEventListener;
import au.edu.uwa.mech.mom.event.PeerEventListener;

import au.edu.uwa.mech.devices.robot.StateListener;
import au.edu.uwa.mech.devices.robot.StateHandler;
import au.edu.uwa.mech.devices.robot.RobotHandler;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.devices.robot.RobotException;

import au.edu.uwa.mech.io.CopyStream;
import au.edu.uwa.mech.util.ConfigFile;
import au.edu.uwa.mech.util.XMLConfigFile;

import java.util.Enumeration;

public class Server implements StateListener//, LoginEventListener
{
  static String defaultmomURL = "rmi://localhost";
  CameraManager cameraManager;
  ContextURL cSessionURL = new ContextURL("cameras");
  Session cSession;
  boolean moving;
  Integer moveSignal=new Integer(1);
  
  public Server(String configFile, CameraManager cameraManager) throws MOMException
  {
    this.cameraManager = cameraManager;
    String momURL;
    try{
      ConfigFile config = new XMLConfigFile(configFile);
      momURL=config.getString("MOM", "url", defaultmomURL);
      System.out.println(au.edu.uwa.mech.util.MomUrls.masked(momURL));
    } catch (Exception e){
      e.printStackTrace();
      momURL=defaultmomURL;
    }
    
    root     = new SessionRoot(momURL);
    //root.addLoginEventListener(this);
    root.login();

    cSession = (Session)root.lookupOrCreate(cSessionURL);
    cSession.join();

    camerasConfigured = true;
    Enumeration cameras = cameraManager.cameras();
               
    while (cameras.hasMoreElements())
    {
      try{
        Camera camera = (Camera)cameras.nextElement();
        Channel channel = (Channel)cSession.lookupOrCreate(new ContextURL(camera.getName()+'.'+Channel.TYPE_NAME));
        new CameraMessageListener(camera, channel);

      } catch (MOMException e){
        e.printStackTrace();
      }
    }
    
    
    robotClient = new au.edu.uwa.mech.devices.robot.Client();
    robotClient.addStateListener(this);
    robotClient.setRoot(root);

    Thread shutter = new Thread(new ShutterThread());
    shutter.setDaemon(true);
    shutter.start();
  }

  public void takePhotos()
  {
    Enumeration cameras = cameraManager.cameras();
    while (cameras.hasMoreElements())
      try{((Camera)cameras.nextElement()).takePhoto();}
      catch (CameraException e){e.printStackTrace();}
  }
  
  
//    public void login() throws MOMException{
//      root.login();
//    }

  public void logout() throws MOMException{
    //robotClient.stop()???
    root.logout();
  }

  protected void cleanup(){
    // nothing to release: camera managers own their own resources
  }
  

  public void setConnected(StateHandler source, boolean connected){
    System.out.println("ImageServer received robotclient connected "+ connected);
  }
  
  public void newRobotState(StateHandler source, RobotState robotState)
  {

    moving = robotState.moving();
    synchronized (moveSignal){
      moveSignal.notifyAll();
    }
    
    
    Enumeration cameras = cameraManager.cameras();
    while (cameras.hasMoreElements()){
      //iupdate centre position for calibrated cameras
      Camera c=(Camera)cameras.nextElement();
      c.setCentre(robotState.pose.position);
    }
  }

//    public void loggedIn(Peer p)
//    {    
//                                  // create camera channels
//                                  // get a list of cameras somehow?
//      Enumeration cameras = cameraManager.cameras();
               
//      while (cameras.hasMoreElements())
//      {
//        try{
//          Camera camera = (Camera)cameras.nextElement();
//          Channel channel = (Channel)root.lookupOrCreate(new ContextURL("cameras", camera.getName(), Channel.TYPE_NAME));
//          new CameraMessageListener(camera, channel);

//        } catch (MOMException e){
//          e.printStackTrace();
//        }
//      }                                       
//    }
  
//    public void loggedOut(Peer p){    
//      System.out.println("WE've been logged out, how sad");
//    }

  public String toString(){
    StringBuffer s=new StringBuffer();
    Enumeration cameras = cameraManager.cameras();
    while (cameras.hasMoreElements())
      s.append(((Camera)cameras.nextElement()).toString());
    return s.toString();
  }
  

  public void talkToConsole(){

    ConsoleInput in=new ConsoleInput();
    String request;
    while (true)
    {
      request=in.readString();

      if(request.length()==0)
        continue;
      
      if (request.length() == 1){
        if(request.charAt(0) =='q')
          break;
    
        if (request.charAt(0)== 's')
          synchronized(System.out){
            synchronized(System.err){
              System.out.println("Server Status");
              System.out.println(this);
            }
          }
      }
      
      
      //if (NTCameraManager.getCamera(request)!=null)
      if (request.equals("gui"))
      {
        System.out.println("creating frame");
        Frame f= new Frame();
        f.add(new CameraManagerPanel(cameraManager, 400,400));
        f.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {((Window)e.getSource()).setVisible(false); ((Window)e.getSource()).dispose();}
          });
        f.pack();
        f.show();
        f.toFront();
      }
    }
      
  }
    
  
  
  //listens for both camera events and client events
  class CameraMessageListener implements MessageEventListener, PeerEventListener, ImageListener, CameraListener
  {
    CameraMessageListener(Camera camera, Channel channel){
      this.camera=camera;
      this.channel=channel;
      camera.addImageListener(this);
      camera.addCameraListener(this);

      try{ 
        channel.addMessageEventListener(this);
        channel.addPeerEventListener(this);
        channel.join();
      } catch (MOMException e){
        e.printStackTrace();
      }
    }
    
    public void message(Context c, MOMPacket packet)
    {
      //System.out.println("Message Listener Got message "+packet);
      try{

        Object arg = packet.getArgDeserialized();
        System.out.println("CameraMessageListener deserialized body is "+arg);
        
        if (arg instanceof CameraCommand)
        {
          MOMPacket reply=channel.getPacketFactory().replyTo(packet);
        
          if ((robotClient == null) ||
              !robotClient.hasController() ||
              packet.getSender().equals(robotClient.getController()))
          {
            Object result = ((CameraCommand)arg).dDispatch(camera);   
            reply.setArgSerialized(result);
          }
          else //not controller
          {
            reply.setArgSerialized("You must be in control of the robot to change camerasettings");
          }
          
          channel.sendMessage(reply);
        }
      } catch (MOMIOException e){
        e.printStackTrace();
      } catch (CameraException e){
        // TO DO let the client know!!
        e.printStackTrace();
      } catch (RobotException e){
        // TO DO let the client know!!
        e.printStackTrace();
      } 

    }

    public void peerJoined(Context c, Peer p)
    {
      try{
        if (lastImage !=null){
          
          MOMPacket message = channel.getPacketFactory().message();
          message.setArgSerialized(lastImage);
          message.setTO(p);
          channel.sendMessage(message);

          message = channel.getPacketFactory().message();
          message.setTO(p);
          message.setArgSerialized(camera.getSettings());
          channel.sendMessage(message);
        }
        else
          try {camera.takePhoto();}
          catch (CameraException e){e.printStackTrace();}
        
        
      } catch (MOMIOException e){
        e.printStackTrace();
      } 
      
    }
    
    public void peerLeft(Context c, Peer p)
    {
    }    
    

    public void joined(Context c){
    }

    public void left(Context c){
    }
    
    
    public void newImage(Object source, ImageData image)
    {
      try{

        if(image.pixels == null)
        {
          try{
            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            CopyStream.copy(image.filename, bao);
            image.pixels = bao.toByteArray();
          } catch (IOException e){
            e.printStackTrace();
          } 
        }
        
        lastImage = image;
        MOMPacket message = channel.getPacketFactory().message();
        message.setBulk(true);
        message.setUnimportant(true);
        message.setArgSerialized(image);
        channel.sendXMessage(message);
      } catch (MOMIOException e){
        e.printStackTrace();
      } 
    }

    public void newSettings(Object source, CameraSettings settings)
    {
      try{
        MOMPacket message = channel.getPacketFactory().message();
        message.setArgSerialized(settings);
        channel.sendXMessage(message);
      } catch (MOMIOException e){
        e.printStackTrace();
      } 
    }

    
    private Channel channel;
    private Camera camera;
    ImageData lastImage;
  }

  
  class ShutterThread implements Runnable
  {
    public void run(){
      try{
          Thread.currentThread().sleep(5000);
        
        while(true)
        {
          while(!moving)
            synchronized (moveSignal){moveSignal.wait();}

          while(moving){
            takePhotos();
             Thread.currentThread().sleep(500);
          }

          //not moving - take photos after move
          takePhotos();          
        }
      
      } catch (InterruptedException e){
        e.printStackTrace();
      }
    }
  }



  MOMConnection connection;
  SessionRoot   root;
  //replace with state client?
  au.edu.uwa.mech.devices.robot.Client robotClient;
  boolean       camerasConfigured=false;
  
  /**
   * Run until the JVM is asked to stop (Ctrl-C etc), then log out of MOM.
   * The original waited on a native console-interrupt helper.
   */
  public void runUntilShutdown()
  {
    final Server server = this;
    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        public void run() {
          try { server.logout(); }
          catch (MOMException e) { e.printStackTrace(); }
          server.cleanup();
        }
      }));
    synchronized (this) {
      while (true)
        try { wait(); } catch (InterruptedException e) { return; }
    }
  }

  // Cameras are supplied by whatever constructs the server: see
  // au.edu.uwa.mech.sim.VirtualLab for the simulated ones. (The 1999 main
  // used the native NT framegrabber CameraManager.)
}
