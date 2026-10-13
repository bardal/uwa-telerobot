
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLReader.java,v 1.6 1999/06/28 12:47:21 bdalton Exp $
// Time-stamp: "1999-06-28 19:37:56 barney"

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

import org.xml.sax.HandlerBase;
import org.xml.sax.AttributeList;
import org.xml.sax.Parser;
import org.xml.sax.DocumentHandler;
import org.xml.sax.helpers.ParserFactory;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

import java.io.Reader;
import java.io.IOException;
import java.util.Stack;
import java.util.Hashtable;



public class XMLReader extends HandlerBase implements XMLConstants
{
  static final String parserClass = "com.microstar.xml.SAXDriver";

  public XMLReader() throws XMLSetupException
  {
    try{
      parser = ParserFactory.makeParser(parserClass);
      parser.setDocumentHandler(this);
      registry=new Hashtable();
    } catch (ClassNotFoundException e){
      throw new XMLSetupException ("Failed to setup SAX parser : "+e);
    } catch (IllegalAccessException e){
      throw new XMLSetupException ("Failed to setup SAX parser : "+e);
    } catch (InstantiationException e){
      throw new XMLSetupException ("Failed to setup SAX parser : "+e);
    }
           
    
      
  }
  

  synchronized public Object readObject(Reader reader)
    throws IOException, XMLConversionException
  {
    try{
      InputSource input= new InputSource(reader);
      parser.parse (input);
      return getParsedObject();
    } catch (SAXException e){
      throw new XMLConversionException("Error reading Object : "+e);
    }
    
  }
  

  public void startDocument()
  {
    objects=new Stack();
    objectReaders=new Stack();

    currentObjectReader=null;
    parentObjectReader=null;

    currentObject=null;
    parentObject=null;
  }
  

  // may be easier to save details up from startElement and characters
  // and instantiate the object in end tag - otherwise creatig
  // immutable objects like strings is a problem. This makes setting
  // fields in the parent a lot harder as the parent won't yet exist
  // ??. The other alternative is to get the StringReader to use a
  // stringBuffer and have soem kind of endRead callback that returns
  // a reference to the final string.

  public void startElement (String name, AttributeList atts) throws SAXException
  {
    // remember parent
    
    parentObject=currentObject;
    parentObjectReader=currentObjectReader;
    
    // push currentObject onto the stack 
    objects.push(currentObject);
    // push the reader on the objectReader stack too
    objectReaders.push(currentObjectReader);


    
    // lookup the reader that deals with this type
    currentObjectReader = getObjectReaderFor(atts.getValue(TYPELABEL));
    if (currentObjectReader == null)
      throw new SAXException("No Reader for xml type "+atts.getValue(TYPELABEL));

    // maybe we should ask the parent for a reference to the field
    // before trying to create one ourselves. On second thoughts we
    // need to pass an instance of the object to the readers set function. 


    
    // create default constructor instance here
    currentObject = currentObjectReader.newInstance();
   
    
    // Object currentObject=getXMLObjectReaderFor(atts.getValue(TYPELABEL))
    
    // get type of element
    // Class type = typeRegistry.get(atts.getValue(TYPELABEL));
    // create instance
    // currentObject=type.newInstance();
    // add it to the stack of objects

    
    
    // don't be too generic just get something that works
    // need t create the right type of Packet
    // PeerEvent, ContectEvent, MessageEvent, TokenEvent
    // Is there any point int having these derived types?
    // Just include extra fields that do the same thing
    // we don't add any functionality in the derivation?
    // Event, Command, Result?
    // if (name.equals(PACKET))
    //  packet=new Packet();
    
    // get current object off stack
    // get type from attributes
    // create new instance of type
    // add
    // call its 
    debug("Start element: " + name);
  }

  public void characters(char ch[], int start, int length) throws SAXException 
  {
    String data = new String(ch, start, length);
    // set current object to data
    // this may return a new instance in the case of immutable objects
    // such as strings. Other readers will probably return the
    // reference they are passed 
    currentObject=currentObjectReader.setTo(currentObject, data);
  }
  
  public void endElement (String name) throws SAXException
  {
    try{

      if (parentObject == null)
        parsedObject = currentObject; // must be the top level object
      else
      {
        // set field in parent using its reader
        parentObjectReader.setField(name, currentObject, parentObject);
      }
      
      // go back to previous object
      currentObject=objects.pop();
      // and find its reader
      currentObjectReader=(XMLObjectReader)objectReaders.pop();

      // get parent - this will produce an exception for the root
      // object, but we ignore it
      try{
        parentObject=objects.peek();
        parentObjectReader=(XMLObjectReader)objectReaders.peek(); 
      } catch (java.util.EmptyStackException e) {
        debug("Empty Object stack in XMLReader::endElement hopefully this is the last element");
      } 
    
      debug("End element: " + name);
    } catch (Exception e){
      e.printStackTrace();
      throw new SAXException(e.toString());
    } 
  }

  public void endDocument()
  {
    debug("End document");
    debug(parsedObject);
  }

  // maybe should have a class wide registry and instance specific
  // registry. Keep the parser class hmmm probably can't do
  // that. bugger want to aviod crating a parser for each thread but
  // maybe you ahve ot do that.
  
  public void setObjectReaderFor(String xmltype, XMLObjectReader reader) {
    registry.put(xmltype, reader);
  }
  
  public Object getParsedObject() {return parsedObject;}
  
  protected XMLObjectReader getObjectReaderFor(String xmltype) {
    return (XMLObjectReader)registry.get(xmltype);
  }
  
  
  Stack objects;
  Stack objectReaders;

  Hashtable registry;
  
  
  XMLObjectReader currentObjectReader;
  XMLObjectReader parentObjectReader;

  Object currentObject;
  Object parentObject;
  Object parsedObject;

  Parser parser;
  
  static protected boolean debug=false;
  static protected void debug(String s)
  {
    if (debug)
      System.err.println(s);
  }

  static protected void debug(Object o)
  {
    if (debug)
      System.err.println(o);
  }
  
  public static void main (String args[])
    throws Exception
  {
    Parser parser = ParserFactory.makeParser(parserClass);
    //DocumentHandler handler = new PacketReader();
    //parser.setDocumentHandler(handler);

    //Reader in=new FileReader("packet.xml");
    //ByteArrayInputStream in=new ByteArrayInputStream(
    //for (int i = 0; i < args.length; i++) {
    //  parser.parse(args[i]);
    //}
  }

  
  
}



