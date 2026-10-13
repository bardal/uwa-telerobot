//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TextAreas.java,v 1.2 2000/01/23 13:02:31 bdalton Exp $ 
// Time-stamp: "2000-01-23 16:37:46 barney"

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

import java.awt.*;

class TextAreas extends Panel
{
  
  TextArea one = new TextArea(40,40);
  TextArea two = new TextArea();
  TextArea three = new TextArea();
  
  TextAreas(){
    one.setRows(10);
    setLayout(new BorderLayout());
    add(one,"North");
    add(two,"Center");
    add(three,"Center");
   
  }

  void printSizes()
  { 
    System.out.println("onepre "+one.getPreferredSize());
    System.out.println("onemin "+one.getMinimumSize());
    System.out.println("onemax "+one.getMaximumSize());
    System.out.println("twopre "+two.getPreferredSize());
    System.out.println("thrpre "+three.getPreferredSize());
  }
  

  void changeSizes(){
    two.setRows(7);
    two.setColumns(5);
    one.setRows(20);
    three.setRows(1);
  }
  
  
  public static void main (String[] args)
  {
    Frame frame = new Frame("TA test");
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {System.exit(0);}
      });

    TextAreas tas = new TextAreas();
    frame.add(tas);
    frame.pack();
    frame.show();

    tas.printSizes();
    System.out.println();
    tas.changeSizes();
    tas.printSizes();


    for (int i=0;i<500;i++){
      tas.one.append(Integer.toString(i));
      tas.one.append("\n");
    }
    
    
  } // end of main ()
  
  
}
