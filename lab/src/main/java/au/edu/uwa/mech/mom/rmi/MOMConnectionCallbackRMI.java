
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMConnectionCallbackRMI.java,v 1.7 1999/06/29 12:55:00 bdalton Exp $
// Time-stamp: "1999-06-29 19:53:56 barney"

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


package au.edu.uwa.mech.mom.rmi; //inserted by perl script 'checkPackages'

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.Packet;

public interface MOMConnectionCallbackRMI extends java.rmi.Remote
{
  public void receive(Object ea) throws java.rmi.RemoteException;
}

//  I think this shud help you,
//  I am listing down the steps !

//  Server
//  1)  public interface serverInterface extends java.rmi.Remote {
//          public void registerCallBack(ClientObject client ) throws
//  java.rmi.RemoteException;
//          public void someOtherCalls();
//        }
//  2) public class serverImpl extends UnicastRemoteObject implements
//  serverInterface
//       {
//                  ClientInterface client = null;
//                  // implements the above interfaces ....
//                  public void registerCallBack(ClientInterface client)
//                  throws java.rmi.RemoteException
//                  {
//                          this.client = client;
//                  }
//                  public void someOtherCalls(){
//                  // ...
//                  }
//              }
//  3) Now you can make calls to the client using this reference - "client"
//                  public void callClient(){
//                          client.MakeSomeClientCall(int i);
//                  }


//  Client
//  1) public interface clientInterface extends java.rmi.Remote {
//          public void MakeSomeClientCall( int i ) throws java.rmi.RemoteException;
//        }
//  2) public class clientImpl extends UnicastRemoteObject implements
//  clientInterface
//       {
//          //constructor
//          public clientImpl(){
//          }
//          public void MakeSomeClientCall( int i ) {
//          // Implement this interface
//          }
//      }
//  3)      public void clientClass{
//                  // constructor
//                  public clientClass(){
//                  clientImpl client = new ClientImpl();
//                  // get the server reference
//                  serverInterface server = (serverInterface )
//  Naming.lookup(SomeString);
//                  // now register itself to the server
//                  server.registerCallback(client);
//                  }
//          }

//  You can pass on the client reference to the server in the above fashion and tell
//  the server to "call back" the cleint informing that it is "over".

//  Hope it helps,
//  Santosh

