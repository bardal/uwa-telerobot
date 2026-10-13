
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLVectorReader.java,v 1.1 1999/06/21 06:18:16 bdalton Exp $
// Time-stamp: "1999-06-21 10:38:44 barney"

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

import java.util.Vector;

public class XMLVectorReader implements XMLObjectReader
{
  //return a new instance of the class we are registered to read
  public Object newInstance()
  {
    return new Vector();
  }
  

  //set field named 'name' to 'value' in 'parent'
  //how the hell will this work for Hashtables?
  public void setField(String field, Object value, Object container)
  {

    Vector vec= (Vector)container;
    if (field.equals("value"))
    {
      //retreive previous key for this object and use it to store value
      vec.addElement(value);
    } // else unrecognized element (need an exception for this sort of
    // problem 
    
  }
  
  //set 'obj' from the string 'data'
  public Object setTo(Object obj, String data) throws org.xml.sax.SAXException
  {
    //need a specific subtye of SAXException for these formatting errors
    throw new org.xml.sax.SAXException("XMLVectorReader::setTo should never get called");
    //this shouodn't get called for a vector
  }
  
  

}



