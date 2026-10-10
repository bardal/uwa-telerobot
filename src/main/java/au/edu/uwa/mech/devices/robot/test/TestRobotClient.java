//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TestRobotClient.java,v 1.1 2000/01/11 16:46:01 bdalton Exp $ 
// Time-stamp: "2000-01-11 23:48:34 barney"

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


package  au.edu.uwa.mech.devices.robot.test;

import java.awt.*;

import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.mom.event.LoginEventListener;
import au.edu.uwa.mech.mom.event.PeerEventListener;
import au.edu.uwa.mech.mom.event.MessageEventListener;
import au.edu.uwa.mech.devices.robot.*;

import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.mom.impl.*;
import au.edu.uwa.mech.mom.gui.*;
import au.edu.uwa.mech.gui.Console;
import java.util.Observer;
import java.util.Observable;

public class TestRobotClient //implements LoginEventListener 
{
  SessionRoot sr;

  
  TestRobotClient(String momURL, Frame f)
  {
    CommandPanel cp=new CommandPanelMC();
    LoginPanel lp=new LoginPanel();

    lp.setLoginHandler(sr);
    f.setLayout(new GridLayout(0,1));
    f.add(cp);
    f.add(lp);

    
    try{   

      SessionRoot sr = new SessionRoot(momURL);
      //sr.addLoginEventListener(this);
      lp.setLoginHandler(sr);
      cp.setRobotHandler(new Client(sr));
      
    } catch (MOMException e) {
      e.printStackTrace();
    }    

  }
  
  
  
  public static void main (String[] args)
  {
    Frame f = new Frame();
    TestRobotClient trc = new TestRobotClient(args[0], f);
    f.pack();
    f.show();
    f.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {System.exit(0);}
      });

    
  } // end of main ()

}
