package au.edu.uwa.mech.sim;

/**
 * Motion time for tests: each {@link #step} advances the clock by {@link MotionTime#STEP_NANOS} at once,
 * so a move runs through all its poses without waiting. {@link #hold} stops the arm at its next step
 * (it stays "moving" where it is) until {@link #release}.
 */
public final class SteppedTime implements MotionTime
{
  private long nanos;
  private boolean held;
  private int waiting;

  public synchronized long nanos() { return nanos; }

  public synchronized void step() throws InterruptedException
  {
    waiting++;
    notifyAll();
    try {
      while (held)
        wait();
    } finally {
      waiting--;
    }
    nanos += STEP_NANOS;
  }

  /** The arm stops at its next step until {@link #release}. */
  public synchronized void hold() { held = true; }

  public synchronized void release()
  {
    held = false;
    notifyAll();
  }

  /** Waits (at most {@code millis}) until a move is stopped at a step by {@link #hold}; true if one is. */
  public synchronized boolean awaitHeld(long millis) throws InterruptedException
  {
    long end = System.currentTimeMillis() + millis;
    while (!(held && waiting > 0)) {
      long left = end - System.currentTimeMillis();
      if (left <= 0)
        return false;
      wait(left);
    }
    return true;
  }
}
