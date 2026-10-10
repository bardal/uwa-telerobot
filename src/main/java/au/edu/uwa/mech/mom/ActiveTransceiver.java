//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ActiveTransceiver.java,v 1.4 2000/01/23 13:02:31 bdalton Exp $
// Time-stamp: "2000-01-23 13:11:42 barney"

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

package au.edu.uwa.mech.mom;

import au.edu.uwa.mech.util.ActiveFifo;
import au.edu.uwa.mech.util.Next;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.util.TimeoutException;

public class ActiveTransceiver extends SimpleTransceiver 
{
  public ActiveTransceiver(TransceiverManager manager) 
  {
    upstreamQ=new ActiveFifo();
    downstreamQ=new ActiveFifo();
    upstreamQ.setNext(new UpstreamWorker());
    downstreamQ.setNext(new DownstreamWorker());
    
    this.manager = manager;
  }
  
  public void close(){
    activateUpstream(false);
    activateDownstream(false);
    super.close();
  }

  long downstreamTimeout=0;
  public void setDownstreamTimeout(long timeout){
    downstreamTimeout = timeout;
  }
  
  
  // implementation of au.edu.uwa.mech.mom.Transceiver interface

  /**
   *
   * @param param1 <description>
   * @param param2 <description>
   * @exception au.edu.uwa.mech.mom.MOMIOException <description>
   */
  public void putqDownstream(Object source, Object arg) throws MOMIOException {
    try{
      if(downstreamQ.activated())
        downstreamQ.put(arg, downstreamTimeout);
      else
        nextDownstream(source, arg);
    } catch (InterruptedException e){
      throw new MOMIOException("ActiveTransceiver putqDownstream interrupted");
    } catch (TimeoutException e){
      throw new MOMIOException("ActiveTransceiver putqDownstream timeout");
    }
    
  }

  /**
   *
   * @param param1 <description>
   * @param param2 <description>
   * @exception au.edu.uwa.mech.mom.MOMIOException <description>
   */
  public void putqUpstream(Object source, Object arg) throws MOMIOException {
    try{
      if(upstreamQ.activated())
        upstreamQ.put(arg);
      else
        nextUpstream(source, arg);
    } catch (InterruptedException e){
      throw new MOMIOException("ActiveTransceiver putqUpstream interrupted");
    }
  }

  //true active, false deactivate
  public void activateUpstream(boolean newState)
  {
    upstreamQ.setActive(newState);
  }

  public void activateDownstream(boolean newState){
    downstreamQ.setActive(newState);
  }

  class DownstreamWorker implements Next
  {
    public void put(Object source, Object arg){
      try{
        //System.out.println("DownstreamWorker put "+arg);
        nextDownstream(this, arg);
      } catch (MOMIOException e){
        manager.handleError(ActiveTransceiver.this, e, UPSTREAM);
        //TheLog.println("TO DO how to handle exceptions in ActiveTransceiver");
        //e.printStackTrace();
      }
    }
  }
  

  class UpstreamWorker implements Next
  {
    public void put(Object source, Object arg){
      try{
        nextUpstream(this, arg);
      } catch (MOMIOException e){
        manager.handleError(ActiveTransceiver.this, e, UPSTREAM);
        //TheLog.println("TO DO how to handle exceptions in ActiveTransceiver");
        //e.printStackTrace();
      }
    }
    
  }


  public void setName(String name){
    this.name=name;
  }

  public String toString(){
    return new String("ActiveTransceiver:"+name);
  }
  

  String name="";
  
  //who we send errors to
  TransceiverManager manager;

  //use ActiveFIFO;
  ActiveFifo downstreamQ, upstreamQ;
  //boolean downstreamActivated, upstreamActivated;
  //Thread upstreamThread, downstreamThread;
  
}



  
//    class DownstreamWorker implements Runnable
//    {
//      public void run()
//      {
//        downstreamActivated=true;
//        while(downstreamActivated) {
//          try{
//            nextDownstream(this, downstreamQ.get());
//          } catch (MOMIOException e){
//            manager.handleError(ActiveTransceiver.this, e, UPSTREAM);
//            //TheLog.println("TO DO how to handle exceptions in ActiveTransceiver");
//            //e.printStackTrace();
//          }
        
        
//        }
//      }
//    }
