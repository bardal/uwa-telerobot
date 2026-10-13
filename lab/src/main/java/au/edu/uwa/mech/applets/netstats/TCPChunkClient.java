
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TCPChunkClient.java,v 1.1 1999/12/24 10:34:08 bdalton Exp $
// Time-stamp: "1999-12-18 12:51:18 barney"

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
import java.util.*;
import java.io.*;
import java.net.*;
import java.awt.*;


public class TCPChunkClient extends Applet
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

  public void paint(Graphics g){
    int x = 5;
    int y = getSize().height / 2;
    if(pinging)
      g.drawString("Completed "+completed+" of "+noSizes+" tests",x,y);
    else
      g.drawString("receiving test data",x,y);
  }
  
  
  public String getAppletInfo(){
    return ("TCPChunkClient :  Copyright (C) 1999 Barney Dalton  <barney@daltons.net>");
  }
  
  
  protected void readParameters(){
    //parameter = getParameter("parameter");
  }

  boolean doneInit=false;
  boolean pinging =false;
  int completed=0;
  int noSizes=0;
  
  class TestConnection extends Thread
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

        noSizes = dis.readInt();

        pinging = true;
        for (completed=0;completed<noSizes;completed++){  

          repaint();
          
          int testsPerSize  = dis.readInt();
          int noPackets = dis.readInt();
          int packetSize = dis.readInt();

          byte stuff[] = new byte[packetSize];


          for(int i=0;i<testsPerSize;i++)
          {
            for (int j=0; j<noPackets;j++)
              dis.readFully(stuff);
            for (int j=0; j<noPackets ;j++){
              dos.write(stuff);
              dos.flush();
            }
          }
        }
      
        s.close();
        System.out.println("finished echo test");

        if (resultURL !=null)
          postResult("completed");
      
      } catch (IOException e){
        e.printStackTrace();
        postResult("failed "+e.toString());
      }
      finally {
        System.out.println("All done");
        try{getAppletContext().showDocument(new URL(getParameter("nexturl")), "_self");}
        catch (MalformedURLException e){e.printStackTrace();}
      }
    }


    protected void postResult(String result) {
      try {
        URL url = new URL(resultURL);
        URLConnection connection = url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty ("Content-type","application/x-www-form-urlencoded");
        
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


}


  









