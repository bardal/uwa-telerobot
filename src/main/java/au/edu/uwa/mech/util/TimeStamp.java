
package au.edu.uwa.mech.util;


public class TimeStamp
{
    private long time;
    
    public TimeStamp() {
        time = System.currentTimeMillis();
    }
    
    public void stamp(String msg) {
        TheLog.println("[" + time() + "ms\t"+ msg + "]");
    }
    
    public long time() {
        long t = System.currentTimeMillis();
        long res = t - time;
        time = t;
        return res;
    }
}
