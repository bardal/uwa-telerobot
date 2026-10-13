
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLWriter.java,v 1.4 1999/06/22 10:23:33 bdalton Exp $
// Time-stamp: "1999-06-22 10:04:13 barney"

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

public class XMLWriter implements XMLConstants
{
  public String toXML(String name, Object obj) throws XMLConversionException
  {
    //try{
      XMLObjectWriter writer = getObjectWriterFor(obj.getClass());
      debug("XMLWriter::toXML writer "+writer);
      if (writer==null)
        throw new XMLConversionException ("No writer registered for type "+obj.getClass().getName());
      return writer.toXML(name, obj, this);
      //} catch(java.lang.NullPointerException e) {//
      //e.printStackTrace();
      //throw new XMLConversionException

      //}
    

  }

  //arrays are objects so the above function will always be called
//    public String toXML(byte[] bytes) throws XMLConversionException
//    {
//      //handle CID stuff here
//      return "cid:unhandled";
//    }

  
  protected Hashtable registry=new Hashtable();
  
  public XMLObjectWriter getObjectWriterFor(Class theClass)
  {
    debug("getObjectWriterFor "+theClass);
    debug("from registry "+registry.get(theClass));
    return (XMLObjectWriter)registry.get(theClass);
  }

  public void setObjectWriterFor(Class theClass, XMLObjectWriter writer)
  {
    debug("setObjectWriterFor "+theClass+" writer "+writer);
    registry.put(theClass, writer);
  }

    
  public static String writeTag(String tag, String type, String contents) throws XMLConversionException
  {
    java.io.CharArrayWriter out=new java.io.CharArrayWriter();

    try{
      out.write(startTag(tag, type));
      out.write(contents);
      out.write(endTag(tag));
      return out.toString();
    } catch (java.io.IOException e) {
      throw new XMLConversionException("IOException in writeTag");
    }
    
  }

  public static String startTag(String tag, String type) throws XMLConversionException
  {
    try{
      java.io.CharArrayWriter out=new java.io.CharArrayWriter();
      out.write("<"+tag);
      if (type != null)
        out.write(" "+TYPELABEL+"=\""+type+"\"");
      out.write(">");
      return out.toString();
    } catch (java.io.IOException e) {
      throw new XMLConversionException("IOException in startTag");
    }
  }
  
  public static String endTag(String tag) throws XMLConversionException
  {
     try{
      java.io.CharArrayWriter out=new java.io.CharArrayWriter();
      out.write("</"+tag+">");
      return out.toString();
     } catch (java.io.IOException e) {
       throw new XMLConversionException("IOException in endTag");
    }
  }
  
  
  static protected boolean debug=false;
  static protected void debug(String s)
  {
    if (debug)
      System.err.println(s);
  }
  
  public static void main (String[] args)
  {
    
    XMLWriter writer=new XMLWriter();
  } // end of main ()
  
}

  //protected 
  



