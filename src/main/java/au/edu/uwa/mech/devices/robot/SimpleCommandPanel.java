//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SimpleCommandPanel.java,v 1.8 2000/01/27 11:50:44 bdalton Exp $ 
// Time-stamp: "2000-01-27 15:33:50 barney"

// {{{ GNU General Public License

//  This program is free software; you can redistribute it and/or
//  modify it under the terms of the GNU General Public License
//  as published by the Free Software Foundation; either version 2
//  of the License, or (at your option) any later version.

//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

// }}}

package au.edu.uwa.mech.devices.robot;

//import java.util.Observable;
//import java.util.Enumeration;
import java.lang.Object;
import java.awt.*;
import java.awt.event.*;
import java.util.Observable;
import java.util.Observer;
import java.util.Enumeration;
//import au.edu.uwa.mech.util.ObservableVector;
import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.gui.TheConsole;
//import au.edu.uwa.mech.gui.SimpleConsole;
//import au.edu.uwa.mech.util.Debug;



public class SimpleCommandPanel  extends java.awt.Panel
  implements RobotListener, CommandEditor  
  //  extends CommandPanel - JAX problems with this                                  
{
  protected Button       bSend          = new Button("Send command");
  protected Button       bControl       = new Button("Control Robot");
  protected Label        lCommandStatus = new Label("Command Status:");
  protected Label        lRobotPosition = new Label("Robot Position :");
  protected Label        lController    = new Label("Robot Controller :");
  protected RobotHandler robotHandler;
  protected boolean      inControl;
  //protected Command      currentCommand;
  protected TextArea     commandEditor  = new TextArea(5,20);
  protected CommandList  commands;
  
  
  public SimpleCommandPanel()
  {    
    inControl(false);
    setLayout(new BorderLayout());

    Panel topEdit = new Panel(new FlowLayout(FlowLayout.LEFT));
    topEdit.add(new Label("Command editor"));
    add(topEdit, "North");
    
    Panel middleEdit = new Panel();
    middleEdit.setLayout(new BorderLayout());
    commandEditor.setRows(5);
    middleEdit.add(commandEditor, "Center");
    Panel buttons = new Panel();
    buttons.add(bSend);
    buttons.add(bControl);
    Button clear = new Button("Clear");
    buttons.add(clear);
    
    middleEdit.add(buttons, "South");
    add(middleEdit, "Center");
    
    Panel bottomEdit=new Panel();
    bottomEdit.setLayout(new GridLayout(0,1));
    bottomEdit.add(lCommandStatus);
    bottomEdit.add(lRobotPosition);
    bottomEdit.add(lController);
    add(bottomEdit,"South");
    
    bSend.setEnabled(false);
    bControl.setEnabled(false);
    
    bSend.addActionListener(new SendButtonListener());
    bControl.addActionListener(new ControlButtonListener());
    clear.addActionListener(new ClearListener());
  }

  public void appendTextCommand(String command){
      commandEditor.append(command);
    }
  
    
  public void setRobotHandler(RobotHandler robotHandler)
  {
    this.robotHandler = robotHandler;
    this.commands     = robotHandler.getCommands();
    robotHandler.addRobotListener(this);
    commands.observable().addObserver(new CommandListener());
  }
  
  
  public RobotHandler getRobotHandler() {
    return robotHandler;
  }
  
//    public Dimension getPreferredSize() {
//      Dimension s=super.getPreferredSize();
//      System.out.println("Simplecommandpanel.GetPreferredSize() "+s);
//      return s; 
//    }

//    public Dimension getMinimumSize() {
//      Dimension s=super.getMinimumSize();
//      System.out.println("Simplecommandpanel.GetMinimumSize() "+s);
//      return s;
//    }

//    public Dimension getMaximumSize(){
//      Dimension s=super.getMaximumSize();
//      System.out.println("Simplecommandpanel.GetMaximumSize() "+s);
//      return s; 
//    }  

  
  public void setEnabled(boolean b) {
  // 
  //  bSend.setEnabled(b);
  }

  public void setConnected(StateHandler handler, boolean b) {
    bControl.setEnabled(b);
    //setEnabled(b);
  }

  //should called by robothandler - no - client is a robot handler 
  public void newRobotState(StateHandler handler, RobotState robotState) {
    lRobotPosition.setText("Robot Position :"+robotState.toDisplayString());
    //ignore for the moment
  }
  
  public void newController(RobotHandler handler, String name, boolean us) {
    lController.setText("Controller : "+name);
    TheConsole.println("New controller of the robot : "+name);
    inControl(us);
  }
  
  public void noController(RobotHandler handler) {
    inControl(false);
    TheConsole.println("The robot is free");
    lController.setText("No Controller");
  }

  public void inControl(boolean inControl)
  {
    TheLog.println("inControl called with "+ inControl);

    bSend.setEnabled(inControl);
    
    this.inControl = inControl;
    if(inControl)
      bControl.setLabel("Release Robot");
    else
      bControl.setLabel("Control Robot");
  }

  public static void main (String[] args)
  {
    Frame f = new Frame();
    SimpleCommandPanel cp=new SimpleCommandPanel();
    cp.setRobotHandler(new DumbRobotHandler());
    f.add(cp);
    f.pack();
    f.show();
    f.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {System.exit(0);}
      });
  } // end of main ()
  


  class CommandListener implements Observer 
  {
    public void update(Observable obj, Object arg)
    {
      //System.out.println("Comand listener update"+arg);
      Command current = commands.current();
      if (current!=null)
        lCommandStatus.setText
          ("Status: "+current.stateToString()+" command " +current.ID()+". "+
           commands.backlog()+" command"+(commands.backlog() == 1 ? " ":"s")+" queued");
  //      if(currentCommand.sent() && !currentCommand.finished())
//          bSend.setEnabled(false);
//        else
//          bSend.setEnabled(true);
      
      // TheLog.println("Command listener got update");
      //  int pos =taRobotCommands.getCaretPosition();
//        StringBuffer text=new StringBuffer();
//        for (int i=1;i<=commands.getMax();i++){
//          Command c=commands.getCommand(i);
//          if (c!=null)
//            text.append(c.toUserString());
//        }
      
//        taRobotCommands.setText(text.toString());
      //      taRobotCommands.setCaretPosition(pos);
      //TheConsole.println(currentCommand.toUserString());
    }
  }
  
  
  //  class TextFieldListener implements ActionListener
//    {
//      protected StringBuffer toChange;
//      public TextFieldListener(StringBuffer toChange)
//      {this.toChange=toChange;}

//      public void actionPerformed(ActionEvent e) {
//        Debug.println("text is "+e.getActionCommand());
//        toChange.insert(0, e.getActionCommand());
//        //    toChange=new StringBuffer(e.getActionCommand());
//      }
//    }

  class SendButtonListener implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {
      try{
        Command command = new MMoveCommand(commandEditor.getText());
        commands.setCommand(command);
        robotHandler.sendCommands();
      } catch (RobotException ex){
        TheConsole.println(ex.getMessage());
        TheLog.logException(ex);
      }
    }
  }

  
  class ControlButtonListener implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {
      try{
        if (inControl)
          robotHandler.releaseControl();
        else
          robotHandler.gainControl();
      }
      catch(RobotException ex){
        TheConsole.println(ex.getMessage());
        TheLog.logException(ex);
      }
    }
  }

  class ClearListener implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {
      commandEditor.setText("");
    }
  }
  
}


  
//    class NewMoveListener implements ActionListener
//    {
//      public void actionPerformed(ActionEvent e) {
//        currentCommand = (new MMoveCommand(commandEditor.getText());
//        commands.setCommand(currentCommand);
//      }
//    }


      
//    class EditButtonListener implements ActionListener
//    {
//      public void actionPerformed(ActionEvent e) {
//        try{
//          int no = Integer.parseInt(tfNo.getText());
//          setCurrentCommand(commands.getCommand(no));
//        }
//        catch (NumberFormatException ne){
//          TheConsole.println("Type a valid move number first");
//        }
//      }
//    }

//    class UpdateButtonListener implements ActionListener
//    {
//      public void actionPerformed(ActionEvent e) {
//        TheLog.println("Setting current command to "+tfMove.getText());
//        if (currentCommand != null){
//          currentCommand.setFrom(tfMove.getText());
//          setCurrentCommand(null);
//        }
//        else
//          TheConsole.println("No move to edit");
//      }
//    }

//    class DeleteButtonListener implements ActionListener
//    {
//      public void actionPerformed(ActionEvent e) {
//        try{
//          int no = Integer.parseInt(tfNo.getText());
//          setCurrentCommand(commands.getCommand(no));
//          if(currentCommand !=null)
//            commands.delete(currentCommand);
//          setCurrentCommand(null);
//        }
//        catch (NumberFormatException ne){
//          TheConsole.println("Type a valid move number first");
//        }
//      }
//    }
//  }



//    class LoginButtonListener implements ActionListener
//    {
//      //RouterConnectorApplet rca;
//      //LoginButtonListener(RouterConnectorApplet rca)
//      //{this.rca=rca;}

//      public void actionPerformed(ActionEvent e) {
//        Debug.println("text is "+e.getActionCommand());
//        //rca.Login();
//      }
//    }


