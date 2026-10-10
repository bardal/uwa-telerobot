//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ConnectionManager.java,v 1.12 2000/01/23 13:02:32 bdalton Exp $ 
// Time-stamp: "2000-02-05 16:13:31 barney"

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

package au.edu.uwa.mech.mom.server;

import java.util.*;

//class to manager a connection to a peer
import au.edu.uwa.mech.mom.MOMPacket;
import au.edu.uwa.mech.mom.TransceiverManager;
import au.edu.uwa.mech.mom.Transceiver;
import au.edu.uwa.mech.mom.SimpleUpstream;
import au.edu.uwa.mech.mom.ActiveTransceiver;
import au.edu.uwa.mech.mom.ManageableTransceiver;
import au.edu.uwa.mech.mom.MOMIOException;
import au.edu.uwa.mech.mom.rmi.MOMTransceiverRMI;
import au.edu.uwa.mech.util.TheLog;

public class ConnectionManager extends SimpleUpstream implements TransceiverManager 
{
  MOMServer mom;
  Transceiver controlT, bulkT;
  RubbishTransceiver controlRubbish, bulkRubbish;
  
  
  //list of transceivers for this connection
  Vector controlTransceivers = new Vector();
  Vector bulkTransceivers = new Vector();
  //ActiveTransceiver at;
  boolean closed=false;
  boolean closing=false;

  String name;
  public void setName(String name){
    this.name=name;
    controlRubbish.setName(name+":controlRubbishFilter");
    bulkRubbish.setName(name+":bulkRubbishFilter");
    //need to set the network transcevier names as well
    controlT.setName(name+":controlTransceiver");
    bulkT.setName(name+":bulkTransceiver");
  }

  public String toString(){
    if (name !=null)
      return new String("Connection manager for"+name+" "+controlT+"\n");
    else
      return super.toString();
  }
  // ConnectionManager(MOMServer mom, Transceiver dataconnection,
  // Transceiver controlconnection)
  public ConnectionManager(MOMServer mom, Transceiver controlT, Transceiver bulkT)
  {
    this.mom = mom;
    this.controlT = controlT;
    
    controlRubbish = new RubbishTransceiver(this);
    controlRubbish.downstreamThreshold=10;
    controlRubbish.addDownstream(controlT);
    controlRubbish.setName("controlRubbish");
    
    if (controlT instanceof ManageableTransceiver)
      ((ManageableTransceiver)controlT).setManager(this);

    //cut out controlRubbish on the way up
    controlT.addUpstream(this);

    //no real point as we have the sender thread in MOMConnection
    //at.activateDownstream(true);
    //if (controlT instanceof MOMTransceiverRMI)
    //  at.activateUpstream(true);

    controlTransceivers.addElement(controlRubbish);
    controlTransceivers.addElement(controlT);


    //now setup the bulk transceiver
    this.bulkT = bulkT;
    if (bulkT instanceof ManageableTransceiver)
      ((ManageableTransceiver)bulkT).setManager(this);

    //filter out unimportant messages if we're lagging
    bulkRubbish = new RubbishTransceiver(this);
    bulkRubbish.downstreamThreshold=5;
    bulkRubbish.addDownstream(bulkT);
    bulkRubbish.setName("bulkrubbish");
    
    bulkT.addUpstream(this);

    bulkTransceivers.addElement(bulkRubbish);
    bulkTransceivers.addElement(bulkT);

    mom.newConnection(this);
    
    controlRubbish.start();
    bulkRubbish.start();
    
  }


  public void setSendTimeout(long timeout){
    System.out.println("ConnectionManager setting send timeout to "+timeout);
    bulkRubbish.setDownstreamTimeout(timeout);
    controlRubbish.setDownstreamTimeout(timeout);
  }
  

  // bulk or control information
  //if bulk bt.putdownstream
  //else ct.putdownstream
  
  public void send(Object obj) {
    try{
      if (obj instanceof MOMPacket){
        MOMPacket mp = (MOMPacket)obj;
        //find out what type of message it is bulk/control
        if (mp.isBulk())
          bulkRubbish.putqDownstream(this,mp);
        else
          controlRubbish.putqDownstream(this,mp);
      }
      else {
        TheLog.println("ConnectionManager - sending something that isn't a MOMPacket");
        controlRubbish.putqDownstream(this, obj);
      }
      
    }
    catch(MOMIOException e){
      handleError(controlRubbish, e, Transceiver.DOWNSTREAM);
    }
  }

  public void putqUpstream(Object source, Object arg) throws MOMIOException {
    mom.receive(this, arg);
  }
  
  public synchronized void close()
  {
    closing=true;
    for (Enumeration en=controlTransceivers.elements();en.hasMoreElements();)
      ((Transceiver)en.nextElement()).close();
    controlTransceivers.removeAllElements();

    for (Enumeration en=bulkTransceivers.elements();en.hasMoreElements();)
      ((Transceiver)en.nextElement()).close();
    bulkTransceivers.removeAllElements();

    closed = true;
  }
  
  
  
  public void handleError(Transceiver source, Exception e, int direction)
  {
    try{
      if(!closed && !closing){
        TheLog.println("ConnectionManager.handleError start");
        TheLog.println(e.toString());
        close();
        //assume fatal for the moment - close the connection
        mom.lostConnection(this);
      }
    } catch (Exception e2){
      TheLog.println("Error in ConnectionManager::handleError");
      TheLog.logException(e2);
    }
    
    
  }
  
}

