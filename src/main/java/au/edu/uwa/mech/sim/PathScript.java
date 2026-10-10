package au.edu.uwa.mech.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses GBH path scripts, following RobotServer's TPathScriptParser:
 * waypoints are separated by ';', space or newline, axes within a waypoint
 * by ','. Axes are x y z (mm), s (spin), t (tilt, or tf / tv / tb for
 * -45 / 0 / 45) and g (g0/go = open, g1/gc = closed; also bare o / c).
 * An 'r' before an axis makes that value relative to the current pose.
 */
public final class PathScript
{
  private PathScript() {}

  public static List<RobotPose> parse(String script, RobotPose start)
  {
    List<RobotPose> path = new ArrayList<RobotPose>();
    RobotPose cur = start;
    boolean pending = false, relative = false;
    int[] i = {0};
    int n = script.length();
    while (i[0] < n) {
      char ch = script.charAt(i[0]++);
      switch (ch) {
        case 'r':
          relative = true;
          continue;
        case '\r': case '\n': case ' ': case ';':
          path.add(cur);
          pending = false;
          relative = false;
          while (i[0] < n && !Character.isLetterOrDigit(script.charAt(i[0])))
            i[0]++;
          break;
        case ',':
          relative = false;
          break;
        case 'x': case 'y': case 'z': case 's':
          cur = cur.with(ch, number(script, i) + (relative ? cur.get(ch) : 0));
          pending = true;
          break;
        case 't': {
          char next = i[0] < n ? script.charAt(i[0]) : '\0';
          if (next == 'f' || next == 'v' || next == 'b') {
            i[0]++;
            cur = cur.with('t', next == 'f' ? -45 : next == 'v' ? 0 : 45);
          } else
            cur = cur.with('t', number(script, i) + (relative ? cur.tilt : 0));
          pending = true;
          break;
        }
        case 'g': {
          char next = i[0] < n ? script.charAt(i[0]) : '\0';
          if ("0Oo".indexOf(next) >= 0)
            cur = cur.withGripper(true);
          else if ("1Cc".indexOf(next) >= 0)
            cur = cur.withGripper(false);
          while (i[0] < n && Character.isLetterOrDigit(script.charAt(i[0])))
            i[0]++;
          pending = true;
          break;
        }
        case 'o': case 'c':
          cur = cur.withGripper(ch == 'o');
          while (i[0] < n && Character.isLetterOrDigit(script.charAt(i[0])))
            i[0]++;
          pending = true;
          break;
        default:
          break;
      }
    }
    if (pending)
      path.add(cur);
    // a separator adds the current pose, so consecutive repeats are dropped
    List<RobotPose> out = new ArrayList<RobotPose>();
    for (RobotPose p : path)
      if (out.isEmpty() || !out.get(out.size() - 1).equals(p))
        out.add(p);
    return out;
  }

  /** Integer argument: skip to the next digit or sign, read an optional sign and digits. */
  private static int number(String s, int[] i)
  {
    int n = s.length();
    while (i[0] < n && !(Character.isDigit(s.charAt(i[0])) || s.charAt(i[0]) == '-' || s.charAt(i[0]) == '+'))
      i[0]++;
    int start = i[0];
    if (i[0] < n && (s.charAt(i[0]) == '-' || s.charAt(i[0]) == '+'))
      i[0]++;
    while (i[0] < n && Character.isDigit(s.charAt(i[0])))
      i[0]++;
    try {
      return Integer.parseInt(s.substring(start, i[0]).replace("+", ""));
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
