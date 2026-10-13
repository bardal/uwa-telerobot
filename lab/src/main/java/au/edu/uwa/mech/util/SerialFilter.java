package au.edu.uwa.mech.util;

/**
 * The Java deserialization allowlist for every process that reads serialized
 * objects from a socket (MOM, VirtualLab, RobotProxy). It is a
 * <code>jdk.serialFilter</code> pattern: set it with
 * <code>-Djdk.serialFilter="$(java -cp telerobot.jar au.edu.uwa.mech.util.SerialFilter)"</code>.
 */
public final class SerialFilter
{
  public static final String PATTERN =
      "au.edu.uwa.mech.**;Jama.**;java.lang.*;java.util.*;java.awt.Point;java.awt.Rectangle;[*;!*";

  private SerialFilter() {}

  public static void main(String[] args)
  {
    System.out.println(PATTERN);
  }
}
