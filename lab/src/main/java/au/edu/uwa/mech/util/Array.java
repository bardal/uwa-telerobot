
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Array.java,v 1.1 1999/08/27 22:30:14 bdalton Exp $
// Time-stamp: "1999-08-26 20:47:13 student"

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

package au.edu.uwa.mech.util;

public class Array
{
  public static boolean equal(byte[] one, byte[] two)
  {
    if ((one == null) || (two == null))
      return false;

    if(one.length != two.length)
      return false;

    for (int i=0; i< one.length; i++)
      if (one[i] != two[i])
        return false;

    return true;
  }

  public static int indexOf(byte[] bs, byte b)
  {
    for (int i=0;i<bs.length;i++)
      if (bs[i]==b)
        return i;
    return -1;
               
  }
  
  public static void main (String[] args)
  {

      byte one[]= {1, 2, 3};
      byte two[]= {1,2,3};
      byte three[]=one;
      
      System.out.println(Array.equal(one, two));
      System.out.println(Array.indexOf(one, (byte)1));
      System.out.println(Array.indexOf(one, (byte)4));
             
      
      
  } // end of main ()
  

}



