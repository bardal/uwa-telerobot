//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TimedWaitCondition.java,v 1.1 2000/01/14 10:42:31 bdalton Exp $ 
// Time-stamp: "2000-01-14 14:49:16 barney"

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

// from http://www.cs.wustl.edu/~pjain/java/java_notes.html
package au.edu.uwa.mech.util;

public abstract class TimedWaitCondition
{
  // By default, we ``delegate'' to ourself.
  public TimedWaitCondition () { object_ = this; }

  // Subclasses can also supply us with an 
  // Object that is delegated the wait() call
  // so we can borrow its monitor lock.
  public TimedWaitCondition (Object obj)
  {
    object_ = obj;
  }

  // This is the object we delegate to if a 
  // subclass gives us a particular object, 
  // otherwise, we ``delegate'' to ourself 
  // (i.e., to this).
  protected Object object_;

  // This hook method must be overridden 
  // by a subclass to provide the condition.
  public abstract boolean condition ();

  // Make this final so that no one can 
  // override this Template Method.  This
  // method assumes it's called with the
  // object_'s monitor lock already held.
  public final void timedWait (long msecTimeout) 
    throws InterruptedException,
           TimeoutException 
  {
    boolean indefinite = (msecTimeout ==0);
    if (!condition ()) {
      // Only attempt to perform the timed wait 
      // if the condition isn't true initially.
      long start = System.currentTimeMillis ();
      long wait_time = msecTimeout;
      
      for (;;) {
        // Wait until we are notified 
        // (releases the monitor lock).
        object_.wait (wait_time);
        
        // Recheck the condition.
        if (!condition ()) {
          if(!indefinite){
            long now = System.currentTimeMillis ();
            long timeSoFar = now - start;
          
            // Timed out!
            if (timeSoFar >= msecTimeout) 
              throw new TimeoutException ();
            else 
              // We still have some time left to wait, 
              // so adjust the wait_time.
              wait_time = msecTimeout - timeSoFar;
          }
        }
        else
          break;  // Condition became true.
      }
    }
  }

  // Notify all threads waiting on the object_.
  public final void broadcast () {
    object_.notifyAll ();
  }
}
