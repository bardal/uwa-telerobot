
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TA200001.java,v 1.14 2000/02/02 17:30:46 bdalton Exp $
// Time-stamp: "2000-02-05 01:05:51 barney"

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
import java.applet.*;
import java.awt.event.*;
import java.util.*;
import java.net.*;

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.LoginEventListener;
import au.edu.uwa.mech.mom.gui.LoginPanel;
import au.edu.uwa.mech.mom.gui.ChatPanel;
import au.edu.uwa.mech.maths.Pose3d;
import au.edu.uwa.mech.maths.Point3d;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.gui.MultiConsole;
import au.edu.uwa.mech.gui.awt.BorderPanel;
import au.edu.uwa.mech.gui.awt.TextConsole;
import au.edu.uwa.mech.gui.awt.YesNoHandler;
import au.edu.uwa.mech.gui.awt.ConfirmDialog;
import au.edu.uwa.mech.gui.overlaid.StickCursor;
import au.edu.uwa.mech.gui.overlaid.Path;
import au.edu.uwa.mech.gui.overlaid.Overlay;
import au.edu.uwa.mech.gui.overlaid.Axes;
import au.edu.uwa.mech.gui.overlaid.CalibratedCanvas;
import au.edu.uwa.mech.gui.overlaid.PointMeasure;
import au.edu.uwa.mech.gui.overlaid.TapeMeasure;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.util.CGILog;
//import au.edu.uwa.mech.devices.robot.CommandPanel;
import au.edu.uwa.mech.devices.robot.SimpleCommandPanel;
import au.edu.uwa.mech.devices.robot.Client;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.devices.robot.MMoveCommand;
import au.edu.uwa.mech.devices.robot.Command;
import au.edu.uwa.mech.devices.robot.Gripper;
import au.edu.uwa.mech.devices.robot.RobotException;
import au.edu.uwa.mech.devices.robot.StateListener;
import au.edu.uwa.mech.devices.robot.RobotListener;
import au.edu.uwa.mech.devices.robot.RobotHandler;
import au.edu.uwa.mech.devices.robot.StateHandler;
import au.edu.uwa.mech.devices.camera.CameraManagerPanel;
import au.edu.uwa.mech.devices.camera.CameraManager;
import au.edu.uwa.mech.devices.camera.CameraManagerClient;
import au.edu.uwa.mech.devices.camera.CameraPanelListener;
import au.edu.uwa.mech.devices.camera.CameraPanel;
import au.edu.uwa.mech.devices.camera.Camera;
import au.edu.uwa.mech.telerobotclient.ArchiveImages;
import au.edu.uwa.mech.model.*;
import au.edu.uwa.mech.net.Hostsfile;

//THIS HAS GOT WAY TO BIG

public class TA200001 extends Applet
{

  TAGUI gui;
  //boolean inited = false;
  
  public String getAppletInfo() {
    return "Robot Control Applet Copyright (C) Barney Dalton January 2000";
  }

    
  public void init () {
    System.out.println("INIT BEING CALLED");
    if (gui==null){
      gui = new TAGUI();
      gui.setup();
      gui.addWindowListener(new java.awt.event.WindowAdapter() {
          public void windowClosing(java.awt.event.WindowEvent e) {gui.finishSession();}
        });
      gui.pack();
      gui.show();
      //inited=true;
    } else
      System.out.println("IGNORING INIT");
  }    
  
  public void destroy() {
    System.out.println("DESTROY BEING CALLED");
  }
  
  public void start() {
  }

  public void stop() {
    System.out.println("STOP BEING CALLED");
  }
  
  class TAGUI extends Frame
    implements CameraPanelListener,
               RobotListener,
               LoginEventListener,
               Observer, YesNoHandler
  {
    SessionRoot        root;
    CameraManager      cameraManager;
    Client             robotClient;
  
    SimpleCommandPanel       commandPanel;
    CameraManagerPanel cameraPanel;
    LoginPanel         lPanel;
    TextConsole        console1, console2;
    ChatPanel          chatPanel;
    Button             move, fromCursor;
 
  
    Vector             sticks;
    Pose3d             stickPose;
    //StickCursor        stickCursor;
    StickCursorCoords  stickCoords;
    Path               path;
    Panel controlPage, loginPage;//, bottom;
    ThePopUpMenu       thePopup;

    //Theses things should be part of An AugrmantedReality panel that
    //contains the cameramanger panel + overlays. It would also handle
    //all mouse clicks etc
    TapeMeasure        tapeMeasure;
    PointMeasure       pointMeasure;
    WorkspaceClient    workspaceClient;
    WorkspaceModel     model;
    //Log                uiLog, delayLog;
    
    final static String LOGINPAGE = "Login Page";
    final static String CONTROLPAGE = "Control Page";
  
  
    public TAGUI() {
      super("Telerobot Control Applet v0.1");
    }

    public Dimension getPreferredSize(){
      return new Dimension( getIntParameter("appletwidth", 800),
                            getIntParameter("appletheight",600));
    }
    
    
    public void setup()
    {
      System.out.println("Setting up TAGUI");
      try{
        long now = new Date().getTime();
        String basename = "TA20001"+InetAddress.getLocalHost().getHostAddress()+"_"+now;
        URL poster = new URL(getParameter("LogURL"));
        TheLog.setLog(new CGILog(basename+".rlog", poster));
        //uiLog = new CGILog(basename+".uilog", poster);
        //delayLog = new CGILog("remote_delay.log", poster);
      } catch (Exception e){
        System.out.println(e.getMessage());
      }

      setFont(new Font("Helvetica", Font.PLAIN, getIntParameter("fontsize", 10)));
    
 
      lPanel = new LoginPanel();
  
      robotClient = new Client();
      console1    = new TextConsole();
      console2    = new TextConsole(getIntParameter("consolerows",8),20);
      chatPanel   = new ChatPanel();
      controlPage = new Panel();
      //bottom    = new Panel();
      thePopup    = new ThePopUpMenu();
      add(thePopup.pum);
      
      //stickCursor = new StickCursor();
      sticks        = new Vector();
      stickPose     = new Pose3d(250,250,100,0,0);
      stickCoords   = new StickCursorCoords();
      path          = new Path();
      pointMeasure  = new PointMeasure();
      tapeMeasure   = new TapeMeasure();
      pointMeasure.setEnabled(false);
      tapeMeasure.setEnabled(false);
      
      MultiConsole mc = new MultiConsole();
      mc.addConsole(console1);
      mc.addConsole(console2);
      TheConsole.setConsole(mc);
      
    
      setBackground(new Color(220,140,100));
      setLayout(new CardLayout());
      //setLayout(new BorderLayout());
      loginPage=new Panel();
      loginPage.setLayout(new BorderLayout());
      controlPage.setLayout(new BorderLayout());
    
      add(LOGINPAGE, loginPage);
      add(CONTROLPAGE, controlPage);

      Hostsfile.put(getStringParameter("remotehost", "developer.mech.uwa.edu.au"),
                   getStringParameter("remoteip", "130.95.52.61"));
      System.out.println("Creating SessionRoot");
      try{root = new SessionRoot(getParameter("MOMURL"));}
      catch(MOMException e){e.printStackTrace();}

      root.addLoginEventListener(this);
      lPanel.setLoginHandler(root);
      robotClient.setRoot(root);
    
      commandPanel = new SimpleCommandPanel();
      commandPanel.setRobotHandler(robotClient); // or something
      //do this from within controlPanel
  
      robotClient.addRobotListener(this);

      //Login page
      loginPage.add(lPanel, "North");
      loginPage.add(new TextArea("Login using your username, PIN and type a nickname.\nGuests don't need to worry about a PIN, but you should use a nickname.\n After you login you should see images of the robot.\nTry and gain control and move it.\nNo help is available for the interface yet!!!!"), "Center");
    
      loginPage.add(console1,"South");

      //Control page
    
      //Panel top = new Panel();
      //Panel left = new Panel();
      //left.setLayout(new BorderLayout());
      //Panel buttons = new Panel();
      //move         = new Button ("Random command");
      //fromCursor   = new Button ("Command from cursor");
      //buttons.add(move);
      //move.addActionListener(new RandomMoveListener());
      //buttons.add(fromCursor);
      //fromCursor.addActionListener(new FromCursorListener());
    
      //left.add(buttons, "North");
      //left.add(commandPanel, "Center");
      //top.add(lPanel);

      //Panel overlayButtons = new Panel();
      //Button toggle=new Button("Toggle cursor");
      //toggle.addActionListener(new ToggleCursor());
      //overlayButtons.add(toggle);
      //Button clearPath=new Button("Clear path");
      //clearPath.addActionListener(new ClearPathListener());
      //overlayButtons.add(clearPath);
    
      cameraManager = new CameraManagerClient(root);
      cameraPanel =  new CameraManagerPanel
        (cameraManager,  getIntParameter("imagewidth", 400) , getIntParameter("imageheight", 300));
      cameraPanel.setUseURLs(true);
      cameraPanel.addCameraPanelListener(this);
    
      commandPanel.setEnabled(false);
    

      
      //TheLog.println("console parent "+console2.getParent()+" "+cp);

      //bottom = new Panel(new GridLayout(1,2));
      //bottom.add(cp);
      //bottom.add(chatB);

      

      //TheLog.println("done bottom panels");


      
    
      Panel consoleB = new BorderPanel("Console", new BorderLayout());
      consoleB.add(console2, "Center");
      Panel chatB = new BorderPanel("Chat", new BorderLayout());
      chatB.add(chatPanel, "Center");

      Panel robotB = new BorderPanel("Robot", new BorderLayout());
      robotB.add(commandPanel, "Center");

      Panel cameraB = new BorderPanel("Cameras", new BorderLayout()); 
      //cameraB.add(overlayButtons, "North");
      cameraB.add(cameraPanel, "Center");

      // fixed width for the controls; the cameras take the rest
      Panel left = new Panel(new BorderLayout()) {
          public Dimension getPreferredSize() {
            return new Dimension(getIntParameter("controlswidth", 360), super.getPreferredSize().height);
          }
        };
      left.add(robotB, "North");
      left.add(consoleB, "Center");

      // the cameras take any extra space when the window is enlarged
      Panel top = new Panel(new BorderLayout());
      top.add(left, "West");
      top.add(cameraB, "Center");
      
      controlPage.add(top, "Center");
      Panel chatArea = new Panel(new BorderLayout()) {
          public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, getIntParameter("chatheight", 110));
          }
        };
      chatArea.add(chatB, "Center");
      controlPage.add(chatArea, "South");
      
      ((CardLayout)getLayout()).show(this, LOGINPAGE);
    }

    public void setConnected(StateHandler source, boolean connected){
    }
  
    public void newRobotState(StateHandler source, RobotState robotState){
      path.addPoint(robotState.pose.position);
      cameraPanel.repaint();
    }

    public void newController(RobotHandler source, String name, boolean us){
      thePopup.setInControl(us);
    }
    
    public void noController(RobotHandler source){
      thePopup.setInControl(false);
    }
    
    public void loggedIn(Peer p){
    
      try{
        Channel chat=(Channel)root.lookupOrCreate(new ContextURL("chat.channel"));
        chatPanel.setChannel(chat);
      } catch (MOMException e){
        TheConsole.println("Problem subscribing to chat channel : "+e);
        e.printStackTrace();
      }

      try{
        Channel channel=(Channel)root.lookupOrCreate(new ContextURL("workspace.channel"));
        workspaceClient = new WorkspaceClient(channel);
        model = workspaceClient.model;
      } catch (MOMException e){
        TheConsole.println("Problem subscribing to workspace channel : "+e);
        e.printStackTrace();
      }
      

      buildMenus();
      
      ((CardLayout)getLayout()).show(this, CONTROLPAGE);

      //bottom.add(chatPanel, "Center");
      //controlPage.add(bottom, "South");
    }
  
    public void loggedOut(Peer p){
      ;
    }

    CheckboxMenuItem miPoint, miLine;
    public void buildMenus()
    {
      MenuBar mb;
      Menu mRobot, mCameras, mHelp, mworkspace;
      MenuItem miRMove, miCommandCursor, miClearPath;
      CheckboxMenuItem  miToggleCursor;

      //Build the menu bar.
      mb = new MenuBar();
      setMenuBar(mb);


      Menu mSession = new Menu("Connection", true);
      MenuItem mi = new MenuItem("Logout");
      mi.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e) {
            ConfirmDialog cd=new ConfirmDialog(TAGUI.this, "Really logout?", TAGUI.this);
          }});
      mSession.add(mi);
      mb.add(mSession);
      
      mRobot = new Menu("Robot", true);
      mb.add(mRobot);
      miRMove = new MenuItem("Generate Random Move");
      miRMove.addActionListener(new RandomMoveListener());
      mRobot.add(miRMove);
      miCommandCursor = new MenuItem("Comand from cursor");
      miCommandCursor.addActionListener(new FromCursorListener());
      mRobot.add(miCommandCursor);

      mi = new MenuItem("Command from selected point");
      mi.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e) {
            if (model.getSelected() != null)
              commandPanel.appendTextCommand(MMoveCommand.pointAsGBHString(model.getSelected().getPosition()));
            else
              TheConsole.println("No point selected");
          }});
      mRobot.add(mi);
        
      
      MenuItem miClose = new MenuItem("Close gripper");
      miClose.addActionListener(new CloseListener());
      mRobot.add(miClose);
      MenuItem miOpen = new MenuItem("Open gripper");
      miOpen.addActionListener(new OpenListener());
      mRobot.add(miOpen);

      mCameras = new Menu("Images", true);
      mb.add(mCameras);
      mi = new MenuItem("Archive Images");
      mi.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e) {
            try{
              Vector images = new Vector();
              for (Enumeration en = cameraManager.cameras();en.hasMoreElements();){
                Camera c= (Camera)en.nextElement();
                images.addElement(c.lastPhoto().filename);
              }
              ArchiveImages ai = new ArchiveImages
                (new URL(getStringParameter("archiveURL", "http://developer.mech.uwa.edu.au:10005/cgi-shl/ArchiveCreation.pl")),
                         TAGUI.this, root.getUs().toString(), images);
            
            } catch (MalformedURLException mue){
              TheLog.logException(mue);
            } 
        }});
      mCameras.add(mi);
      mCameras.addSeparator();
      

      miClearPath = new MenuItem("Clear Path");
      miClearPath.addActionListener(new ClearPathListener());
      mCameras.add(miClearPath);
      miToggleCursor = new CheckboxMenuItem("Show Cursor", showing);
      miToggleCursor.addItemListener(new ToggleCursor());
      mCameras.add(miToggleCursor);
      CheckboxMenuItem cbmi = new CheckboxMenuItem("Show Axes", true);
      cbmi.addItemListener(new ItemListener(){
          public void itemStateChanged(ItemEvent e) {
          axes.setVisible(e.getStateChange()==ItemEvent.SELECTED);
          }});
      mCameras.add(cbmi);


      
      cbmi = new CheckboxMenuItem("Show Points", true);
      cbmi.addItemListener(new ItemListener(){
          public void itemStateChanged(ItemEvent e) {
          model.setVisible(e.getStateChange()==ItemEvent.SELECTED);
        }});
      mCameras.add(cbmi);
      
      Menu mWorkspace = new Menu("Workspace", true);
      mb.add(mWorkspace);
      MenuItem miAddPoint = new MenuItem("Add Point");
      miAddPoint.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e) {
            AddPointDialog d = new AddPointDialog(TAGUI.this, workspaceClient);
            }
        });
      mWorkspace.add(miAddPoint);

      MenuItem miDeletePoint = new MenuItem("Delete Point");
      miDeletePoint.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e) {
            System.out.println("Showing delete point dialog");
            DeletePointDialog d = new DeletePointDialog(TAGUI.this, workspaceClient);
            }
        });
      mWorkspace.add(miDeletePoint);

      
      miPoint = new CheckboxMenuItem("Measure point", false);
      miPoint.addItemListener(new ItemListener(){
          public void itemStateChanged(ItemEvent e) {
            boolean checked = (e.getStateChange()==ItemEvent.SELECTED);
            pointMeasure.setEnabled(checked);
            if(checked){
              pointMeasure.start();
              tapeMeasure.setEnabled(!checked);
              miLine.setState(!checked);
            }
          }});
      mWorkspace.add(miPoint);
        
      miLine = new CheckboxMenuItem("Measure line", false);
      miLine.addItemListener(new ItemListener(){
          public void itemStateChanged(ItemEvent e) {
            boolean checked = (e.getStateChange()==ItemEvent.SELECTED);
            tapeMeasure.setEnabled(checked);
            if(checked){
              tapeMeasure.start();
              pointMeasure.setEnabled(!checked);
              miPoint.setState(!checked);
            }
          }});
      mWorkspace.add(miLine);

      
      mHelp = new Menu("Help", true);
      mb.add(mHelp);
      MenuItem miBrief = new MenuItem("Brief Help");
      miBrief.addActionListener(new HelpListener());
      mHelp.add(miBrief);
      
      
//        final PopupMenu popup = new PopupMenu("Commands");
//        popup.add(new MenuItem("From Cursor"));
//        popup.add(new MenuItem("gripper close"));
//        popup.add(new MenuItem("gripper open"));

//        commandPanel.addMouseListener( new MouseAdapter() {
//            public void mousePressed(MouseEvent e) {
//              System.out.println("popup mouse listener");
//              if(e.isPopupTrigger())
//                popup.show(TAGUI.this, e.getX(), e.getY());
//            }
//          });

//        controlPage.addMouseListener(new TempMouseListener());
    }

    Axes axes= new Axes();
    public void created(CameraPanel cPanel){

      CalibratedCanvas cc= cPanel.getImageCanvas();
      StickCursor sc= new StickCursor();
      sticks.addElement(sc);
      cc.addOverlay(sc);
      cc.addOverlay(path);
      cc.addOverlay(axes);
      cc.addOverlay(tapeMeasure);
      cc.addOverlay(pointMeasure);
      cc.addOverlay(stickCoords);
      cc.addOverlay(workspaceClient.model);
      cc.addMouseMotionListener(sc);
      cc.addMouseListener(sc);
      cc.addMouseListener(tapeMeasure);
      cc.addMouseListener(pointMeasure);
      cc.addMouseListener(workspaceClient.model);
      TheLog.println( "CREATED NEW CAMERA PANEL moving stickcursor to "+stickPose);
      sc.movePose(stickPose);
      sc.observable().addObserver(this);
      workspaceClient.model.observable().addObserver(this);
      //tapeMeasure.addObserver(this);
      doLayout();

      cc.addMouseListener( new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            //System.out.println("popup event "+e);
            if(e.isPopupTrigger() || ((e.getModifiers() & InputEvent.BUTTON3_MASK) !=0)){
              thePopup.pum.show(e.getComponent(), e.getX(), e.getY());
              e.consume();
            }
          }
        });
      
    }

    //mess !!!!! should probably only have one stick cursor and recalc
    //for each image. not sure....
    //shoudl be part of an AR panel whatever happens
    public void moveStickCursor(Pose3d pose, Object ignoreStick)
    {
      for(Enumeration e=sticks.elements();e.hasMoreElements();)
      {
        StickCursor sc=(StickCursor)e.nextElement();
        if (sc != ignoreStick)
          sc.movePose(pose);
      }
    }

    public void moveStickCursor(Point3d point, Object ignoreStick)
    {
      for(Enumeration e=sticks.elements();e.hasMoreElements();)
      {
        StickCursor sc=(StickCursor)e.nextElement();
        if (sc != ignoreStick)
          sc.movePoint(point);
      }
    }
    
    
    public void update(Observable source, Object arg)
    {
      if (arg instanceof StickCursor){
        stickPose=((StickCursor)arg).getPose();
        moveStickCursor(stickPose, arg);
      } ///else if (arg instanceof tapeMeasure){
        //}
      
    }  
  
    class StickCursorCoords implements  Overlay {
      public void overlay(java.awt.Graphics g){
        g.setColor(Color.red);
        Font old = g.getFont();
        Font bold = new Font(old.getName(), Font.BOLD, old.getSize()+1);
        g.setFont(bold);
        g.drawString(stickPose.toDisplayString(), 10, 20);
        g.setFont(old);
      }
    
    }
  
  
    class RandomMoveListener  implements ActionListener
    {

      public void actionPerformed(ActionEvent e) {
        RobotState rs = RobotState.random();
        commandPanel.appendTextCommand(MMoveCommand.stateAsGBHString(rs));
      }
    }

    class OpenListener  implements ActionListener
    {

      public void actionPerformed(ActionEvent e) {
        commandPanel.appendTextCommand("gopen");
      }
    }

    class CloseListener  implements ActionListener
    {
      public void actionPerformed(ActionEvent e) {
        commandPanel.appendTextCommand("gclose");
      }
    }
    
    class FromCursorListener  implements ActionListener
    {
      public void actionPerformed(ActionEvent e) {
        commandPanel.appendTextCommand(MMoveCommand.poseAsGBHString(stickPose));
      }
    }

    boolean showing = true;
    class ToggleCursor implements  ActionListener, ItemListener
    {
      public void actionPerformed(ActionEvent ev) {
        setShowing(!showing);
      }

      public void itemStateChanged(ItemEvent e) {
        setShowing(e.getStateChange()==ItemEvent.SELECTED);
      }

      void setShowing(boolean s)
      {
        showing = s;
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

    class HelpListener  implements ActionListener
    {

      public void actionPerformed(ActionEvent e) {
        try {
          System.out.println("trying to open help window");
          getAppletContext().showDocument
            (new URL(getDocumentBase(), "help.html"), "help");
            //(new URL("javascript:myOpen(\"help.html\",\"helpWindow\",320,400)"));
        }
        catch (MalformedURLException me) {
          me.printStackTrace();
        }
      }
    }
    
    
//      void resizeConsole(int width)
//      {
//        Dimension d= console.getPreferredSize(10, 100);
//        console.setColumns(Math.round(((float)width/d.width)*100));
//        TheLog.println("Set console to "+console.getColumns()+" columns");
//      }
    int getIntParameter(String name, int defaultVal)
    {
      try{return Integer.parseInt(getParameter(name));}
      catch(Exception e){return defaultVal;}
    }
    
    String getStringParameter(String name, String defaultVal)
    {
      String p=getParameter(name);
      if (p!=null)
        return p;
      else
        return defaultVal;
    }

    void sendCommand(Command command){
      try {
        robotClient.getCommands().setCommand(command);
        robotClient.sendCommands();
      } catch (RobotException e){
        TheConsole.println(e.getMessage());
      }
      
      
    }

    //yesnohandler
    public void no(Object source){;}

    //callled to logout
    public void yes(Object source){
      finishSession();
    }
    

    void finishSession()
    {
      setVisible(false);
      ((CardLayout)getLayout()).show(this, LOGINPAGE);
        
      try{
        getAppletContext().showDocument
            (new URL(getStringParameter("logouturl", "http://telerobot.mech.uwa.edu.au/")
                     +"?nickname="+root.getUs()));
      }
      catch(MalformedURLException e){TheLog.logException(e);}
      
      try{root.logout();}
      catch(Exception e){TheLog.logException(e);}

    
    }
    
    
    class ThePopUpMenu {

      MenuItem miMove, miMovePoint, miOpen, miClose;

      
      PopupMenu pum= new PopupMenu("Robot <-> Cursor");
      
      ThePopUpMenu()
      {
        MenuItem mi;
        

        mi = new MenuItem("align cursor with robot");
        mi.addActionListener(new ActionListener(){
             public void actionPerformed(ActionEvent e) {
               moveStickCursor(robotClient.getState().pose,null);
             }});
        pum.add(mi);
	

        mi = new MenuItem("set cursor to home");
        mi.addActionListener(new ActionListener(){
              public void actionPerformed(ActionEvent e) {
              moveStickCursor(new Pose3d(250,250,100,0,0),null);
            }});
        pum.add(mi);

        mi = new MenuItem("set cursor to point");
        mi.addActionListener(new ActionListener(){
              public void actionPerformed(ActionEvent e) {
                if (model.getSelected() != null)
                  moveStickCursor(model.getSelected().getPosition(),null);
                else
                  TheConsole.println("No point selected");
            }});
        pum.add(mi);

        pum.addSeparator();


        miMove = new MenuItem("move robot to cursor");
        miMove.addActionListener(new ActionListener(){
              public void actionPerformed(ActionEvent e) {
              sendCommand(new MMoveCommand(stickPose));
            }});
        miMove.setEnabled(false);
        pum.add(miMove);

        
        miMovePoint = new MenuItem("move robot to point");
        miMovePoint.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
              if (model.getSelected() != null)
                sendCommand(new MMoveCommand(model.getSelected().getPosition()));
              else
                TheConsole.println("No point selected");
            }});
        miMovePoint.setEnabled(false);
        pum.add(miMovePoint);

        miOpen = new MenuItem("open gripper");
        miOpen.addActionListener(new ActionListener(){
              public void actionPerformed(ActionEvent e) {
              sendCommand(new MMoveCommand(Gripper.newOpen()));
          }});
        miOpen.setEnabled(false);
      	pum.add(miOpen);
	

	miClose = new MenuItem("close gripper");
        miClose.addActionListener(new ActionListener(){
              public void actionPerformed(ActionEvent e) {
              sendCommand(new MMoveCommand(Gripper.newClosed()));
            }});
        miClose.setEnabled(false);
      	pum.add(miClose);

//          pum.addSeparator();
        
//          mi = new MenuItem("Clear Path");
//          mi.addActionListener(new ClearPathListener());
//          pum.add(mi);

//          mi = new MenuItem("Measure Point");
//          mi.addActionListener(new ActionListener(){
//              public void actionPerformed(ActionEvent e) {
//                positionMeasurer.measurePoint();
//              }});
//        	pum.add(mi);

      }

      void setInControl(boolean inControl){
        miMove.setEnabled(inControl);
        miOpen.setEnabled(inControl);
        miClose.setEnabled(inControl);
        miMovePoint.setEnabled(inControl);
      }
    }
    
      
  }
}
