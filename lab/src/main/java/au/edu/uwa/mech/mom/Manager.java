
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Manager.java,v 1.6 1999/07/19 12:51:21 bdalton Exp $
// Time-stamp: "1999-07-19 20:12:31 barney"

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

import au.edu.uwa.mech.mom.server.ContextMaster;

//TO DO managers should be created by a manager factory. Peers should
//send a set of configuration variables that determine which manager
//get created - and with what info - a hashtable would do 
//repackage to server directory


public interface Manager
{
  public void authorise(ContextMaster cMaster, MOMPacket packet)  throws MOMPermissionException;

                                // if we have peers passing a manager
                                // as a create argument then it will
                                // need to load thee ContextMaster
                                // class - would prefer to avoid this
                                // make it an object?
  //public void attachTo(ContextMaster context);
  
}



