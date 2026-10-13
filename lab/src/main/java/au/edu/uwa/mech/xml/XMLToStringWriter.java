
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLToStringWriter.java,v 1.1 1999/06/17 13:19:38 bdalton Exp $
// Time-stamp: "1999-06-17 11:47:29 barney"

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


//class to write any object as XML using its toString method
//contruct this object with the type of the Object you are writing

package au.edu.uwa.mech.xml;

public class XMLToStringWriter implements XMLObjectWriter
{
  public XMLToStringWriter(String type)
  {
    this.type=type;
  }
  
  
  public String toXML(String name, Object obj, XMLWriter writer) throws XMLConversionException
  {
    return writer.writeTag(name, type, obj.toString());
  }

  protected String type;
  
}



