
package au.edu.uwa.mech.devices.robot;

import au.edu.uwa.mech.maths.Pose3d;
import au.edu.uwa.mech.maths.Point3d;
import java.lang.String;
import java.lang.StringBuffer;
import java.util.Vector;
import java.util.Enumeration;

//GBH - gintarus,barney,harald - or more commin GBH meaning!

public class MMoveCommand extends Command{

  public MMoveCommand()
  {
    super();
  }

  public MMoveCommand(Pose3d pose){
    this(poseAsGBHString(pose));
  }

  public MMoveCommand(Point3d point){
    this(pointAsGBHString(point));
  }

  
  public MMoveCommand(Gripper gripper){
    this("g" + gripper.toString());
  }
  

  public MMoveCommand(String commandString){
    this.commandString = commandString;
  }

  public static String stateAsGBHString(RobotState waypoint)
  {
    StringBuffer move = new StringBuffer();
    move.append(poseAsGBHString(waypoint.pose));
    move.append(",g" + waypoint.gripper.toString());
    return move.toString();
  }

  public static String poseAsGBHString(Pose3d pose)
  {
    StringBuffer move = new StringBuffer();
    move.append("x" + Math.round(pose.position.x));
    move.append(",y" + Math.round(pose.position.y));
    move.append(",s" + Math.round(pose.orientation.spinDeg()));
    move.append(",t" + Math.round(pose.orientation.tiltDeg()));
    move.append(",z" + Math.round(pose.position.z));
    return move.toString();
  }

  public static String pointAsGBHString(Point3d point)
  {
    StringBuffer move = new StringBuffer();
    move.append("x" + Math.round(point.x));
    move.append(",y" + Math.round(point.y));
    move.append(",z" + Math.round(point.z));
    return move.toString();
  }

  
  public void setFrom(String string){
    commandString = string;
     _observable.forceNotifyObservers();
  }
  
  
  public String toEditableString(){
    return commandString;
  }
  
  
  public String toString(){
    return "[MMovecommand "+commandString+"]";
  }
  
  public String toUserString()
  {
    StringBuffer text=new StringBuffer();
    text.append("Command Number :"+_ID+"\tStatus :"+stateToString()+linefeed);
    text.append(commandString+linefeed);
    return text.toString();
  }

  public String toRobotString()
  {
    return "gbh move "+commandString;
  }
  
//    public void append(RobotState rs)
//    {
//      path.addElement(new RobotState(rs));
//      _observable.forceNotifyObservers();
//    }

  static final long serialVersionUID = -3646776803950912355L;
  protected String commandString;
  protected static String linefeed=System.getProperty("line.separator");

  //  public static void main (String[] args)
//    {
//      GBHCommand c=new GBHCommand();
//      c.append(new RobotState());
//      c.append(new RobotState(new Pose3d(), new Gripper(true)));
//      System.out.println(c.toRobotString());
    
    
//    } // end of main ()
  
  //state of command ? sent / executing / finished / error
};
