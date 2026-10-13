
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: PacketWriter.java,v 1.10 1999/12/12 10:31:28 bdalton Exp $
// Time-stamp: "1999-12-12 12:22:25 barney"

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

import java.util.Hashtable;
import java.util.Vector;
import java.io.CharArrayWriter;
import java.util.Enumeration;


import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.xml.XMLWriter;
import au.edu.uwa.mech.xml.XMLConversionException;
import au.edu.uwa.mech.xml.XMLObjectWriter;
import au.edu.uwa.mech.xml.XMLStringWriter;
import au.edu.uwa.mech.xml.XMLToStringWriter;
import au.edu.uwa.mech.xml.XMLHashtableWriter;
import au.edu.uwa.mech.xml.XMLVectorWriter;
import au.edu.uwa.mech.mom.Packet;
import au.edu.uwa.mech.mom.MOMReadOnlyException;
import au.edu.uwa.mech.mom.Peer;



public class PacketWriter implements XMLObjectWriter
{

  public String toXML(String name, Object obj, XMLWriter writer) throws XMLConversionException
  {
    Packet packet=(Packet)obj;
    //Hashtable properties=packet.getProperties();

    try{
      CharArrayWriter out=new CharArrayWriter();
      //out.write("<"+name+" type=\"packet\">");
      out.write(writer.startTag(name, "packet"));
      out.write(writer.toXML("properties", packet.getProperties()));

      //body is now wtored in properties so don't worry about doing it seperately
      //how do we handle the byte array?
      //if (packet.getBody() !=null)
      //{
        //out.write("<Body>");
        //either encode the data in place or generate a cid - rfc 2378
      //out.write(writer.toXML("Body", packet.getBody()));
        //out.write("</Body");
        //out.write(packet.getBody());
      //}
      out.write(writer.endTag(name));
    
      return out.toString();
    } catch (java.io.IOException e){
      throw new XMLConversionException("IOException in PacketWriter.toXML()"); 
    } 
  }
  
  public static void main (String[] args)
  {
    try{
      //PacketWriter pw=new PacketWriter();
      Packet packet=new Packet();
      packet.setProperty("curl", new au.edu.uwa.mech.mom.ContextURL("telerobot"));

      packet.setProperty("humpher", "hasty");
      Vector To=new Vector();
      To.add("barney");
      To.add("farrow");
      To.add(new Peer("brian", "bummer"));
      packet.setProperty("to", To);

      
      XMLWriter writer=new XMLWriter();
      writer.setObjectWriterFor(au.edu.uwa.mech.mom.Packet.class, new PacketWriter());
      writer.setObjectWriterFor(au.edu.uwa.mech.mom.ContextURL.class, new XMLToStringWriter("context"));
      writer.setObjectWriterFor(au.edu.uwa.mech.mom.Peer.class, new XMLToStringWriter("peer"));
      writer.setObjectWriterFor(java.lang.String.class, new XMLStringWriter());
      writer.setObjectWriterFor(java.util.Hashtable.class, new XMLHashtableWriter());
      writer.setObjectWriterFor(java.util.Vector.class, new XMLVectorWriter());
      //writer.setObjectWriterFor(byte[].class, new XMLBinaryWriter());
          
      TheLog.println(writer.toXML("packet", packet));

    } catch (MOMReadOnlyException e) {
      e.printStackTrace();
    } catch (XMLConversionException e) {
      e.printStackTrace();
    }
    
      
    
  } // end of main ()
  

}



