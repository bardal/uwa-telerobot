
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: NetStats.java,v 1.3 1999/10/12 11:59:57 bdalton Exp $
// Time-stamp: "1999-10-12 11:56:18 barney"

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

package au.edu.uwa.mech.applets.netstats;

import java.applet.Applet;
import au.edu.uwa.mech.net.TestConnection;

public class NetStats extends Applet
{
  
  public void init (){
    if (!doneInit){
      readParameters();
      doneInit = true;
    }
    TestConnection c =  new TestConnection
      (getParameter("host"),
       Integer.parseInt(getParameter("port")),
       getParameter("resultsURL"));
    
    c.start();
  }
  
  public void start(){
  }

  public void stop() {
  }

  public void destroy() {
  }
  
  public String getAppletInfo(){
    return ("NetStats :  Copyright (C) 1999 Barney Dalton  <barney@daltons.net>");
  }
  
  
  protected void readParameters(){
    //parameter = getParameter("parameter");
  }

  boolean doneInit=false;

//    public static void main (String[] args)
//    {
//      TestConnection c = new TestConnection("voyager", 9033);
//      c.start();
//    } // end of main ()
  

}



