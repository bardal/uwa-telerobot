
//  Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 11:17:07 1999
// Version       : $Id: TCPDistributionTest.java,v 1.1 1999/12/18 08:40:29 bdalton Exp $ 
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
import java.util.*;

import au.edu.uwa.mech.util.Shuffle;

public class TCPDistributionTest
{
  public TCPDistributionTest(String logdir, int port) throws Exception
  {
    ServerSocket ss = new java.net.ServerSocket (port);
    System.err.println("Listening for connections on "+port);

    packetsPerSize = 200;
    logDirectory   = logdir;
    random         = new Random();
      
    //System.out.println(packetSizes);
    //System.out.println(sum);
    
    while (true) {
      try{
        Connection c = new Connection(ss.accept());
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
        System.err.println("Accepted connection "+s.getInetAddress().getHostAddress());
  
        long connectionTime = dis.readLong();
        Date start      = new Date();
        String filename = logDirectory+"/"+s.getInetAddress().getHostAddress()+"_"+start.getTime()+".distribution";
        
        statFile = new FileWriter(filename, true);
        statFilebw = new BufferedWriter(statFile);
                  
        statFilebw.write
          (s.getInetAddress().getHostAddress()+RS+
           start.getTime()+RS+
           connectionTime+RS+
           packetsPerSize);
        statFilebw.newLine();

        
        doEchoTest();

        System.err.println("Finished network test");
        

      }
      catch (IOException e){
        e.printStackTrace();
      }
      finally {
        try{
          s.close();
        }
        catch (IOException e){
          e.printStackTrace();
        }
        finally{
          try{
            statFilebw.close();
          }
          catch (IOException e){
            e.printStackTrace();
          }
        }
      }
    }

    protected void doEchoTest() throws IOException
    {
      //1 test of a particular size
      dos.writeInt(1);

      
      //int packetSizes[] = {1, 5, 10, 50, 100, 500, 1000, 5000, 10000};            
      int packetSizes[] = {1, 5, 10, 50, 100, 500, 1000};            
      int packetSize    = packetSizes[(int)(Math.random() * packetSizes.length)];      
      
      byte stuff[] = new byte[packetSize];
      random.nextBytes(stuff);

      statFilebw.write(packetsPerSize+RS+packetSize+RS);
        
      dos.writeInt(packetsPerSize);
      dos.writeInt(packetSize);
      
      for (int i=0; i<packetsPerSize;i++){
        Date start      = new Date();
        dos.write(stuff);
        dos.flush();
        dis.readFully(stuff);
        Date finish = new Date();
        statFilebw.write((finish.getTime()-start.getTime())+RS);
      }
      statFilebw.newLine();
      statFilebw.flush();
      //try{sleep(10000);}
      //catch(InterruptedException e2){}
    }
    
    
    FileWriter statFile;
    BufferedWriter statFilebw;
    Socket              s;
    InputStream         i;
    OutputStream        o;
    DataInputStream     dis;
    DataOutputStream    dos;
    final String RS = " ";
    
  }
  //int sequenceNo = 0;
  //Vector packetSizes;
  int packetsPerSize;
  int maxSize;  
  int noSizes;
  int totalBytes;
  double step;
  String logDirectory;
  Random random;
  
  public static void main (String[] args)
  {
    try{
      //usage logfile subdir, port
      TCPDistributionTest s = new TCPDistributionTest(args[0], Integer.parseInt(args[1]));
    } catch (Exception e){
      e.printStackTrace();
    } 
  }// end of main ()
}

