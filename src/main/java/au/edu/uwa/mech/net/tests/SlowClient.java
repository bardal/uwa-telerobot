//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SlowClient.java,v 1.1 2000/01/17 14:12:39 bdalton Exp $ 
// Time-stamp: "2000-01-17 15:22:23 barney"

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

import java.net.*;
import java.io.*;
import java.util.*;
import au.edu.uwa.mech.util.*;

class SlowClient
{
  public static void main (String[] args) throws Exception
  {
    FileLog fl = new FileLog("slow.log");
    TheLog.setLog(fl);
      
    Socket s = new Socket(args[0], Integer.parseInt(args[1]));
    InputStream i = s.getInputStream();

    while(true){
      byte [] ib = new byte[2000];
      System.out.println("read in" +i.read(ib)+ " bytes");
      Thread.currentThread().sleep(1000);
    }
    
  } // end of main ()
  
}
