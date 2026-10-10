// End-to-end check of the local stack without the GUI: log in to MOM as a
// guest, grab the robot token, send a GBH move through RobotProxy to the
// simulator and wait for the completion reply and new position.
// Run against a stack started with start.ps1 / start.sh:
//   java -cp target/telerobot.jar;target/test-classes au.edu.uwa.mech.it.SmokeCheck

package au.edu.uwa.mech.it;

import au.edu.uwa.mech.devices.robot.Client;
import au.edu.uwa.mech.devices.robot.Command;
import au.edu.uwa.mech.devices.robot.MMoveCommand;
import au.edu.uwa.mech.maths.Pose3d;
import au.edu.uwa.mech.mom.SessionRoot;

public class SmokeCheck
{
  public static void main(String[] args) throws Exception
  {
    String url = args.length > 0 ? args[0] : "moms://guest:-1:smoketest@localhost:4444/";
    SessionRoot root = new SessionRoot(url);
    root.login();
    System.out.println("SMOKE logged in as " + root.getUs());

    Client client = new Client(root);
    client.gainControl();
    for (int i = 0; i < 50 && !client.hasController(); i++)
      Thread.sleep(100);
    System.out.println("SMOKE controller is " + client.getController());

    Pose3d target = new Pose3d(150, 300, 60, 0, 0);
    Command move = new MMoveCommand(target);
    System.out.println("SMOKE sending " + move.toRobotString());
    long t0 = System.currentTimeMillis();
    client.executeCommand(move);
    System.out.println("SMOKE command state " + move.stateToString().trim()
                       + " after " + (System.currentTimeMillis() - t0) + " ms");
    Thread.sleep(1000); // let the final position broadcast arrive
    System.out.println("SMOKE robot state " + client.getState());

    client.releaseControl();
    Thread.sleep(1000); // let the release reach MOM before we drop the connection
    System.exit(move.state() == Command.COMPLETED ? 0 : 1);
  }
}
