
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: HUWManager.java,v 1.5 1999/07/19 12:51:21 bdalton Exp $
// Time-stamp: "1999-07-19 20:15:48 barney"

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

package au.edu.uwa.mech.mom.impl;


import  au.edu.uwa.mech.mom.server.ContextMaster;
import  au.edu.uwa.mech.mom.server.TokenMaster;
import au.edu.uwa.mech.mom.*;
import java.util.Date;


public class HUWManager implements Manager, java.io.Serializable
{

  
  public HUWManager(int stimeout) // timeout in seconds
  {
    this.timeout=stimeout*1000;
  }
  

  //public void attachTo(MasterContext curl)
  //{
  //  this.curl=curl;
  //}
  
                                // have two copies of owner - here and
                                // in the token we are managing 
  public void authorise(ContextMaster cm, MOMPacket packet)  throws MOMPermissionException
  {
    System.out.println("HUW manager called for "+packet.getContextURL()+":"+packet.getName()+":"+packet.getSender());
    
    ContextURL target = packet.getContextURL();
    Peer requestor = packet.getSender();

    // if its a token
    if (cm instanceof TokenMaster)
    {
      TokenMaster tm=(TokenMaster)cm;
      // find request type
      if (packet.getName().equals(MOMPacket.Values.GRAB))
      {
        long now = (new Date()).getTime();
        if ((tm.getOwner() ==  null) ||
            (requestor.getUserLevel() > tm.getOwner().getUserLevel()) ||
            ((now - lastTime) > timeout))
        {
          lastTime = now;
          return;
        }
        else if (requestor.equals(tm.getOwner())){
          lastTime=now;
          return;
        }
        else
          throw new MOMPermissionException(requestor+" can't grab token from "+tm.getOwner());
      }
      else if (packet.getName().equals(MOMPacket.Values.RELEASE))
      {
        if (!requestor.equals(tm.getOwner()))
          throw new MOMPermissionException(requestor+" can't release token as "+tm.getOwner()+" owns it");
      }
      
      
      // check request
      // if user level > than owner
      // if idle time > timout
    }
    //System.out.println("finsihed authorize");
    
  }

  long lastTime;
  //Peer owner;
  long timeout;
  
}



