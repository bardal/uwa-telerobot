
package au.edu.uwa.mech.devices.robot;

import au.edu.uwa.mech.io.ASCIIInputStream;
import au.edu.uwa.mech.io.ASCIIReader;

public class Gripper implements java.io.Serializable
{
  public Gripper(){}

  public Gripper(Gripper g) {
    this.state=g.state;
  }

  public Gripper(int state){
    if (state== OPEN)
      this.state=OPEN;
    else
      state=CLOSED;
  }
  
  
  //true for Gripper open, backwards compatibility
  public Gripper(boolean go){
    if (go)
      state = OPEN;
    else
      state = CLOSED;
  }

  public static Gripper newOpen(){
    return new Gripper(OPEN);
  }

  public static Gripper newClosed(){
    return new Gripper(CLOSED);
  }

  
  
  public boolean open(){
    return state==OPEN;
  }

  public boolean closed(){
    return state==CLOSED;
  }

  public String toString()
  {
    if (open()) return "open";
    else        return "closed"; 
  }

  public void read(ASCIIInputStream AI) throws java.io.IOException
  {
    state=AI.readInt();
  }

  public void read(ASCIIReader aReader) throws java.io.IOException
  {
    state=aReader.readInt();
  }


  public static final int CLOSED=0;
  public static final int OPEN=1;

  // false for closed , true for open
  protected int state=CLOSED;

}
