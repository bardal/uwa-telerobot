
// Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 09:45:35 1999
// Version       : $Id: Client.java,v 1.3 1999/11/01 03:39:36 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

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

package au.edu.uwa.mech.applications.netstats;


import au.edu.uwa.mech.net.TestConnection;

public class Client
{
  public static void main (String[] args)
  {
    for (int i=0;i<100;i++)
    {
      TestConnection c = new TestConnection(args[0], Integer.parseInt(args[1]), null);
      c.run();
    }
    
  } // end of main ()

}
