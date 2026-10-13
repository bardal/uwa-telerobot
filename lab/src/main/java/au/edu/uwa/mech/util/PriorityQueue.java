
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: PriorityQueue.java,v 1.1 1999/06/08 11:52:04 bdalton Exp $
// Time-stamp: "1999-06-08 17:32:33 barney"

// {{{ GNU General Public License
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as
// published by the Free Software Foundation; either version 2 of the
// License, or (at your option) any later version. This program is
// distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
// License for more details.  You should have received a copy of the
// GNU General Public License along with this program; if not, write
// to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
// Boston, MA 02111-1307, USA.
// }}}

package au.edu.uwa.mech.util;

public class PriorityQueue
{
  public void put(Object obj)
  {
    queue.addElement(obj);
  }

  public Object pop()
  {
    Object popped=queue.firstElement();
    queue.removeElementAt(0);
    return popped;
  }
  

  
  java.util.Vector queue;
  

}



