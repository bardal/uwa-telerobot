//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MultiConsole.java,v 1.1 2000/01/18 17:48:03 bdalton Exp $ 
// Time-stamp: "2000-01-18 23:47:34 barney"

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

//hack to get round some awt problems

package au.edu.uwa.mech.gui;

import java.util.*;

public class MultiConsole implements Console
{

  Vector consoles = new Vector();
  public void addConsole(Console c){
    consoles.addElement(c);
  }

  public void removeConsole(Console c){
    consoles.removeElement(c);
  }

  
  public void println(String text)
  {
    for (Enumeration e=consoles.elements();e.hasMoreElements();)
      ((Console)e.nextElement()).println(text);
  }

}
