//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: FastDataHandler.java,v 1.1 2000/01/17 14:12:39 bdalton Exp $ 
// Time-stamp: "2000-01-17 17:47:50 barney"

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




package au.edu.uwa.mech.net.tests;

import java.net.*;
import java.io.*;
import java.util.*;

import au.edu.uwa.mech.util.*;
import au.edu.uwa.mech.net.IOHandler;

public class FastDataHandler implements IOHandler
{
  public void doIO(InputStream is,OutputStream os) throws IOException
  {
    int size = 2000;
    long sent =0;
    long startTime = System.currentTimeMillis();
    TimeStamp stamp = new TimeStamp();
    while(true){
      stamp.stamp("before send");
      os.write(new byte[size]);
      stamp.stamp("after write");
      os.flush();
      stamp.stamp("after flush");
      sent+=size;

      long now = System.currentTimeMillis();
      long timeSecs = (now-startTime) /1000;
      if (timeSecs >0)
        System.out.println("data rate "+(sent/timeSecs)+ "bytes/sec");
      
    }
    
  }
  
}

