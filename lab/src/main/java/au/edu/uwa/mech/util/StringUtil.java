
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: StringUtil.java,v 1.2 2000/01/28 11:20:54 bdalton Exp $
// Time-stamp: "2000-01-28 14:03:10 barney"

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

public class StringUtil
{
  public static String multiply(String s, int n)
  {
    StringBuffer sb=new StringBuffer(s.length()*n);
    for (int i=0;i<n;i++)
      sb.append(s);

    return sb.toString();
  }
  
  public static String removeChar(String s, char c) {
    StringBuffer r = new StringBuffer( s.length() );
    r.setLength( s.length() ); 
    int current = 0;
    for (int i = 0; i < s.length(); i ++) {
      char cur = s.charAt(i);
      if (cur != c) r.setCharAt( current++, cur );
    }
    return r.toString();
  }

   public static String replaceCharAt(String s, int pos, char c) {
     StringBuffer buf = new StringBuffer( s );
     buf.setCharAt( pos, c );
     return buf.toString( );    
   }

  public static String removeCharAt(String s, int pos) {
    StringBuffer buf = new StringBuffer( s.length() - 1 );
    buf.append( s.substring(0,pos) ).append( s.substring(pos+1) );
    return buf.toString();
  }


}



