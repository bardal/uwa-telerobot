package au.edu.uwa.mech.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.CharArrayReader;
import java.io.File;
import java.io.FileWriter;

import org.junit.Test;

import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.io.ASCIIReader;
import au.edu.uwa.mech.mom.impl.IniUserDatabase;
import au.edu.uwa.mech.shared.Result;

/** The original client code must parse what the virtual robot sends. */
public class ProtocolParsingTest
{
  @Test
  public void resultLines() throws Exception
  {
    Result r = new Result();
    r.read("1 0 \"Move completed\"");
    assertTrue(r.OK());
    assertEquals("Move completed", r.getText());
    r.read("4 2 \"z=0 is outside the workspace [5, 450]\"");
    assertTrue(r.Error());
    assertEquals(2, r.MinorCode());
  }

  @Test
  public void positionLines() throws Exception
  {
    String line = new RobotPose(150, 300, 60, -90, 10, false).toPositionLine(true);
    RobotState s = new RobotState();
    s.readDeg(new ASCIIReader(new CharArrayReader(line.toCharArray())));
    assertEquals(150, s.pose.position.x, 1e-3);
    assertEquals(60, s.pose.position.z, 1e-3);
    assertEquals(-90, s.pose.orientation.spinDeg(), 1e-3);
    assertEquals(10, s.pose.orientation.tiltDeg(), 1e-3);
    assertFalse(s.gripper.open());
    assertTrue(s.moving());
  }

  /** Non-numbers raise NumberFormatException, which the robot Client deliberately ignores. */
  @Test(expected = NumberFormatException.class)
  public void replyIsNotAPosition() throws Exception
  {
    String reply = "1 0 \"Move completed\"";
    new RobotState().readDeg(new ASCIIReader(new CharArrayReader(reply.toCharArray())));
  }

  @Test
  public void userDatabase() throws Exception
  {
    File f = File.createTempFile("users", ".ini");
    f.deleteOnExit();
    try (FileWriter w = new FileWriter(f)) {
      w.write("; comment\n[Guest]\nPIN=-1\nUserLevel=Guest\n\n[team]\npin=1234\nUserLevel=Team\n");
    }
    IniUserDatabase db = new IniUserDatabase();
    db.setDatabaseFile(f.getPath());
    assertEquals("-1", db.getStringField("guest", "PIN"));
    assertEquals(1234, db.getIntField("TEAM", "PIN"));
    assertEquals("NULL", db.getStringField("nobody", "PIN"));
    assertEquals("NULL", db.getStringField("team", "email"));
  }
}
