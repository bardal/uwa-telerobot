
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MIMETranslator.java,v 1.8 2000/01/13 13:08:15 bdalton Exp $
// Time-stamp: "2000-01-13 20:40:57 barney"

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

package au.edu.uwa.mech.mom.socket.impl;

import java.io.Writer;
import java.io.Reader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.StreamTokenizer;
import java.io.IOException;
import java.io.CharArrayReader;
import java.io.PushbackReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PushbackInputStream;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;

import au.edu.uwa.mech.xml.*;

import au.edu.uwa.mech.mom.xml.PacketWriter;
import au.edu.uwa.mech.mom.xml.PacketReader;
import au.edu.uwa.mech.mom.Packet;
import au.edu.uwa.mech.io.DelimiterReader;
import au.edu.uwa.mech.io.DelimiterInputStream;
import au.edu.uwa.mech.io.ioUtil;
import au.edu.uwa.mech.util.TheLog;

//need to cache translated objects 

//VERY QUICK AND DIRRRTY!!!

// write / read class?

//needs a rethink

public class MIMETranslator implements Translator, BinaryHandler
{

  //maybe we should set the input/output stream at construction
  // should this class be split into two reader/writer ?

  
  public MIMETranslator()
  {
    xmlWriter = new XMLWriter();
    // register classes/types
    xmlWriter.setObjectWriterFor(au.edu.uwa.mech.mom.Packet.class, new PacketWriter());
    xmlWriter.setObjectWriterFor(au.edu.uwa.mech.mom.ContextURL.class, new XMLToStringWriter("context"));
    xmlWriter.setObjectWriterFor(au.edu.uwa.mech.mom.Peer.class, new XMLToStringWriter("peer"));
    xmlWriter.setObjectWriterFor(java.lang.String.class, new XMLStringWriter());
    xmlWriter.setObjectWriterFor(java.lang.Long.class, new XMLToStringWriter("long"));
    xmlWriter.setObjectWriterFor(java.lang.Integer.class, new XMLToStringWriter("int"));
    xmlWriter.setObjectWriterFor(java.lang.Boolean.class, new XMLToStringWriter("boolean"));
    xmlWriter.setObjectWriterFor(java.util.Hashtable.class, new XMLHashtableWriter());
    xmlWriter.setObjectWriterFor(java.util.Vector.class, new XMLVectorWriter());
    xmlWriter.setObjectWriterFor(char[].class, new XMLBinaryWriter("chars", this));
    xmlWriter.setObjectWriterFor(byte[].class, new XMLBinaryWriter("bytes", this));
    // register for binary encode callbacks

    try{
      
      xmlReader = new XMLReader();
      xmlReader.setObjectReaderFor("packet", new PacketReader());
      xmlReader.setObjectReaderFor("hashtable", new XMLHashtableReader());
      xmlReader.setObjectReaderFor("list", new XMLVectorReader());
      xmlReader.setObjectReaderFor("string", new XMLStringReader());
      xmlReader.setObjectReaderFor("long", new XMLFromStringReader(java.lang.Long.class));
      xmlReader.setObjectReaderFor("int", new XMLFromStringReader(java.lang.Integer.class));
      xmlReader.setObjectReaderFor("boolean", new XMLFromStringReader(java.lang.Boolean.class));
      xmlReader.setObjectReaderFor("context", new XMLFromStringReader(au.edu.uwa.mech.mom.ContextURL.class));
      xmlReader.setObjectReaderFor("peer", new XMLFromStringReader(au.edu.uwa.mech.mom.Peer.class, "setNickname"));
      xmlReader.setObjectReaderFor("chars", new XMLBinaryReader(char[].class, this));
      xmlReader.setObjectReaderFor("bytes", new XMLBinaryReader(byte[].class, this));
      
      
    } catch (XMLSetupException e){
      TheLog.println("BIG PROBLEM!!! error in MIMETranslator::MIMETranslator");
      e.printStackTrace();
    }
    
    
  }

  // synchronized? - bit of cop out
  // public void writeObject(Object obj, Writer writer)
  public void writeObject(Object obj, OutputStream out)
    throws java.io.IOException, ConversionException
  {
    try{
      
    //TheLog.println("MIMETranslator.writeObject writing "+obj+" to "+out);
    
    //synchronized(writer){
    synchronized(out){

      Writer writer = new OutputStreamWriter(out, ENCODING);

      writer.write(CTYPEH+": multipart/related; boundary=");
      writer.write(BOUNDARY);
      writer.write(BREAK);
      
      //CharArrayOutputStream
      //String xmlBit=
      writer.write(BOUNDARY+NEWLINE);
      writer.write(CTYPEH+": text/xml"+NEWLINE);
      writer.write(CIDH+": object"+NEWLINE);
      writer.write(NEWLINE);
      writer.write(XMLHEADER);

      //synchonize on the xmlwriter so that other threads odn't
      //generate binary attachements for us - got to be a better way

      //should create a new object for each encoding or pass a storage
      //object to xmlwriter
      //xmlwriter should have a reistry object, then create temporary
      //encoders for each encoding
      //use objects on the stack to keep stuff apart

      synchronized(xmlWriter){
        binaryAttachments=new Vector();
        writer.write(xmlWriter.toXML("object", obj));
        writer.write(BREAK);
        writer.write(BOUNDARY);

        for (java.util.Enumeration e=binaryAttachments.elements();e.hasMoreElements();)
        {
          Attachment a=(Attachment)e.nextElement();
          //byte[] data=(byte[])(binaryAttachments.get(key));
          //depends on type here change header for char or byte
          //easier if we put the type in the XML
          writer.write(NEWLINE+CTYPEH+": application/unknown"+NEWLINE);
          writer.write(CIDH+": "+a.key+NEWLINE);
          writer.write(CLENGTHH+": "+a.data.length+NEWLINE);
          //writer.write("Content-Length: "+object"+NEWLINE);
          writer.write(NEWLINE);
          writer.flush();
          out.write(a.data);
          writer.write(BOUNDARY);
        }
      }
      
      writer.flush();
      //TheLog.println("finished MIMETranslator.writeObject");
      
    }
    } catch (XMLConversionException e){
      throw new ConversionException(e.getMessage());
    }
    
    
    //Add Headers to xmlBit
    //String xmlAsMIME = toMIMEPart(xmlBit, "   
    //CharArrayOutputStream.write(
  }


  
  static int binaryID=0;    
  //should be writing bytes here not char
  public String encode(Object binaryStuff) throws XMLConversionException
  {

    try{
      //generate a name for the stuff
      String name = CID+":body"+(++binaryID % Integer.MAX_VALUE);

      //add it to list of attachments
      if (binaryStuff instanceof byte[])
        binaryAttachments.addElement(new Attachment(name, (byte[])binaryStuff));
      else if (binaryStuff instanceof char[])
        binaryAttachments.addElement(new Attachment(name, ioUtil.CharToByte((char [])binaryStuff, ENCODING)));
      //return name
      return name;
    } catch (java.io.UnsupportedEncodingException e){
      e.printStackTrace();
      throw new XMLConversionException (e.toString());
    } catch (IOException e){
      e.printStackTrace();
      throw new XMLConversionException (e.toString());
    }
  }


  //should be reading bytes
  public Object decode(Class theClass, String characters) throws XMLConversionException
  {
    //should use characters to look at the binary ID we'return
    //interested int
    
    //debug ("Implement DECODE");
    // try and read the next part of the MIME message
    try{
      ByteArrayInputStream bytesI = new ByteArrayInputStream(dis.nextToken());
      Hashtable headers=readMIMEHeaders(bytesI);
    
      int contentLength=Integer.parseInt(((String)headers.get(CLENGTHH)).trim());
      //debug("contentLength :"+contentLength);
      
      //debug("body headers "+readMIMEHeaders(chunkReader));
      //rest is binary data
      //read into a stringBuffer
      byte[] data = new byte[contentLength];
      bytesI.read(data);

      if (theClass == byte[].class)
        return data;
      else
        return ioUtil.ByteToChar(data, ENCODING);
      
    } catch (IOException e) {
      throw new XMLConversionException("IO error reading part of MIME multipart/related body : "+e.getMessage());
    } catch(NullPointerException e){
      throw new XMLConversionException("No Content-Length specified for binary attachment ");
    }
  }
  

  //DelimiterReader dr;
  DelimiterInputStream dis;
  
  //NOT THREAD SAFE - what if multiple threads call this with
  //different readers?
  //simple fix - synchronize on a readLock - but this is inefficient
  //static ?
  // readers  or imputstreams?

  // should only return null if eof is reached, otherwise should try
  // and read the next valid object

  public Object readObject(InputStream i) throws java.io.IOException
  {
    synchronized(i) {

      //can't use this as it eats the whole stream leaving nothing
      //left for the inputstream
      //Reader reader = new InputStreamReader (i, ENCODING);
      
      Object packet=null;
      while(packet == null)     // keep reading till we find a valid packet
      {
        Hashtable headers=readMIMEHeaders(i);
        //debug("mime headers "+headers);
                                // find mime type of message

                                // if its multipart/related then find
                                // first section 
        //dr=new DelimiterReader(reader, BOUNDARY);
        
        dis= new DelimiterInputStream(i, BOUNDARY.getBytes(ENCODING));
        
        byte[] fp=dis.nextToken(); //reads to first boundary after headers

        //debug("first boundary stuff !"+new String(fp)+"!"+dis.hasMoreTokens());
        
        if(dis.hasMoreTokens())
        {
          fp=dis.nextToken();    // the first lot of data
        
          //debug("first part \n>>"+new String(fp)+"<<\n");
          InputStream firstPart=new ByteArrayInputStream(fp);

          readMIMEHeaders(firstPart); // ignore the headers and move
                                // on to the interesting stuff


          // if its xml and we assume it is cos I'm lazy
          try{
            synchronized (xmlReader) {
              packet=xmlReader.readObject(new InputStreamReader(firstPart, ENCODING));
            }
          } catch (XMLConversionException e) {
            TheLog.println("ERROR reading object from XML - will try again");
            e.printStackTrace();
          }
        }
        else
        {
          debug("eof in MIMETranslator::readObject returning null");
          break;
        }
        if(packet == null)
          TheLog.println("WARNING: Read in a null packet");
      }
      return packet;
    }
  }

  // MIME class maybe
  // this is very quick and dirty
  protected Hashtable readMIMEHeaders(InputStream i) throws IOException
  {
    Hashtable headers=new Hashtable();
    
    byte[] header;

    PushbackInputStream pbi = new PushbackInputStream(i, 2);
    ioUtil.eatWhitespace(pbi);

    //TheLog.println("Eaten whitespace");
    //eat whitespace
    //char chr;
    //ioUtil.eatWhitespace(dr);
    DelimiterInputStream disHeaders=new DelimiterInputStream(pbi, NEWLINE.getBytes(ENCODING));
    
    while (!((header=disHeaders.nextToken()).length==0))
    {
      // what about multiline headers?
      StringTokenizer tokenizer= new StringTokenizer(new String(header, ENCODING), ":");
      headers.put(tokenizer.nextToken(), tokenizer.nextToken());
    }
    return headers;
  }
  

  XMLWriter xmlWriter;
  XMLReader xmlReader;
  Vector binaryAttachments;
  
  static final String CTYPEH="Content-Type";
  static final String CIDH="Content-ID";
  static final String CLENGTHH="Content-Length";
  static final String XMLHEADER="<?xml version=\"1.0\" ?>";
  static final String BOUNDARY="xxxxBOUNDARYxxxx";
  static final String NEWLINE="\r\n";
  static final String BREAK="\r\n\r\n";
  // string used to reference other ContentIDs 
  static final String CID="cid";

  static final String ENCODING="UTF8";

  class Attachment{
    String key;
    byte[] data;
    Attachment(String key, byte[] data)
    {
      this.key=key;
      this.data=data;
    }
    
  }
  

  
  public static void main (String[] args)
  {
    for (int i=0;i<10;i++)
    {
    
    //PacketWriter pw=new PacketWriter();
    Packet packet=new Packet();
    //packet.setContextURL(new au.edu.uwa.mech.mom.ContextURL("telerobot"));
    try{
      packet.setProperty("humpher", "hasty");
      java.util.Vector To=new java.util.Vector();
      To.addElement("barney");
      To.addElement("farrow");
      To.addElement(new au.edu.uwa.mech.mom.Peer("brian", "bummer"));
      packet.setProperty("to", To);
      //char[] stuff = new char[]{'%','<','a', '[', '$'};
      byte[] stuff = new byte[]{20, 40, 60, 34, 56, 100 };
      
      packet.setProperty("buggerup", stuff);
      
      //debug("Packet before SEND:\n"+packet);
      
      
    } catch (au.edu.uwa.mech.mom.MOMReadOnlyException e) {
      e.printStackTrace();
    }
    
    try{
      MIMETranslator mimet=new MIMETranslator();
      java.io.ByteArrayOutputStream out= new java.io.ByteArrayOutputStream();
      mimet.writeObject(packet, out);
      TheLog.println(out.toString());

      java.io.ByteArrayInputStream in = new
        java.io.ByteArrayInputStream(out.toByteArray());

      TheLog.println("\n\n\n");
      
      
      Object o=mimet.readObject(in);
      TheLog.println(o);
      

      
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    
       
    } // end of for (int i=0;i<50;i++)
    
    
  } // end of main ()


  
  static protected boolean debug=true;
  static protected void debug(String s)
  {
    if (debug)
      System.err.println(s);
  }

}




