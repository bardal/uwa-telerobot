
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SimpleTransceiver.java,v 1.5 2000/01/23 13:02:32 bdalton Exp $
// Time-stamp: "2000-01-23 13:12:30 barney"

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

public class SimpleTransceiver implements Transceiver
{

  public SimpleTransceiver()
  {
    downstreamers=new Vector();
    upstreamers=new Vector();
  }

  public void close()
  {
    downstreamers.removeAllElements();
    upstreamers.removeAllElements();
  }
  
  
  public void putqDownstream(Object source, Object arg) throws MOMIOException {
    nextDownstream(this, arg);
  }
  

  public void nextDownstream(Object source, Object arg) throws MOMIOException 
  {
    for (Enumeration e=downstreamers.elements();e.hasMoreElements();)
      ((Downstream)e.nextElement()).putqDownstream(this, arg); 
  }
  
  public void putqUpstream(Object source, Object arg) throws MOMIOException {
    nextUpstream(this, arg);
  }
  

  protected void nextUpstream(Object source, Object arg) throws MOMIOException
  {
    for (Enumeration e=upstreamers.elements();e.hasMoreElements();)
      ((Upstream)e.nextElement()).putqUpstream(this, arg); 
  }
  
  public void addDownstream(Downstream downstream) {
    downstreamers.addElement(downstream);
  }
  
  public void removeDownstream(Downstream downstream) {
    downstreamers.removeElement(downstream);
  }
  
  public void addUpstream(Upstream upstream) {
    upstreamers.addElement(upstream);
  }
  public void removeUpstream(Upstream upstream) {
    upstreamers.addElement(upstream);
  }

  Vector downstreamers,upstreamers;

  public void setName(String name){
    this.name=name;
  }

  public String toString(){
    return new String("SimpleTransceiver:"+name);
  }
  

  String name="";
}



