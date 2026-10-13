
package au.edu.uwa.mech.maths;



//this will hopefully be replaced by the Java 3D API class

public class Point3i 
{
  public Point3i(){}

  public Point3i(int x, int y, int z)
  {this.x=x; this.y=y;this.z=z;}

  public Point3i(Point3i p)
  {
    x=p.x;
    y=p.y;
    z=p.z;
  }

  public String toString()
  {return new String(x+","+y+","+z);}

  public int x,y,z;
  
}
