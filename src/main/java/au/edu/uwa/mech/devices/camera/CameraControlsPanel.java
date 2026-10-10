
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: CameraControlsPanel.java,v 1.12 2000/01/27 11:50:43 bdalton Exp $
// Time-stamp: "2000-01-27 15:47:33 barney"

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


package au.edu.uwa.mech.devices.camera;

import java.awt.TextField;
import java.awt.Button;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import au.edu.uwa.mech.gui.awt.IntegerTextField;
import au.edu.uwa.mech.gui.awt.FloatTextField;
import au.edu.uwa.mech.util.MathExtras;
import au.edu.uwa.mech.util.TheLog;
//import au.edu.uwa.mech.mom.impl.*;
import au.edu.uwa.mech.gui.TheConsole;


public class CameraControlsPanel extends java.awt.Panel
  implements ActionListener, ItemListener, CameraListener 
{
  protected IntegerTextField widthField         = new IntegerTextField(400, 5);
  protected IntegerTextField heightField        = new IntegerTextField(400, 5);
  protected IntegerTextField qualityField       = new IntegerTextField(100, 4);
  protected FloatTextField   zoomField          = new FloatTextField((float)0.0, 4);
  protected Button           changeSettings     = new Button("Change Settings");
  protected Button           shutter            = new Button("New Image");
  protected Checkbox         fixedCB            = new Checkbox(); //Default state is "off" (false).
  protected Checkbox         onBox              = new Checkbox("",true);
  protected Panel            settingsPanel      = new Panel();
  protected Camera           camera;
  protected Vector           settingsComponents = new Vector();
  
  public CameraControlsPanel(){
    setupUI();
  }
  
  
  public CameraControlsPanel(Camera camera){
    setCamera(camera);
    setupUI();
  }


  protected void setupUI(){
    //setLayout(new GridLayout(2,5));

    Panel p=new Panel(new GridLayout(2,4));
    
    //first row
    p.add(new Label("width"));
    p.add(new Label("height"));
    p.add(new Label("zoom"));
    p.add(new Label("quality"));
    
    //next row
    p.add(widthField);
    p.add(heightField);
    p.add(zoomField);
    p.add(qualityField);
    settingsPanel.add(p);
    
    p=new Panel(new GridLayout(2,1));
    p.add(new Label("fixed"));
    p.add(fixedCB);
    settingsPanel.add(p);

  
    
    p=new Panel(new GridLayout(2,1));
    p.add(shutter);
    p.add(changeSettings);
    settingsPanel.add(p);

    p=new Panel(new GridLayout(2,1));
    p.add(new Label("on"));
    p.add(onBox);
    onBox.setEnabled(false);
    add(p);
    add(settingsPanel);
    
    changeSettings.addActionListener(this);
    shutter.addActionListener(this);
    onBox.addItemListener(this);

    settingsComponents.addElement(widthField);
    settingsComponents.addElement(heightField);
    settingsComponents.addElement(qualityField);
    settingsComponents.addElement(zoomField);
    settingsComponents.addElement(changeSettings);
    settingsComponents.addElement(shutter);
    settingsComponents.addElement(fixedCB);
    setEnabled(false);
    
  }

  public void setEnabled(boolean b){
    for (Enumeration e=settingsComponents.elements();e.hasMoreElements();){
      ((Component)e.nextElement()).setEnabled(b);
    }
    
  }
  
  protected void updateGUI(CameraSettings settings){
    System.out.println("Updating control panel with "+settings);
    widthField.setValue(settings.getWidth());
    heightField.setValue(settings.getHeight());
    zoomField.setValue(settings.getZoom());
    qualityField.setValue(settings.getQuality());
    fixedCB.setState(settings.getFixed());
  }
  
  
  public void newSettings(Object source, CameraSettings settings){
    System.out.println("CameraControlsPanel changing settings");
    updateGUI(settings);
  }
  


  public void actionPerformed(ActionEvent e){
    try{
      
      if(e.getSource() == changeSettings){
        CameraSettings settings=camera.getSettings();
        //make new settings object
        settings.setWidth(widthField.getValue());
        settings.setHeight(heightField.getValue());
        settings.setZoom(MathExtras.limit(zoomField.getValue(),0,1));
        settings.setFixed(fixedCB.getState());
        settings.setQuality(MathExtras.limit(qualityField.getValue(),0,50));
        camera.setSettings(settings);
      }
      else if (e.getSource() == shutter){
        camera.takePhoto();
      } 
      
      
    } catch (CameraException ex){
      TheConsole.println("Problem with camera "+getCamera().getName()+" "+ex.toString());
      TheLog.logException(ex);
    }
  }
  

  public void itemStateChanged(ItemEvent e)
  {
    camera.setOn(onBox.getState());
    setEnabled(onBox.getState());
  }
  
  
  /**
     * Get the value of camera.
     * @return Value of camera.
     */
  public Camera getCamera() {return camera;}
  
  /**
     * Set the value of camera.
     * @param v  Value to assign to camera.
     */
  public void setCamera(Camera  v) {
    this.camera = v;
    camera.addCameraListener(this);
    setEnabled(true);
    onBox.setEnabled(true);
  }
 
  static public void debug(String s){
    if (debug)
      System.err.println(s);
  }
  
  static protected boolean debug=true;

  public static void main (String[] args)
  {
    Frame f = new Frame();
    CameraControlsPanel cp=new CameraControlsPanel();
    f.add(cp);
    f.pack();
    f.show();
    f.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {System.exit(0);}
      });
  } // end 
}



