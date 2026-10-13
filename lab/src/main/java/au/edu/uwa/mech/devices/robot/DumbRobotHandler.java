
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: DumbRobotHandler.java,v 1.3 1999/11/14 08:29:57 bdalton Exp $
// Time-stamp: "1999-11-14 16:25:10 barney"

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

package au.edu.uwa.mech.devices.robot;

import au.edu.uwa.mech.util.ObservableVector;


public class DumbRobotHandler implements RobotHandler
{
  protected CommandList robotCommands = new CommandList();
  
  public void gainControl() throws RobotException
  {
  }
  
  public void releaseControl() throws RobotException
  {  
  }
  
  public CommandList getCommands()
  {
    return robotCommands;
  }
  
  public void sendCommands() throws RobotException
  {
  }

  public RobotState getState(){return null;}
  

  public void addStateListener(StateListener eh){;}
  
  public void removeStateListener(StateListener eh){;}
  
  public void addRobotListener(RobotListener eh){;}
  public void removeRobotListener(RobotListener eh){;}
  
  
}



