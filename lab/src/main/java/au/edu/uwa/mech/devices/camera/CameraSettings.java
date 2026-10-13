
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: CameraSettings.java,v 1.5 1999/09/09 21:20:18 bdalton Exp $
// Time-stamp: "1999-09-09 22:28:01 student"

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

import  au.edu.uwa.mech.maths.Point3d;



//public interface CameraSettings extends java.io.Serializable, Displayable
//{
//    public int getWidth();
//    public void setWidth(int  v);
//    public int getHeight();
//    public void setHeight(int  v);
//    public int getQuality();
//    public void setQuality(int  v);
//    public float getZoom();
//    public void setZoom(float  v);
//    int getMaxWidth();
//    int getMaxHeight();
  
//}


class CameraSettings implements java.io.Serializable//implements CameraSettings
  {
    CameraSettings(int maxWidth, int maxHeight){
      this.maxWidth=maxWidth;
      this.maxHeight=maxHeight;
    }
    
    private int width;
  
    /**
     * Get the value of width.
     * @return Value of width.
     */
    public int getWidth() {return width;}
  
    /**
     * Set the value of width.
     * @param v  Value to assign to width.
     */
    public int setWidth(int  v) {
      if(v <= maxWidth)
        this.width = v;
      else
        this.width = maxWidth;
      return this.width;
    }
  
  
    private int height;
  
    /**
     * Get the value of height.
     * @return Value of height.
     */
    public int getHeight() {return height;}
  
    /**
     * Set the value of height.
     * @param v  Value to assign to height.
     */
    public int setHeight(int  v) {
      if(v <= maxHeight)
        this.height = v;
      else
        this.height = maxHeight;
      return this.height;
    }

  
    private int quality;
  
    /**
     * Get the value of quality.
     * @return Value of quality.
     */
    public int getQuality() {return quality;}
  
    /**
     * Set the value of quality.
     * @param v  Value to assign to quality.
     */
    public void setQuality(int  v) {this.quality = v;}
  
    private float zoom;
  
    /**
     * Get the value of zoom.
     * @return Value of zoom.
     */
    public float getZoom() {return zoom;}
  
    /**
     * Set the value of zoom.
     * @param v  Value to assign to zoom.
     */
    public void setZoom(float  v) {this.zoom = v;}

    
    boolean fixed=false;
    
    /**
       * Get the value of fixed.
       * @return Value of fixed.
       */
    public boolean getFixed() {return fixed;}
    
    /**
       * Set the value of fixed.
       * @param v  Value to assign to fixed.
       */
    public void setFixed(boolean  v) {this.fixed = v;}
    
    
    int getMaxWidth(){return maxWidth;}
    int getMaxHeight(){return maxHeight;}

    public String toString(){
      return "h="+height+"("+maxHeight+") w="+width+"("+maxWidth+") q="+quality+" z="+zoom;
    }

    
    private int maxWidth, maxHeight;
    static final long serialVersionUID = 4109756857463403463L;
  }


