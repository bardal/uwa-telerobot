package au.edu.uwa.mech.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.Status;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import Jama.Matrix;
import au.edu.uwa.mech.devices.camera.ImageData;
import au.edu.uwa.mech.devices.robot.RobotState;
import au.edu.uwa.mech.mom.MOMPacket;
import au.edu.uwa.mech.mom.Packet;

/** Needs java.io.ObjectInputFilter (Java 9+); excluded from the test compile on JDK 8 by the lab pom. */
public class SerialFilterTest
{
  private ObjectInputFilter filter;

  @Before
  public void setUp()
  {
    Assume.assumeTrue(Double.parseDouble(System.getProperty("java.specification.version")) >= 9);
    filter = ObjectInputFilter.Config.createFilter(SerialFilter.PATTERN);
  }

  private Status check(Class<?> c)
  {
    return filter.checkInput(new ObjectInputFilter.FilterInfo() {
      public Class<?> serialClass() { return c; }
      public long arrayLength() { return -1; }
      public long depth() { return 1; }
      public long references() { return 1; }
      public long streamBytes() { return 1; }
    });
  }

  private static Class<?> named(String name) throws ClassNotFoundException
  {
    return Class.forName(name, false, SerialFilterTest.class.getClassLoader());
  }

  @Test
  public void gadgetClassesAreRejected() throws Exception
  {
    for (String n : new String[] {"javax.management.BadAttributeValueExpException", "java.net.URL"})
      assertEquals(n, Status.REJECTED, check(named(n)));
    // not on the classpath (and not meant to be), so these are checked by name
    assertEquals(Status.REJECTED, checkName("java.rmi.server.UnicastRef"));
    assertEquals(Status.REJECTED, checkName("org.apache.commons.collections.functors.InvokerTransformer"));
    assertEquals(Status.REJECTED, checkName("sun.reflect.annotation.AnnotationInvocationHandler"));
  }

  /** Check a class by name: through the real filter if it is loadable, else by the pattern's allow rules. */
  private Status checkName(String name)
  {
    try {
      return check(Class.forName(name, false, SerialFilterTest.class.getClassLoader()));
    } catch (ClassNotFoundException e) {
      for (String rule : SerialFilter.PATTERN.split(";")) {
        if (rule.startsWith("!"))
          continue;
        String prefix = rule.endsWith("**") ? rule.substring(0, rule.length() - 2)
                      : rule.endsWith("*") ? rule.substring(0, rule.length() - 1) : null;
        if (name.equals(rule) || (prefix != null && name.startsWith(prefix) && !prefix.isEmpty()))
          return Status.ALLOWED;
      }
      return Status.REJECTED;
    }
  }

  @Test
  public void thisProgramsClassesAreAllowed() throws Exception
  {
    for (Class<?> c : new Class<?>[] {RobotState.class, ImageData.class, Matrix.class, java.util.Vector.class,
                                      String.class})
      assertEquals(c.getName(), Status.ALLOWED, check(c));
    // the JDK leaves primitive arrays undecided (not rejected), which a stream accepts
    assertTrue(check(byte[].class) != Status.REJECTED);
    assertTrue(check(char[].class) != Status.REJECTED);
  }

  private static byte[] write(Object o) throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(o);
    }
    return bytes.toByteArray();
  }

  private Object read(byte[] data) throws Exception
  {
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
      in.setObjectInputFilter(filter);
      return in.readObject();
    }
  }

  @Test
  public void realTrafficRoundTrips() throws Exception
  {
    ImageData image = new ImageData();
    image.filename = "a.jpg";
    image.url = "http://localhost/a.jpg";
    image.width = 400;
    image.height = 300;
    image.calibrationMatrix = new Matrix(new double[][] {{1, 2, 3, 4}, {5, 6, 7, 8}, {9, 10, 11, 12}});
    image.pixels = new byte[] {1, 2, 3};
    ImageData image2 = (ImageData) read(write(image));
    assertEquals(image.filename, image2.filename);
    assertEquals(image.url, image2.url);
    assertEquals(image.width, image2.width);
    assertEquals(image.height, image2.height);
    assertArrayEquals(image.calibrationMatrix.getRowPackedCopy(), image2.calibrationMatrix.getRowPackedCopy(), 0);
    assertArrayEquals(image.pixels, image2.pixels);

    RobotState state = RobotState.random();
    assertEquals(state.toString(), ((RobotState) read(write(state))).toString());

    MOMPacket mp = new MOMPacket(new Packet());
    mp.setFamily(MOMPacket.Values.EVENT);
    mp.setName("chat");
    mp.setArg("hello robot".toCharArray());
    Packet packet2 = (Packet) read(write(mp.getPacket()));
    assertEquals(MOMPacket.Values.EVENT, packet2.getProperty(MOMPacket.Keys.FAMILY));
    assertEquals("chat", packet2.getProperty(MOMPacket.Keys.NAME));
    assertArrayEquals("hello robot".toCharArray(), (char[]) packet2.getProperty(MOMPacket.Keys.ARG));
    assertTrue(packet2.getProperties().size() >= 3);
  }
}
