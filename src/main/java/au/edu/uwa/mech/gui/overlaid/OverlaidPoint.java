
// Copyright (C) 1999, 2000 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Wed Sep 08 23:19:14 1999
// Version       : $Id: OverlaidPoint.java,v 1.2 2000/01/27 11:50:44 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

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

package au.edu.uwa.mech.gui.overlaid;

import java.awt.*;
import java.awt.Point;
import java.awt.event.*;
import java.util.*;

import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.gui.overlaid.Line;
import au.edu.uwa.mech.util.*;
import au.edu.uwa.mech.maths.*;
import au.edu.uwa.mech.util.*;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.gui.awt.PointExtras;
import au.edu.uwa.mech.maths.geom.*;

public class OverlaidPoint implements InteractiveOverlay3D 
{
  Point3d xyzLocation;
  int size=5;
  transient boolean selected=false;
  
  
  public OverlaidPoint(double x, double y, double z){
    xyzLocation = new Point3d(x,y,z);
  }

  public Point3d getPosition(){
    return xyzLocation;
  }
  
  public void setPosition(Point3d position){
    xyzLocation = new Point3d(position);
    tellObservers();
  }
  

  
  //overlay yourself onto graphis object 'g'
  public void projectOnTo(CalibratedBackground cb, java.awt.Graphics g)
  {
    Camera camera=new Camera(cb.getCalibrationMatrix().getArray(), new Point(0,0), 1);
    Point uvLocation=camera.xyz2uv(xyzLocation);

    Color c=g.getColor();
    if(selected)
      g.setColor(Color.red);
    else
      g.setColor(Color.black);
    
    g.drawLine(uvLocation.x-size, uvLocation.y, uvLocation.x+size, uvLocation.y);
    g.drawLine(uvLocation.x, uvLocation.y-size, uvLocation.x, uvLocation.y+size);
    if (name != null)
      g.drawString(name,  uvLocation.x+size, uvLocation.y);
    g.setColor(c);
  }

  //should be in a base class
  public void mouseClicked(MouseEvent e){
    if (e.getSource() instanceof CalibratedBackground)
    {
      //System.out.println("PointMeasure got click from calibratedcanvas");
      CalibratedBackground cc = (CalibratedBackground)e.getSource();
      Camera camera=new Camera(cc.getCalibrationMatrix().getArray(), new Point(0,0), 1);
      Point uvLocation=camera.xyz2uv(xyzLocation);
      if (PointExtras.distance(uvLocation, e.getPoint()) < size){
        e.consume();
        setSelected(!selected);
      }
    }
    
  }
  

  public boolean getSelected(){
    return selected;
  }

  public void setSelected(boolean selected){
    if(this.selected != selected){
      this.selected = selected;
      tellObservers();
    }
    if(selected)
      TheConsole.println(this);
  }

  //put this stuff in  a base class pleeeease
  public void mouseDragged(MouseEvent e) {;}
  public void mouseMoved (MouseEvent e ) {;}

  public void mouseExited(MouseEvent e) {;}
  public void mousePressed(MouseEvent e) {;}
  public void mouseReleased(MouseEvent e) {;}
  public void mouseEntered(MouseEvent e){;}
  
  protected transient ContainedObservable _observable;
  public Observable observable() {
    if(_observable == null)
      _observable = new ContainedObservable();
    return _observable;
  }
  
  public void tellObservers()
  {
    if (_observable == null)
      return;
    _observable.setChanged();
    _observable.notifyObservers(this);
  }

  static final long serialVersionUID = -3785919503742752993L;
  
  String name;
  
  /**
     * Get the value of name.
     * @return Value of name.
     */
  public String getName() {return name;}
  
  /**
     * Set the value of name.
     * @param v  Value to assign to name.
     */
  public void setName(String  v) {this.name = v;}
  
  
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    if(name !=null)
      sb.append(name+":");
    if(xyzLocation != null)
      sb.append("("+new Point3l(xyzLocation.x,xyzLocation.y,xyzLocation.z)+")");
    return sb.toString();
  }
  
  
}



