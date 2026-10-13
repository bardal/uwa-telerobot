
package au.edu.uwa.mech.maths;

import au.edu.uwa.mech.util.Debug;

public class Orientation3f 
{
  public Orientation3f()
  {}

  public Orientation3f(float s, float t)
  {_spin=s;_tilt=t;}

  public Orientation3f(Orientation3f o)
  {
    _spin=o._spin;
    _tilt=o._tilt;
  }

  public float spin() { return _spin;}
  public void  spin(float nspin) { _spin=nspin;}

  public float tilt() { return _tilt;}
  public void  tilt(float ntilt) { _tilt=ntilt;}

  public float roll() {Debug.tbi(); return 0;}
  public void  roll(float ntilt) {Debug.tbi() ;}

  public float pitch(){Debug.tbi(); return 0;}
  public void  pitch(float ntilt) {Debug.tbi();}

  public float yaw()  {Debug.tbi(); return 0;}
  public void  yaw(float ntilt) {Debug.tbi();}


  protected float _spin=0;
  protected float _tilt=0;

}
