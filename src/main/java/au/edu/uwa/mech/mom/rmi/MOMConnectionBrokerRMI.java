
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMConnectionBrokerRMI.java,v 1.22 1999/07/05 12:00:41 bdalton Exp $
// Time-stamp: "1999-07-05 18:37:33 barney"

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


import java.rmi.RemoteException;
import au.edu.uwa.mech.mom.*;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Vector;
import au.edu.uwa.mech.mom.Packet;


public interface MOMConnectionBrokerRMI extends java.rmi.Remote
{
  
  public void receive(Object arg)
    throws RemoteException, MOMPermissionException, MOMNotFoundException;

  public void registerCallback(MOMConnectionCallbackRMI callback) throws RemoteException;

  
}
  
//  public java.util.Date getDate() throws RemoteException;

  
//    public Peer login(String userName, String password, String nickname)
//      throws RemoteException, MOMLoginException; 

//    public void logout() throws RemoteException, MOMLoginException;
//    //public void joinSession(String name)
//    //  throws RemoteException, MOMPermissionException; 
//    //public void joinConstituent(String type, String name, String session)
//    //  throws RemoteException, MOMPermissionException;

//    //todo need to allow specification of a manager here 
//    //public void create(String fullname) throws RemoteException, MOMCreateException, MOMPermissionException;
//    public void create(ContextURL curl) throws RemoteException, MOMCreateException, MOMPermissionException;
//    public void join(ContextURL curl) throws RemoteException, MOMPermissionException, MOMNotFoundException;
//    public boolean checkExists(ContextURL curl) throws RemoteException, MOMPermissionException;



  //public Hashtable getChildren() throws RemoteException, MOMPermissionException, MOMNotFoundException; 
  //public Hashtable getChildren(ContextURL parent) throws RemoteException, MOMPermissionException, MOMNotFoundException; 

  //public Vector getChildrenContextURLs() throws RemoteException, MOMPermissionException, MOMNotFoundException; 
  //public Vector getChildrenContextURLs(ContextURL parent) throws RemoteException, MOMPermissionException, MOMNotFoundException; 

  
  
  //public Enumeration getPeers() throws RemoteException, MOMPermissionException; 
  //public Hashtable getSessions() throws RemoteException, MOMPermissionException; 
  //public Hashtable getPeers() throws RemoteException, MOMPermissionException; 
  //public Hashtable getConstituents(String s) throws RemoteException, MOMPermissionException; 
