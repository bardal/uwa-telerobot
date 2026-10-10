//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TapeMeasure.java,v 1.5 2000/01/26 13:35:53 bdalton Exp $ 
// Time-stamp: "2000-01-26 12:21:36 barney"

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
import java.awt.event.*;
import java.util.*;


import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.gui.overlaid.Line;
import au.edu.uwa.mech.gui.overlaid.OverlaidObject;

import au.edu.uwa.mech.maths.*;
import au.edu.uwa.mech.util.*;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.maths.geom.*;


public class TapeMeasure  implements Overlay3D, PointListener,
  MouseListener, Observer
{
  boolean enabled=false;
  CrossHairs crosshair1=new CrossHairs(5);
  CrossHairs crosshair2=new CrossHairs(5);
  Point3D point1, point2;
  PointMeasure pm=new PointMeasure();
  Line3D measuredLine;
  
  public TapeMeasure(){
    pm.observable().addObserver(this);
    pm.addPointListener(this);
  }
  
  public void setEnabled(boolean enabled){
    if (this.enabled != enabled){
      this.enabled = enabled;
      point1=null;
      point2=null;
      measuredLine=null;
      pm.setEnabled(enabled);
    } 
  }

  public void start(){
    clear();
    TheConsole.println("Click on the start of the line");
  }
  
  
  public void clear(){
    point1=null;
    point2=null;
    measuredLine=null;
    pm.clear();
    pm.setEnabled(true);
    tellObservers();
  }

  public void pointMeasured(Object source, Point3D point){
    if(point1 == null){
      point1 = point;
      pm.clear();
      TheConsole.println("Now click on the end of the line");
    }
    else if(point2 == null){
      point2 = point;
      measuredLine=new Line3D(point1, point2);
      TheConsole.println("Measured line length "+Math.round(measuredLine.length())+"mm");
      pm.clear();
      pm.setEnabled(false);
    }
  }
  
  
  //overlay yourself onto graphis object 'g'
  public void projectOnTo(CalibratedBackground cb, Graphics g)
  {
    if (!enabled)
      return;

    pm.projectOnTo(cb,g);
    
    Camera camera=new Camera(cb.getCalibrationMatrix().getArray(), new Point(0,0), 1);
    if(point1 != null){
      crosshair1.point=camera.xyz2uv(point1);
      crosshair1.overlay(g);
    }
    
    if((point1 != null) && (point2 != null)){
      crosshair2.point=camera.xyz2uv(point2);
      crosshair2.overlay(g);
      g.drawLine(crosshair1.point.x, crosshair1.point.y, crosshair2.point.x, crosshair2.point.y);
      return;
    }
  }
  
  
  public void mouseClicked(MouseEvent e){
    pm.mouseClicked(e);
  }

  public void update(Observable source, Object arg){
    tellObservers();
  }
  
  public void tellObservers()
  {
    observable.setChanged();
    observable.notifyObservers(this);
  }
    
  public void mouseDragged(MouseEvent e) {;}
  public void mouseMoved (MouseEvent e ) {;}

  public void mouseExited(MouseEvent e) {;}
  public void mousePressed(MouseEvent e) {;}
  public void mouseReleased(MouseEvent e) {;}
  public void mouseEntered(MouseEvent e){;}
  ContainedObservable observable = new ContainedObservable();
  public Observable observable() {return observable;}
  
}

