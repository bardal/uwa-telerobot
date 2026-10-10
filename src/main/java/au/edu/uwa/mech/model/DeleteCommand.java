
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: DeleteCommand.java,v 1.2 2000/01/27 11:50:44 bdalton Exp $
// Time-stamp: "2000-01-27 14:49:35 barney"

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

package au.edu.uwa.mech.model; 

public class DeleteCommand implements WorkspaceCommand
{
  DeleteCommand(String name){
    this.name=name;
  }
  
  String name;
  //dispatch command to camera
  public Object dDispatch(WorkspaceModel model){
    model.deleteObject(name);
    return null;
  }

  public String toString(){
    return new String("delete "+name);
  }
  static final long serialVersionUID = 7974440053429457185L;
}



