
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMListenerFactory.java,v 1.2 1999/06/29 12:55:00 bdalton Exp $
// Time-stamp: "1999-06-29 20:30:24 barney"

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


package au.edu.uwa.mech.mom.server; //inserted by perl script 'checkPackages'

import au.edu.uwa.mech.mom.MOMListener;
//import au.edu.uwa.mech.mom.rmi.impl.MOMListenerRMIImpl;

public class MOMListenerFactory extends au.edu.uwa.mech.util.Factory

{
  public MOMListenerFactory() {
    register("rmi", "au.edu.uwa.mech.mom.rmi.impl.MOMListenerRMIImpl");
    register("socket", "au.edu.uwa.mech.mom.socket.impl.MOMListenerImpl");
    
  }
  
  
  
//    public Object build(String config)
//    {
//      System.err.println("MOMListener build(String config) not finished!!");

//      //just build an RMI one at the moment
//      try {
//        return super.build(config);    
//      } catch (Exception e) {
//        System.err.println("MOMListenerRMIImpl Exception: " + e.getMessage());
//        e.printStackTrace();
//        return null;
//      }
   
//    }

}



