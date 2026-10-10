
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: BinaryHandler.java,v 1.3 1999/06/24 12:17:26 bdalton Exp $
// Time-stamp: "1999-06-24 08:49:09 barney"

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

public interface BinaryHandler
{
  //binary Object would typically be byte[] or char[], but could be
  //anything you want to include as binary instead of as XML

  //encode Object as string
  public String encode(Object binary) throws XMLConversionException;
  //deocde characters into something of class 'theClass'
  public Object decode(Class theClass, String characters) throws XMLConversionException;
}



