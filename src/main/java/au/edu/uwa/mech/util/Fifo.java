
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Fifo.java,v 1.3 2000/01/14 10:42:31 bdalton Exp $
// Time-stamp: "2000-01-14 16:25:13 barney"

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

public class Fifo
{                    
  public Fifo()
  {
    capacity=DEFAULT_CAPACITY;
    setup();
  }

  public Fifo(int capacity)
  {
    this.capacity=capacity;
    setup();
  }


  private void setup() {
    start = 0;
    end = 0;
    spaceAvailable = new Object();
    elementAvailable = new Object();
    elements = new Object[capacity];
    notFullCondition = new NotFullCondition();
    notEmptyCondition = new NotEmptyCondition();
  }

  public synchronized void put(Object newElement, long msecTimeout) throws InterruptedException, TimeoutException
  {
    notFullCondition.timedWait(msecTimeout);
    
    elements[end] = newElement;                     
    end = (end+1) % capacity;

    notifyAll();
  }
  
  
  public synchronized void put(Object newElement) throws InterruptedException
  {
    try {
      put(newElement, 0);
    } catch(TimeoutException e){
      System.out.println("THIS SHOULD NEVER HAPPEN");
      e.printStackTrace();
    }
  }

  public synchronized Object[] getAll(long msecTimeout)  throws InterruptedException, TimeoutException
  {
    notEmptyCondition.timedWait(msecTimeout);
    int size = count();
    Object[] ret = new Object[size];
    for (int i=0;i<size;i++)
      ret[i] = get();
    return ret;
  }
  

  
  public synchronized Object get(long msecTimeout) throws InterruptedException, TimeoutException
  {
    notEmptyCondition.timedWait(msecTimeout);

    firstElement = elements[start];
    elements[start] = null;
    start = (start+1)% capacity;

    notifyAll();
    return firstElement;
  }

  public synchronized Object get() throws InterruptedException
  {
    try {
      return get(0);
    } catch(TimeoutException e){
      System.out.println("THIS SHOULD NEVER HAPPEN");
      e.printStackTrace();
      return null;
    }
  }

  public synchronized int count(){
    return (end + capacity - start) % capacity;
  }
  
  
  public synchronized boolean isEmpty(){
    return (start == end);
  }

  //always need a gap of 1 between start and end otherwise can't tell
  //the difference between full and empty
  private synchronized boolean isFull(){
    return (start == (end+1) % capacity);
  }

//    private void grow()
//    {
//      System.out.println("growing");
//      int oldCapacity = capacity;
//      int newCapacity = (int)(capacity * 1.5);
//      Object oldElements[] = elements;
//      elements = new Object[newCapacity];
//      System.arraycopy(oldElements, 0, elements, 0, oldCapacity);
//      capacity = newCapacity;
//    }
  
  
  private static final int DEFAULT_CAPACITY = 128;

  private int capacity;
  private int start, end;
  private Object firstElement;
  private Object[] elements;  // Circular array
  private Object spaceAvailable;
  private Object elementAvailable;
  private NotFullCondition notFullCondition;
  private NotEmptyCondition notEmptyCondition;
  
  class NotFullCondition extends TimedWaitCondition
  {
    public NotFullCondition ()
    { super (Fifo.this); }
    
    public boolean condition () {
      // Delegate to the appropriate conditional
      // check on the MessageQueue.
      return !isFull ();
    }
  }

  class NotEmptyCondition extends TimedWaitCondition
  {
    public NotEmptyCondition ()
    { super(Fifo.this); }
    
    public boolean condition () {
      // Delegate to the appropriate conditional
      // check on the MessageQueue.
      return !isEmpty ();
    }
  }
  

  public static void main (String[] args)
  {

    
    Fifo fifo=new Fifo(6);

    TimeStamp t =new TimeStamp();
    t.stamp("Before get");
    try{
      Object test=fifo.get(1000);
    } catch (Exception e){
      e.printStackTrace();
    }
    t.stamp("After get");
    
    
    Producer p1=new Producer(fifo);
    Consumer c1=new Consumer(fifo);
    Consumer c2=new Consumer(fifo);

    try
    {
    p1.start();
    Thread.currentThread().sleep(4000);
    System.out.println(fifo.count());
    System.out.println(fifo.getAll(0));
    c1.start();
    c2.start();
    } catch (Exception e){
      e.printStackTrace();
    }
    
     
  } // end of main ()
  
  
}


class Producer extends Thread {

  public Producer(Fifo fifo)
  {
    this.fifo = fifo;
  }

  Fifo fifo;
  
  public void run() 
  {
    try{
      int i=0;
      while(true){
        for(int j=0;j<10;j++){
          i= ++i % 100;
          fifo.put(new Integer(i));
          System.out.println("producer put \t\t\t\t\t"+i);
          System.out.flush();
    
          //   System.out.println("producer put done");
        
        }
      }
    }
    catch (InterruptedException e){
      System.out.println("Producer interrupted - thread finishing");
    }
  }
  
}
    
        
    

class Consumer extends Thread {

  public Consumer(Fifo fifo)
  {
    this.fifo = fifo;
    id = sid++;
  }

  int id;
  Fifo fifo;
  static int sid=1;
  
  
  public void run() 
  {
    System.out.println("consumer : run");

    try{
      while (true){
        String padding;
        if(id==1) padding = "\t\t";
        else padding = "\t\t\t";
        
        System.out.println("consumer"+id+" got"+padding+fifo.get());
        System.out.flush();
      }
    } catch (InterruptedException e){
      System.out.println("Consumer interrupted - thread dying");
    }
  }
  
}
    


