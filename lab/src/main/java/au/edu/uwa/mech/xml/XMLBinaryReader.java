
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLBinaryReader.java,v 1.1 1999/06/24 12:17:26 bdalton Exp $
// Time-stamp: "1999-06-24 09:00:23 barney"

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

import org.xml.sax.SAXException;

public class XMLBinaryReader implements XMLObjectReader
{

  BinaryHandler handler;
  Class theClass;
  
  
  public XMLBinaryReader(Class theClass, BinaryHandler handler)
  {
    this.theClass=theClass;
    this.handler=handler;
    
  }
  

  //dont't bother creating anything yet - wait till we have the
  //character data
  public Object newInstance() {return null;}
  

  //set field named 'name' to 'value' in 'parent'
  //how the hell will this work for Strings?
  public void setField(String field, Object value, Object container)
    throws SAXException
  {throw new SAXException("XMLBinaryReader::setField binary can't contain any fields");}
  
  //set 'obj' from the string 'data'
  public Object setTo(Object obj, String data) throws SAXException
  {
    try{
      return handler.decode(theClass, data);
    } catch (XMLConversionException e) {
      throw new SAXException("Decoding binary data failed : "+e);
    }
    
  }
   


}



