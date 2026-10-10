//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: RubbishTransceiver.java,v 1.4 2000/01/21 15:24:30 bdalton Exp $
// Time-stamp: "2000-01-21 10:03:33 barney"

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

//quick hack

package au.edu.uwa.mech.mom.server;

import au.edu.uwa.mech.util.Fifo;
import au.edu.uwa.mech.util.Next;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.util.TimeoutException;

import au.edu.uwa.mech.mom.*;

public class RubbishTransceiver extends SimpleTransceiver 
{
  public RubbishTransceiver(TransceiverManager manager) 
  {
    //upstreamQ=new Fifo();
    downstreamQ=new Fifo();
    this.manager = manager;
    downstreamThread = new Thread(new DownstreamWorker());
  }

  public void start(){
    downstreamThread.start();
  }
  
  
  public void close(){
    //activateUpstream(false);
    //activateDownstream(false);
    downstreamThread.interrupt();
    try{downstreamThread.join(1000);}
    catch (InterruptedException e){
      e.printStackTrace();
    }
    
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
    // if(downstreamActive)
      downstreamQ.put(arg, downstreamTimeout);
    //  else
    //    nextDownstream(source, arg);
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
    //try{
      //if(upstreamActive)
      //  upstreamQ.put(arg);
      //else
    nextUpstream(source, arg);
      //} catch (InterruptedException e){
      //throw new MOMIOException("ActiveTransceiver putqUpstream interrupted");
      //}
  }

  //true active, false deactivate
  //public void activateUpstream(boolean newState)
  //{
  //  upstreamActive=true;
  //}

//    public void activateDownstream(boolean newState){
//      if (newState != ;
//    }

  
  String name="RubbishTransceiver";
  
  /**
     * Get the value of name.
     * @return Value of name.
     */
  public String getName() {return name;}
  
  /**
     * Set the value of name.
     * @param v  Value to assign to name.
     */
  public void setName(String  v) {
    downstreamThread.setName(v+" downstreamQ");
    this.name = v;
  }
  

  public void debug(String stuff){
    TheLog.println(name+":"+stuff);
  }
  
  
  class DownstreamWorker implements Runnable
  {
    public void run(){
      try{
        while(true){
          Object toSend;
          synchronized(downstreamQ){
            if (downstreamQ.count() > downstreamThreshold)
              toSend = getNextNeedsDelivery();
            else
              toSend = downstreamQ.get();
          }
          if (toSend instanceof MOMPacket)
            toSend = ((MOMPacket)toSend).getPacket();
          
          nextDownstream(this, toSend);
        }
      } catch (Exception e){
        manager.handleError(RubbishTransceiver.this, e, UPSTREAM);
        //TheLog.println("TO DO how to handle exceptions in ActiveTransceiver");
        //e.printStackTrace();
      }
    }

    //strange logic here
    protected Object getNextNeedsDelivery() throws InterruptedException
    {
      Object toSend=downstreamQ.get();
      synchronized(downstreamQ){
        while (downstreamQ.count() > downstreamThreshold)
        {
          debug("Q overload "+downstreamQ.count()+" greater than threshold "+downstreamThreshold);
          if (toSend instanceof MOMPacket){
            if (((MOMPacket)toSend).unimportant()){
              //debug("RubbishTransceiver discarding "+toSend);
              toSend = downstreamQ.get();
            } else {
              //debug("Can't discard "+toSend);
              break;
            }
            
          } else {
            //debug("Not a MOMPacket can't check "+toSend);
            //not mompacket so don't know if it can be discarded
            //play safe
            break;
          }
        }
      }
      return toSend;
    }
  }
  

//    class UpstreamWorker implements Next
//    {
//      public void put(Object source, Object arg){
//        try{
//          nextUpstream(this, arg);
//        } catch (MOMIOException e){
//          manager.handleError(ActiveTransceiver.this, e, UPSTREAM);
//          //TheLog.println("TO DO how to handle exceptions in ActiveTransceiver");
//          //e.printStackTrace();
//        }
//      }
    
//    }


  int downstreamThreshold =10;
  //who we send errors to
  TransceiverManager manager;

  Thread downstreamThread;
  //use ActiveFIFO;
  Fifo downstreamQ;//, upstreamQ;
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

