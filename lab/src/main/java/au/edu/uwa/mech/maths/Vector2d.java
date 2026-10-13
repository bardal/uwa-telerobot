//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Vector2d.java,v 1.2 2000/01/25 15:42:40 bdalton Exp $ 
// Time-stamp: "2000-01-25 22:37:40 barney"

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

public class Vector2d extends Point2d
{
  public Vector2d(java.awt.Point p){
    super(p);
  }
  

  public Vector2d(Point2d p){
    super(p);
  }

  public Vector2d(double x, double y){
    super(x,y);
  }

  public double dot(Vector2d v2){
    return (x*v2.x + y*v2.y);
  }

  public Vector2d add(Vector2d v2){
    return new Vector2d(x+v2.x, y+v2.y);
  }
  
  public Vector2d sub(Vector2d v2){
    return new Vector2d(x-v2.x, y-v2.y);
  }
  
    
  
  public Vector2d lambda(double lambda){
    return new Vector2d(x*lambda, y*lambda);
  }
  
  public Vector2d normalise(){
    double size = Math.sqrt(x*x + y*y);
    return new Vector2d(x/size, y/size);
  }

  //anticlockwise
  public Vector2d rot90(){
    return new Vector2d(-y,x);
  }

  //clockwise
  public Vector2d rotMinus90(){
    return new Vector2d(y,-x);
  }
  
}
