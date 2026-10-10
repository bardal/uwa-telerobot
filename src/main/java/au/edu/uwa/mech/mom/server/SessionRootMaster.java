
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SessionRootMaster.java,v 1.4 1999/09/02 18:17:19 bdalton Exp $
// Time-stamp: "1999-09-01 15:22:11 student"

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

import au.edu.uwa.mech.mom.ContextURL;


public class SessionRootMaster extends SessionMaster
{
  public SessionRootMaster()
  {
    super();
    directory=new Directory();
    setContextURL(new ContextURL(""));
    this.sessionRoot=this;
  }

  public String toString(){
    return super.toString()+"\n"+directory.toString();
  }
  
  
  Directory directory;
}



class Directory
{
  void put(ContextURL curl, ContextMaster cm) {
    directory.put(curl, cm);
  }
  
  ContextMaster remove(ContextURL curl) {
    return (ContextMaster)directory.remove(curl);
  }

  ContextMaster lookup(ContextURL curl) {
    return (ContextMaster)directory.get(curl);
  }

  boolean contains(ContextURL curl) {
    return directory.containsKey(curl);
  }

  public String toString(){
    return directory.toString();
  }
  
  
  java.util.Hashtable directory = new java.util.Hashtable();
}



