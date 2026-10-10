//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Hostsfile.java,v 1.1 2000/02/02 17:30:48 bdalton Exp $ 
// Time-stamp: "2000-02-03 00:20:06 barney"

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

package au.edu.uwa.mech.net;

import java.util.*;
import java.net.*;

public class Hostsfile 
{
  static Hashtable h = new Hashtable();

  public static void put(String host, String ip){
    h.put (host, ip);
  }

  public static String lookup(String host) throws UnknownHostException {
    if (h.containsKey(host))
      return (String)h.get(host);
    else
      throw new UnknownHostException(host+" is not on the hostsfile");
  }
  
}
