package au.edu.uwa.mech.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * A clock and a scheduler that only move when a test says so: {@link #millis()} for the code's
 * {@code LongSupplier} clocks, {@link #scheduler()} for its timers. {@link #advance} moves the clock
 * and runs every timer that falls due, in time order, on the calling thread.
 */
final class ManualTime implements LongSupplier {
    private long now;
    private long seq;
    private boolean shutdown;
    private final PriorityQueue<Task> queue = new PriorityQueue<>(
        Comparator.comparingLong((Task t) -> t.at).thenComparingLong(t -> t.seq));
    private final Scheduler scheduler = new Scheduler();

    ManualTime() {
        this(1_792_000_000_000L); // a plausible epoch time: ids and archive days are made from it
    }

    ManualTime(long start) {
        now = start;
    }

    @Override
    public synchronized long getAsLong() {
        return now;
    }

    LongSupplier millis() {
        return this;
    }

    ScheduledExecutorService scheduler() {
        return scheduler;
    }

    /** Timers not yet run or cancelled (periodic ones count once). */
    synchronized int pending() {
        return (int) queue.stream().filter(t -> !t.cancelled).count();
    }

    /** Moves the clock on by {@code millis}, running each timer as the clock reaches it. */
    void advance(long millis) {
        long target;
        synchronized (this) {
            target = now + millis;
        }
        while (true) {
            Task due;
            synchronized (this) {
                Task head = queue.peek();
                if (head == null || head.at > target) {
                    now = target;
                    return;
                }
                due = queue.poll();
                if (due.cancelled) continue;
                now = Math.max(now, due.at);
            }
            due.run();
        }
    }

    private synchronized Task add(Runnable body, long delayMillis, long periodMillis, boolean fixedRate) {
        if (shutdown) throw new java.util.concurrent.RejectedExecutionException("shut down");
        Task t = new Task(body, now + Math.max(0, delayMillis), periodMillis, fixedRate, seq++);
        queue.add(t);
        return t;
    }

    private final class Task implements ScheduledFuture<Object> {
        final Runnable body;
        final long periodMillis;
        final boolean fixedRate;
        final long seq;
        long at;
        volatile boolean cancelled;
        volatile boolean done;

        Task(Runnable body, long at, long periodMillis, boolean fixedRate, long seq) {
            this.body = body;
            this.at = at;
            this.periodMillis = periodMillis;
            this.fixedRate = fixedRate;
            this.seq = seq;
        }

        void run() {
            body.run();
            synchronized (ManualTime.this) {
                if (periodMillis > 0 && !cancelled && !shutdown) {
                    at = (fixedRate ? at : now) + periodMillis;
                    queue.add(this);
                } else {
                    done = true;
                }
            }
        }

        @Override public long getDelay(TimeUnit unit) {
            return unit.convert(at - getAsLong(), TimeUnit.MILLISECONDS);
        }

        @Override public int compareTo(Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override public boolean cancel(boolean mayInterrupt) {
            boolean was = !cancelled && !done;
            cancelled = true;
            return was;
        }

        @Override public boolean isCancelled() { return cancelled; }

        @Override public boolean isDone() { return done || cancelled; }

        @Override public Object get() { throw new UnsupportedOperationException(); }

        @Override public Object get(long timeout, TimeUnit unit) { throw new UnsupportedOperationException(); }
    }

    private final class Scheduler extends AbstractExecutorService implements ScheduledExecutorService {
        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return add(command, unit.toMillis(delay), 0, false);
        }

        @Override public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                TimeUnit unit) {
            return add(command, unit.toMillis(initialDelay), unit.toMillis(period), true);
        }

        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                TimeUnit unit) {
            return add(command, unit.toMillis(initialDelay), unit.toMillis(delay), false);
        }

        /** Runs at the next {@link #advance}, like a timer due now. */
        @Override public void execute(Runnable command) {
            add(command, 0, 0, false);
        }

        @Override public void shutdown() {
            synchronized (ManualTime.this) {
                shutdown = true;
            }
        }

        @Override public List<Runnable> shutdownNow() {
            synchronized (ManualTime.this) {
                shutdown = true;
                List<Runnable> left = new ArrayList<>();
                for (Task t : queue) if (!t.cancelled) left.add(t.body);
                queue.clear();
                return left;
            }
        }

        @Override public boolean isShutdown() {
            synchronized (ManualTime.this) {
                return shutdown;
            }
        }

        @Override public boolean isTerminated() {
            return isShutdown();
        }

        @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
