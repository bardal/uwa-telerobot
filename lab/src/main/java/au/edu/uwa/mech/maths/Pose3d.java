
package au.edu.uwa.mech.maths;

import au.edu.uwa.mech.io.ASCIIInputStream;
import au.edu.uwa.mech.io.ASCIIReader;
import java.io.IOException;

public class Pose3d implements java.io.Serializable
{

  public Pose3d(){}

  public Pose3d(Point3d p, Orientation3d o)
  {position=p;orientation=o;}

  public Pose3d(double x, double y, double z, double spin, double tilt)
  {position=new Point3d(x,y,z); orientation=new Orientation3d(spin, tilt);}

  public Pose3d(Pose3d p)
  {
    position=new Point3d(p.position);
    orientation=new Orientation3d(p.orientation);
  }

  public Point3d position= new Point3d();
  public Orientation3d orientation= new Orientation3d();
 
 
  public String toString() {
    return "[pose [position "+position+"][orientation "+orientation+"]]";
  }

  public String toDisplayString() {
    return new String
      ("x=" + Math.round(position.x) + " "+
       "y=" + Math.round(position.y) + " "+
       "z=" + Math.round(position.z) + " "+
       "s=" + Math.round(orientation.spinDeg()) + "\u00b0 "+
       "t=" + Math.round(orientation.tiltDeg()) + "\u00b0");
  }
  
  
  
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

  public void readDeg(ASCIIInputStream AI) throws IOException
  {
    position.x=AI.readFloat();
    position.y=AI.readFloat();
    position.z=AI.readFloat();
    boolean UseSpinTilt=AI.readBool();
    if (UseSpinTilt)
    {
      orientation.spinDeg(AI.readFloat());
      orientation.tiltDeg(AI.readFloat());
    }
    else
    {
      orientation.roll(AI.readFloat());
      orientation.pitch(AI.readFloat());
      orientation.yaw(AI.readFloat());
    }
  }

    public void readDeg(ASCIIReader aReader) throws IOException
  {
    position.x=aReader.readFloat();
    position.y=aReader.readFloat();
    position.z=aReader.readFloat();
    boolean UseSpinTilt=aReader.readBool();
    if (UseSpinTilt)
    {
      orientation.spinDeg(aReader.readFloat());
      orientation.tiltDeg(aReader.readFloat());
    }
    else
    {
      orientation.roll(aReader.readFloat());
      orientation.pitch(aReader.readFloat());
      orientation.yaw(aReader.readFloat());
    }
  }

  

}
