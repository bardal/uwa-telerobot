//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Downstream.java,v 1.2 2000/01/23 13:02:31 bdalton Exp $ 
// Time-stamp: "2000-01-23 13:04:07 barney"

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



package au.edu.uwa.mech.mom;

//if transceivers are managed then they should throw exceptions via
//this emanager?

public interface Downstream
{
  public void putqDownstream(Object source, Object arg) throws MOMIOException;

  public void addDownstream(Downstream downstream);
  public void removeDownstream(Downstream downstream);

  public void setName(String name);
  
  public void close();
}
