
package au.edu.uwa.mech.gui.awt;

import java.awt.*;
import au.edu.uwa.mech.gui.Console;
import au.edu.uwa.mech.util.TheLog;


public class TextConsole extends TextArea implements Console 
{
  static protected String linefeed=System.getProperty("line.separator");

  public TextConsole(){
    super();
    setEditable(false);
  }
  
  
  public TextConsole(int rows, int cols)
  {
    super (rows,cols);
    setEditable(false);
  }


  public void println(String text)
  {
    //TheLog.println("writing to console :"+text);
    append(text);
    append(linefeed);
  }

  public Dimension getMinimumSize(){
    return new Dimension(1,1);
  }
  
  
} 
