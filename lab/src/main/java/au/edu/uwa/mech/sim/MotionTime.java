package au.edu.uwa.mech.sim;

/**
 * How the simulated arm passes time while it moves: {@link #REAL} on the lab, a {@link SteppedTime}
 * in tests, so a move takes no wall-clock time and a test can hold the arm mid-move.
 */
public interface MotionTime
{
  /** One animation step of a move or a gripper action. */
  long STEP_NANOS = 40_000_000L;

  /** Now, in nanoseconds from an arbitrary origin. */
  long nanos();

  /** Wait until the next step is due ({@link #STEP_NANOS} later). */
  void step() throws InterruptedException;

  MotionTime REAL = new MotionTime()
  {
    public long nanos() { return System.nanoTime(); }

    public void step() throws InterruptedException { Thread.sleep(STEP_NANOS / 1_000_000); }
  };
}
