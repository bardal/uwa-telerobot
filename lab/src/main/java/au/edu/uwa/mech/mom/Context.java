
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Context.java,v 1.14 1999/08/27 22:30:13 bdalton Exp $
// Time-stamp: "1999-08-27 11:22:37 student"

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



package au.edu.uwa.mech.mom; //inserted by perl script 'checkPackages'


public interface Context
{
  //public String getName(); 
  //public void   setName(String  v); 
  public ContextURL getContextURL();
  public void setContextURL(ContextURL curl);
  //public void setParent(Context parent);
  //public void setConnection(MOMConnection connection);
  public void setRoot(SessionRoot root);
  
  
  //create
  public void join() throws MOMIOException, MOMPermissionException, MOMNotFoundException;
  public void leave()throws MOMIOException, MOMPermissionException, MOMNotFoundException;
 
  //this should be part of a seperate interface
  //public void receive(Event e, Object ea);
  
  //public void setConnection(MOMConnection connection);
  
  static final String TYPE_NAME="Context";
}



