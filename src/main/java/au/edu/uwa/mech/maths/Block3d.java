
package au.edu.uwa.mech.maths;

import au.edu.uwa.mech.maths.geom.*;
import java.util.Vector;
import java.util.Enumeration;


public class Block3d
{

  public void setSize(Vector3D s)
  {
    size=s;
    recalc();
  }
  
  public void move(Pose3d p)
  {
    pose=p;
    recalc();
  }

  protected void recalc()
  {
    //setup block vertices centred on origin
    Vector vertices=calcVertices();
    
    //translate and rotate each vertex by p
    Matrix4x4 trans = new Matrix4x4();
    trans.makeTrans(pose.position);
    Matrix4x4 rotz = new Matrix4x4();
    //now rotation
    rotz.makeRotZ((float)pose.orientation.spin());

    Matrix4x4 roty = new Matrix4x4();
    roty.makeRotY((float)pose.orientation.tilt());
    
    //System.out.println(trans);
    //System.out.println(rotz);
    //System.out.println("product coming up.............");
    trans = trans.mult(rotz);
    trans = trans.mult(roty);
    //not done
    //System.out.println(trans);
    //System.out.println(trans.toString());
    
    //apply transformation
    int i=0;
    Vector transformedvertices=new Vector();
    for (Enumeration e=vertices.elements();e.hasMoreElements();)
    {
      //System.out.println(i++ +" iterations" + vertices.size());
      VectorN homvec = Homogeneous.makeHom((Point3D)e.nextElement());
      //System.out.println(homvec.toString());
      //System.out.println(trans);
      homvec = trans.mult(homvec);
      //System.out.println(homvec.toString());
      transformedvertices.addElement(Homogeneous.make3D(homvec));
    }
    //now work out the edges
    _edges = calcEdges(transformedvertices);

    
  }

  public Vector calcVertices()
  {
    Vector v=new Vector();
    v.addElement(new Point3D(-size.x/2,-size.y/2, -size.z/2));
    v.addElement(new Point3D(-size.x/2,-size.y/2, size.z/2));
    v.addElement(new Point3D(size.x/2, -size.y/2, -size.z/2));
    v.addElement(new Point3D(size.x/2, -size.y/2, size.z/2));
    v.addElement(new Point3D(size.x/2,  size.y/2, -size.z/2));
    v.addElement(new Point3D(size.x/2,  size.y/2, size.z/2));
    v.addElement(new Point3D(-size.x/2, size.y/2, -size.z/2));
    v.addElement(new Point3D(-size.x/2, size.y/2, size.z/2));

    //for (Enumeration e=v.elements();e.hasMoreElements();)
    //  System.out.println(e.nextElement().toString());
    
    return v;
  }

  public Vector calcEdges(Vector vertices)
  {
    Vector v=new Vector();
    v.addElement(new Line3D((Point3D)vertices.elementAt(0), (Point3D)vertices.elementAt(1)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(2), (Point3D)vertices.elementAt(3)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(4), (Point3D)vertices.elementAt(5)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(6), (Point3D)vertices.elementAt(7)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(0), (Point3D)vertices.elementAt(2)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(1), (Point3D)vertices.elementAt(3)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(6), (Point3D)vertices.elementAt(4)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(7), (Point3D)vertices.elementAt(5)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(0), (Point3D)vertices.elementAt(6)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(1), (Point3D)vertices.elementAt(7)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(2), (Point3D)vertices.elementAt(4)));
    v.addElement(new Line3D((Point3D)vertices.elementAt(3), (Point3D)vertices.elementAt(5)));

    // for (Enumeration e=v.elements();e.hasMoreElements();)
    //   System.out.println(e.nextElement().toString());
    
    return v;
  }
    //int polygons(){return _polygons.elements();}

  public Vector edges(){return _edges;}
  
  Vector _edges;
  Vector3D size=new Vector3D(65,32,32);
  Pose3d pose=new Pose3d(0,0,0,0,0);

  public Pose3d getPose(){return pose;}
}
