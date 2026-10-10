
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: DefaultUserManager.java,v 1.3 1999/09/16 14:49:56 bdalton Exp $
// Time-stamp: "2000-02-05 17:20:27 barney"

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

package au.edu.uwa.mech.mom.server;

import au.edu.uwa.mech.mom.Peer;
import au.edu.uwa.mech.mom.impl.IniUserDatabase;
import au.edu.uwa.mech.mom.MOMLoginException;
import java.util.Hashtable;


public class DefaultUserManager implements UserManager
{
  IniUserDatabase userDatabase = new IniUserDatabase();
  Hashtable userlevels = new Hashtable();

  public DefaultUserManager() {
    userlevels.put("Guest", new Integer(1));
    userlevels.put("Registered", new Integer(2));
    userlevels.put("Teaching", new Integer(30));
    userlevels.put("Team", new Integer(99));
  }

  public void setDatabaseFile(String filename){
    userDatabase.setDatabaseFile(filename);
  }
  
  
  public Peer login(String userName, String password, String nickname) throws MOMLoginException
  {
    
    
    String filePassword  = userDatabase.getStringField(userName, "PIN");

    System.out.println("in default user manager login"+userName+","+nickname+","+password+","+filePassword);
    
    if (filePassword.equals("NULL"))
    {
      throw new MOMLoginException("username "+userName+" doesn't exist");
    }
    else if (!filePassword.equals(NO_PASSWORD_REQUIRED) &&
             !filePassword.equals(password)){
      System.out.println("Throwing password incorrect");
      throw new MOMLoginException("password incorrect");
    }
    else
    {
      String userlevelString = userDatabase.getStringField(userName, "UserLevel");
      int userLevel = ((Integer)userlevels.get(userlevelString)).intValue();

      Peer peer = new Peer(userName, nickname);
      peer.setUserLevel(userLevel);
      // to do full name, email?
      return peer;
    }

  }
  
  public void logout(Peer p)
  {
    
  }
  
  static final String NO_PASSWORD_REQUIRED="-1";
}



