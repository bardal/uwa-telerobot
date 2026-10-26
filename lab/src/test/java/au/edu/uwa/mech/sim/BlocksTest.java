package au.edu.uwa.mech.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class BlocksTest
{
  private static final Color GREY = new Color(128, 128, 128);

  private static Block find(World w, String name)
  {
    for (Block b : w.snapshot().blocks)
      if (b.name.equals(name))
        return b;
    throw new AssertionError(name);
  }

  private static World empty()
  {
    return new World(new Random(1));
  }

  private static Block column(String name, double x, double y)
  {
    return new Block(name, Shape.COLUMN, x, y, 0, 0, 30, 30, 60, GREY);
  }

  @Test
  public void standardTableHasOriginalFiveAndAboutFifteen()
  {
    World w = World.withDefaultBlocks(new Random(1));
    List<Block> blocks = w.snapshot().blocks;
    assertTrue("count " + blocks.size(), blocks.size() >= 12 && blocks.size() <= 15);
    assertAt(find(w, "red"), 120, 150, 0);
    assertAt(find(w, "blue"), 330, 120, 20);
    assertAt(find(w, "yellow"), 200, 360, -15);
    assertAt(find(w, "green"), 400, 330, 45);
    assertAt(find(w, "bar"), 260, 200, 70);
    Set<Shape> kinds = EnumSet.noneOf(Shape.class);
    for (Block b : blocks)
      kinds.add(b.shape);
    assertEquals(EnumSet.allOf(Shape.class), kinds);
    assertNoOverlaps(blocks);
  }

  private static void assertAt(Block b, double x, double y, double yawDeg)
  {
    assertEquals(b.name + " x", x, b.getX(), 1e-6);
    assertEquals(b.name + " y", y, b.getY(), 1e-6);
    assertEquals(b.name + " yaw", Math.toRadians(yawDeg), b.getYaw(), 1e-6);
    assertEquals(b.name + " z", 0, b.getZ(), 1e-6);
  }

  @Test
  public void barAcrossTwoColumnsRestsOnTop()
  {
    World w = empty();
    w.addBlock(column("left", 200, 250));
    w.addBlock(column("right", 260, 250));
    w.addBlock(new Block("plank", Shape.BAR, 230, 250, 0, 0, 90, 30, 30, GREY));
    assertEquals(60, find(w, "plank").getZ(), 1e-6);
  }

  @Test
  public void gripperLaysBarAcrossColumns()
  {
    World w = empty();
    w.addBlock(column("left", 200, 250));
    w.addBlock(column("right", 260, 250));
    w.addBlock(new Block("plank", Shape.BAR, 230, 150, 0, 0, 90, 30, 30, GREY));
    VirtualRobotServer robot = new VirtualRobotServer(w, new SteppedTime());
    String reply = robot.execute("gbh move x230,y150,z60,s0,t0,g0;z15;g1;z120;y250;z95;g0;z130");
    assertEquals("1 0 \"Move completed\"", reply);
    Block plank = find(w, "plank");
    assertEquals(250, plank.getY(), 1e-6);
    assertEquals(60, plank.getZ(), 1e-6);
  }

  @Test
  public void nothingRestsOnARoof()
  {
    World w = empty();
    w.addBlock(new Block("roof", Shape.ROOF, 300, 300, 0, 0, 60, 30, 30, GREY));
    w.addBlock(new Block("cube", Shape.CUBE, 300, 300, 0, 0, 30, 30, 30, GREY));
    assertEquals(0, find(w, "cube").getZ(), 1e-6);
  }

  @Test
  public void addRandomStopsAtTwenty()
  {
    World w = World.withDefaultBlocks(new Random(2));
    while (w.snapshot().blocks.size() < World.MAX_BLOCKS) {
      World.Change c = w.addRandom();
      assertTrue(c.text(), c.ok());
      assertTrue(c.text(), c.text().startsWith("added a"));
    }
    World.Change full = w.addRandom();
    assertFalse(full.ok());
    assertEquals("the table is full (20 blocks)", full.text());
    assertEquals(World.MAX_BLOCKS, w.snapshot().blocks.size());
  }

  @Test
  public void randomiseTwentyHasNoOverlaps()
  {
    World w = World.withDefaultBlocks(new Random(3));
    while (w.snapshot().blocks.size() < World.MAX_BLOCKS)
      w.addRandom();
    World.Change c = w.randomise();
    assertTrue(c.ok());
    assertEquals("randomised the table", c.text());
    List<Block> blocks = w.snapshot().blocks;
    assertEquals(World.MAX_BLOCKS, blocks.size());
    for (Block b : blocks) {
      assertEquals(b.name + " z", 0, b.getZ(), 1e-6);
      assertTrue(b.name + " x " + b.getX(), b.getX() >= World.AREA_MIN_X && b.getX() <= World.AREA_MAX_X);
      assertTrue(b.name + " y " + b.getY(), b.getY() >= World.AREA_MIN_Y && b.getY() <= World.AREA_MAX_Y);
    }
    assertNoOverlaps(blocks);
    assertFalse("red moved", Math.abs(find(w, "red").getX() - 120) < 1e-6 && Math.abs(find(w, "red").getY() - 150) < 1e-6);
  }

  @Test
  public void resetBlocksKeepsTheArm()
  {
    World w = World.withDefaultBlocks(new Random(4));
    w.randomise();
    w.addRandom();
    RobotPose arm = new RobotPose(300, 200, 150, -45, 0, true);
    w.setPose(arm, false);
    World.Change c = w.resetBlocks();
    assertTrue(c.ok());
    assertEquals("reset the blocks", c.text());
    assertEquals(World.withDefaultBlocks(new Random(4)).snapshot().blocks.size(), w.snapshot().blocks.size());
    assertAt(find(w, "red"), 120, 150, 0);
    assertEquals(arm, w.getPose());
  }

  @Test
  public void refusedWhileMoving()
  {
    World w = World.withDefaultBlocks(new Random(5));
    w.setPose(new RobotPose(250, 250, 100, -90, 0, true), true);
    int before = w.snapshot().blocks.size();
    for (World.Change c : Arrays.asList(w.addRandom(), w.randomise(), w.resetBlocks())) {
      assertFalse(c.ok());
      assertEquals("wait for the arm to stop", c.text());
    }
    assertEquals(before, w.snapshot().blocks.size());
  }

  @Test
  public void randomiseAndResetRefusedWhileHolding()
  {
    World w = World.withDefaultBlocks(new Random(6));
    VirtualRobotServer robot = new VirtualRobotServer(w, new SteppedTime());
    robot.execute("gbh move x120,y150,z60,s-90,t0,g0;z15;g1;z80");
    assertEquals(65, find(w, "red").getZ(), 1e-6);
    for (World.Change c : Arrays.asList(w.randomise(), w.resetBlocks())) {
      assertFalse(c.ok());
      assertEquals("put the block down first", c.text());
    }
    assertEquals(65, find(w, "red").getZ(), 1e-6);
    assertTrue(w.addRandom().ok());
  }

  @Test
  public void eachChangeTellsListenersOnce()
  {
    World w = World.withDefaultBlocks(new Random(7));
    AtomicInteger told = new AtomicInteger();
    w.addListener(x -> told.incrementAndGet());
    w.addRandom();
    assertEquals(1, told.get());
    w.randomise();
    assertEquals(2, told.get());
    w.resetBlocks();
    assertEquals(3, told.get());
    w.setPose(new RobotPose(250, 250, 100, -90, 0, true), true);
    w.randomise();
    assertEquals("a refusal tells nobody", 4, told.get());
  }

  // ---- drawing -----------------------------------------------------------------------------

  private static List<Scene.Tri> drawn(Block b)
  {
    World w = empty();
    w.addBlock(b);
    int arm = Scene.of(empty().snapshot()).tris.size();
    List<Scene.Tri> all = Scene.of(w.snapshot()).tris;
    return all.subList(0, all.size() - arm);   // the blocks come first, then the arm
  }

  @Test
  public void everyShapeIsDrawn()
  {
    for (Shape s : Shape.values()) {
      List<Scene.Tri> t = drawn(new Block("one", s, 250, 250, 0, 0, s == Shape.CUBE || s == Shape.CYLINDER ? 30 : 60, 30,
                                          s == Shape.COLUMN ? 60 : 30, GREY));
      assertTrue(s + " drew " + t.size(), t.size() >= 8);   // a triangular prism is the fewest: 2 ends + 3 faces
    }
  }

  @Test
  public void roofRisesToARidge()
  {
    List<Scene.Tri> t = drawn(new Block("roof", Shape.ROOF, 250, 250, 0, 0, 60, 30, 30, GREY));
    double top = -1, bottom = 99;
    for (Scene.Tri tri : t)
      for (double[] p : new double[][] {tri.a, tri.b, tri.c}) {
        top = Math.max(top, p[2]);
        bottom = Math.min(bottom, p[2]);
        if (p[2] > 29.999)
          assertEquals("the ridge runs down the middle", 250, p[0], 1e-6);
      }
    assertEquals(30, top, 1e-6);
    assertEquals(0, bottom, 1e-6);
  }

  @Test
  public void archHasAnOpeningUnderneath()
  {
    double r = Scene.ARCH_RADIUS;
    List<Scene.Tri> t = drawn(new Block("arch", Shape.ARCH, 250, 250, 0, 0, 90, 30, 30, GREY));
    boolean apex = false;
    for (Scene.Tri tri : t) {
      for (double[] p : new double[][] {tri.a, tri.b, tri.c}) {
        double dx = p[0] - 250;
        assertFalse("vertex inside the opening at " + dx + "," + p[2],
                    Math.abs(dx) < r - 1e-6 && p[2] < Math.sqrt(r * r - dx * dx) - 1e-6);
        apex |= Math.abs(dx) < 1e-6 && Math.abs(p[2] - r) < 1e-6;
      }
      boolean flat = tri.a[2] == 0 && tri.b[2] == 0 && tri.c[2] == 0;
      double cx = (tri.a[0] + tri.b[0] + tri.c[0]) / 3 - 250;
      assertFalse("floor drawn under the opening", flat && Math.abs(cx) < r);
    }
    assertTrue("the top of the opening is drawn", apex);
  }

  // ---- footprint overlap by separating axes ------------------------------------------------

  static void assertNoOverlaps(List<Block> blocks)
  {
    for (int i = 0; i < blocks.size(); i++)
      for (int j = i + 1; j < blocks.size(); j++)
        assertFalse(blocks.get(i).name + " overlaps " + blocks.get(j).name, overlap(blocks.get(i), blocks.get(j)));
  }

  private static double[][] corners(Block b)
  {
    double c = Math.cos(b.getYaw()), s = Math.sin(b.getYaw());
    double[][] k = new double[4][];
    int n = 0;
    for (int sx = -1; sx <= 1; sx += 2)
      for (int sy = -1; sy <= 1; sy += 2) {
        double lx = sx * b.sx / 2, ly = sy * b.sy / 2;
        k[n++] = new double[] {b.getX() + c * lx - s * ly, b.getY() + s * lx + c * ly};
      }
    return k;
  }

  private static boolean overlap(Block a, Block b)
  {
    double[][] ca = corners(a), cb = corners(b);
    for (Block owner : new Block[] {a, b})
      for (double ang : new double[] {owner.getYaw(), owner.getYaw() + Math.PI / 2}) {
        double ax = Math.cos(ang), ay = Math.sin(ang);
        double minA = Double.MAX_VALUE, maxA = -Double.MAX_VALUE, minB = Double.MAX_VALUE, maxB = -Double.MAX_VALUE;
        for (double[] p : ca) { double d = p[0] * ax + p[1] * ay; minA = Math.min(minA, d); maxA = Math.max(maxA, d); }
        for (double[] p : cb) { double d = p[0] * ax + p[1] * ay; minB = Math.min(minB, d); maxB = Math.max(maxB, d); }
        if (maxA <= minB || maxB <= minA)
          return false;
      }
    return true;
  }
}
