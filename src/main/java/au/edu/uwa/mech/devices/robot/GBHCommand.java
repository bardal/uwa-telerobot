
package au.edu.uwa.mech.devices.robot;

import au.edu.uwa.mech.maths.Pose3d;
import java.lang.String;
import java.lang.StringBuffer;
import java.util.Vector;
import java.util.Enumeration;
import au.edu.uwa.mech.util.TheLog;

//GBH - gintarus,barney,harald - or more commin GBH meaning!

public class GBHCommand extends Command{

  public GBHCommand()
  {
    super();
  }

  public GBHCommand(RobotState rs)
  {
    this();
    append(rs);
  }

  public String toString(){
    return "[GBHcommand [path "+path+"]"+super.toString()+"]";
  }
  
  public String toUserString()
  {
    StringBuffer text=new StringBuffer();
    text.append("Command Number :"+_ID+"\tStatus :"+stateToString()+linefeed);
    for (Enumeration e = path.elements() ; e.hasMoreElements() ;) 
      text.append(stateAsGBHString((RobotState)e.nextElement())+linefeed);
    return text.toString();
  }

  public String toEditableString(){
    return "GBH commands aren't editable (yet)";
  }
  
  public void setFrom(String from){
    throw new RuntimeException("can't use setFrom for GBH Commands");
  }
  
  
  public String toRobotString()
  {
    StringBuffer text=new StringBuffer();
    text.append("gbh move ");
    text.append(pathAsGBHString());
    return text.toString();
  }
  
  public void append(RobotState rs)
  {
    path.addElement(new RobotState(rs));
    _observable.forceNotifyObservers();
  }

 

  protected String pathAsGBHString()
  {
    StringBuffer text=new StringBuffer();
    for (Enumeration e = path.elements() ; e.hasMoreElements() ;) 
      text.append(stateAsGBHString((RobotState)e.nextElement())+";");
    return text.toString();
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
    move.append(",z" + Math.round(pose.position.z));
    move.append(",s" + Math.round(pose.orientation.spinDeg()));
    move.append(",t" + Math.round(pose.orientation.tiltDeg()));

    return move.toString();
  }
  
  
  protected static String linefeed=System.getProperty("line.separator");
 
  protected Vector path= new Vector();

  public static void main (String[] args)
  {
    GBHCommand c=new GBHCommand();
    c.append(new RobotState());
    c.append(new RobotState(new Pose3d(), new Gripper(true)));
    TheLog.println(c.toRobotString());
    
    
  } // end of main ()
  
  //state of command ? sent / executing / finished / error
};
