
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Packet.java,v 1.7 2000/01/20 11:49:16 bdalton Exp $
// Time-stamp: "2000-01-20 19:26:18 barney"

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


package au.edu.uwa.mech.mom; //inserted by perl script 'checkPackages'

import au.edu.uwa.mech.mom.MOMReadOnlyException;
import au.edu.uwa.mech.mom.ContextURL;
import au.edu.uwa.mech.util.Hook;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;

public class Packet implements java.io.Serializable
{

  public Packet()
  {
    for (Enumeration en=createHooks.elements(); en.hasMoreElements();)
      ((Hook)en.nextElement()).doYourStuffOn(this);
  }
  

  public static Vector createHooks= new Vector();
  
  public void setPropertyReadOnly(String name, Object obj) throws MOMReadOnlyException
  {
    setProperty(name, obj);
    readOnlyProperties.addElement(name);
  }
  
  public void setProperty(String name, Object obj) throws MOMReadOnlyException
  {
    if (readOnlyProperties.contains(name))
      throw new MOMReadOnlyException("Property "+name+" is read only");

    properties.put(name, obj);
    
  }

  public void unsetProperty(String name) throws MOMReadOnlyException
  {
    properties.remove(name);
  }
  
  
  protected void _setProperty (String name, Object obj) 
  {
    properties.put(name, obj);
  }
  
  
  public Object getProperty(String name)
  {
    return properties.get(name);
  }

  //is the property set
  public boolean isPropertySet(String name){
    return properties.containsKey(name);
  }

  public Enumeration getPropertyKeys()
  {
    return properties.keys();
  }

  //need this for writing to XML
  //would be nice if XML writer could get this via reflection
  //havne't worked otu the security implications of this though
  public Hashtable getProperties()
  {
    return properties;
  }

  //would be nice not make this public acces, but we need it for XML
  //serialization at the moment
  
  public void setProperties(Hashtable p)
  {
    this.properties=p;
  }
  

  public String toString()
  {
    StringBuffer buf=new StringBuffer();
    buf.append("{Packet ");
    buf.append("{Properties ");
    buf.append(properties.toString());
    // body now part of properties
    //buf.append("\nBody:\n");
    //char[] body=getBody();
    //if (body != null)
    //  buf.append(new String(body));
    //else
    //  buf.append("null");
    buf.append("}}");
    return buf.toString();
  }
  

  static final long serialVersionUID = -5454354318876977631L;
  
  protected Hashtable properties=new Hashtable();
  protected Vector readOnlyProperties=new Vector();
}



