
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOM.java,v 1.24 1999/06/29 12:54:59 bdalton Exp $
// Time-stamp: "1999-06-29 19:14:30 barney"

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


//should this be in the server directory?
//the MOM server,
//listens for new connections 
//validates client login
//implements, share, join, leave for constituents
//allows managment of constituents
//routes messages on channels
//accepts changes to shared data
//notifys peers of changes to shared data
//handles token requests


//NO point in int this interface anymore

package au.edu.uwa.mech.mom;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public interface MOM extends Transceiver
{
  public Peer login(String userName, String password, String nickname) throws MOMLoginException;
  public void logout(Peer p) throws MOMLoginException;
  //public SessionMaster joinSession(String peerName, String sessionName) throws MOMPermissionException;
  //public void joinConstituent(String peerName, String constituentType,
  //                            String constituentName, String sessionName)
  //  throws MOMPermissionException;

  public void joinContext(ContextURL curl, Peer p)
    throws MOMPermissionException, MOMNotFoundException;
 
  public boolean checkExists(ContextURL curl, Peer p)
    throws MOMPermissionException;

  public void create(ContextURL curl, Peer peer)
    throws MOMPermissionException, MOMCreateException;


  //connection brokers
  //public void addReceiver(Receiver handler);
  //public void removeReceiver(Receiver handler);

  
  //public void          addBrokerFor(Peer p, MOMConnectionBroker cb);
  //public void          removeBrokerFor(Peer p);

  
  /*Enumerations are not serializable, os have to commit ot hastables
   * or vectors
   */
  //public Hashtable   getChildren(ContextURL curl, Peer p)throws
  //MOMPermissionException, MOMNotFoundException;

  //    public Hashtable getChildren(ContextURL curl, Peer p)throws
  //    MOMPermissionException, MOMNotFoundException;

  //    public Vector getChildrenContextURLs(ContextURL curl, Peer p)throws
  //    MOMPermissionException, MOMNotFoundException;
  

  //public Enumeration   getConnections();//String peerName);
  //public Enumeration   getPeers(String peerName); //the peerName is to
                                                  //check for permission
  public static String ROOT="root";

  //public Context lookup(String context, String peerName) throws MOMPermissionException;
  //public Context create(String context, String peerName) throws MOMPermissionException;


  //replce with EventHandler interface
  //public void receive(Object arg, Peer p)
  //  throws MOMPermissionException, MOMNotFoundException;
  
 
}




