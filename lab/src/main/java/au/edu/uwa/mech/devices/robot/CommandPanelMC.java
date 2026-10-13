
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



public class CommandPanelMC
  extends CommandPanel            
{
  protected Button       bSend;
  protected Button       bControl;
  protected TextArea     taRobotCommands;
  protected TextField    tfMove, tfNo;
  protected Label        lRobotPosition;
  protected Label        lController;
  protected CommandList  commands; 
  protected RobotHandler robotHandler;
  protected boolean      inControl;
  protected Command      currentCommand;
  
  
  public CommandPanelMC()
  {
    
    bSend           = new Button("Send queued commands");
    bControl        = new Button("Control Robot");
    taRobotCommands = new TextArea();
    lRobotPosition  = new Label("Robot Position :");
    lController     = new Label("Robot Controller :");
    tfMove          = new TextField();
    tfNo            = new TextField(2);


    
    inControl(false);
    setLayout(new BorderLayout());

    Panel topEdit = new Panel(new FlowLayout(FlowLayout.LEFT));
    topEdit.add(new Label("Edit/Queue move commands e.g. x20,y50,s10,gopen"));

    Panel middleEdit = new Panel();
    middleEdit.setLayout(new BorderLayout());
    middleEdit.add(tfMove, "Center");
    Button add = new Button("Queue Command");
    add.addActionListener(new NewMoveListener());
    middleEdit.add(add, "East");

    
    Panel bottomEdit=new Panel();
    bottomEdit.add(new Label("Move No"));
    bottomEdit.add(tfNo);

    Button edit = new Button("Edit");
    Button update = new Button("Update");
    Button delete = new Button("Delete");
    
    edit.addActionListener(new EditButtonListener());          
    update.addActionListener(new UpdateButtonListener());
    delete.addActionListener(new DeleteButtonListener());
    bottomEdit.add(edit);
    bottomEdit.add(update);
    bottomEdit.add(delete);
           
    Panel editPanel = new Panel();
    editPanel.setLayout(new BorderLayout());
    editPanel.add(topEdit, "North");
    editPanel.add(middleEdit, "Center");
    editPanel.add(bottomEdit, "South");
    
    add(editPanel, "North");
    
    Panel ptac = new Panel(new BorderLayout());
    ptac.add(new Label("Robot Commands"), "North");
    ptac.add(taRobotCommands, "Center");

    Panel p = new Panel();
    p.setLayout(new BorderLayout());
    p.add(lRobotPosition, "North");
    p.add(lController, "South");
    ptac.add(p, "South");
    add(ptac, "Center");
           
    p = new Panel();
    p.add(bSend);
    p.add(bControl);
    add("South", p);

    
    bSend.setEnabled(false);
    bControl.setEnabled(false);
    taRobotCommands.setEditable(false);
    
    bSend.addActionListener(new SendButtonListener());
    bControl.addActionListener(new ControlButtonListener());
    //tfMove.addActionListener(new NewMoveListener());


  }

  public void appendTextCommand(String command){
    tfMove.setText(tfMove.getText()+" "+command);
  }
  
    
  public void setRobotHandler(RobotHandler robotHandler)
  {
    this.robotHandler = robotHandler;
    robotHandler.addRobotListener(this);
    this.commands     = robotHandler.getCommands();
    commands.observable().addObserver(new CommandListener());
  }
  
  
  public RobotHandler getRobotHandler() {
    return robotHandler;
  }
  
  
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


  protected void setCurrentCommand(Command command)
  {
    currentCommand = command;
    if (command == null){
      tfNo.setText("");
      tfMove.setText("");
    } else
      tfMove.setText(command.toEditableString());
    
  }
  
  
  public static void main (String[] args)
  {
    Frame f = new Frame();
    CommandPanel cp=new CommandPanelMC();
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
      int pos =taRobotCommands.getCaretPosition();
      StringBuffer text=new StringBuffer();
      for (int i=1;i<=commands.getMax();i++){
        Command c=commands.getCommand(i);
        if (c!=null)
          text.append(c.toUserString());
      }
      
      taRobotCommands.setText(text.toString());
      taRobotCommands.setCaretPosition(pos);
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
        robotHandler.sendCommands();
      } catch (RobotException ex){
        ex.printStackTrace();
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
        ex.printStackTrace();
      }
    }
  }
    
  class NewMoveListener implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {
      commands.setCommand(new MMoveCommand(tfMove.getText()));
      setCurrentCommand(null);
    }
  }

      
  class EditButtonListener implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {
      try{
        int no = Integer.parseInt(tfNo.getText());
        setCurrentCommand(commands.getCommand(no));
      }
      catch (NumberFormatException ne){
        TheConsole.println("Type a valid move number first");
      }
    }
  }

  class UpdateButtonListener implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {
      TheLog.println("Setting current command to "+tfMove.getText());
      if (currentCommand != null){
        currentCommand.setFrom(tfMove.getText());
        setCurrentCommand(null);
      }
      else
        TheConsole.println("No move to edit");
    }
  }

  class DeleteButtonListener implements ActionListener
  {
    public void actionPerformed(ActionEvent e) {
      try{
        int no = Integer.parseInt(tfNo.getText());
        setCurrentCommand(commands.getCommand(no));
        if(currentCommand !=null)
          commands.delete(currentCommand);
        setCurrentCommand(null);
      }
      catch (NumberFormatException ne){
        TheConsole.println("Type a valid move number first");
      }
    }
  }
}



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


