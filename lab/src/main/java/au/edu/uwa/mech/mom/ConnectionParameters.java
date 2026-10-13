
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ConnectionParameters.java,v 1.2 1999/04/16 14:33:28 bdalton Exp $
// Time-stamp: "1999-04-16 16:46:16 barney"

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

package au.edu.uwa.mech.mom;

public class ConnectionParameters
{
  public ConnectionParameters(String host, int port)
  {
    this.hostname=host;
    this.port=port;
  }

  
  String hostname;
  
  /**
     * Get the value of hostname.
     * @return Value of hostname.
     */
  public String getHostname() {return hostname;}
  
  /**
     * Set the value of hostname.
     * @param v  Value to assign to hostname.
     */
  public void setHostname(String  v) {this.hostname = v;}
  
  
  
  int port;
  
  /**
     * Get the value of port.
     * @return Value of port.
     */
  public int getPort() {return port;}
  
  /**
     * Set the value of port.
     * @param v  Value to assign to port.
     */
  public void setPort(int  v) {this.port = v;}
  
  
}



