
// Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Sun Sep 12 17:05:29 1999
// Version       : $Id: SetMainClass.java,v 1.1 1999/09/12 17:51:39 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

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

package au.edu.uwa.mech.util;

public class SetMainClass
{
  public static void main (String[] args)
  {
    try{
      
      Jar.setMainClass(args[0], args[1]);


    } catch (ArrayIndexOutOfBoundsException e){
      System.out.println("Usage : java au.edu.uwa.mech.util.SetMainClass jarfilename mainclassname");
    } catch (java.io.IOException e){
      e.printStackTrace();
    }
    
  } // end of main ()
}



