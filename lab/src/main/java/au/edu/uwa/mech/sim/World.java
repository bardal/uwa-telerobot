package au.edu.uwa.mech.sim;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
  /** The most pieces the table holds. */
  public static final int MAX_BLOCKS = 20;
  /** Where random pieces are put: the whole grid less a margin (all three cameras see it, the arm reaches it). */
  public static final double AREA_MIN_X = 20, AREA_MAX_X = Renderer.GRID_MAX - 20,
      AREA_MIN_Y = 20, AREA_MAX_Y = Renderer.GRID_MAX - 20;
  /** Space kept clear around a randomly placed piece, in mm. */
  static final double CLEARANCE = 5;
  private static final int TRIES = 200;

  static final Color RED = new Color(196, 48, 40);
  static final Color BLUE = new Color(40, 80, 180);
  static final Color YELLOW = new Color(225, 190, 40);
  static final Color GREEN = new Color(50, 150, 70);
  static final Color WOOD = new Color(180, 130, 80);
  private static final Color[] COLOURS = {RED, BLUE, YELLOW, GREEN, WOOD};

  public interface Listener { void changed(World w); }

  /** What a block command did: ok with what happened ("added a roof"), or not with why not. */
  public static final class Change
  {
    private final boolean ok;
    private final String text;

    Change(boolean ok, String text)
    {
      this.ok = ok;
      this.text = text;
    }

    public boolean ok() { return ok; }
    public String text() { return text; }
  }

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
  private final Random random;
  private int added;   // numbers the names of added pieces

  World(Random random)
  {
    this.random = random;
  }

  public static World withDefaultBlocks()
  {
    return withDefaultBlocks(new Random());
  }

  static World withDefaultBlocks(Random random)
  {
    World w = new World(random);
    w.addDefaultBlocks();
    return w;
  }

  /** The standard table: the original five where they always stood, and ten more pieces around them. */
  private void addDefaultBlocks()
  {
    addBlock(new Block("red",    120, 150, 0,   0, 30, 30, 30, RED));
    addBlock(new Block("blue",   330, 120, 0,  20, 30, 30, 30, BLUE));
    addBlock(new Block("yellow", 200, 360, 0, -15, 30, 30, 30, YELLOW));
    addBlock(new Block("green",  400, 330, 0,  45, 30, 30, 30, GREEN));
    addBlock(new Block("bar",    260, 200, 0,  70, 60, 30, 30, WOOD));
    addBlock(make("column1", Shape.COLUMN, 160, 250, 0, BLUE));
    addBlock(make("column2", Shape.COLUMN, 220, 250, 0, BLUE));
    addBlock(make("plank", Shape.BAR, 300, 280, 0, WOOD));
    addBlock(make("roof1", Shape.ROOF, 370, 190, 0, RED));
    addBlock(make("roof2", Shape.ROOF, 120, 330, 90, RED));
    addBlock(make("arch1", Shape.ARCH, 420, 255, 90, YELLOW));
    addBlock(make("arch2", Shape.ARCH, 180, 100, 0, GREEN));
    addBlock(make("cylinder1", Shape.CYLINDER, 260, 360, 0, GREEN));
    addBlock(make("cylinder2", Shape.CYLINDER, 340, 370, 0, YELLOW));
    addBlock(make("cube1", Shape.CUBE, 100, 220, 0, GREEN));
  }

  /** A piece of the standard size for its shape (a bar made this way is the long, 90 mm one). */
  private static Block make(String name, Shape shape, double x, double y, double yawDeg, Color colour)
  {
    double[] size;
    switch (shape) {
      case BAR: case ARCH: size = new double[] {90, 30, 30}; break;
      case COLUMN:         size = new double[] {30, 30, 60}; break;
      case ROOF:           size = new double[] {60, 30, 30}; break;
      default:             size = new double[] {30, 30, 30}; break;   // cube, cylinder
    }
    return new Block(name, shape, x, y, 0, yawDeg, size[0], size[1], size[2], colour);
  }

  // ---- block commands ------------------------------------------------------------------------

  /** One more random piece in a free spot, unless the arm is moving or the table is full. */
  public Change addRandom()
  {
    Change c;
    synchronized (this) {
      c = refusal(false);
      if (c == null) {
        String full = "the table is full (" + MAX_BLOCKS + " blocks)";
        if (blocks.size() >= MAX_BLOCKS)
          return new Change(false, full);
        Shape shape = Shape.values()[random.nextInt(Shape.values().length)];
        Block b = make(shape.noun() + (++added), shape, 0, 0, 0, COLOURS[random.nextInt(COLOURS.length)]);
        if (shape == Shape.BAR && random.nextBoolean())
          b = new Block(b.name, Shape.BAR, 0, 0, 0, 0, 60, 30, 30, b.colour);
        if (!placeFreely(b, others(null)))
          return new Change(false, full);
        b.z = 0;
        blocks.add(b);
        c = new Change(true, "added a " + shape.noun());
      }
    }
    if (c.ok())
      fire();
    return c;
  }

  /** Every piece laid flat at a random free spot and turn. */
  public Change randomise()
  {
    Change c;
    synchronized (this) {
      c = refusal(true);
      if (c == null) {
        List<Block> placed = new ArrayList<Block>();
        for (int attempt = 0; attempt < TRIES && placed.size() < blocks.size(); attempt++) {
          placed.clear();
          for (Block b : blocks)
            if (placeFreely(b, placed))
              placed.add(b);
            else
              break;
        }
        for (Block b : blocks)
          b.z = 0;
        c = new Change(true, "randomised the table");
      }
    }
    if (c.ok())
      fire();
    return c;
  }

  /** Back to the standard table; the arm stays where it is. */
  public Change resetBlocks()
  {
    Change c;
    synchronized (this) {
      c = refusal(true);
      if (c == null) {
        blocks.clear();
        added = 0;
        addDefaultBlocks();
        c = new Change(true, "reset the blocks");
      }
    }
    if (c.ok())
      fire();
    return c;
  }

  /** Why a block command can't run now, or null. A held piece only stops commands that move every piece. */
  private Change refusal(boolean movesHeld)
  {
    if (moving)
      return new Change(false, "wait for the arm to stop");
    if (movesHeld && held != null)
      return new Change(false, "put the block down first");
    return null;
  }

  /** The pieces a new one must keep clear of: all but the held one and the one being placed. */
  private List<Block> others(Block except)
  {
    List<Block> list = new ArrayList<Block>();
    for (Block b : blocks)
      if (b != except && b != held)
        list.add(b);
    return list;
  }

  /** Moves b to a random spot and turn in the area clear of every block in avoid; false if none was found. */
  private boolean placeFreely(Block b, List<Block> avoid)
  {
    double r = Math.hypot(b.sx, b.sy) / 2;
    for (int i = 0; i < TRIES; i++) {
      b.yaw = random.nextDouble() * Math.PI;
      b.x = AREA_MIN_X + r + random.nextDouble() * (AREA_MAX_X - AREA_MIN_X - 2 * r);
      b.y = AREA_MIN_Y + r + random.nextDouble() * (AREA_MAX_Y - AREA_MIN_Y - 2 * r);
      boolean clear = true;
      for (Block o : avoid)
        if (o != b && b.footprintOverlaps(o, CLEARANCE / 2)) {
          clear = false;
          break;
        }
      if (clear)
        return true;
    }
    return false;
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
    b.z = supportHeight(b);
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
        held.z = supportHeight(held);
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

  /** Height of the highest surface anywhere under piece's base (so a bar can span two columns); roofs don't count. */
  private double supportHeight(Block piece)
  {
    double h = 0;
    for (Block b : blocks) {
      if (b == piece || !b.supports() || b.top() <= h)
        continue;
      for (double[] p : piece.footprintSamples())
        if (b.footprintContains(p[0], p[1], 0)) {
          h = b.top();
          break;
        }
    }
    return h;
  }

  private void fire()
  {
    for (Listener l : listeners)
      l.changed(this);
  }
}
