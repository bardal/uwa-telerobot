

package au.edu.uwa.mech.util;

import java.util.Vector;
import java.util.Observable;
import java.util.Observer;
import java.util.Enumeration;

import au.edu.uwa.mech.util.ContainedObservable;


//vector class that notifyies all Observers of any change in vector or
//any of its observable members - does that make sense?

public class ObservableVector implements ObservableI, Observer
{
  public Object elementAt(int index)
  { return _vector.elementAt(index);}

  public void addElement(Object obj){ 
    _vector.addElement(obj);

    if (obj instanceof Observable)
      ((Observable)obj).addObserver(this);
    else if (obj instanceof ObservableI)
      ((ObservableI)obj).observable().addObserver(this);

    _observable.forceNotifyObservers(this);
  } 

  public String toString() {
    return "[vector "+_vector+"][observable "+_observable+"]";
  }
  
  
  public Enumeration elements() {
    return _vector.elements();
  }

  public Observable observable() {
    return _observable;
  }


  public void update(Observable o, Object arg)
  {
    _observable.forceNotifyObservers(this);
  }

  protected Vector _vector = new Vector();
  protected ContainedObservable _observable = new ContainedObservable();

}
