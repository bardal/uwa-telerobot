//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Shuffle.java,v 1.1 1999/12/11 11:11:23 bdalton Exp $ 
// Time-stamp: "1999-12-11 14:32:53 barney"

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

import java.util.*;

//for JDK 1.2 use the Collections class

public class Shuffle 
{
  public static final Vector shuffle(Vector v)
  {
    Vector indices = new Vector();
    for (int i=0;i<v.size();i++)
      indices.addElement(new Integer(i));

    Random random = new Random();
    
    Vector shuffled = new Vector();
    
    for (int i=0;i<v.size();i++){
      int j = (random.nextInt() & Integer.MAX_VALUE) % indices.size();
      int index = ((Integer)indices.elementAt(j)).intValue();
      indices.removeElementAt(j);
      
      shuffled.addElement(v.elementAt(index));

    }
    return shuffled;
  }

  public static void main (String[] args)
  {
    Vector test = new Vector();
    for (int i=0;i<20;i++)
      test.addElement(new Integer(i));

    System.out.println(test);
    System.out.println(shuffle(test));
    
  } // end of main ()
  
}
