
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TakePhotoCommand.java,v 1.4 1999/09/09 21:20:18 bdalton Exp $
// Time-stamp: "1999-09-09 22:36:05 student"

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

public class TakePhotoCommand implements CameraCommand
{
  //dispatch command to camera
  public Object dDispatch(Camera camera) throws CameraException
  {
    System.out.println("Taking photo using TakePhotoCommand");
    return camera.takePhoto();
  }
  
  static final long serialVersionUID = 9184082640539547459L;

}



