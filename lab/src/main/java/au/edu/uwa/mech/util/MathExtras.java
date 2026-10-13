//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MathExtras.java,v 1.2 2000/01/20 11:49:18 bdalton Exp $ 
// Time-stamp: "2000-01-20 16:11:22 barney"

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

package au.edu.uwa.mech.util;

public class MathExtras 
{
  public static int limit(int a, int min, int max){
    a= Math.max(a,min);
    a=Math.min(a,max);
    return a;
  }

  public static float limit(float a, float min, float max){
    a= Math.max(a,min);
    a=Math.min(a,max);
    return a;
  }
  
  public static void main (String[] args)
  {
    System.out.println(limit(-1,0,100));
    System.out.println(limit(0,0,100));
    System.out.println(limit(50,0,100));
    System.out.println(limit(100,0,100));
    System.out.println(limit(101,0,100));
  } // end of main ()
  
}
