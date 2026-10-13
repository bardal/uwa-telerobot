package au.edu.uwa.mech.sim;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The simulated workcell: the gripper pose, whether the arm is moving, and
 * the blocks on the table. Closing the gripper around a block picks it up;
 * opening it drops the block onto whatever is below.
 */
public class World
{
  /** Finger gap when fully open, in mm. */
  public static final double OPEN_GAP = 70;

  public interface Listener { void changed(World w); }

  /** A consistent copy of the world for rendering. */
  public static final class Snapshot
  {
    public final RobotPose pose;
    public final boolean moving;
    public final double fingerGap;
    public final List<Block> blocks;
    public final long time;

    Snapshot(RobotPose pose, boolean moving, double fingerGap, List<Block> blocks)
    {
      this.pose = pose;
      this.moving = moving;
      this.fingerGap = fingerGap;
      this.blocks = blocks;
      this.time = System.currentTimeMillis();
    }
  }

  private RobotPose pose = new RobotPose(250, 250, 100, -90, 0, true);
  private boolean moving;
  private double fingerGap = OPEN_GAP;
  private final List<Block> blocks = new ArrayList<Block>();
  private Block held;
  private double heldDz, heldDyaw;   // held block's base relative to the tool point
  private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

  public static World withDefaultBlocks()
  {
    World w = new World();
    w.addDefaultBlocks();
    return w;
  }

  private void addDefaultBlocks()
  {
    addBlock(new Block("red",    120, 150, 0,   0, 30, 30, 30, new Color(196, 48, 40)));
    addBlock(new Block("blue",   330, 120, 0,  20, 30, 30, 30, new Color(40, 80, 180)));
    addBlock(new Block("yellow", 200, 360, 0, -15, 30, 30, 30, new Color(225, 190, 40)));
    addBlock(new Block("green",  400, 330, 0,  45, 30, 30, 30, new Color(50, 150, 70)));
    addBlock(new Block("bar",    260, 200, 0,  70, 60, 30, 30, new Color(180, 130, 80)));
  }

  /** Back to the start: default blocks, the arm at (250,250,100) open, fingers open. Listeners are told. */
  public void reset()
  {
    synchronized (this) {
      held = null;
      blocks.clear();
      addDefaultBlocks();
      pose = new RobotPose(250, 250, 100, -90, 0, true);
      moving = false;
      fingerGap = OPEN_GAP;
    }
    fire();
  }

  public synchronized void addBlock(Block b)
  {
    b.z = supportHeight(b.x, b.y, b);
    blocks.add(b);
  }

  public void addListener(Listener l) { listeners.add(l); }

  public synchronized RobotPose getPose() { return pose; }

  public synchronized boolean isMoving() { return moving; }

  public synchronized Snapshot snapshot()
  {
    List<Block> copies = new ArrayList<Block>();
    for (Block b : blocks)
      copies.add(b.copy());
    return new Snapshot(pose, moving, fingerGap, copies);
  }

  /** Move the gripper (an intermediate point of a motion); a held block comes along. */
  public void setPose(RobotPose p, boolean moving)
  {
    synchronized (this) {
      pose = p;
      this.moving = moving;
      if (held != null) {
        held.x = p.x;
        held.y = p.y;
        held.z = p.z + heldDz;
        held.yaw = p.spinRadians() + heldDyaw;
      }
    }
    fire();
  }

  /** Set the finger gap during a gripper motion (0..OPEN_GAP). */
  public void setFingerGap(double gap)
  {
    synchronized (this) {
      fingerGap = gap;
    }
    fire();
  }

  /** How far the fingers can close from here: the width of a block between them, else 0. */
  public synchronized double closedGap()
  {
    Block b = graspable();
    return b == null ? 0 : b.widthAlong(pose.spinRadians());
  }

  /** The gripper has finished closing: pick up the block between the fingers, if any. */
  public void closed()
  {
    synchronized (this) {
      Block b = graspable();
      if (b != null && held == null) {
        held = b;
        heldDz = b.z - pose.z;
        heldDyaw = b.yaw - pose.spinRadians();
      }
    }
    fire();
  }

  /** The gripper has opened: drop anything held onto whatever is below it. */
  public void opened()
  {
    synchronized (this) {
      if (held != null) {
        held.z = supportHeight(held.x, held.y, held);
        held = null;
      }
    }
    fire();
  }

  /** The block the fingers would close on: under the tool point, spanning its height. */
  private Block graspable()
  {
    if (held != null)
      return held;
    if (Math.abs(pose.tilt) > 20)
      return null;
    Block best = null;
    for (Block b : blocks)
      if (b.footprintContains(pose.x, pose.y, -2)
          && pose.z > b.z + 3 && pose.z < b.top() + 5
          && b.widthAlong(pose.spinRadians()) < OPEN_GAP - 4
          && (best == null || b.top() > best.top()))
        best = b;
    return best;
  }

  /** Height of the highest surface under (x, y), ignoring one block. */
  private double supportHeight(double x, double y, Block ignore)
  {
    double h = 0;
    for (Block b : blocks)
      if (b != ignore && b.footprintContains(x, y, 0) && b.top() > h)
        h = b.top();
    return h;
  }

  private void fire()
  {
    for (Listener l : listeners)
      l.changed(this);
  }
}
