
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ImageCanvas.java,v 1.7 2000/01/20 11:49:16 bdalton Exp $
// Time-stamp: "2000-01-20 18:57:29 barney"

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

import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.util.TheLog;

public class ImageCanvas
  extends au.edu.uwa.mech.gui.overlaid.CalibratedCanvas
  implements ImageListener
{
  boolean useURLs=false;
  
  ImageCanvas(int initialWidth, int initialHeight){
    super (initialWidth, initialHeight);
  }
  
  
  public void newImage(Object source, ImageData image)
  {
    //TheLog.println("ImageCanvas trying to load "+image);

    try{
      if (image.pixels != null)
        setImage(java.awt.Toolkit.getDefaultToolkit().createImage(image.pixels));                 
      else if (useURLs)
        setImage(java.awt.Toolkit.getDefaultToolkit().getImage(new java.net.URL(image.url)));
      else
        setImage(java.awt.Toolkit.getDefaultToolkit().getImage(image.filename));
      this.imageData = image;
      //TheLog.println("Setting Calibration matrix to ");
      //imageData.calibrationMatrix.print(10,3);
      setCalibrationMatrix(imageData.calibrationMatrix);
    } catch (java.net.MalformedURLException e){
      e.printStackTrace();
      TheConsole.println("Error loading image "+image.url);
    }
    
    
  }

  ImageData imageData;
  

}



