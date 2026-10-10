
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SimpleUpstream.java,v 1.2 2000/01/23 13:02:32 bdalton Exp $
// Time-stamp: "2000-01-23 13:09:52 barney"

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

package au.edu.uwa.mech.mom;

import java.util.Vector;
import java.util.Enumeration;

public class SimpleUpstream implements Upstream
{

  public SimpleUpstream(){
    upstreamers=new Vector();
  }

  public void close()
  {
    upstreamers.removeAllElements();
  }
  
  public void putqUpstream(Object source, Object arg) throws MOMIOException {
    nextUpstream(this, arg);
  }

  protected void nextUpstream(Object source, Object arg) throws MOMIOException
  {
    for (Enumeration e=upstreamers.elements();e.hasMoreElements();)
      ((Upstream)e.nextElement()).putqUpstream(this, arg); 
  }
  
  public void addUpstream(Upstream upstream) {
    upstreamers.addElement(upstream);
  }
  public void removeUpstream(Upstream upstream) {
    upstreamers.addElement(upstream);
  }

  public void setName(String name){
    this.name=name;
  }

  public String toString(){
    return new String("SimpleUpstream:"+name);
  }
  

  String name="";
  Vector upstreamers;
  
}



