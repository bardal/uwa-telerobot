
// Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 09:43:52 1999
// Version       : $Id: TestConnection.java,v 1.4 1999/12/10 11:15:14 bdalton Exp $ 
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

//this class should not be here, used for network tests only!!

package au.edu.uwa.mech.net;

import java.net.*;
import java.io.*;
import java.util.Date;

public class TestConnection extends Thread
{
  String host;
  int    port;
  String resultURL;
  
  public TestConnection(String host, int port, String resultURL){
    this.host = host;
    this.port = port;
    this.resultURL = resultURL;
  }
    
    
  public void run()
  {
    try{
      Date start = new Date();
        
      Socket s      = new Socket(host, port);
      System.out.println("Connection establised, performing echo test");
      Date   finish = new Date();

      s.setTcpNoDelay(true);
      InputStream      in  = s.getInputStream ();
      OutputStream     out = s.getOutputStream ();
      DataInputStream  dis = new DataInputStream(new BufferedInputStream(in));
      DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(out));

      dos.writeLong(finish.getTime()-start.getTime());
      dos.flush();
        
      int noPackets  = dis.readInt();
      int packetSize = dis.readInt();

      byte stuff[] = new byte[packetSize];

      
      for (int i=0; i<noPackets;i++){
        dis.readFully(stuff);
        dos.write(stuff);
        dos.flush();
      }
      s.close();
      System.out.println("finished echo test");

      if (resultURL !=null)
        postResult("completed");
      
    } catch (IOException e){
      e.printStackTrace();
      postResult("failed "+e.toString());
    }
  }


  protected void postResult(String result) {
    try {
      URL url = new URL(resultURL);
      URLConnection connection = url.openConnection();
      connection.setDoOutput(true);
      PrintStream outStream = new
        PrintStream(connection.getOutputStream());
      outStream.println("result="+result);
      outStream.close();
         
      DataInputStream inStream = new
        DataInputStream(connection.getInputStream());
      String inputLine;
                             
      while ((inputLine = inStream.readLine()) != null) {
      }
      inStream.close();
                     
    } catch (MalformedURLException me) {
      System.err.println("MalformedURLException: " + me);
    } catch (IOException ioe) {
      System.err.println("IOException: " + ioe);
    }
  }
  
}
  






