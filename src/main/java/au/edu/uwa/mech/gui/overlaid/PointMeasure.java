//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: PointMeasure.java,v 1.2 2000/01/26 13:35:53 bdalton Exp $ 
// Time-stamp: "2000-01-26 12:20:53 barney"

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


public class PointMeasure  implements Overlay3D, MouseListener
{
  boolean enabled=false;
  PointDetails pd=null;
  CrossHairs crosshair=new CrossHairs(5);
  Point3D triangulatedPoint;
  
  public void setEnabled(boolean enabled){
    if (this.enabled != enabled){
      this.enabled = enabled;
      pd=null;
      triangulatedPoint=null;
      tellObservers();
    } 
  }

  public void start(){
    clear();
    TheConsole.println("Click on the point to measure");
  }
  
  public void clear(){
    pd=null;
    triangulatedPoint=null;
    tellObservers();
  }
  //overlay yourself onto graphis object 'g'
  public void projectOnTo(CalibratedBackground cb, Graphics g)
  {
    if (!enabled)
      return;

    Camera camera=new Camera(cb.getCalibrationMatrix().getArray(), new Point(0,0), 1);
    if(triangulatedPoint != null){
      crosshair.point=camera.xyz2uv(triangulatedPoint);
      //System.out.println("Drawing crosshairs at "+crosshair.point);
      crosshair.overlay(g);
      return;
    }
    
    if (pd != null){
      //System.out.println(pd);

      if (cb==pd.source){
        //System.out.println("pd line is"+pd.line);
        Vector3D nearOrigin = pd.line.closestPointOnLineTo(new Point3D (0,0,0));
        crosshair.point=camera.xyz2uv(nearOrigin);
        //System.out.println("Drawing crosshairs at "+crosshair.point);
        crosshair.overlay(g);
      } else {
        Line uvLine = camera.projectLine(pd.line);
        drawEpipole(g,uvLine);
      }
    }
  }
  
    
  void drawEpipole(Graphics g, Line l){
    //find v for u=0
    Rectangle cr = g.getClipBounds();
    Vector2d centre = new Vector2d(cr.x+((float)cr.width/2), cr.y+((float)cr.height/2));
    Line2d randomLine = new Line2d(l.getStart(), l.getEnd());
    Point2d p = randomLine.closestPointOnLineTo(centre);

    //find the nearest point on the epiole ot the centre of the clip
    //rect, then draw out +- hyp to make sure we covered the rectangle
    //at the same time we avoid drawingot ridiculous numbers
    Line2d vaguelyRight = new Line2d(p, randomLine.direction.normalise());
    System.out.println("vaguelyRight "+vaguelyRight);
    double hyp = Math.sqrt(cr.width*cr.width + cr.height*cr.height);
    Point start = vaguelyRight.lambda(hyp).point();
    Point end = vaguelyRight.lambda(-hyp).point();
    
    System.out.println("Drawing epipole "+start+" "+end);
    //g.setLi
    g.setColor(Color.green);
    g.drawLine(start.x, start.y, end.x, end.y);
    
  }

  Vector pointListeners = new Vector();
  public void addPointListener(PointListener pl){
    pointListeners.addElement(pl);
  }

  public void removePointListener(PointListener pl){
    pointListeners.removeElement(pl);
  }
  
  public void mouseClicked(MouseEvent e){
    if(!enabled)
      return;

    if((e.getModifiers() & InputEvent.BUTTON1_MASK) ==0)
      return;
    
    if(triangulatedPoint != null){
      pd=null;
      triangulatedPoint=null;
    } 
    
    if (e.getSource() instanceof CalibratedBackground)
    {
      //System.out.println("PointMeasure got click from calibratedcanvas");
      CalibratedBackground cc = (CalibratedBackground)e.getSource();
      Camera camera=new Camera(cc.getCalibrationMatrix().getArray(), new Point(0,0), 1);
      if (pd==null){
        pd=new PointDetails(camera.uv2line(e.getPoint()), cc);
        TheConsole.println("Now click the same point in a second image");
      }
      
      else if  (pd.source==cc)
        pd=new PointDetails(camera.uv2line(e.getPoint()), cc);
      else{
        Line3D second = camera.uv2line(e.getPoint());
        triangulatedPoint = pd.line.midPoint(second);
        Point3D p=triangulatedPoint;
        TheConsole.println("Point location is ("+new Point3l(p.x, p.y,p.z)+")");
        for (Enumeration en=pointListeners.elements(); en.hasMoreElements();)
          ((PointListener)en.nextElement()).pointMeasured(this, triangulatedPoint);
        //pd=null;
      }

      tellObservers();
      e.consume();
    }
    else
      System.out.println("PointMeasure mouseCLicked no calibrated source "+e.getSource());
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


class PointDetails
{
  PointDetails(Line3D line, Object source){
    this.line=line;
    this.source=source;
    //this.point=point;
  }

  public String toString(){
    return "PointDetails "+line+" "+source;
  }
  

  //Matrix calibrationMatrix;
  Line3D line;
  //Point point;
  Object source;
//Matrix calibrationMatrix;
}
