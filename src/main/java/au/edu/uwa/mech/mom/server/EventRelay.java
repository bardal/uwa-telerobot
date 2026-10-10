
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: EventRelay.java,v 1.8 2000/01/14 10:42:30 bdalton Exp $
// Time-stamp: "2000-01-14 17:15:33 barney"

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

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.Packet;

public interface EventRelay
{
  public void send(Object args);
  //send to a list of peers
  public void sendTo(java.util.Vector Peers, Object args);
  //send to peer only
  public void sendTo(Peer p, Object args);
  //send to everyone but peer
  public void sendX(Peer P, Object args);
  
}



