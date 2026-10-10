
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SettingsCommand.java,v 1.6 1999/11/05 14:16:13 bdalton Exp $
// Time-stamp: "1999-11-05 21:11:08 barney"

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

public class SettingsCommand implements CameraCommand
{
  public SettingsCommand(){
    type=GET;
  }
  
  
  public SettingsCommand(CameraSettings cs, int type)
  {
    this.type=type;
    this.cameraSettings=cs;
  }

  public Object dDispatch(Camera camera){
    if(type==SET)
      camera.setSettings(cameraSettings);

    return camera.getSettings();
    
  }
  
  public String toString(){
    return "settings="+cameraSettings+" type="+type;
  }
  
  
  CameraSettings cameraSettings;
  int type;

  static final int SET=1;
  static final int GET=2;

  static final long serialVersionUID = 3146033638391190963L;
}



