
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Camera.java,v 1.10 2000/02/02 17:30:47 bdalton Exp $
// Time-stamp: "2000-02-02 16:35:29 barney"

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

package au.edu.uwa.mech.devices.camera; //inserted by perl script 'checkPackages'

import  au.edu.uwa.mech.maths.Point3d;
import  java.awt.Point;

public interface Camera
{
  //need  a takePhoto()
  ImageData takePhoto()                throws CameraException;
  ImageData takePhoto(String filename) throws CameraException;

  ImageData lastPhoto();
  
  public CameraSettings getSettings();
  public void setSettings(CameraSettings settings);

  
  public void setCentre(Point3d point);
  
  public void setOn(boolean on);
  public boolean getOn();
  
  //last image???
  
  // width and height of final images
 //   public void setWidth(int width);
//    public int getWidth();
  
//    public void setHeight(int height);
//    public int       getHeight();
  
  // int getMaxWidth();
  //int getMaxHeight();
  
  // zoom 0->1
  // zoom 0 as far out as we can get
  // zoom 1 as far in as we can get
//    void setZoom(float zoom);
//    public float     getZoom();
  
  // set centre of image about a real world point - calibrated cameras
  // only 
  //void setCentre(Point3d point);
  // set centre of image to a point on image plane - any camera
  //void SetCentre(Point point);
  // set center of image to centre of camera - any camera
  //void setCentred();

  String getName();

  public void           addCameraListener(CameraListener l);
  public void           removeCameraListener(CameraListener l);

  public void           addImageListener(ImageListener l);
  public void           removeImageListener(ImageListener l);
  
}



