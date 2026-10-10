
//  Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 11:17:07 1999
// Version       : $Id: UDPServer.java,v 1.3 1999/12/04 01:21:26 bdalton Exp $ 
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

public class UDPServer 
{
  public UDPServer(int port) throws Exception
  {
    ds = new DatagramSocket (port);
    System.err.println("Listening for UDP connections on "+port);

    byte[] buf = new byte[65535];


    while (true) {
      try{
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        ds.receive(packet);
        if (packet.getLength() == 0)
          sendConnectionDetails(packet);
        else
          echoPacket(packet);
      } catch (Exception e){
        e.printStackTrace();
      }
    }
    //statFile.close();
  }

  protected  void sendConnectionDetails(DatagramPacket p){
    try{
      System.err.println("New client "+p.getAddress().getHostAddress());
      //DON'T DO THIS IS CAUSES A REVERSE DNS LOOKUP
      
      int  noPackets  = 20;
      int  packetSize = (int)(Math.pow(10,Math.random()*4));

      ByteArrayOutputStream bao = new ByteArrayOutputStream();
      DataOutputStream     dos = new DataOutputStream(bao);
      dos.writeInt(noPackets);
      dos.writeInt(packetSize);
      reply(p, bao.toByteArray());
      
    } catch (IOException e){
      e.printStackTrace();
    }
  }

  
  protected final void echoPacket(DatagramPacket p) throws IOException
  {
    //System.out.println("Echoing packet of length "+p.getLength()+" "+p.getData().length);
    byte[] replyBuf = new byte[p.getLength()];
    System.arraycopy(p.getData(), 0, replyBuf, 0, p.getLength());
    reply(p, replyBuf);
  }
  
  
  protected final void reply(DatagramPacket packet, byte[] reply) throws IOException
  {
    InetAddress address = packet.getAddress();
    int port = packet.getPort();
    DatagramPacket replyPacket = new DatagramPacket(reply, reply.length, address, port);
    ds.send(replyPacket);      
  }
    

  DatagramSocket      ds;
  final String RS = " ";

  FileWriter statFile;
  BufferedWriter statFilebw;
  
  public static void main (String[] args)
  {
    try{
      UDPServer s = new UDPServer(Integer.parseInt(args[0]));
    } catch (Exception e){
      e.printStackTrace();
    } 
  }// end of main ()
}
