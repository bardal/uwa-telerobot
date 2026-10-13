
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: PacketReader.java,v 1.4 1999/06/29 12:55:00 bdalton Exp $
// Time-stamp: "1999-06-29 19:53:55 barney"

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

package au.edu.uwa.mech.mom.xml;

import au.edu.uwa.mech.xml.XMLObjectReader;
import au.edu.uwa.mech.mom.Packet;



//for testing only
import org.xml.sax.*;
import org.xml.sax.helpers.ParserFactory;
import au.edu.uwa.mech.xml.XMLReader;
import au.edu.uwa.mech.xml.XMLHashtableReader;
import au.edu.uwa.mech.xml.XMLStringReader;
import au.edu.uwa.mech.xml.XMLVectorReader;
import au.edu.uwa.mech.xml.XMLFromStringReader;
import java.io.FileReader;

public class PacketReader implements XMLObjectReader 
{
  //return a new instance of the class we are registered to read
  public Object newInstance()
  {
    return new Packet();
  }
  

  //set field named 'name' to 'value' in 'container'
  //how the hell will this work for Hashtables?
  public void setField(String field, Object value, Object container)
  {
    Packet packet=(Packet)container;
    if (field.equals("properties"))
    {
      packet.setProperties((java.util.Hashtable)value);
    }
    
    
  }
  
  //set 'obj' from the string 'data'
  public Object setTo(Object obj, String data) throws SAXException
  {
    throw new  SAXException("PacketReader::setTo should never be called");
    //shouldn't be called for PacketReader 
  }
  
  
  
  public static void main (String argv[]) throws Exception
  {
    InputSource	input;

    if (argv.length != 1) {
      System.err.println ("Usage: cmd filename");
      System.exit (1);
    }
    try {
      // Turn the filename into an XML input source
      //input = Resolver.createInputSource (new File (argv [0]));
      FileReader filein=new FileReader(argv [0]);
      input = new InputSource(filein);
            
      // Get an instance of the non-validating parser.
      Parser parser;
      parser = ParserFactory.makeParser("com.microstar.xml.SAXDriver");
      XMLReader reader=new XMLReader();
      
      //put these in as properties in the future
      reader.setObjectReaderFor("packet", new PacketReader());
      reader.setObjectReaderFor("hashtable", new XMLStringReader());
      reader.setObjectReaderFor("hashtable", new XMLHashtableReader());
      reader.setObjectReaderFor("list", new XMLVectorReader());
      reader.setObjectReaderFor("string", new XMLStringReader());
      reader.setObjectReaderFor("context", new XMLFromStringReader(au.edu.uwa.mech.mom.ContextURL.class));
      reader.setObjectReaderFor("peer", new XMLFromStringReader(au.edu.uwa.mech.mom.Peer.class, "setNickname"));
      
      parser.setDocumentHandler (reader);
      // Parse the input
      parser.parse (input);

      Packet packet=(Packet)reader.getParsedObject();
      
      

    } catch (Throwable t) {
      t.printStackTrace ();
    }

    System.exit (0);
        
    //      DocumentHandler handler = new PacketReader();
    //      parser.setDocumentHandler(handler);

//      Reader in=new FileReader("packet.xml");
//      ByteArrayInputStream in=new ByteArrayInputStream(
    //for (int i = 0; i < args.length; i++) {
    //  parser.parse(args[i]);
    //}
  }

}




