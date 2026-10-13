
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ABBDemoApplet.java,v 1.11 1999/12/12 10:31:26 bdalton Exp $
// Time-stamp: "1999-12-12 17:43:38 barney"

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

package au.edu.uwa.mech.applets;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.*;
import java.net.*;

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.LoginEventListener;
import au.edu.uwa.mech.mom.gui.LoginPanel;
import au.edu.uwa.mech.mom.gui.ChatPanel;
import au.edu.uwa.mech.maths.Pose3d;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.gui.awt.BorderPanel;
import au.edu.uwa.mech.gui.awt.TextConsole;
import au.edu.uwa.mech.gui.overlaid.StickCursor;
import au.edu.uwa.mech.gui.overlaid.Path;
import au.edu.uwa.mech.gui.overlaid.Overlay;
import au.edu.uwa.mech.gui.overlaid.CalibratedCanvas;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.util.CGILog;
import au.edu.uwa.mech.devices.robot.CommandPanel;
import au.edu.uwa.mech.devices.robot.CommandPanelMC;
import au.edu.uwa.mech.devices.robot.Client;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.devices.robot.MMoveCommand;
import au.edu.uwa.mech.devices.robot.RobotException;
import au.edu.uwa.mech.devices.robot.StateListener;
import au.edu.uwa.mech.devices.robot.StateHandler;
import au.edu.uwa.mech.devices.camera.CameraManagerPanel;
import au.edu.uwa.mech.devices.camera.CameraManager;
import au.edu.uwa.mech.devices.camera.CameraManagerClient;
import au.edu.uwa.mech.devices.camera.CameraPanelListener;
import au.edu.uwa.mech.devices.camera.CameraPanel;


public class ABBDemoApplet
  extends java.applet.Applet
  implements CameraPanelListener,
             StateListener,
             LoginEventListener,
             Observer
{
  SessionRoot        root;
  CameraManager      cameraManager;
  Client             robotClient;
  
  CommandPanel       commandPanel;
  CameraManagerPanel cameraPanel;
  LoginPanel         lPanel;
  TextConsole        console;
  ChatPanel          chatPanel;
  Button             move, fromCursor;
 
  
  Vector             sticks;
  Pose3d             stickPose;
  //StickCursor        stickCursor;
  StickCursorCoords  stickCoords;
  Path               path;
  Panel controlPage, loginPage, bottom;
  
  final static String LOGINPAGE = "Login Page";
  final static String CONTROLPAGE = "Control Page";
  
  
  public ABBDemoApplet() {
  }

  public String getAppletInfo() {
    return "ABB Demo Robot Control Applet Copyright (C) Barney Dalton August 1999";
  }
  
  public void init () {

    try{
      TheLog.setLog(new CGILog("ABBD"+InetAddress.getLocalHost().getHostAddress()+"_"+(new Date()).getTime()+".rlog",
                               new URL(getParameter("LogURL"))));
    } catch (Exception e){
      e.printStackTrace();
    }

    
    move         = new Button ("Random move");
    fromCursor   = new Button ("From cursor");
    lPanel       = new LoginPanel();
    commandPanel = new CommandPanelMC();
    robotClient  = new Client();
    console      = new TextConsole(7,45);
    chatPanel    = new ChatPanel();
    controlPage  = new Panel();
    bottom       = new Panel();
    
    //stickCursor  = new StickCursor();
    sticks       = new Vector();
    stickPose=new Pose3d(250,250,100,0,0);
    stickCoords  = new StickCursorCoords();
    path         = new Path();
    
    setup();

  }

  public void setup()
  {    
    
    TheConsole.setConsole(console);

    
    setBackground(new Color(255,230,204));
    setLayout(new CardLayout());
    //setLayout(new BorderLayout());
    loginPage=new Panel();
    loginPage.setLayout(new BorderLayout());
    controlPage.setLayout(new BorderLayout());
    
    add(LOGINPAGE, loginPage);
    add(CONTROLPAGE, controlPage);
    
    try{root = new SessionRoot(getParameter("MOMURL"));}
    catch(MOMException e){e.printStackTrace();}

    root.addLoginEventListener(this);
    lPanel.setLoginHandler(root);
    robotClient.setRoot(root);
    commandPanel.setRobotHandler(robotClient); // or something
    //do this from within controlPanel
    robotClient.addRobotListener(commandPanel);
    robotClient.addStateListener(this);

    //Login page
    loginPage.add(lPanel, "North");
    loginPage.add(new TextArea("Login using your username, PIN and type a nickname.\nGuests don't need to worry about a PIN, but you should use a nickname.\n After you login you should see images of the robot.\nTry and gain control and move it.\nNo help is available for the interface yet!!!!"), "Center");
    
    loginPage.add(console,"South");

    //Control page
    
    //Panel top = new Panel();
    Panel left = new Panel();
    left.setLayout(new BorderLayout());
    Panel buttons = new Panel();
    buttons.add(move);
    buttons.add(fromCursor);

    Button toggle=new Button("Toggle cursor");
    buttons.add(toggle);
    Button clearPath=new Button("Clear path");
    buttons.add(clearPath);

    left.add(buttons, "North");
    left.add(commandPanel, "Center");
    //top.add(lPanel);
  
    cameraManager = new CameraManagerClient(root);
    cameraPanel =  new CameraManagerPanel(cameraManager, 300, 300);
    cameraPanel.setUseURLs(true);
    cameraPanel.addCameraPanelListener(this);
    
    commandPanel.setEnabled(false);
    
    move.addActionListener(new RandomMoveListener());
    fromCursor.addActionListener(new FromCursorListener());
    toggle.addActionListener(new ToggleCursor());
    clearPath.addActionListener(new ClearPathListener());

    

    bottom = new Panel(new BorderLayout());
    Panel lp = new BorderPanel("Chat", new BorderLayout());
    //Panel lp = new
    lp.add(chatPanel, "Center");
    bottom.add(lp, "Center");


    TheLog.println("done bottom panels");


    Panel robotB = new BorderPanel("Robot", new BorderLayout());
    robotB.add(left, "Center");

    Panel cameraB = new BorderPanel("Cameras", new BorderLayout()); 
    cameraB.add(cameraPanel, "Center");
    
    controlPage.add(robotB, "Center");
    controlPage.add(cameraB, "East");
    controlPage.add(bottom, "South");


    ((CardLayout)getLayout()).show(this, LOGINPAGE);
  }

  public void setConnected(StateHandler source, boolean connected){
  }
  
  public void newRobotState(StateHandler source, RobotState robotState){
    path.addPoint(robotState.pose.position);
    cameraPanel.repaint();
  }

  public void loggedIn(Peer p){

    loginPage.remove(console);
    Panel cp = new BorderPanel("Console", new BorderLayout());
    cp.add(console, "Center");
    bottom.add(cp, "West");
    invalidate();
    
    try{
      Channel chat=(Channel)root.lookupOrCreate(new ContextURL("chat.channel"));
      chatPanel.setChannel(chat);
    } catch (MOMException e){
      TheConsole.println("Problem subscribing to chat channel : "+e);
      e.printStackTrace();
    }
     ((CardLayout)getLayout()).show(this, CONTROLPAGE);

    //bottom.add(chatPanel, "Center");
    //controlPage.add(bottom, "South");
  }

  
  public void loggedOut(Peer p){
    ;
  }
  
  
  
  public void destroy() {
  }
  
  public void start() {
  }

  public void stop() {
  }

  public void created(CameraPanel cPanel){

    CalibratedCanvas cc= cPanel.getImageCanvas();
    StickCursor sc= new StickCursor();
    sticks.addElement(sc);
    cc.addOverlay(sc);
    cc.addOverlay(path);
    cc.addOverlay(stickCoords);
    cc.addMouseMotionListener(sc);
    cc.addMouseListener(sc);
    TheLog.println( "CREATED NEW CAMERA PANEL moving stickcursor to "+stickPose);
    sc.movePose(stickPose);
    sc.observable().addObserver(this);
    doLayout();
  }

  public void update(Observable source, Object arg)
  {
    if (arg instanceof StickCursor){
      stickPose=((StickCursor)arg).getPose();

      for(Enumeration e=sticks.elements();e.hasMoreElements();)
      {
        StickCursor sc=(StickCursor)e.nextElement();
        if (sc != arg)
          sc.movePose(stickPose);
      }
    }
  }

  
  
  class StickCursorCoords implements  Overlay {
    public void overlay(java.awt.Graphics g){
      g.setColor(Color.cyan);
      g.drawString(stickPose.toDisplayString(), 10, 20);
    }
    
  }
  
  
  class RandomMoveListener  implements ActionListener
  {

    public void actionPerformed(ActionEvent e) {
      RobotState rs = RobotState.random();
      commandPanel.appendTextCommand(MMoveCommand.stateAsGBHString(rs));
    }
  }

  class FromCursorListener  implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {
        commandPanel.appendTextCommand(MMoveCommand.poseAsGBHString(stickPose));
    }
  }

  class ToggleCursor implements  ActionListener
  {
    boolean showing = true;
    public void actionPerformed(ActionEvent ev) {
      showing = !showing;
      for(Enumeration e=sticks.elements();e.hasMoreElements();)
        ((StickCursor)e.nextElement()).setVisible(showing);
      cameraPanel.repaint();
    }
  }
  
  class ClearPathListener  implements ActionListener
  {

    public void actionPerformed(ActionEvent e) {
      path.clear();
      cameraPanel.repaint();
    }
  }

  
}
