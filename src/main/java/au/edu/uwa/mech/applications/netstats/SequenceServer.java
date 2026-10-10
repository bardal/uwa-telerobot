
//  Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 11:17:07 1999
// Version       : $Id: SequenceServer.java,v 1.1 1999/11/01 03:39:36 bdalton Exp $ 
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

public class SequenceServer 
{
  public SequenceServer(String logfile, int port) throws Exception
  {
    ServerSocket ss = new java.net.ServerSocket (port);
    System.err.println("Listening for connections on "+port);

    statFile = new FileWriter(logfile, true);
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
        System.out.println("Sequence no "+((sequenceNo % sequenceCount) * step));
        System.err.println("Accepted connection "+s.getInetAddress().getHostAddress());
  
        long connectionTime = dis.readLong();
        Date start      = new Date();
        int  noPackets  = 50;
        int packetSize = (int)(Math.pow(10,(++sequenceNo % sequenceCount) * step));
        
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

    static final int sequenceCount=100;
    static final float step = (float)(4.0/sequenceCount);

    

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

  int sequenceNo = 0;
  
  public static void main (String[] args)
  {
    try{
      SequenceServer s = new SequenceServer(args[0], 9034);
    } catch (Exception e){
      e.printStackTrace();
    } 
  }// end of main ()
}
