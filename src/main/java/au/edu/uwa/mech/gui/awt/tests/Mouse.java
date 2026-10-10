//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Mouse.java,v 1.2 2000/01/20 11:49:16 bdalton Exp $ 
// Time-stamp: "2000-01-20 18:15:35 barney"

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
import java.awt.event.*;

class Mouse extends Panel
{
  protected void processMouseEvent(MouseEvent e){
    System.out.println("process mouse event "+e);
    super.processMouseEvent(e);
  }
  
   
  Mouse(){
    add(new Button("hello"));
    add(new Label("sadfasdfasdf asdfasdfa"));
    addMouseListener(new TempMouseListener());
  }

  class TempMouseListener implements MouseListener
  {
    public void mouseClicked(MouseEvent e){
    }
    public void mousePressed(MouseEvent e){
      System.out.println(e);
      if(e.isPopupTrigger())
        System.out.println("is popup");
      else
        System.out.println("isn't popup");
    }
    public void mouseReleased(MouseEvent e){
    }
    public void mouseEntered(MouseEvent e){
    }
    public void mouseExited(MouseEvent e){
    }
  }
  
  
  
  public static void main (String[] args)
  {
    Frame frame = new Frame("TA test");
    frame.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {System.exit(0);}
      });

    Mouse tas = new Mouse();
    frame.add(tas);
    frame.pack();
    frame.show();
    
  } // end of main ()
  
  
}
