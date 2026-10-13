
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLHashtableReader.java,v 1.4 1999/12/12 10:31:28 bdalton Exp $
// Time-stamp: "1999-12-12 18:26:13 barney"

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


package au.edu.uwa.mech.xml;

import au.edu.uwa.mech.util.TheLog;
import java.util.Hashtable;
import java.util.Stack;

public class XMLHashtableReader implements XMLObjectReader
{
  //return a new instance of the class we are registered to read
  public Object newInstance()
  {
    return new Hashtable();
  }
  

  //set field named 'name' to 'value' in 'parent'
  //how the hell will this work for Hashtables?
  public void setField(String field, Object value, Object container)
  {
    //inherit key/value from parent interface?
    //either a 'key' or 'value'
    //this instance may be parsing multiple hashtables
    //so have to be careful about rembering the key
    //container better be a Hashtable
    Hashtable hash=(Hashtable)container;
    if (field.equals("key"))
    {
      //save the key for the next call with a value
      //pendingKeys.put(hash, value);
      pendingKeys.push(value);
      
    }
    else if (field.equals("value"))
    {
      //retreive previous key for this object and use it to store value
      //System.out.println("Putting "+value+" into "+hash+" pending keys are "+pendingKeys);
      try{
        hash.put(pendingKeys.pop(), value);
      } catch (NullPointerException e){
        TheLog.println("Warning, null value received for hashatable entry" );
      }
      
      
      //hash.put(pendingKeys.get(hash), value);
      //pendingKeys.remove(hash);
    } // end of else
    
  }

  //Hashtable pendingKeys = new Hashtable();
  Stack pendingKeys = new Stack();
  
  //set 'obj' from the string 'data'
  public Object setTo(Object obj, String data) throws org.xml.sax.SAXException
  {
    throw new org.xml.sax.SAXException("XMLHashtableReader::setTo should never get called");
    //this shouodn't get called for a hashtable
  }
  
  

}



