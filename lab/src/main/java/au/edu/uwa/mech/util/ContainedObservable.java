
package au.edu.uwa.mech.util;

import java.util.Observable;

//designed ot allow objects to implememten observable without 
//having to subclass from it, instead they contain an Observable object
//see http://ruby.doc.ic.ac.uk/jc8/Links/JavaTips/observer.html
public class ContainedObservable extends Observable {
  
  public void clearChanged() {
    super.clearChanged();
  }

  public void setChanged() {
    super.setChanged();
  }

  public void forceNotifyObservers() {
    super.setChanged();
    super.notifyObservers();
  }

  public void forceNotifyObservers(java.lang.Object arg) {
    super.setChanged();
    super.notifyObservers(arg);
  }

}
