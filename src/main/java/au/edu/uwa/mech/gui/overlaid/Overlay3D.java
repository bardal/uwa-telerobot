
// Copyright (C) 1999, 2000 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Wed Sep 08 17:08:15 1999
// Version       : $Id: Overlay3D.java,v 1.4 2000/01/27 11:50:44 bdalton Exp $ 
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

import au.edu.uwa.mech.util.ObservableI;

public interface Overlay3D extends  ObservableI, java.io.Serializable
{  
  //overlay yourself onto graphics object 'g'
  public void projectOnTo(CalibratedBackground cb, java.awt.Graphics g);
  static final long serialVersionUID = -1228910528950649962L;
}







