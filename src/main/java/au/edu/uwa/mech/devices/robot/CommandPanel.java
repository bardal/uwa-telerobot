
package au.edu.uwa.mech.devices.robot;

//import java.util.Observable;
//import java.util.Enumeration;
import java.lang.Object;
import java.awt.Button;
import java.awt.TextArea;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Label;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Observable;
import java.util.Observer;
import java.util.Enumeration;
//import au.edu.uwa.mech.util.ObservableVector;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.gui.TheConsole;
//import au.edu.uwa.mech.gui.SimpleConsole;
import au.edu.uwa.mech.util.Debug;



public abstract class CommandPanel extends java.awt.Panel
  implements RobotListener, CommandEditor                  
{
  public void setRobotHandler(RobotHandler h){;}
  
}

