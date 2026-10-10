
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Hook.java,v 1.1 1999/06/30 11:21:08 bdalton Exp $
// Time-stamp: "1999-06-30 11:46:18 barney"

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


// generic interface for performing a function on an object - used in
// the packet contructor as a way of influencing the create process
// without having to subclass a new type 

package au.edu.uwa.mech.util;

public interface Hook
{
  public Object doYourStuffOn(Object arg);
}



