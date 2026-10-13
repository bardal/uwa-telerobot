
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ChannelMaster.java,v 1.14 2000/01/18 17:48:03 bdalton Exp $
// Time-stamp: "2000-01-19 01:27:29 barney"

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


package au.edu.uwa.mech.mom.server; //inserted by perl script 'checkPackages'

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.Packet;
import java.util.Vector;


public class ChannelMaster extends ContextMaster
{
  public ChannelMaster()
  {
    packetProcessors.put(MOMPacket.Values.MESSAGE, new MessageProcessor());  
  }
  

  public static String TYPE_NAME="channel";

  class MessageProcessor implements  PacketProcessor
  {
    public void process(MOMPacket packet)
    {
      Vector to = packet.getTO();
      Peer exclude = packet.getExclude();
      if (to != null)
        sendTo(to, packet);
      else if (exclude != null)
        sendX(exclude, packet);
      else
        send(packet);
    }    
  }
  
}



