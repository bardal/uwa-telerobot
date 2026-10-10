
package au.edu.uwa.mech.maths;

import au.edu.uwa.mech.maths.geom.Point3D;
import java.text.DecimalFormat;

//this will hopefully be replaced by the Java 3D API class

public class Point3d extends au.edu.uwa.mech.maths.geom.Point3D 
{
  public Point3d(){super();}

  public Point3d(double x, double y, double z)
  {super (x,y,z);}

  public Point3d(Point3d p)
  {super (p);}

  public String toString()
  {return new String(df.format(x)+", "+df.format(y)+", "+df.format(super.z));}
  
  static DecimalFormat df = new DecimalFormat();
  
  static {
    df.setMaximumFractionDigits(2);
    df.setGroupingUsed(false);
  }

  public double getZ(){
    return z;
  }
  
  
  public static void main (String[] args)
  {
    Point3d p=new Point3d(1.3324324523452345,4.2341234234,456456.32452345);
    System.out.println(p);
    
  } // end of main ()
  
  static final long serialVersionUID = 110068162522202877L;
}
