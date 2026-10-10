

package au.edu.uwa.mech.devices.robot;

import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Enumeration;

import au.edu.uwa.mech.util.ContainedObservable;
import au.edu.uwa.mech.util.ObservableI;


//list of commands startign at 1 !!!!!!!!!!
//we don't have a command 0

//commands should be in a vector

public class CommandList implements ObservableI, Observer
{
  //public Object elementAt(int index)
  //{ return _vector.elementAt(index);}

  public void setCommand(Command command){
    if (command.ID() > max)
      max = command.ID();
    _hash.put(new Integer(command.ID()), command);
    command.observable().addObserver(this);
    _observable.forceNotifyObservers(this);
  } 

  public Command getCommand(int no){
    return (Command)_hash.get(new Integer(no));
  }

  public void delete(Command command){
    command.observable().deleteObservers();
    _hash.remove(new Integer(command.ID()));
    _observable.forceNotifyObservers(this);
  }
  
  
  public int size(){
    return _hash.size();
  }
  
  
  public String toString() {
    return "[hash "+_hash+"][observable "+_observable+"]";
  }
  
  public Enumeration elements() {
    return _hash.elements();
  }

  public Observable observable() {
    return _observable;
  }

  public int backlog(){
    return lastSent-lastReceived;
  }

  public int lastSent(){
    return lastSent;
  }

  public Command current(){
      return getCommand(currentExecuting);
  }
  

  public void update(Observable o, Object arg)
  {
    if(arg instanceof Command){
      //System.out.println("command list got command update "+arg);
      Command c = (Command)arg;
      if ((c.ID() > lastSent) && c.sent())
        lastSent = c.ID();
      if ((c.ID() > lastReceived) && c.received())
        lastReceived = c.ID();
      if ((c.ID() > lastFinished) && c.finished())
        lastFinished = c.ID();
      if (c.executing())
        currentExecuting = c.ID();
      if (current().finished() && c.sent())
        currentExecuting = c.ID();
    }
    
    _observable.forceNotifyObservers(this);
  }

  public int getMax(){
    return max;
  }
  

  protected int lastSent=1;
  protected int lastReceived=1;
  protected int lastFinished=1;
  protected int backlog=0;
  protected int max=0;
  protected int currentExecuting=1;
  protected Hashtable _hash = new Hashtable();
  protected ContainedObservable _observable = new ContainedObservable();

}
