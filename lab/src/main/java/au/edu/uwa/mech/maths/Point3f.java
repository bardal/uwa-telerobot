
package au.edu.uwa.mech.maths;

import au.edu.uwa.mech.maths.geom.Point3D;

//this will hopefully be replaced by the Java 3D API class

public class Point3f extends Point3D implements Cloneable
{
  public Point3f(){super();}

  public Point3f(float x, float y, float z)
  {super (x,y,z);}

  public Point3f(Point3f p)
  {super (p);}
}
