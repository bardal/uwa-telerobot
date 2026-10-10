
package au.edu.uwa.mech.devices.robot;

import au.edu.uwa.mech.io.ASCIIInputStream;
import au.edu.uwa.mech.io.ASCIIReader;
import au.edu.uwa.mech.maths.*;
import java.io.IOException;

public class RobotState implements java.io.Serializable {

  String exeState="";
  
  public RobotState(){}

  public RobotState(RobotState rs) {
    this.pose = new Pose3d(rs.pose);
    this.gripper= new Gripper(rs.gripper);
    this.exeState = new String(rs.exeState);
  }

  public static RobotState random() {
    RobotState rs =new RobotState();
    rs.pose.position.x = Math.random() * 500;
    rs.pose.position.y = Math.random() * 500;  
    rs.pose.position.z = Math.random() * 500;  
    rs.pose.orientation.spinDeg((Math.random() * 90) -45);  
    rs.pose.orientation.tiltDeg((Math.random() * 90) -45);  
    return rs;
    
    
  }
  
  
  public RobotState(Pose3d p, Gripper g) {
    pose=p; gripper=g;
  }

  public RobotState(Point3d p, Orientation3d o, Gripper g) {
    pose=new Pose3d(p,o); gripper=g;
  }

  public Pose3d pose= new Pose3d();
  public Gripper gripper=new Gripper(); 

  public boolean moving(){
    return exeState.equals(exeRUN);
  }
  
  
  public void read(ASCIIInputStream AI) throws IOException
  {
    pose.read(AI);
    gripper.read(AI);
  }

  public void readDeg(ASCIIInputStream AI) throws IOException
  {
    pose.readDeg(AI);
    gripper.read(AI);
    int coordtype=AI.readInt();
    int proximity=AI.readInt();
    exeState = AI.readWord();
  }

  public void readDeg(ASCIIReader ar) throws IOException
  {
    pose.readDeg(ar);
    gripper.read(ar);
    int coordtype=ar.readInt();
    int proximity=ar.readInt();
    exeState = ar.readWord();
  } 

  public String toString() {
    return "[robotstate "+pose.toString()+" [gripper "+gripper.toString()+"] [exe "+exeState+"]]";
  }

  public String toDisplayString() {
     return exeState+" "+pose.toDisplayString()+" gripper " +gripper.toString();
  }

  static final String exeRUN = "Moving";
  static final String exeSTOP = "Stopped";
  
}
