
package au.edu.uwa.mech.maths;



//this will hopefully be replaced by the Java 3D API class

public class Point3l 
{
  public Point3l(){}

  public Point3l(int x, int y, int z)
  {this.x=x; this.y=y;this.z=z;}

  public Point3l(Point3l p)
  {
    x=p.x;
    y=p.y;
    z=p.z;
  }

  public Point3l(Point3d p)
  {
    this(p.x, p.y,p.z);
  }

  public Point3l(double x, double y, double z)
  {
    this.x=Math.round(x);
    this.y=Math.round(y);
    this.z=Math.round(z);
  }
  
  public String toString()
  {return new String(x+","+y+","+z);}

  public long x,y,z;
  
}
