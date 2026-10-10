
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLStringReader.java,v 1.2 1999/06/21 06:18:16 bdalton Exp $
// Time-stamp: "1999-06-21 10:25:54 barney"

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

//hmmm strings are immutable

public class XMLStringReader implements XMLObjectReader
{
  //dont't bother creating anything yet - wait till we have the
  //character data
  public Object newInstance()
  {
    return null;
  }
  

  //set field named 'name' to 'value' in 'parent'
  //how the hell will this work for Strings?
  public void setField(String field, Object value, Object container)
    throws SAXException
  {
    //shouldn't get called for string
    throw new SAXException("XMLStringReader::setField String can't contain any fields");
  }
  
  //set 'obj' from the string 'data'
  public Object setTo(Object obj, String data)
  {
    return data;

    // the above hopefully addresses comments below... 
    
    // would prefer to atcually instantiate the string here
    // maybe we could at least return a new reference here,
    // but what happens about the object stack int the XMLReader, it
    // will contain the wrong refernce. Safest option is just to do a
    // copy - seems like we have to make data a char[] in order to
    // copy a string!
   
    
  }
  
  

}



