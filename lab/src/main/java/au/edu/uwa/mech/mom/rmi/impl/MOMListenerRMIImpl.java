
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMListenerRMIImpl.java,v 1.9 2000/01/06 17:05:16 bdalton Exp $
// Time-stamp: "2000-01-06 21:41:40 barney"

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

package au.edu.uwa.mech.mom.rmi.impl;


import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.ServerNotActiveException;


import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.rmi.*;



public class MOMListenerRMIImpl extends UnicastRemoteObject
  //implements MOMServices
  implements MOMListenerRMI, MOMListener
{

  public MOMListenerRMIImpl() throws RemoteException {
    super();
    if (System.getSecurityManager() == null) {
      System.setSecurityManager(new RMISecurityManager());
    }
    try {
      Naming.rebind(name, this);
    } catch (java.net.MalformedURLException e) {
      debug(e.toString());
    } catch (java.rmi.ConnectException e){
      System.out.println("Failed to Connect to RMIregistry is it running?");
    }
    
    
  }
                                // called by client to initiate a
                                // connection, we return a reference
                                // to a new RMIconnection object -
                                // also create an activated queue on
                                // the incoming side
  public MOMTransceiverRMI connect() throws RemoteException
  {
    
    MOMConnectionRMIImpl  cb = new MOMConnectionRMIImpl();
    connectionHandler.newConnection(cb);
    //cb.addUpstream(upstream);
    //QueuedTransceiver queue = new QueuedTransceiver();

    //insert a queue between raw connection and mom
    
    //queue.addUpstream(upstream);
    //queue.addDownstream(cb);
    //cb.addUpstream(queue);

                                // rmi permission denied remote call
                                // not allowed to create a new thread
                                // don't have time to try and work out
                                // security managers
    //create a seperate thread to handle receiving messages
    //queue.activateUpstream(true);
    
    try{
      debug("Connection from "+ getClientHost());
    } catch (ServerNotActiveException e) {
      debug(e.toString());
    }
    return cb;
  }

    public void setConnectionHandler(NewConnectionHandler connectionHandler){
    this.connectionHandler = connectionHandler;
  }
  protected NewConnectionHandler connectionHandler=null;
  

  public void listen()
  {
    //do nothing RMI is alreasy listenimg
    //maybe the rebind should be here
  }
  
  public void configure(String name, Object arg)
  {
    // nothing to configure
  }
  

  static protected boolean debug=true;
  static protected void debug(String s)
  {
    if (debug)
      System.err.println(s);
  }


  public static void main (String[] args)
  {
    if (System.getSecurityManager() == null) {
      System.setSecurityManager(new RMISecurityManager());
    }

    try {
      MOMListenerRMIImpl l=new MOMListenerRMIImpl();

    } catch (Exception e) {
      debug("MOMListenerRMIImpl Exception: " + e.getMessage());
      e.printStackTrace();
    }
  
  } // end of main ()

  
}



