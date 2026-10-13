
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: XMLFromStringReader.java,v 1.2 1999/06/23 11:15:59 bdalton Exp $
// Time-stamp: "1999-06-23 19:02:04 barney"

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

public class XMLFromStringReader implements XMLObjectReader
{
  protected static Class[] params= new Class[] {String.class};

  protected static final int USE_NONE=0;
  protected static final int USE_CONSTRUCTOR=1;
  protected static final int USE_SET=2;


  int                           setMethod  = USE_NONE;
  Class                         theClass;
  java.lang.reflect.Method      method;
  java.lang.reflect.Constructor constructor;
  
  // could have another contructor that takes the name of the setMethod
  // to use
  // setMethodName must be the name of a method that takes a single
  // String as an argument
  public XMLFromStringReader(Class theClass, String setMethodName) throws XMLSetupException
  {
    this.theClass=theClass;
    try{
      method = theClass.getMethod(setMethodName, params);
      setMethod =USE_SET;
    } catch (NoSuchMethodException e) {
      throw new XMLSetupException("No suitable methods named "+setMethodName+" in "+
                                       theClass + " for XMLFromStringReader to use");
    }
    
    
    
    //else
    //  throw new XMLConversionException("No suitable methods named "+setMethodName+" in "+
    //                                  theClass + " for XMLFromStringReader to use");

  }
  

  
  public XMLFromStringReader(Class theClass) throws XMLSetupException
  {
    this.theClass=theClass;
    //look for a constructor that takes a string as an argument
    try{
      constructor = theClass.getConstructor(params);
      setMethod=USE_CONSTRUCTOR;
    } catch (NoSuchMethodException e) {
      throw new XMLSetupException("No suitable contructors or methods in "+
                                       theClass +" for XMLFromStringReader to use");
    }
    
      

    

  }
  
  
  //dont't bother creating anything yet - wait till we have the
  //character data
  public Object newInstance() throws SAXException
  {
    if (setMethod == USE_SET)
    {
      
      try {
        return theClass.newInstance();
      } catch (java.lang.InstantiationException e) {
        throw new SAXException("Exception creating newInstance of "+
                                theClass +" Does it have a default constructor? Details :" + e.toString());
      } catch (java.lang.IllegalAccessException e) {
         throw new SAXException("Exception creating newInstance of "+
                                theClass +" Details :" + e.toString());
      } catch (java.lang.IllegalArgumentException e) {
         throw new SAXException("Exception creating newInstance of "+
                                theClass +" Details :" + e.toString());
      }
    
         //} catch (java.lang.InvocationTargetException e) {
         //throw new SAXException("Exception creating newInstance of "+
         //                       theClass +" Details :" + e.toString());
    }
    else // we're using the constructor to set/create at the same time  so wait till setTo is called 
      return null;
  }

  //set field named 'name' to 'value' in 'parent'
  //how the hell will this work for Strings?
  public void setField(String field, Object value, Object container)
    throws SAXException
  { 
    //shouldn't get called for string
    throw new SAXException("XMLFromStringReader::setField String can't contain any fields");
  }
  
  //set 'obj' from the string 'data'
  public Object setTo(Object obj, String data) throws SAXException
  {
    switch (setMethod)
    {
      case USE_CONSTRUCTOR:
        try {
          return constructor.newInstance(new Object[] {data});
        } catch (java.lang.InstantiationException e) {
          throw new SAXException("Exception creating newInstance of "+
                                 theClass +" Details :" + e.toString());
        } catch (java.lang.IllegalAccessException e) {
          throw new SAXException("Exception creating newInstance of "+
                                 theClass +" Details :" + e.toString());
        } catch (java.lang.IllegalArgumentException e) {
          throw new SAXException("Exception creating newInstance of "+
                                 theClass +" Details :" + e.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
          throw new SAXException("Exception creating newInstance of "+
                                 theClass +" Details :" + e.toString());
        } 

        
      case USE_SET:
        try{
          method.invoke(obj, new Object[] {data});
          return obj;
        } catch (java.lang.IllegalAccessException e) {
          throw new SAXException("Exception creating newInstance of "+
                                 theClass +" Details :" + e.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
          throw new SAXException("Exception creating newInstance of "+
                                 theClass +" Details :" + e.toString());
        }
      default:
        throw new SAXException("No method for setTo to use for Class "+theClass);
    } // end of switch (setMethod)
    

  }
  

}



