
package au.edu.uwa.mech.maths;
import au.edu.uwa.mech.maths.geom.*;

public class Homogeneous
{
  public static VectorN makeHom(Point3D p)
  {return new VectorN(p, 1);}

  public static Point3D make3D(VectorN h)
  {return new Point3D(h.elementAt(0), h.elementAt(1), h.elementAt(2));}
  
}
