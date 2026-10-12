
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: CameraPanel.java,v 1.5 2000/01/19 11:43:20 bdalton Exp $
// Time-stamp: "2000-01-19 14:36:40 barney"

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

public class CameraPanel extends java.awt.Panel
{
  protected ImageCanvas imageCanvas;
  protected CameraControlsPanel controls;
  protected Camera camera;

  
  public CameraPanel(int width, int height){
    setupUI(width, height);
  }
  
  
  public CameraPanel(Camera camera, int width, int height)
  {
    setupUI(width, height);
    setCamera(camera);
  }

//    public Dimension getPreferredSize() {
//      Dimension s=super.getPreferredSize();
//      System.out.println("CameraPanel.GetPreferredSize() "+s);
//      return s; 
//    }

//    public Dimension getMinimumSize() {
//      Dimension s=super.getMinimumSize();
//      System.out.println("CameraPanel.GetMinimumSize() "+s);
//      return s;
//    }

//    public Dimension getMaximumSize(){
//      Dimension s=super.getMaximumSize();
//      System.out.println("CameraPanel.GetMaximumSize() "+s);
//      return s; 
//    }  
  

  protected void setupUI(int width, int height){
    
    imageCanvas = new ImageCanvas(width, height);
    
    controls = new CameraControlsPanel();

    setLayout(new BorderLayout());
    // the canvas fills the panel and scales the image to fit
    add(imageCanvas, "Center");
    add(controls, "South");
  }
  
  
  public void setUseURLs(boolean use){
    imageCanvas.useURLs = use;
  }

  public ImageCanvas getImageCanvas(){
    return imageCanvas;
  }
  
  public void setCamera(Camera camera)
  {
    this.camera = camera;
    controls.setCamera(camera);
    camera.addImageListener(imageCanvas);
  }
  
  
}



