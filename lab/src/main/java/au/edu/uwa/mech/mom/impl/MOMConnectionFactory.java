
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMConnectionFactory.java,v 1.11 1999/12/12 10:31:28 bdalton Exp $
// Time-stamp: "1999-12-12 14:33:07 barney"

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


import java.util.Hashtable;

import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.mom.MOMConnection;
import au.edu.uwa.mech.mom.MOMConnectionBuildException;

public class MOMConnectionFactory
{
  static Hashtable MOMConnectionClasses=new Hashtable();

  
  static {
      registerMOMConnectionClass ("rmi","au.edu.uwa.mech.mom.rmi.impl.MOMConnectionRMIImpl");
      registerMOMConnectionClass ("socket","au.edu.uwa.mech.mom.socket.impl.MOMConnectionSocketImpl");
      registerMOMConnectionClass ("moms","au.edu.uwa.mech.mom.socket.impl.MOMConnectionSocketImpl");
  }
  

  public static MOMConnection build(String MOMConnectionType)
    throws MOMConnectionBuildException 
  {
    try {
      String className=(String)MOMConnectionClasses.get(MOMConnectionType);
      if (className == null)
        throw new MOMConnectionBuildException("MOMConnectionType "+MOMConnectionType+
                                              " not registered with MOMConnectionFactory ");
      Class type=
        Class.forName(className);
      TheLog.println("making "+type);
      Object mc = type.newInstance();
      TheLog.println("made "+mc);
      return (MOMConnection)type.newInstance();
      //} catch (java.lang.NullPointerException e) {
      
    } catch (java.lang.InstantiationException e){
      throw new MOMConnectionBuildException("MOMConnectionFactory Configuration Error for "
                                         +MOMConnectionType+
                                         ". Registered class is Abstract or an Interface"); 
    } catch (java.lang.IllegalAccessException e){
      throw new MOMConnectionBuildException("MOMConnectionFactory Configuration Error for "
                                         +MOMConnectionType+
                                         ". Registered class is not accessible");
    } catch (java.lang.ClassNotFoundException e){
      throw new MOMConnectionBuildException("MOMConnectionFactory Class Not Found : "+e.getMessage());
    }
    
  }
  
  //static public MOMConnection create(MOMConnectionParameters sp, Manager m)
  //    {
  
  //    }

  public static void registerMOMConnectionClass(String shortName, String fullName)//Class MOMConnectionClass)
  {
    MOMConnectionClasses.put(shortName, fullName);
  }


  public static void main (String[] args)
  {
    try {
      MOMConnection r = MOMConnectionFactory.build("rmi"); 
      MOMConnection s = MOMConnectionFactory.build("socket");
    } catch (MOMConnectionBuildException e)
    {
      TheLog.println("Exception testing MOMConnectionFactory "+e.getMessage());
      e.printStackTrace();
      
    }
  } // end of main ()
  
  
}



