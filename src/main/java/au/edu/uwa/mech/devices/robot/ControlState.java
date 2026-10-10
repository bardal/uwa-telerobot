
package au.edu.uwa.mech.devices.robot;

import java.util.Observable;

public class ControlState extends Observable{

  protected int _state=OBSERVER;

  public int get(){return _state;}
  public void set(int newstate)
  {
    if (_state != newstate)
    {_state=newstate; setChanged(); notifyObservers();}
  }

  public static final int INCONTROL=1;
  public static final int OBSERVER=2;
  
}
