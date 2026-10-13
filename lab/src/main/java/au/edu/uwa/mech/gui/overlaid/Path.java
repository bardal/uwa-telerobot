
// Copyright (C) 1999, 2000 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Wed Sep 08 23:19:14 1999
// Version       : $Id: Path.java,v 1.5 2000/01/25 15:42:40 bdalton Exp $ 
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

import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.gui.overlaid.Line;
import au.edu.uwa.mech.maths.Point3d;
import java.awt.Point;
import java.awt.Color;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Observable;

import au.edu.uwa.mech.util.*;

public class Path implements Overlay3D
{
  //overlay yourself onto graphis object 'g'
  public void projectOnTo(CalibratedBackground cb, java.awt.Graphics g)
  {
    Camera camera=new Camera(cb.getCalibrationMatrix().getArray(), new Point(0,0), 1);
    
    if (nodes.size()<2)
      return;

    Point3d lastNode=(Point3d)nodes.elementAt(0);
    for (Enumeration e=nodes.elements();e.hasMoreElements();)
    {
      Point3d node = (Point3d)e.nextElement();
      (new Line(camera.xyz2uv(lastNode), camera.xyz2uv(node))).draw(g, Color.blue);
      lastNode = node;
    }
  }

  public void addPoint(Point3d point){
    nodes.addElement(new Point3d(point));
    tellObservers();
    //TheLog.println(nodes.toString());
  }
  
  public void clear(){
    Object last=nodes.lastElement();
    nodes.removeAllElements();
    nodes.addElement(last);
    tellObservers();
  }

  protected ContainedObservable _observable= new ContainedObservable();
  public Observable observable() {
    return _observable;
  }
  
  public void tellObservers()
  {
    _observable.setChanged();
    _observable.notifyObservers(this);
  }
  
  Vector nodes=new Vector();

}



