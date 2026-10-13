
package au.edu.uwa.mech.devices.robot;

import au.edu.uwa.mech.util.*;

public abstract class Command implements ObservableI, java.io.Serializable {

  public Command(){_ID = ++_CurrentID;}

  public abstract String toEditableString();
  public abstract String toUserString();
  public abstract String toRobotString();

  public abstract void setFrom(String string);
  
  public boolean sent(){
    return (_state > NOTSENT);
  }

  public boolean received(){
    return (_state > SENDING);
  }


  public boolean finished(){
    return (_state >= COMPLETED);
  }

  public boolean executing(){
    return (_state >= EXECUTING);
  }
  
  
  
//   public void sent(boolean sent)
//   {
//     if (sent)
//       state = SENDING;
//     _observable.forceNotifyObservers();
//   }

  public int state(){return _state;}
  public synchronized void state(int s)
  {
    if (_state != s)
    {
      _state = s; 
      _observable.forceNotifyObservers(this);
      notifyAll();
    }
  }

  public String toString()
  {
    return "[Command [state "+stateToString()+"][id "+_ID+"]]";
  }


  
  public String stateToString()
  {
    switch (_state)
    {
      case NOTSENT:   return "Unsent   "; 
      case SENDING:   return "Sending  "; 
      case EXECUTING: return "Executing"; 
      case COMPLETED: return "Finished "; 
      case ERROR:     return "Error    "; 
      default:        return "Invalid State";
    }
  }

  public int ID(){return _ID;}
  public java.util.Observable observable() {return _observable;}

  protected static int          _CurrentID  = 0; 
  protected int                 _ID;
  protected transient ContainedObservable _observable = new ContainedObservable();
  
  private transient int _state;

  public static final int NOTSENT   = 0;
  public static final int SENDING   = 1;
  public static final int EXECUTING = 2;
  public static final int COMPLETED = 3;
  public static final int ERROR     = 4;

  static final long serialVersionUID = 8043349783253376888L;
  //state of command ? sent / executing / finished / error
};
