//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ImageApplet.java,v 1.1 1999/08/30 21:22:50 bdalton Exp $ 
// Time-stamp: "1999-08-30 22:53:46 student"

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




package au.edu.uwa.mech.devices.camera;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import au.edu.uwa.mech.gui.awt.ImageCanvas;


import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.gui.LoginPanel;
import au.edu.uwa.mech.devices.camera.CameraPanel;
import au.edu.uwa.mech.devices.camera.Camera;

public class ImageApplet extends java.applet.Applet 
{

  protected CameraManager cameraManager;
  protected CameraManagerPanel cPanel;
  protected LoginPanel   lPanel;
  protected SessionRoot  root;
  
  public void destroy() {
  }

  public String getAppletInfo() {
    return "ImageApplet Copyright Barney Dalton August 1999";
  }

  public void init () {
    setBackground(new Color(255,230,204));
     
    setLayout(new BorderLayout());

    try{root = new SessionRoot(getParameter("MOMURL"));}
    catch(MOMException e){e.printStackTrace();}

    lPanel = new LoginPanel();
    lPanel.setLoginHandler(root);

    cameraManager = new CameraManagerClient(root);
    cPanel =  new CameraManagerPanel(cameraManager, 400, 300);
    cPanel.useURLs = true;
    
    add(lPanel, "North");
    add(cPanel, "Center");
  }  

  public void start() {
    //    imagearea.showImage("images/test2.jpg");
    showStatus("Loading image (wait even longer!!)...");
  }

  public void stop() {
  }


  
}
