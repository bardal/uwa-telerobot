//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TimedWait.java,v 1.2 2000/01/14 10:42:31 bdalton Exp $ 
// Time-stamp: "2000-01-14 15:35:10 barney"

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


package au.edu.uwa.mech.util;


class Timer implements Runnable
{
  long timeout;
  boolean timedOut=false;
  Object o;
    
  Timer(long timeout, Object o){
    this.timeout = timeout;
    this.o = o;
    //this.timedOut = timedOut;
  }
      
  public void run()
  {
    try{
      Thread.currentThread().sleep(timeout);
      timedOut=true;
      synchronized (o){
        o.notifyAll();
      }
    }
    catch (InterruptedException e){
    }
      
  }
}


public class TimedWait
{
  public static long wait(Object o, long msecTimeout) throws InterruptedException, TimeoutException
  {
    if(msecTimeout ==0) synchronized (o){o.wait(); return 0;}
    else {
      
      Timer t = new Timer(msecTimeout, o);
      Thread thread = new Thread(t);
      thread.setDaemon(true);

      long start,now;
      synchronized (o){
        start = System.currentTimeMillis();
        thread.start();
        //o.wait(msecTime);
        o.wait();
        now = System.currentTimeMillis();
        thread.interrupt();
      }

      // Timed out!
      System.out.println("timeout values "+start+" "+now+" "+(now-start)+" "+msecTimeout);
      //if ((now - start)>= msecTimeout)
      if(t.timedOut)
        throw new TimeoutException ();
      return (now -start);
    }
  }

  
  public static void main (String[] args)
  {
    class Waker extends Thread
    {
      Object o;
      Waker(Object o){
        this.o=o;
      }
      
      public void run(){
        try{
          while(true){
            sleep(1000);
            synchronized(o){
              o.notifyAll();
            }
          }
          
        } catch (InterruptedException e){
          e.printStackTrace();
        }
      }
    }

    System.out.println("TimedWait test");
    
    Integer sig = new Integer(1);
    Waker w = new Waker(sig);
    w.setDaemon(true);
    w.start();

    synchronized(sig)
    {
      for (int i=1;i<30;i++){
        try{
          long t=TimedWait.wait(sig, 100);
          System.out.println("got signal after "+t+" milliseconds");
        } catch (Exception e){
          System.out.println(e);
        }
      }
    }
    
    
  } // end of main ()
}



