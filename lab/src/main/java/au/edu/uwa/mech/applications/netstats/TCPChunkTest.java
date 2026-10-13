
//  Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 11:17:07 1999
// Version       : $Id: TCPChunkTest.java,v 1.2 1999/12/18 10:13:31 bdalton Exp $ 
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

//see how splitting a pice of data into n parts effects rtt
public class TCPChunkTest
{
  String logDirectory;
  Random random = new Random();

  public TCPChunkTest(String logdir, int port) throws Exception
  {
    ServerSocket ss = new java.net.ServerSocket (port);
    System.err.println("Listening for connections on "+port);
    logDirectory   = logdir;
    
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
        String filename = logDirectory+"/"+s.getInetAddress().getHostAddress()+"_"+start.getTime()+".chunks";
        
        statFile = new FileWriter(filename, true);
        statFilebw = new BufferedWriter(statFile);
                  
        statFilebw.write
          (s.getInetAddress().getHostAddress()+RS+
           start.getTime()+RS+
           connectionTime);
        statFilebw.newLine();
        
        doEchoTest();

        System.err.println("Finished network test for "+s.getInetAddress().getHostAddress());
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
      int messageSize=1260; //highly composite number
      //int[] factors = new int[]{1, 2, 3, 4, 5, 6, 7, 9, 10, 12, 14,
      //15, 18, 20, 21, 28, 30, 35, 36, 42, 45, 60, 63, 70, 84, 90, 105,
      //126, 140, 180, 210, 252, 315, 420, 630, 1260};

      //start at size 10 tests are taking too long
      int[] factors = new int[]{10, 12, 14, 15, 18, 20, 21, 28, 30,
                                35, 36, 42, 45, 60, 63, 70, 84, 90,
                                105, 126, 140, 180, 210, 252, 315,
                                420, 630, 1260};

      int testsPerSize=5;

      
      Vector indices = new Vector();
      for (int i=0; i<factors.length ; i++)
        indices.addElement(new Integer(i));

      indices = Shuffle.shuffle(indices);

      dos.writeInt(indices.size());      
      
      for (Enumeration e=indices.elements();e.hasMoreElements();){

        int index = ((Integer)e.nextElement()).intValue();
        int packetSize    = factors[index];
        int noPackets     = messageSize / packetSize;
        
        byte stuff[] = new byte[packetSize];
        random.nextBytes(stuff);

        //do testsPerSize tests of message split into noPackets of
        //size packetSize 
        statFilebw.write(testsPerSize+RS+noPackets+RS+packetSize+RS);

        dos.writeInt(testsPerSize);
        dos.writeInt(noPackets);
        dos.writeInt(packetSize);
      
        for (int j=0; j<testsPerSize;j++){
          //send the data
          Date start      = new Date();
          for (int k=0; k<noPackets;k++){
            dos.write(stuff);
            dos.flush();
          }
          for (int k=0; k<noPackets;k++)
            dis.readFully(stuff);
          Date finish = new Date();
          statFilebw.write((finish.getTime()-start.getTime())+RS);
        }
        statFilebw.newLine();
        statFilebw.flush();
        //try{sleep(10000);}
        //catch(InterruptedException e2){}
      }
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
  
  public static void main (String[] args)
  {
    try{
      //usage logfile subdir, port
      TCPChunkTest s = new TCPChunkTest(args[0], Integer.parseInt(args[1]));
    } catch (Exception e){
      e.printStackTrace();
    } 
  }// end of main ()
}

