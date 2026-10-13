package au.edu.uwa.mech.util;

/** Minimal debug output helpers. Enable with -Dtelerobot.debug=true. */
public final class Debug
{
  public static boolean enabled = Boolean.getBoolean("telerobot.debug");

  private Debug() {}

  public static void println(String s)
  {
    if (enabled)
      System.err.println(s);
  }

  public static void println(String s, boolean show)
  {
    if (show)
      System.err.println(s);
  }

  /** Marks something that is "to be implemented". */
  public static void tbi()
  {
    StackTraceElement[] st = new Throwable().getStackTrace();
    println("not implemented yet: " + (st.length > 1 ? st[1] : "?"));
  }

  public static void trace(Throwable t, String context)
  {
    System.err.println(context + ": " + t);
    if (enabled)
      t.printStackTrace();
  }
}
