
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ActiveFifo.java,v 1.5 2000/01/13 13:08:15 bdalton Exp $
// Time-stamp: "2000-01-13 19:06:13 barney"

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

public class ActiveFifo implements Runnable
{
  public ActiveFifo() {
    fifo = new Fifo();
  }
  
  public void setNext(Next next) {
    this.next=next;
  }

  public boolean activated(){
    return active;
  }
  
  public void setActive(boolean wantActive) {

    if (wantActive == active){
      TheLog.println("ActiveFifo active already "+active);
    }
    else if (wantActive){
      activeThread=new Thread(this);
      activeThread.start();
    } else {
      this.active=false;
      activeThread.interrupt();
      try{
        activeThread.join();
      } catch (InterruptedException e){
        e.printStackTrace();
      }
      
    }
    
  }

  public void put(Object arg) throws InterruptedException {
    fifo.put(arg);
  }

  public void put(Object arg, long timeout) throws InterruptedException, TimeoutException {
    fifo.put(arg, timeout);
  }
  
  public void run()
  {
    active=true;

    try{
    while(active)
        next.put(this, fifo.get()); 
    } catch (InterruptedException e){
      TheLog.println("ActiveFifo interrupted - thread "+this+" dying");
      //active = exceptionHandler.carryOn(this,e);
    }
      
    active = false;
  }

  Thread activeThread;
  boolean active;
  Next next;
  Fifo fifo;
  //ExceptionHandler exceptionHandler;
  
  public static void main (String[] args)
  {
    ActiveFifo af= new ActiveFifo();
  } // end of main ()
  
  
}



