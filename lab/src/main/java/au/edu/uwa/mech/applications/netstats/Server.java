
//  Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 11:17:07 1999
// Version       : $Id: Server.java,v 1.11 1999/11/01 03:39:36 bdalton Exp $ 
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

package au.edu.uwa.mech.applications.netstats;

import java.net.*;
import java.io.*;
import java.util.Date;

public class Server 
{
  public Server(int port) throws Exception
  {
    ServerSocket ss = new java.net.ServerSocket (port);
    System.err.println("Listening for connections on "+port);

    statFile = new FileWriter("netstats50.log", true);
    statFilebw = new BufferedWriter(statFile);
    
    while (true) {
      try{
        Connection c = new Connection(ss.accept ());
        c.start();
      } catch (Exception e){
        e.printStackTrace();
      }
    }
    //statFile.close();
  }

  class Connection extends Thread
  {
    public Connection(Socket s) throws IOException {
      this.s = s;
      s.setTcpNoDelay(true);
      i      = s.getInputStream ();
      o      = s.getOutputStream ();
      dis    = new DataInputStream(new BufferedInputStream(i));
      dos    = new DataOutputStream(new BufferedOutputStream(o));

    }
    
    
    public void run(){
      try{
        System.err.println("Accepted connection"+s.getInetAddress().getHostAddress());
        //DON'T DO THIS IS CAUSES A REVERSE DNS LOOKUP
        //System.out.println("Accepted connection"+s);

        long connectionTime = dis.readLong();

        int packetNos[]={1, 10, 100};
        int packetSizes[]={1, 5, 10, 50, 100, 500, 1000};            
        
        Date start      = new Date();
        //int  noPackets  = packetNos[(int)(Math.random() * packetNos.length)];
        //int  packetSize = packetSizes[(int)(Math.random() * packetSizes.length)];
        
        int  noPackets  = 50;
        //int  packetSize = (int)(Math.pow(10,Math.random() * 3.3));
        //try for some bigger packet sizes
        //int  packetSize = (int)(Math.pow(10,(Math.random()*0.7)+ 3.3));
        int  packetSize = (int)(Math.pow(10,Math.random()*4));
        
        doPing(noPackets, packetSize);

        Date finish = new Date();

        //syncronised write results to log file
        synchronized (statFile){
          
          statFilebw.write
            (s.getInetAddress().getHostAddress()+RS+
             start.getTime()+RS+
             connectionTime+RS+
             noPackets+RS+
             packetSize+RS+
             (finish.getTime()-start.getTime()));
          statFilebw.newLine();
          statFilebw.flush();
        
        }
        

        System.err.println("Finished network test");
        
        s.close();
      } catch (IOException e){
        e.printStackTrace();
      }
    }

    protected void doPing(int noPackets, int packetSize) throws IOException
    {
      System.err.println("Using "+ noPackets + " packets of size " + packetSize);
      
      byte stuff[] = new byte[packetSize];

      dos.writeInt(noPackets);
      dos.writeInt(packetSize);
      
      for (int i=0; i<noPackets;i++){
        dos.write(stuff);
        dos.flush();
        dis.readFully(stuff);
      }
    }
    
    
    Socket              s;
    InputStream         i;
    OutputStream        o;
    DataInputStream     dis;
    DataOutputStream    dos;
    final String RS = " ";
    
  }

  FileWriter statFile;
  BufferedWriter statFilebw;
  
  public static void main (String[] args)
  {
    try{
      Server s = new Server(9033);
    } catch (Exception e){
      e.printStackTrace();
    } 
  }// end of main ()
}
