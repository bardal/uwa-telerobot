
package au.edu.uwa.mech.maths;

import au.edu.uwa.mech.util.Debug;
import java.text.DecimalFormat;

//all angles stored as radians
public class Orientation3d implements java.io.Serializable
{
  public Orientation3d()
  {}
  
  public Orientation3d(double s, double t)
  {_spin=s;_tilt=t;}

  //public static Orientation3d newOrientation3dDeg(double s, double t)
  // {
    //difference between robot and haralds veriosn of 0 degrees
  //spinDeg(s);
  // tiltDeg(t);
  //}
  
  public Orientation3d(Orientation3d o)
  {
    _spin=o._spin;
    _tilt=o._tilt;
  }

  public String toString() {
    return new String(df.format(_spin)+", "+df.format(_tilt));
  }
  
  
  public double spin() { return _spin;}
  public void   spin(double nspin) { _spin=nspin;}
  public void   spinDeg(double s) { _spin=((s - spinOffset) / 180) * Math.PI;}
  public double spinDeg()
  {return _spin * (180 / Math.PI) + spinOffset;}
  
  public double tilt() { return _tilt;}
  public void   tilt(double ntilt) { _tilt=ntilt;}
  public void   tiltDeg(double t) {_tilt= -t / 180 * Math.PI;}
  public double tiltDeg() {return - _tilt * 180 / Math.PI;}

  
  public double roll() {Debug.tbi(); return 0;}
  public void  roll(double ntilt) {Debug.tbi() ;}

  public double pitch(){Debug.tbi(); return 0;}
  public void  pitch(double ntilt) {Debug.tbi();}

  public double yaw()  {Debug.tbi(); return 0;}
  public void  yaw(double ntilt) {Debug.tbi();}


  protected double _spin=0;
  protected double _tilt=0;

  protected static double spinOffset=-90;
  static DecimalFormat df = new DecimalFormat();

  static  {
    df.setMaximumFractionDigits(3);
    df.setGroupingUsed(false);
  }

  public static void main (String[] args)
  {
    Orientation3d o = new Orientation3d(0,0);
    System.out.println(o.spinDeg());
    
  } // end of main ()
  
}
