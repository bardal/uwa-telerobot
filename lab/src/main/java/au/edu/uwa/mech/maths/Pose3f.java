
package au.edu.uwa.mech.maths;

import au.edu.uwa.mech.io.ASCIIInputStream;
import java.io.IOException;

public class Pose3f implements Cloneable
{

  public Pose3f(){}

  public Pose3f(Point3f p, Orientation3f o)
  {position=p;orientation=o;}

  public Pose3f(float x, float y, float z, float spin, float tilt)
  {position=new Point3f(x,y,z); orientation=new Orientation3f(spin, tilt);}

  public Pose3f(Pose3f p)
  {
    position=new Point3f(p.position);
    orientation=new Orientation3f(p.orientation);
  }

  public Point3f position= new Point3f();
  public Orientation3f orientation= new Orientation3f();

 

  public void read(ASCIIInputStream AI) throws IOException
  {
    position.x=AI.readFloat();
    position.y=AI.readFloat();
    position.z=AI.readFloat();
    boolean UseSpinTilt=AI.readBool();
    if (UseSpinTilt)
    {
      orientation.spin(AI.readFloat());
      orientation.tilt(AI.readFloat());
    }
    else
    {
      orientation.roll(AI.readFloat());
      orientation.pitch(AI.readFloat());
      orientation.yaw(AI.readFloat());
    }
  }
}
