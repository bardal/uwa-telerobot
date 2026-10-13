
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLHashtableWriter.java,v 1.1 1999/06/17 13:19:38 bdalton Exp $
// Time-stamp: "1999-06-17 12:20:23 barney"

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

import java.util.Hashtable;
import java.io.CharArrayWriter;
import java.io.IOException;

public class XMLHashtableWriter implements XMLObjectWriter
{
  public String toXML(String name, Object obj, XMLWriter writer)
    throws XMLConversionException
  {
    try{
      Hashtable hash=(Hashtable)obj;
      return writer.writeTag(name, "hashtable", contentsASXML(hash,writer));
    } catch(IOException e) {
      throw new XMLConversionException
        ("IOException writing contents of Hashtable");
      
    }
    
      
  }

  protected String contentsASXML(Hashtable hash,  XMLWriter writer)
    throws IOException, XMLConversionException
  {
    CharArrayWriter out=new CharArrayWriter();
    for (java.util.Enumeration e=hash.keys();e.hasMoreElements();)
    {
      String key=(String)e.nextElement();
      Object value=hash.get(key);
      out.write(writer.toXML("key", key)); //use StringObjectWriter
      out.write(writer.toXML("value", value));
      //out.write("</"+key+">");
      
    }
    return out.toString();
  }
  

}



