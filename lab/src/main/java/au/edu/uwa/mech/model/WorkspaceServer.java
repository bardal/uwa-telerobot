//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: WorkspaceServer.java,v 1.2 2000/01/27 11:50:44 bdalton Exp $ 
// Time-stamp: "2000-01-27 14:04:23 barney"

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
import au.edu.uwa.mech.util.FileLog;
import java.io.*;
import java.util.*;


public class WorkspaceServer  implements MessageEventListener, PeerEventListener 
{
  Channel channel;
  SessionRoot sr;
  Peer us;
  BufferedWriter writer;
  WorkspaceModel model;
  FileLog commandLog;
  String workspacefile;
  
  WorkspaceServer(String momurl, String logfile, String workspacefile) throws IOException
  {
    this.workspacefile=workspacefile;
    commandLog = new FileLog(logfile, true);

    model = new WorkspaceModel();
    model.loadFromFile(workspacefile);
    
    try
    {    
      sr = new SessionRoot(momurl);
      us = sr.login("guest", "", "workspace");
      channel = (Channel)sr.lookupOrCreate(new ContextURL("workspace.channel"));
      channel.join();
      channel.addMessageEventListener(this);
      channel.addPeerEventListener(this);
    } catch (MOMException e) {
      e.printStackTrace();
    }
  }


  
  public void peerJoined(Context c, Peer p){
    //send workspace to client
    System.out.println("Peer "+p+" joined sending add commands");
    if (!p.equals(us))
      for (Enumeration e=model.objectNames();e.hasMoreElements();)
        sendAddCommand(p, model.get((String)e.nextElement()));
    
  }

  protected void sendAddCommand(Peer p, WorkspaceObject wo)
  {
    try{
      WorkspaceCommand c = new AddCommand(wo);
      System.out.println("sending add command "+c);
      MOMPacket message = channel.getPacketFactory().message();
      message.setArgSerialized(c);
      message.setTO(p);
      channel.sendMessage(message);
    } catch (Exception e){
      e.printStackTrace();
    }
  }
  
  
  public void peerLeft(Context c, Peer p){
  }

  public void joined(Context c)
  {
  }

  public void left(Context c)
  {
  }
  
  
  public void message(Context source, MOMPacket m){
    Date now = new Date();
    try{
      Object o = m.getArgDeserialized();
      if (o instanceof WorkspaceCommand){
        try{commandLog.println(now+"!!"+m.getSender()+"!!"+o);}
        catch (IOException e){e.printStackTrace();}
        
        ((WorkspaceCommand)o).dDispatch(model);
        model.saveToFile(workspacefile);
      }
    }
    catch(MOMIOException e){
      e.printStackTrace();
    }
  }

  
  
  public static void main (String[] args) throws Exception
  {
    WorkspaceServer ws= new WorkspaceServer(args[0], args[1], args[2]);
    Integer i = new Integer(1);
    synchronized(i){i.wait();}
  } // end of main ()
  

}
