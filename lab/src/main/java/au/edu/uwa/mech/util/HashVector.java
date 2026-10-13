
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: HashVector.java,v 1.1 1999/07/05 12:00:42 bdalton Exp $
// Time-stamp: "1999-07-05 12:22:04 barney"

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

//stores objects in a series of vectors organised by a hashtable key 
package au.edu.uwa.mech.util;

  
import java.util.Vector;
import java.util.Hashtable;

public class HashVector
{
  public void put(Object key, Object element)
  {
    Vector v;
    if ((v=getVector(key))==null)
    {
      v=new Vector();
      entries.put(key, v);
    }
    v.addElement(element);
  }

  public boolean remove(Object key, Object element)
  {
    Vector v;
    if ((v=getVector(key))!=null)
    {
      return v.removeElement(element);
    }
    return false;
  }
  
  
  public Vector getVector(Object key)
  {
    return (Vector)entries.get(key);
  }
  

  Hashtable entries=new Hashtable();
}



