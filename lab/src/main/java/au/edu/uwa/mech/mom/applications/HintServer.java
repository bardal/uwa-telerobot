
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: HintServer.java,v 1.1 2000/01/20 17:06:51 bdalton Exp $
// Time-stamp: "2000-01-21 00:45:18 barney"

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

package au.edu.uwa.mech.mom.applications;


import au.edu.uwa.mech.mom.*;
import java.io.*;
import java.util.*;

public class HintServer  
{
  
  Channel hintChannel;
  SessionRoot sr;
  Peer us;
  File hintFile;
  long lastModified=0;
  Vector hints;
  int interval=60000;
  
  public HintServer(String hintFile, String momurl)
  {
    this.hintFile = new File(hintFile);
    if (!this.hintFile.exists())
      throw new RuntimeException(hintFile + "doesn't exist");
    
    System.out.println(this.hintFile.lastModified());
    try
    {    
      sr = new SessionRoot(momurl);
      Peer us = sr.login("guest", "", "hints");
      hintChannel = (Channel)sr.lookupOrCreate(new ContextURL("chat.channel"));
      hintChannel.join();
    } catch (MOMException e) {
      e.printStackTrace();
    }     
  }

  void loadFile() throws IOException
  {
    System.out.println("loading file");
    hints = new Vector();
    lastModified=hintFile.lastModified();
    BufferedReader r = new BufferedReader(new FileReader(hintFile));
    String line;
    try{interval = Integer.parseInt(r.readLine());}
    catch (NumberFormatException e){
      System.out.println("warning: couldn't read hint interval from hints file");
    }
    while((line=r.readLine()) != null)
      hints.add(line);
    r.close();
  }
  
  public void hint() throws InterruptedException, MOMIOException, IOException
  {
    while(true){
      if(lastModified != hintFile.lastModified())
        loadFile();
      
      Collections.shuffle(hints);
      
      for (Enumeration e=hints.elements();e.hasMoreElements();) {
        if(lastModified != hintFile.lastModified())
          break;
        
        String hint = (String)e.nextElement();
        MOMPacket message = hintChannel.getPacketFactory().message(hint.toCharArray());
        message.setUnimportant(true);
        message.setBulk(true);
        hintChannel.sendMessage(message);
        Thread.currentThread().sleep(interval);
      }
    } 
  }
  
  public static void main (String[] args)
  {
    try{
      HintServer hint = new HintServer(args[0], args[1]);
      hint.hint();
    } catch (Exception e){
      e.printStackTrace();
    }
    
  } // end of main ()
  
}



