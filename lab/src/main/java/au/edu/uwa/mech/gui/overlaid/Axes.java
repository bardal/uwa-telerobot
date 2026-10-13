//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Axes.java,v 1.5 2000/01/27 11:50:44 bdalton Exp $ 
// Time-stamp: "2000-01-27 14:44:23 barney"

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
import au.edu.uwa.mech.gui.overlaid.OverlaidObject;
import au.edu.uwa.mech.maths.*;
import au.edu.uwa.mech.util.*;
import au.edu.uwa.mech.maths.geom.*;



public class Axes implements Overlay3D
{

  int inset = 25;
  int length =25;
  int arrowlength=7;
  Color color = Color.black;
  Point3D axes[] = {new Point3D(500,0,0), new Point3D(0,500,0), new Point3D(0,0,500)};
  String labels[] = {"x", "y", "z"};
  Point3D origin = new Point3D(0,0,0);  
  boolean visible=true;
  ContainedObservable observable = new ContainedObservable();

  public void projectOnTo(CalibratedBackground cb,java.awt.Graphics g){
    
    if(visible){
      g.setColor(color);
      Camera c=new Camera(cb.getCalibrationMatrix().getArray(), new Point(0,0), 1);
      Rectangle r=g.getClipBounds();
      Point startPoint = c.xyz2uv(origin);
      if (!r.contains(startPoint)){
        r.setBounds(r.x+inset, r.y+inset, r.width-(2*inset),r.height-(2*inset));
        startPoint = findBestCorner(r,startPoint);
      }

      for (int i=0;i<axes.length;i++){
        Vector2d directionVec = calc2dVec(c, origin, axes[i]);

        Point endpoint = calcPoint(startPoint, directionVec, length);
        g.drawLine(startPoint.x, startPoint.y, endpoint.x, endpoint.y);
        
        drawArrow(g, endpoint, directionVec, arrowlength);
        
        Point textpoint = calcPoint(startPoint, directionVec, length+(int)(arrowlength*1.5));
        g.drawString(labels[i],
                     textpoint.x-(g.getFontMetrics().stringWidth(labels[i]))/2,
                     textpoint.y);
      }
    }
  }

  protected Point calcPoint(Point start, Vector2d direction, int length)
  {
    Point translateBy = direction.normalise().lambda(length).point();
    return new Point(start.x+translateBy.x, start.y+translateBy.y);
  }
  
  
  protected Vector2d calc2dVec(Camera c, Point3D start, Point3D end)
  {
    Point2d uv1 = new Point2d(c.xyz2uv(start));
    Point2d uv2 = new Point2d(c.xyz2uv(end));
    return new Vector2d(uv2.sub(uv1));
    
  }
  
  protected void drawArrow(Graphics g, Point base, Vector2d direction, int arrowlength)
  {
    int xps[]=new int[3];
    int yps[]=new int[3];
    int hLength = arrowlength/2;
    Vector2d unitVec = direction.normalise();
    Vector2d endPoint = unitVec.lambda(arrowlength);
    Vector2d leftPoint = unitVec.rot90().lambda(hLength);
    Vector2d rightPoint = unitVec.rotMinus90().lambda(hLength);
    int u=base.x;
    int v=base.y;
      
    xps[0] = (int)Math.round(u+endPoint.x);
    yps[0] =  (int)Math.round(v+endPoint.y);
    xps[1] =  (int)Math.round(u+leftPoint.x);
    yps[1] =  (int)Math.round(v+leftPoint.y);
    xps[2] =  (int)Math.round(u+rightPoint.x);
    yps[2] =  (int)Math.round(v+rightPoint.y);

    g.fillPolygon(xps, yps, 3);
  }

  double distance(Point a, Point b){
    return Math.sqrt(((a.x-b.x)*(a.x-b.x))+((a.y-b.y)*(a.y-b.y)));
  }
  
  
  Point findBestCorner(Rectangle r,Point originUV)
  {
    Point closest=r.getLocation();
    double minDistance = Double.MAX_VALUE;
    
    Point corners[] = new Point []{
      new Point(r.x,r.y),
      new Point(r.x+r.width, r.y),
      new Point(r.x+r.width, r.y+r.height),
      new Point(r.x,  r.y+r.height)
    };

    for (int i=0;i<4;i++){
      if (distance(originUV, corners[i]) < minDistance){
        closest = corners[i];
        minDistance = distance(originUV, corners[i]);
      }
    }
    
    return closest;
  }
  

  public void setVisible(boolean vis)
  {
    if (vis != visible){
      visible=vis;
      tellObservers();
    }
  }  

  public Observable observable() {
    if(observable ==null)
      observable = new ContainedObservable();
    return observable;
  }

  public void tellObservers()
  {
    if (observable == null)
      return;
    observable.setChanged();
    observable.notifyObservers(this);
  }
}
