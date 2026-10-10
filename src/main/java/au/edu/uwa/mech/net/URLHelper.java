
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: URLHelper.java,v 1.2 1999/12/12 10:31:28 bdalton Exp $
// Time-stamp: "1999-12-12 14:32:37 barney"

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

package au.edu.uwa.mech.net;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Vector;
import java.util.StringTokenizer;


public class URLHelper
{

  // splits a URL string into parts - without throwing any exceptions
  public URLHelper(String urlstr)
  {
    int rIndex, lIndex = 0;
    StringTokenizer ut;
    
    rIndex = urlstr.indexOf("://");
    if (rIndex > lIndex) {
      protocol=urlstr.substring(0,rIndex);
      lIndex = rIndex +3;
    }
    
    rIndex = urlstr.indexOf("@");
    if (rIndex > lIndex){
      ut = new StringTokenizer(urlstr.substring(lIndex, rIndex), ":");
      while (ut.hasMoreTokens())
        userInfo.addElement(ut.nextElement());
      lIndex = rIndex+1;
    }

    rIndex = urlstr.indexOf("/", lIndex);
    if (rIndex < lIndex)
      rIndex = urlstr.length();
    
      
    ut= new StringTokenizer(urlstr.substring(lIndex, rIndex),":");
    try { 
      host=ut.nextToken();
      port=Integer.parseInt(ut.nextToken());
    } catch (java.util.NoSuchElementException e){
    }

    //TheLog.println(lIndex+","+rIndex);
    lIndex=rIndex+1;
    if (lIndex < urlstr.length())
      rest=urlstr.substring(lIndex);
    
    
  }


  String toDebugString()
  {
    return
      "protocol  : "+protocol+ "\n"+
      "user      : "+userInfo + "\n"+
      "host      : "+host+"\n"+
      "port      : "+port+"\n"+
      "rest      : "+rest;
  }
  
  public String getProtocol() {return protocol;}
  public String getHost() {return host;}
  public int    getPort() {return port;}
  public Vector getUserInfo() {return userInfo;}
  public String getRest() {return rest;}

  
  String protocol;
  String host;
  int port;
  Vector userInfo=new Vector();
  String rest;
  
  public static void main (String[] args)
  {
    URLHelper url=new URLHelper("moms://robot:robot-local:robot@localhost:4444/");
    System.err.println(url.toDebugString());
    url=new URLHelper("moms://localhost:4444/");
    System.err.println(url.toDebugString());
    url=new URLHelper("rmi://lagrange/");
    System.err.println(url.toDebugString());
    url=new URLHelper("barney@lagrange:6679");
    System.err.println(url.toDebugString());
    url=new URLHelper("david@voyager/h");
    System.err.println(url.toDebugString());
    url=new URLHelper("voyager");
    System.err.println(url.toDebugString());
    // this doesn't work but I don't care for the moment
    url=new URLHelper("idiot@:3040");
    System.err.println(url.toDebugString());
  } // end of main ()
  

}



