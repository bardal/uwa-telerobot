
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMListenerImpl.java,v 1.18 2000/02/02 17:30:47 bdalton Exp $
// Time-stamp: "2000-02-04 20:51:14 barney"

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


package au.edu.uwa.mech.mom.socket.impl; //inserted by perl script 'checkPackages'

//shouodn't need to import MOM
import au.edu.uwa.mech.mom.Transceiver;
import au.edu.uwa.mech.mom.NewConnectionHandler;
//being lazy here, didn't really want to introduce a coupling with
//MOMSever but I'm short on time
import au.edu.uwa.mech.mom.server.MOMServer;
import au.edu.uwa.mech.mom.server.ConnectionManager;

import au.edu.uwa.mech.mom.MOMIOException;
import au.edu.uwa.mech.mom.MOMListener;
import au.edu.uwa.mech.util.TheLog;

import java.io.*;
import java.net.*;

public class MOMListenerImpl extends Thread implements MOMListener
{
  public MOMListenerImpl()
  {
    //translator=new MIMETranslator();
    translator=new JavaTranslator();
  }
  
  
  public void setPort(int port)
  {
    this.port=port;
    bulkPort= port+1;
  }
  
  int port, bulkPort;
  Integer connectBulk= new Integer(1);
  ServerSocket s;
  ServerSocket ssBulk=null;
  NewConnectionHandler connectionHandler;
  //upstream transceiver (mom end)
  //Transceiver upstream;
  //downstream transceiver (socket end)
  Translator translator;
  //quick hack
  MOMServer mom;
  

  public void configure(String name, Object arg)
  {
    TheLog.println("configure"+name+","+arg);
    if (name.equals("port"))
      setPort(((Integer)arg).intValue());
    //else if  (name.equals("bulkport"))
    //  bulkPort=((Integer)arg).intValue();
    else if  (name.equals("mom"))
      mom=(MOMServer)arg;
    
  }
  
  
  public void setConnectionHandler(NewConnectionHandler connectionHandler){
    this.connectionHandler = connectionHandler;
  }
    
  public void listen() throws MOMIOException
  {
    try{
      s = new ServerSocket (port);
      ssBulk = new ServerSocket(bulkPort);
      ssBulk.setSoTimeout(10000);
      TheLog.println("Listening for connections on "+port);
      start();
    }
    catch (IOException e){
      throw new MOMIOException("Couldn't listen in MOMListenerImpl::listen "+e.getMessage());
    }
    
  }
  
  public void run() 
  {

    while (true) {
      try{
        TheLog.println ("MOMListener doing accept");
        Socket socket = s.accept ();
        TheLog.println ("MOMListener Accepted connection from " + socket.getInetAddress().getHostAddress());

        if (mom != null){
          //send port back to client
          DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
          dos.writeInt(bulkPort);
          dos.flush();
          
          Socket bulkSocket=null;
          //synchronized(connectBulk)
          //{
          //start listen on a random port;
          //wait for connection (with a timeout maybe)

          //try 10 times get the right connection
          //could be backlog of old bulk accepts queued
          for (int i=0;i<10;i++){
            
            bulkSocket = ssBulk.accept();
            //}

            if (bulkSocket.getInetAddress().equals(socket.getInetAddress()))
            {
              //create transciever for each connection
              //create connectionmanager
              
              SocketTransceiver controlTransceiver
                = new SocketTransceiver(translator);
              controlTransceiver.setName("c");
              
              SocketTransceiver bulkTransceiver
                = new SocketTransceiver(translator);
              bulkTransceiver.setName("b");
              
              ConnectionManager cm= new ConnectionManager(mom, controlTransceiver, bulkTransceiver);
              cm.setName(socket.getInetAddress().getHostAddress());
              controlTransceiver.startTransceiving(socket);
              bulkTransceiver.startTransceiving(bulkSocket);
              System.out.println("Succesfully setup bulk and control sockets");
              break;
              //broker.addUpstream(upstream);
              //upstream.addDownstream(broker);
            }
            else
            {
              System.out.println("Control and Port sockets out of sync (ignoring connection from "
                                 +ssBulk.getInetAddress());
              bulkSocket.close();
              bulkSocket = null;
            }
          }
          if (bulkSocket == null){
            System.out.println("Failed to setup bulk connection - closing the control socket");
            socket.close();
          }
          
        }
        else{
          TheLog.println("NO connectionHandler - what's going on, I'll have to close the connection");
        }
      }catch(IOException e){
        TheLog.println("IGNORING IOEXCEPTION (see next line)\nMOMListenerImpl::run - accept threw an exception - "+e.getMessage());
        e.printStackTrace();
      }
    }
  }

  public static void main (String[] args)
  {
    try{
      MOMListenerImpl listener=new MOMListenerImpl();
      listener.setPort(4444);
      listener.listen();
    }
    catch (Exception e){
      e.printStackTrace();
    }
    
  } // end of main ()
  

}



