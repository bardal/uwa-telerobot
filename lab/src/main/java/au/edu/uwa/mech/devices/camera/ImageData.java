
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ImageData.java,v 1.7 2000/01/18 17:48:03 bdalton Exp $
// Time-stamp: "2000-01-18 15:32:40 barney"

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

import Jama.Matrix;


public class ImageData implements java.io.Serializable
{
  //filename, size, camera matrix
  public String filename;
  public String url;
  public int width, height;
  public Matrix calibrationMatrix=new Matrix(3,4);
  public byte[] pixels;
  
  public String toString(){
     return new String(filename+" "+url+" "+width+" "+height+" "+ calibrationMatrix);
  }
  static final long serialVersionUID = -3552035966998545461L;
}



