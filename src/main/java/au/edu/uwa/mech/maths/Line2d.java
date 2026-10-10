//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Line2d.java,v 1.1 2000/01/25 15:42:40 bdalton Exp $ 
// Time-stamp: "2000-01-25 22:57:49 barney"

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

package au.edu.uwa.mech.maths;

import java.awt.Point;


public class Line2d
{
  public Vector2d point;
  public Vector2d direction;

  public Line2d (Point a, Point b)
  {
    point = new Vector2d(a);
    direction = new Vector2d(b.x-a.x, b.y-a.y);
  }

  public Line2d(Point2d point, Point2d direction){
    this.point = new Vector2d(point);
    this.direction = new Vector2d(direction);
  }
  
  
  public Vector2d lambda(double lambda){
    return point.add(direction.lambda(lambda));
  }
  

  public Vector2d closestPointOnLineTo(Vector2d x1)
  {
    double t = direction.dot(x1.sub(this.point))/direction.dot(direction);
    return this.point.add(direction.lambda(t));
  }

  public String toString(){
    return point+" s*"+direction;
  }
  
  
}

