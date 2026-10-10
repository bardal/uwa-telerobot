
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TheConsole.java,v 1.4 2000/01/12 15:15:36 bdalton Exp $
// Time-stamp: "2000-01-12 17:48:25 barney"

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

package au.edu.uwa.mech.gui;

public class TheConsole {

  static boolean set=false;
  static Console theConsole;
  public static void setConsole(Console newConsole){
    theConsole =  newConsole;
    set=true;
  }

  //these are supposed to be user freindly messages, ie no mention of
  //exception type
  protected static String eString="Exception";

  public static void println(Object stuff){
    println(stuff.toString());
  }
  
  
  public static void println(String text){
    //cut out any exception stuff
    if(set){
      
      int li=0;
      if ((li=text.lastIndexOf(eString))>-1)
        text = text.substring(li+eString.length());
      
      if(text.charAt(0) == ':')
        text=text.substring(1);

      text=text.trim();
      
      theConsole.println(text);
    } else
      System.err.println(text);
    
  }
  

}
