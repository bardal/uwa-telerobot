
//  Copyright (C) 1999, 2000 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Tue Sep 07 11:17:07 1999
// Version       : $Id: BasicServer.java,v 1.1 2000/01/17 14:12:39 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

// {{{ GNU General Public License
// This program is free   software; you can redistribute it and/or
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

package au.edu.uwa.mech.net;

import java.net.*;
import java.io.*;
import java.util.*;
import au.edu.uwa.mech.util.*;


public class BasicServer
{
  Class handlerClass;
  
  public BasicServer(int port, String handlerClassName) throws Exception
  {
    handlerClass = Class.forName(handlerClassName);
    if (!Class.forName("au.edu.uwa.mech.net.IOHandler").isAssignableFrom(handlerClass))
      throw new Exception(handlerClass+" is not an IOHandler");
    
    ServerSocket ss = new java.net.ServerSocket (port);
    System.err.println("Listening for connections on "+port);
    
    while (true) {
      try{
        Connection c = new Connection(ss.accept());
        c.setDaemon(true);
        c.start();
      } catch (Exception e){
        e.printStackTrace();
      }
    }
  }

  class Connection extends Thread
  {
    Socket              s;
    InputStream     is;
    OutputStream    os;
    IOHandler           handler;

    
    public Connection(Socket s) throws IOException {
      this.s = s;
      s.setSoTimeout(1000);
      is    = new BufferedInputStream(s.getInputStream());
      os    = new BufferedOutputStream(s.getOutputStream());
      try{handler = (IOHandler)handlerClass.newInstance();}
      catch( InstantiationException e){
        throw new IOException("couldn't create handler for connection");}
      catch( IllegalAccessException e){
        throw new IOException("couldn't create handler for connection");}
    }
    
    
    public void run(){
      try{
        TheLog.println("Accepted connection "+s.getInetAddress().getHostAddress());
        handler.doIO(is,os);
      } catch (IOException e){
        e.printStackTrace();
      } finally {
        try{
          TheLog.println("closed connection");
          s.close();
        }
        catch (IOException e){}
      }
    }
  }
    


  
  public static void main (String[] args)
  {
    try{
      //usage logfile, port, handler class
      FileLog fl = new FileLog(args[0]);
      TheLog.setLog(fl);
      BasicServer s = new BasicServer(Integer.parseInt(args[1]), args[2]);
    } catch (Exception e){
      e.printStackTrace();
    } finally{
      //fl.close();
    }
    
  }// end of main ()
}

