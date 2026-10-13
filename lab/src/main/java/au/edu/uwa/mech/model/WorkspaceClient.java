//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: WorkspaceClient.java,v 1.1 2000/01/26 13:35:53 bdalton Exp $ 
// Time-stamp: "2000-01-26 19:25:06 barney"

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

package au.edu.uwa.mech.model;

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.*;
import au.edu.uwa.mech.util.TheLog;

public class WorkspaceClient implements MessageEventListener
{
  Channel channel;
  public WorkspaceModel model;
  
  public WorkspaceClient(Channel c) throws MOMException {
    c.addMessageEventListener(this);
    channel=c;
    c.join();
    model = new WorkspaceModel();
  }
  
  
  public void message(Context source, MOMPacket m){
    try{
      Object o = m.getArgDeserialized();
      if (o instanceof WorkspaceCommand)
        ((WorkspaceCommand)o).dDispatch(model);
    }
    catch(MOMIOException e){
      TheLog.logException(e);
    }
  }

  public void addObject(WorkspaceObject wo) throws MOMIOException
  {
    WorkspaceCommand command = new AddCommand(wo);
    doCommand(command);
  }

  public void deleteObject(String name) throws MOMIOException
  {
    WorkspaceCommand command = new DeleteCommand(name);
    doCommand(command);
  }
  
  void doCommand(WorkspaceCommand c) throws MOMIOException
  {
    //operate on our own workspace!
    c.dDispatch(model);
    //let everyone else know
    MOMPacket message = channel.getPacketFactory().message();
    message.setArgSerialized(c);
    channel.sendXMessage(message);
  }
  
    
}
