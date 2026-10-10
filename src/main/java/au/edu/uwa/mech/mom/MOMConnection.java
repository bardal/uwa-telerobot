
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMConnection.java,v 1.32 2000/01/14 16:46:03 bdalton Exp $
// Time-stamp: "2000-01-15 00:16:44 barney"

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


//implemented by socket, RMI, CORBA. HTTP connections

package au.edu.uwa.mech.mom;

public interface MOMConnection extends Transceiver
{
                                //this could be part for MOMConnectionFactory
                                //Conneciton -
                                //MOMconnectionFactory.connect("momrmi://user:pin:nickname@host/") 
                                //
  public void setURL(String url);

  public void connect() throws MOMIOException;

  public void disconnect() throws MOMIOException;
  
  public boolean connected();
}















//import au.edu.uwa.mech.mom.Packet;
//import au.edu.uwa.mech.mom.Receiver;
//import java.io.IOException;
//import java.util.Enumeration;
//import java.util.Date;
//import au.edu.uwa.mech.util.ObservableI;


//ideal interface 99/06/13
/* All Client/Peer events/commands goto throught this interface server
 * server Commands sent as part of a server event - MOM interprets it
 * fully.
 * Peer commands/messages sent as part of peer event as a char[],
 * server does not need to understand contents - just passes them on
 * to the client - after checking access etc first
 */

// NOT IN ACTION YET - see below for current interface

//  public interface MOMConnection
//  {
//    //send stuff to the server or other clients
//    public void send(Object arg) throws MOMIOException, MOMPermissionException, MOMNotFoundException;
//    //synchronous send and receive
//    public Object sendAndReceive(Object arg)  throws MOMIOException, MOMPermissionException, MOMNotFoundException;
//    //synchronous receive of events
//    public Object receive(long timeout) throws MOMIOException;
//    //asynchronous receive of events
//    public void setEventHandler(EventHandler handler);
  
//  }


//is this a transceiver?







  //public Peer    login() throws MOMLoginException, MOMIOException;
  //need nickname
  //just get assigned one for the moment
  //public Peer   login(String userName, String password, String nickname)
  //public Peer    login(LoginParameters lp);
  //  throws MOMLoginException, MOMIOException;
  //public void    logout() throws MOMLoginException, MOMIOException;
  //public boolean loggedIn();

  //public Peer    getPeer(); //get the peer we'return logged in as
  
  //public void send(Object arg)
  //  throws MOMIOException,
  //         MOMPermissionException,
  //         MOMNotFoundException;

  //public void setReceiver(Receiver receiver);
  
  //does this fit with the interface?
  //make part of sessionroot
  //public Date getMOMTime();//return time in MOM time

  //public void    create(ContextURL curl) throws MOMIOException, MOMPermissionException, MOMCreateException;
  //public boolean exists(ContextURL curl) throws MOMIOException, MOMPermissionException, MOMNotFoundException;

  
  //public static int CONNECTED=1;
  //public static int DISCONNECTED=2;
  //public static int LOGGED_IN=3;
  //public static int LOGGED_OUT=4;

  
  //public Enumeration getSessions();
  

  //name is of the form session/constituent.type
  //ie to lookup the roobt channel in the teletobot session
  //lookup("telerobot/robot.channel")
  //to lookup the telerobot session
  //lookup("telerobot")
  //public Context create(String name) throws MOMIOException, MOMPermissionException;

  //replace with a send MOMCommand function??? - been tried the asynch * made it hardto use
    
  //public void    join  (ContextURL curl) throws MOMIOException, MOMPermissionException, MOMNotFoundException;
  //public void    leave (ContextURL curl) throws MOMIOException, MOMPermissionException, MOMNotFoundException; 

  //enumeration is not serializable so this has to be a Vector
  //public java.util.Vector  getChildrenContextURLs(ContextURL curl) throws MOMIOException, MOMPermissionException, MOMNotFoundException;

  
  //public Session lookupSession(String name) throws MOMIOException, MOMPermissionException;
  //public void join(Session c) throws MOMIOException, MOMPermissionException;
  //public void joinConstituent(MOMConstituent mc, Session s) throws MOMIOException, MOMPermissionException;
  
  //public void share(Session c);
  
  //public Session joinSession(String name) throws MOMIOException, MOMPermissionException;
  //public void leave(Session c);
  
  //public MessageOutput getMessageOutput() throws LoginException;
  //public MessageInput  getMessageInput() throws LoginException;


