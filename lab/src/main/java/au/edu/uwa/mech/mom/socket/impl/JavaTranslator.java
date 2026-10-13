//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: JavaTranslator.java,v 1.3 2000/01/18 17:48:04 bdalton Exp $ 
// Time-stamp: "2000-01-18 13:42:02 barney"

// {{{ GNU General Public License
//  This program is free software; you can redistribute it and/or
//  modify it under the terms of the GNU General Public License
//  as published by the Free Software Foundation; either version 2
//  of the License, or (at your option) any later version.

//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
// }}}



package au.edu.uwa.mech.mom.socket.impl;

import java.io.*;

public class JavaTranslator implements Translator 
{
  public void writeObject(Object obj, OutputStream out)
    throws IOException, ConversionException  
  {
    synchronized(out){
      ObjectOutputStream oo = new ObjectOutputStream(out);
      //System.out.println("writing "+obj+" to"+out);
      oo.writeObject(obj);
    }
  }
  
  
  public Object readObject(InputStream i) throws
  IOException, ConversionException
  {
    try{
      int trys=0;
      //Object o=null;
      while(true){
        try{
          ObjectInputStream oi = new ObjectInputStream(i);
          return oi.readObject();
        } catch (StreamCorruptedException e) {
          if (++trys>2)
            throw e;
          else
            System.err.println(e.getMessage());
        }
      }
    } catch (ClassNotFoundException e){
      throw new ConversionException(e.getMessage());
    }
    
     
  }
  
}




