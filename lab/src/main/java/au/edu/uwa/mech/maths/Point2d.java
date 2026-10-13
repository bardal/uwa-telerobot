//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Point2d.java,v 1.1 2000/01/22 09:30:58 bdalton Exp $ 
// Time-stamp: "2000-01-22 16:42:51 barney"

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


import java.text.DecimalFormat;
import java.awt.Point;


public class Point2d 
{
  
  
  public Point2d(){}

  public Point2d(double x, double y){
    this.x=x; this.y=y;
  }

  public Point2d(Point2d p){
    x=p.x;
    y=p.y;
  }

  public Point2d(Point p){
    this(p.x, p.y);
  }
  

  public Point2d sub(Point2d p){
    return new Point2d(x-p.x,y-p.y);
  }
  

  public Point point(){
    return new Point((int)Math.round(x), (int)Math.round(y));
  }
  
  
  public String toString()
  {return new String(df.format(x)+", "+df.format(y));}
  
  static DecimalFormat df = new DecimalFormat();
  
  static {
    df.setMaximumFractionDigits(2);
    df.setGroupingUsed(false);
  }

  public double x,y;
  
  
  public static void main (String[] args)
  {
    Point2d p=new Point2d(1.3324324523452345,4.2341234234);
    System.out.println(p);
    
  } // end of main ()
  
  
}
