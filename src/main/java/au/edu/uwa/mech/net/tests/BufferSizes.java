//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: BufferSizes.java,v 1.1 2000/01/30 14:53:32 bdalton Exp $ 
// Time-stamp: "2000-01-30 22:51:28 barney"

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

class BufferSizes{

  
  
  public static void main (String[] args) throws Exception
  {
    BufferSizes bs = new BufferSizes();
  } // end of main ()

  BufferSizes() throws Exception
  {
    Server server = new Server();
    Thread sthread = new Thread(server);
    sthread.start();
    Thread.currentThread().sleep(2000);
    Socket s = new Socket("localhost", 55555);
    Thread.currentThread().sleep(2000);
    s.close();
  
  }
  
  

  class Server implements Runnable
  {
    public void run() 
    {
      try{
      ServerSocket ss =new ServerSocket(55555);
      Socket s= ss.accept();
      System.out.println(s.getSendBufferSize());
      System.out.println(s.getReceiveBufferSize());
      s.close();
      }
      catch (Exception e){
        e.printStackTrace();
      }
      
    }
    
  }
  
  
}


