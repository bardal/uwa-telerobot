
//  Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 11:17:07 1999
// Version       : $Id: DumpHTTPRequest.java,v 1.1 1999/10/19 06:57:41 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

// {{{ GNU General Public License
// This program is free   software; you can redistribute it and/or
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

 



import java.net.*;
import java.io.*;
import java.util.Date;

public class DumpHTTPRequest
{
  public DumpHTTPRequest(int port) throws Exception
  {
    ServerSocket ss = new java.net.ServerSocket (port);
    System.err.println("Listening for connections on "+port);

    
    while (true) {
      try{
        Socket s=ss.accept ();

	System.out.println("Accepted connection"+s.getInetAddress().getHostAddress());

	BufferedReader d = new BufferedReader(new InputStreamReader(s.getInputStream()));
	String line;
	while ((line=d.readLine()) !=null)
	    System.out.println(line);

      } catch (Exception e){
        e.printStackTrace();
      }
    }
  }
  
  public static void main (String[] args)
  {
    try{
      DumpHTTPRequest s = new DumpHTTPRequest(9034);
    } catch (Exception e){
      e.printStackTrace();
    } 
  }
}




