

// Copyright (C) 1999, 2000 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Sun Sep 05 20:28:35 1999
// Version       : $Id: Calibrate.java,v 1.7 2000/01/27 11:50:43 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

// {{{ GNU General Public License

//  This program is free software; you can redistribute it and/or
//  modify it under the terms of the GNU General Public License
//  as published by the Free Software Foundation; either version 2
//  of the License, or (at your option) any later version.

//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

// }}}

package au.edu.uwa.mech.applications.calibration;


import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import au.edu.uwa.mech.maths.*;

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.devices.camera.CameraPanel;
import au.edu.uwa.mech.devices.camera.CameraManager;
import au.edu.uwa.mech.devices.camera.CameraManagerClient;
import au.edu.uwa.mech.devices.camera.Camera;
import au.edu.uwa.mech.devices.camera.CameraException;
import au.edu.uwa.mech.devices.robot.Client;
import au.edu.uwa.mech.devices.robot.GBHCommand;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.devices.robot.Gripper;
import au.edu.uwa.mech.devices.robot.StateListener;
import au.edu.uwa.mech.devices.robot.StateHandler;
import au.edu.uwa.mech.devices.robot.RobotException;
import au.edu.uwa.mech.maths.Pose3d;

import au.edu.uwa.mech.util.ConfigFile;
import au.edu.uwa.mech.util.XMLConfigFile;

import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.gui.swing.SwingConsole;
import au.edu.uwa.mech.gui.swing.FileViewer;

import au.edu.uwa.mech.gui.overlaid.Overlay;

public class Calibrate extends JApplet implements StateListener {

  
  static Calibrate calibrate;
  static Vector points= new Vector();
  static Vector calPoints= new Vector();
  JLabel label;
  JLabel lCurrentPoint;
  JLabel rLabel;
  JButton bNextMove, bPreviousMove, bCalibrate, bLogin, bGoto, bCalFile, bLogout;
  JTextField tGoto;
  //Enumeration eStates;
  Vector states= new Vector();

  FileViewer calibFrame;

  int currentPoseNo=0;
  int numPoses=0;
  
  SessionRoot root;
  CameraManager cameraManager;
  CameraPanel cameraPanel;
  Client robotClient;

  String xyzfile, uvfile, camfile, momURL, cameraName;
  Container container;
  int defaultSpin=90;
  
  public Calibrate(ConfigFile config){
    xyzfile = config.getString("robot", "points", "xyz.txt");
    uvfile  = config.getString("image", "points", "uv.txt");
    camfile = config.getString("camera", "out", "cam");
    momURL  = config.getString("MOM", "url", "rmi://localhost");
    cameraName = config.getString("camera", "name", "XAxis");
    defaultSpin = config.getInt("robot", "spin", defaultSpin);
  }
  
  public static void main(String[] args) {

    try{
      
      String configFile = "calibrate.xml";
      if(args.length == 1)
        configFile=args[0];

      ConfigFile config = new XMLConfigFile(configFile);
    
      calibrate = new Calibrate(config);
   
      
      JFrame f = new JFrame("ImageDisplayer");
      calibrate.buildUI(f.getContentPane());
      f.addWindowListener(new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            calibrate.logout();
            System.exit(0);
          }
        });
      f.pack();
      f.setVisible(true);

      //calibrate.doCalibration
      //read in x,y,z,spin tilt
      calibrate.loadPoints();
    }
    catch(Throwable e){
      e.printStackTrace();
      calibrate.logout();
      System.exit(0);
    }
  }
  
  
  protected void moveTo(int i) {
    try{
      currentPoseNo = i;
      tGoto.setText(Integer.toString(i+1));
      System.out.println("Executing "+states.elementAt(i));
      robotClient.executeCommand(new GBHCommand((RobotState)states.elementAt(i)));
      pointChanged();
    } catch (RobotException e){
      TheConsole.println("Problem with robot "+e.getMessage());
      e.printStackTrace();
    } catch (NullPointerException e){
      TheConsole.println("Can't move yet, have you logged in?");
    }
    
    
    
  }
  
  protected void setCurrentPointTo(Point point){
    calPoints.setElementAt(point, currentPoseNo);
    pointChanged();
  }

  protected void pointChanged(){
    Point p=getCurrentPoint();
    lCurrentPoint.setText("Calibration Point "+(currentPoseNo+1)+": ("+ p.x + ", "+ p.y + ")");
    cameraPanel.getImageCanvas().repaint();
  }
  
  
  protected Point getCurrentPoint(){
    if (calPoints.size() <=0)
      return null;
    
    Point p= (Point)calPoints.elementAt(currentPoseNo);
    if(p==null)
      calPoints.setElementAt((p=new Point()), currentPoseNo);
    
    return p;
  }
  
  
  protected void finish()
  {
    logout();
    System.exit(0);
  }

  
  protected void calcMatrix() throws IOException{
    
    FileWriter f = new FileWriter(uvfile);
    BufferedWriter w = new BufferedWriter(f);
    
    int n=1;
    for (Enumeration e=calPoints.elements();e.hasMoreElements();){
      Point p=(Point)e.nextElement();
      w.write( "imagepoint "+(n++)+" "+p.x+" "+p.y);
      if(e.hasMoreElements())
        w.newLine();
    }
    w.flush();
    f.close();
    // originally a native call into the VIP library; now a least-squares DLT
    java.util.List<double[]> xyz = CalibrationFile.readPoints(xyzfile, 3);
    java.util.List<double[]> uv = CalibrationFile.readPoints(uvfile, 2);
    Jama.Matrix cal = CalibrationFile.solve(xyz, uv);
    CalibrationFile.write(cal, camfile, "Calibration Data: " + camfile);
    TheConsole.println("Calibrated from " + Math.min(xyz.size(), uv.size()) + " points, rms error "
                       + Math.round(CalibrationFile.rmsError(cal, xyz, uv) * 10) / 10.0 + " pixels");
  }

    
  public void setConnected(StateHandler source, boolean connected){
  }
  
  public void newRobotState(StateHandler source, RobotState robotState){
    rLabel.setText("Robot :"+robotState.toDisplayString());
  }
  
  protected void login(String momURL, String cameraName) throws MOMException, CameraException, RobotException
  {
    try{root = new SessionRoot(momURL);}
    catch(MOMException e){e.printStackTrace();}

    root.login();
    CameraManager cameraManager = new CameraManagerClient(root);
    cameraPanel.setCamera(cameraManager.getCamera(cameraName));
    cameraPanel.setUseURLs(true);
    
    robotClient = new Client(root);
    robotClient.addStateListener(this);
    robotClient.gainControl();
  }

  protected void logout(){
    try{
      if ((root != null) && root.loggedIn()){
        robotClient.releaseControl();
        root.logout();
      }
    }
    catch(MOMException e){e.printStackTrace();}
    catch(RobotException e){e.printStackTrace();}
  }
  
  protected void buildUI(Container c)
  {
    this.container = c;
    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

    cameraPanel          = new CameraPanel(800, 500);    
    label                = new JLabel("Mouse coordinates");
    lCurrentPoint        = new JLabel("Current Point :");
    rLabel               = new JLabel("Robot :");
    JPanel       buttons = new JPanel();
    tGoto                = new JTextField(3);
    bGoto                = new JButton("Goto");
    bNextMove            = new JButton("Next");
    bPreviousMove        = new JButton("Previous");
    bCalibrate           = new JButton("Calibrate");
    bCalFile             = new JButton("Calibration File");
    bLogin               = new JButton("Login");
    bLogout              = new JButton("Logout");
    SwingConsole console = new SwingConsole(5, 20);  
    calibFrame           = new FileViewer(500, 400);
    
    container.add(cameraPanel);

    JPanel labels = new JPanel();
    labels.setLayout(new GridLayout(0,2));
                     
    labels.add(label);
    labels.add(rLabel);
    labels.add(lCurrentPoint);
    
    JPanel calibButtons = new JPanel();
    JPanel moveButtons = new JPanel();
    JPanel momButtons = new JPanel();
    
    buttons.setLayout(new GridLayout(0,3,10,2));
    //buttons.setLayout(new BorderLayout(5,0));

    Border empty = BorderFactory.createEmptyBorder(2,10,2,10);
    //Border cb  = BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("calibration"), empty);
    //calibButtons.setBorder(cb);
    calibButtons.setBorder(BorderFactory.createTitledBorder(empty, "calibration"));
    calibButtons.setLayout(new BoxLayout(calibButtons, BoxLayout.Y_AXIS));

    //Border mb =  BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("calibration"), empty);
    moveButtons.setBorder(BorderFactory.createTitledBorder(empty, "moves"));
    moveButtons.setLayout(new GridLayout(0,2,5,1));
    momButtons.setBorder(BorderFactory.createTitledBorder(empty, "mom"));
    momButtons.setLayout(new BoxLayout(momButtons, BoxLayout.Y_AXIS));
    
    
    bPreviousMove.setAlignmentX(Component.LEFT_ALIGNMENT);
    moveButtons.add(bPreviousMove);
    moveButtons.add(bNextMove);
    //JPanel gotoPanel = new JPanel();
    //gotoPanel.setLayout(new BorderLayout());
    //gotoPanel.add(bGoto);//, BorderLayout.WEST);
    //gotoPanel.add(new JLabel("Move: "), BorderLayout.CENTER);
    //gotoPanel.add(tGoto);//, BorderLayout.CENTER);
    //gotoPanel.add(Box.createHorizontalGlue());
    //moveButtons.add(gotoPanel);
    moveButtons.add(bGoto);
    moveButtons.add(tGoto);

    
    calibButtons.add(bCalibrate);
    calibButtons.add(bCalFile);
    
    buttons.add(momButtons);//, BorderLayout.WEST);
    buttons.add(moveButtons);//, BorderLayout.CENTER);
    buttons.add(calibButtons);//, BorderLayout.EAST);
    
    momButtons.add(bLogin);
    momButtons.add(bLogout);
    
    container.add(labels);
    container.add(buttons);
    container.add(console);

    Overlay crosshairs = new CrossHairs(5);
    cameraPanel.getImageCanvas().addOverlay(crosshairs);
    
    TheConsole.setConsole(console);
    
    bLogin.addActionListener(new LoginButtonListener());
    bNextMove.addActionListener(new NextMoveListener());
    bPreviousMove.addActionListener(new PreviousMoveListener());
    bGoto.addActionListener(new GotoMoveListener());
    bCalibrate.addActionListener(new CalibrateListener());
    bCalFile.addActionListener(new ShowCalibListener());
    
    cameraPanel.getImageCanvas().addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseMoved(MouseEvent e)
        {
          label.setText("Mouse coordinates :("
                        + cameraPanel.getImageCanvas().toImage(e.getPoint()).x + ", "
                        + cameraPanel.getImageCanvas().toImage(e.getPoint()).y + ")"); 
        }
      });

    
    cameraPanel.getImageCanvas().addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e){
          setCurrentPointTo(cameraPanel.getImageCanvas().toImage(e.getPoint()));
        } 
    });
    //Align the left edges of the components.
    //imagepanel.setAlignmentX(LEFT_ALIGNMENT);
    label.setAlignmentX(LEFT_ALIGNMENT); //unnecessary, but doesn't hurt

    //imagepanel.showFirstImage();

  }


    
  protected void loadPoints() throws IOException{
    FileReader fr= new FileReader(xyzfile);
    BufferedReader bri = new BufferedReader(fr);
    String line;
    while ((line=bri.readLine())!=null){
      StringTokenizer sto=new StringTokenizer(line);
      String crap = sto.nextToken();
      crap = sto.nextToken();
      int x= Integer.parseInt(sto.nextToken());
      int y= Integer.parseInt(sto.nextToken());
      int z= Integer.parseInt(sto.nextToken());
      Point3d p  =new Point3d(x,y,z);
      
      Orientation3d o = new Orientation3d();
      o.spinDeg(defaultSpin);
      


      states.addElement(new RobotState(p,o, Gripper.newClosed()));
    }
    //eStates=states.elements();
    numPoses=states.size();
    fr.close();

    try{
      fr= new FileReader(uvfile);
      bri = new BufferedReader(fr);
      while ((line=bri.readLine())!=null){
        StringTokenizer sto=new StringTokenizer(line);
        String crap = sto.nextToken();
        crap = sto.nextToken();
        int x= Integer.parseInt(sto.nextToken());
        int y= Integer.parseInt(sto.nextToken());
        calPoints.addElement(new Point(x,y));
      }
      fr.close();
      TheConsole.println("Read in "+calPoints.size()+" points from previous calibration "+uvfile);
    } catch (IOException e){
      TheConsole.println("Couldn't open uv point file "+uvfile+" not  to worry");
    }
    calPoints.setSize(numPoses);
    pointChanged();
  }

  class CrossHairs implements Overlay
  {
    protected CrossHairs(int size){
      this.size=size;
    }
    
    public void overlay(java.awt.Graphics g)
    {
      //System.out.println("crosshair overlay called");
      Point p = getCurrentPoint();
      if (p!=null){
        Color c=g.getColor();
        g.setColor(Color.red);
        g.drawLine(p.x-size, p.y, p.x+size, p.y);
        g.drawLine(p.x, p.y-size, p.x, p.y+size);
        g.setColor(c);
      }
    }
    int size;
  }
  
  
  class LoginButtonListener implements ActionListener
  {
    public void actionPerformed(ActionEvent ev) 
    {
      try{
        login(momURL,cameraName);
        moveTo(currentPoseNo);
      }
      catch(MOMException e){
        TheConsole.println("IS THE MOM RUNNING?");
        e.printStackTrace();
      }
      catch(CameraException e){
        TheConsole.println("Problem with camera "+e.getMessage());
        e.printStackTrace();
      }
      catch(RobotException e){
        TheConsole.println("Problem with robot "+e.getMessage());
        e.printStackTrace();
      }
    }
  }

  class NextMoveListener implements ActionListener
  {
    public void actionPerformed(ActionEvent ev){
      moveTo(++currentPoseNo % numPoses);
    }
  }

  class PreviousMoveListener implements ActionListener
  {
    public void actionPerformed(ActionEvent ev){
      moveTo((numPoses + --currentPoseNo) % numPoses);
    }
  }

  class GotoMoveListener  implements ActionListener
  {
    public void actionPerformed(ActionEvent ev){
      moveTo(Integer.parseInt(tGoto.getText())-1);
    }
  }
  
  
  class CalibrateListener implements ActionListener
  {
    public void actionPerformed(ActionEvent ev){
      try{
        calcMatrix();
      } catch (IOException e){
        e.printStackTrace();
      }
      
      
    }
  }

  class ShowCalibListener implements ActionListener
  {
    public void actionPerformed(ActionEvent ev){
      calibFrame.setURL(camfile+".txt");
      calibFrame.show();
    }
  }

  
  //getting a bit carried away here
//    class CalibrationFile extends JFrame
//    {
//      CalibrationFile()

//    }
  
  
} 




//  if(eStates.hasMoreElements())
//  {
//    int n = JOptionPane.showConfirmDialog
//      (container,
//       "Add "+e.getPoint()+" to list and goto to next image?",
//       "Confirm Mouse Click",
//       JOptionPane.YES_NO_OPTION);

//    if (n==JOptionPane.YES_OPTION)
//    {
//      System.err.println(e.getPoint());
//      clickedpoints.add(e.getPoint());
//      System.out.println("imagepoint "+ e.getPoint().x+" "+e.getPoint().y);
//      makeNextMove();
//      //imagepanel.nextImage();
//    } // end of if (n==YES_OPTION)
//  }
//  else
//  {
//    if(JOptionPane.showConfirmDialog
//       (container, "Generate calibration matrix",
//        "Calibrate", JOptionPane.YES_NO_OPTION)
//       ==
//       JOptionPane.YES_OPTION)
//    {
//      try{
//        calcMatrix();
//        System.out.println("All done");
//        finish();
//      } catch (IOException ex){
//        System.out.println("Couldn't write camera file");
//        ex.printStackTrace();
//      }
//    }
//  }



