//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: BackgroundCanvas.java,v 1.1 1999/09/08 22:33:43 bdalton Exp $ 
// Time-stamp: "1999-09-08 12:11:29 student"

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

import java.awt.Graphics;
import java.util.Vector;
import java.util.Enumeration;

//import java.awt.image.ImageObserver;

import au.edu.uwa.mech.gui.awt.ImageCanvas;

public class BackgroundCanvas extends ImageCanvas implements Background {

  Vector overlays = new Vector();
  
  public BackgroundCanvas(int w, int h){
    super(w, h);
  }
  
  public void doPaint (Graphics g) {
    //System.out.println("Background canvas doPaint called");
    super.doPaint(g);
    for (Enumeration e=overlays.elements();e.hasMoreElements();)
      ((Overlay)e.nextElement()).overlay(g);
  }

  public void addOverlay(Overlay o){
    overlays.addElement(o);
  }

  public void removeOverlay(Overlay o){
    overlays.removeElement(o);
  }
  
}

