//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: CGIPost.java,v 1.4 2000/02/02 17:30:47 bdalton Exp $ 
// Time-stamp: "2000-02-03 01:02:46 barney"


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
import java.io.*;
import java.net.*;


public class CGIPost
{
  public static String post(URL url, Hashtable data) throws IOException {
    //System.out.println("posting "+data);
    URLConnection connection = url.openConnection();
    connection.setDoOutput(true);
    connection.setUseCaches(false);
    connection.setRequestProperty ("Content-type","application/x-www-form-urlencoded");

    StringBuffer sb = new StringBuffer();
    for (Enumeration e=data.keys();e.hasMoreElements();){
      Object key = e.nextElement();
      sb.append(key+"="+URLEncoder.encode(data.get(key).toString())+"&");
    }

    //System.out.println("posting "+sb);
        
    PrintStream outStream = new
      PrintStream(connection.getOutputStream());
    outStream.print(sb.toString());
    outStream.close();
         
    DataInputStream inStream = new
      DataInputStream(connection.getInputStream());
    String inputLine;

    StringBuffer result = new StringBuffer();
    while ((inputLine = inStream.readLine()) != null) {
      result.append(inputLine+"\n");
    }

    inStream.close();
    return result.toString();
  }
  

  public static void main(String args[]) throws Exception{
    URL url = new URL(args[0]);
    post(url, new Hashtable());
  }
}
