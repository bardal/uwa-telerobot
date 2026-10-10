//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: UDPStats.java,v 1.3 1999/12/10 11:15:14 bdalton Exp $ 
// Time-stamp: "1999-12-10 19:10:12 barney"

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

package au.edu.uwa.mech.applets.netstats;

import java.applet.Applet;

import java.net.*;
import java.io.*;
import java.util.Date;

public class UDPStats extends Applet
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
  
  class TestConnection extends Thread
  {
    String host;
    int    port;
    String resultURL;
    DatagramSocket socket;
    String lastError;
  
    public TestConnection(String host, int port, String resultURL){
      this.host = host;
      this.port = port;
      this.resultURL = resultURL;
    }
    
    
    public void run()
    {
      try{
        //System.out.println("opening datagram socket ");
        socket = new DatagramSocket();
      
        // send request
        byte[] buf = new byte[0];
        //System.out.println("getting address byname for "+host);
        //InetAddress address = InetAddress.getByName(host);
        InetAddress address = InetAddress.getByName(host);
        //System.out.println("creating UDP packet for "+host);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port);
        //System.out.println("sending UDP packet");
        socket.send(packet);

        // get response
        buf = new byte[256];
        packet = new DatagramPacket(buf, buf.length);
        System.out.println("waiting for UDP packet");
        socket.setSoTimeout(10000);
        socket.receive(packet);


        DataInputStream  dis = new DataInputStream(new ByteArrayInputStream(buf));
        int noPackets  = dis.readInt();
        int packetSize = dis.readInt();
        System.out.println("sending "+noPackets+" of size "+packetSize+" bytes");

        buf = new byte[packetSize];

        int packetLoss=0;
        int received=0;
        int totalRoundTripDelay=0;
      
        for (int i = 0; i < noPackets; i++)
        {
          packet = new DatagramPacket(buf, buf.length, address, port);
          Date start = new Date();
          try{
            socket.send(packet);
            DatagramPacket replyPacket= new DatagramPacket(buf, buf.length);
        
            socket.setSoTimeout(5000);
            socket.receive(replyPacket);
            Date finish = new Date();
            received++;
            totalRoundTripDelay+=finish.getTime()-start.getTime();

          }catch (IOException e){
            packetLoss++;          
            lastError = e.toString();
          }

          if(packetLoss > (0.25*noPackets)){
            packetLoss = noPackets-received;
            break;
          }
        
        
        } // end of for (int i = 0; i < noPackets; i++)
      
        System.out.println("Stats: received "+received+" lost "+packetLoss+" delay "+totalRoundTripDelay);

        String RS=" ";
        String result=
          noPackets+RS+
          packetSize+RS+
          received+RS+
          packetLoss+RS+
          totalRoundTripDelay+RS+
          lastError;
        
        postResult(result);
      }
    
      catch(Exception e){
        postResult("0 0 0 0 0 "+e.toString());
      }
    
      try{getAppletContext().showDocument(new URL(getParameter("nexturl")), "_self");}
      catch (MalformedURLException e){e.printStackTrace();}
    
    }

  
  
    protected void postResult(String result) {
      try {
        //System.out.println("Posting result "+result+" to "+resultURL);
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
          //System.out.println(inputLine);
        }
        inStream.close();
                     
      } catch (MalformedURLException me) {
        System.err.println("MalformedURLException: " + me);
      } catch (IOException ioe) {
        System.err.println("IOException: " + ioe);
      } catch (Exception e){
        e.printStackTrace();
      }
    }
  }


}

  








    
//    protected void receive(DatagramPacket replyPacket, int timeout) throws InterruptedException
//    {
//      Integer reply = new Integer(0);
//      synchronized(reply){
//        Receiver receiver = new Receiver(reply, replyPacket);
//        receiver.start();
//        System.out.println("waiting for timeout "+timeout);
//        reply.wait(timeout);
//        System.out.println("interrupting receive");
//        receiver.interrupt();
//      }
//    }
  
//    class Receiver extends Thread
//    {
//      Receiver(Integer reply, DatagramPacket replyPacket){
//        this.replyPacket=replyPacket;
//        this.reply=reply;
//      }
    
//      public void run()
//      {
//        try{
//          System.out.println("Receiver run - waiting for a packet" );
//          socket.receive(replyPacket);
//          System.out.println("Receiver received packet");
//          synchronized(reply){
//            replyPacket.notifyAll();
//          }
//          System.out.println("Finished receive");
//        }
      
//        catch (IOException e){
//          e.printStackTrace();
//        }
//        catch (Exception e){
//          e.printStackTrace();
//        }
      
//      }
//      Integer reply;
//      DatagramPacket replyPacket;
//    }
