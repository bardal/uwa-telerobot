
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: CameraManagerPanel.java,v 1.5 2000/01/19 11:43:20 bdalton Exp $
// Time-stamp: "2000-01-19 16:20:59 barney"

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

import java.awt.*;
import java.awt.event.*;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;
import  au.edu.uwa.mech.util.TheLog;

public class CameraManagerPanel extends java.awt.Panel implements CameraManagerListener, ActionListener 
{
  //Choice      cameraChoice    = new Choice();
  CameraManager cameraManager;
  Hashtable     cameraPanels    = new Hashtable();
  Panel         currentPanel;
  boolean       firstPanel      = true;
  boolean       useURLs         = false;
  Panel         buttons         = new Panel();
  Panel         cameras         = new Panel();
  CardLayout    cameraLayout    = null;
  Vector        cPanelListeners = new Vector();
  int           panelWidth, panelHeight;
  int noCameras=0;
  
  public CameraManagerPanel(CameraManager cameraManager, int panelWidth, int panelHeight){
    TheLog.println("Creating Cameramanagerpanel "+panelWidth+"x"+panelHeight);

    //cameraChoice.addItemListener(this);
    setLayout(new BorderLayout());
    cameras.setLayout(cameraLayout=new CardLayout());
    
    add(buttons, "North");
    add(cameras, "Center");

    this.panelWidth    = panelWidth;
    this.panelHeight   = panelHeight;
    this.cameraManager = cameraManager;
    
    cameraManager.addCameraManagerListener(this);
    //add(cameraChoice);
    //setSize(new java.awt.Dimension(400, 400));
  }

  public void setUseURLs(boolean use){
    useURLs = use;
  }

  //called byte camera manager?
  public void newCamera(CameraManager cameraManager, Camera camera){
    noCameras++;
    //cameraChoice.addItem(camera.getName());
    Button button = new Button(camera.getName());
    buttons.add(button);
    button.addActionListener(this);
    
    CameraPanel cPanel = new CameraPanel(camera, panelWidth, panelHeight);
    cPanel.setUseURLs(useURLs);
    cameraPanels.put(camera.getName(), cPanel);
    cameras.add(camera.getName(), cPanel);

    cPanel.invalidate();
    
    Container parent           = getParent();

    while (parent.getParent() != null)
      parent = parent.getParent();

    System.out.println("PARENT "+parent);
    
    if (parent instanceof Window)
      ((Window)parent).pack();
    else
      parent.validate();

    
    for (Enumeration e=cPanelListeners.elements();e.hasMoreElements();)
      ((CameraPanelListener)e.nextElement()).created(cPanel);
  }

  public void addCameraPanelListener(CameraPanelListener cpl){
    cPanelListeners.addElement(cpl);
  }

  public void removeCameraPanelListener(CameraPanelListener cpl){
    cPanelListeners.removeElement(cpl);
  }
  
  public void actionPerformed(ActionEvent e){
    if (e.getSource() instanceof Button)
      cameraLayout.show(cameras, ((Button)e.getSource()).getLabel());
  }
    


  public Dimension getPreferredSize() {
    if(noCameras ==0)
      return new Dimension(panelWidth, panelHeight);
    else
      return super.getPreferredSize();
  }
  
//      Dimension s=super.getPreferredSize();
//      System.out.println("Cameramanagerpanel.GetPreferredSize() "+s);
//      return s; 
//    }

//    public Dimension getMinimumSize() {
//      Dimension s=super.getMinimumSize();
//      System.out.println("Cameramanagerpanel.GetMinimumSize() "+s);
//      return s;
//    }

//    public Dimension getMaximumSize(){
//      Dimension s=super.getMaximumSize();
//      System.out.println("Cameramanagerpanel.GetMaximumSize() "+s);
//      return s; 
//    }  
  
//    public void itemStateChanged(ItemEvent e) {
//      if(currentPanel != null)
//        currentPanel.setVisible(false);
    
//      currentPanel = (Panel)cameraPanels.get((String)cameraChoice.getSelectedItem());
//      currentPanel.setVisible(true);
//      getParent().validate();
//    }
  
  
}
