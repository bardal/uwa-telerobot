package au.edu.uwa.mech.gui.overlaid;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;

import au.edu.uwa.mech.maths.Point3d;
import au.edu.uwa.mech.maths.Pose3d;
import au.edu.uwa.mech.maths.geom.Line3D;
import au.edu.uwa.mech.maths.geom.Point3D;
import au.edu.uwa.mech.maths.geom.Vector3D;

/**
 * An interactive 3D cursor drawn over a calibrated camera image, used to
 * pick a gripper pose by dragging:
 * <ul>
 * <li>peak marker (gripper position) - drag up and down to change height</li>
 * <li>base marker (on the surface below) - drag to move in x and y</li>
 * <li>plumb line - drag to move in x and y at the height grabbed</li>
 * <li>x / y base lines - drag to change only y / only x</li>
 * <li>spin needle - drag round to change spin</li>
 * <li>tilt line - drag to change tilt</li>
 * </ul>
 * Observers are told whenever the pose changes.
 */
public class StickCursor extends OverlaidObject implements Overlay3D
{
  public PlumblineCursor pc = new PlumblineCursor();
  public SpinCursor sc = new SpinCursor();
  public TiltCursor tc = new TiltCursor();
  private final OverlaidComponent[] parts = {pc, sc, tc};

  public Pose3d pose = new Pose3d();
  /** Recommended mouse cursor for the containing component to show. */
  public Cursor cursor = Cursor.getDefaultCursor();

  private enum Drag { NONE, PEAK, BASE, HEIGHT, X_LINE, Y_LINE, SPIN, TILT }
  private Drag dragging = Drag.NONE;
  private double grabHeight;
  private Camera cam;
  private Jama.Matrix calibrationMatrix;

  public StickCursor()
  {
    for (OverlaidComponent part : parts)
      part.movePose(pose);
  }

  public StickCursor(Camera c)
  {
    this();
    setCamera(c);
  }

  public void setCamera(Camera c)
  {
    super.setCamera(c);
    cam = c;
    for (OverlaidComponent p : parts)
      p.setCamera(c);
  }

  public void projectOnTo(CalibratedBackground cb, Graphics g)
  {
    Jama.Matrix m = cb.getCalibrationMatrix();
    if (m != calibrationMatrix) {
      setCamera(new Camera(m.getArray(), new Point(0, 0), 1));
      calibrationMatrix = m;
    }
    draw(g);
  }

  public void movePoint(Point3d point)
  {
    Pose3d p = new Pose3d(pose);
    p.position = point;
    movePose(p);
  }

  public void movePose(Pose3d p)
  {
    // The applet relays each cursor's moves to the cursors on the other
    // cameras by passing the same Pose3d along; ignoring a pose we already
    // have stops the relay bouncing back and forth.
    if (p == pose)
      return;
    pose = p;
    for (OverlaidComponent part : parts)
      part.movePose(p);
    tellObservers();
  }

  public Pose3d getPose() { return pose; }

  public void setVisible(boolean v)
  {
    super.setVisible(v);
    for (OverlaidComponent p : parts)
      p.setVisible(v);
    tellObservers();
  }

  public void setColorNoFocus(Color c)
  {
    super.setColorNoFocus(c);
    for (OverlaidComponent p : parts)
      p.setColorNoFocus(c);
  }

  public void draw(Graphics g)
  {
    for (OverlaidComponent p : parts)
      p.draw(g);
  }

  public boolean contains(Point m)
  {
    return pc.contains(m) || sc.contains(m) || tc.contains(m);
  }

  public void mousePressed(MouseEvent e)
  {
    if (cam == null)
      return;
    Point m = e.getPoint();
    PlumblineCursor.Part part = pc.partAt(m);
    // markers first, then the orientation handles, then the lines
    if (part == PlumblineCursor.Part.PEAK || part == PlumblineCursor.Part.BASE)
      dragging = part == PlumblineCursor.Part.PEAK ? Drag.PEAK : Drag.BASE;
    else if (sc.contains(m))
      dragging = Drag.SPIN;
    else if (tc.contains(m))
      dragging = Drag.TILT;
    else if (part == PlumblineCursor.Part.X_LINE)
      dragging = Drag.X_LINE;
    else if (part == PlumblineCursor.Part.Y_LINE)
      dragging = Drag.Y_LINE;
    else if (part == PlumblineCursor.Part.HEIGHT) {
      dragging = Drag.HEIGHT;
      grabHeight = pose.position.z * pc.heightFraction(m);
    } else
      return;
    pc.setFocusPart(dragging == Drag.SPIN || dragging == Drag.TILT ? PlumblineCursor.Part.NONE : part);
    sc.setFocus(dragging == Drag.SPIN);
    tc.setFocus(dragging == Drag.TILT);
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    e.consume();
    tellObservers();
  }

  public void mouseDragged(MouseEvent e)
  {
    if (dragging == Drag.NONE || cam == null)
      return;
    Point m = e.getPoint();
    Pose3d p = new Pose3d(pose);
    Point3d at = pose.position;
    Point3D onSurface = cam.uv2xyzSurface(m);
    Point3D atHeight = cam.uv2xyzPlane(m, 0, 0, 1, -at.z);
    double s = pose.orientation.spin();
    switch (dragging) {
      case PEAK: {
        Line3D vertical = new Line3D(new Point3D(at), new Vector3D(0, 0, 1));
        p.position = new Point3d(at.x, at.y, Math.max(0, vertical.closestPoint(cam.uv2line(m)).z));
        break;
      }
      case BASE:
        if (onSurface != null)
          p.position = new Point3d(onSurface.x, onSurface.y, at.z);
        break;
      case HEIGHT: {
        Point3D q = cam.uv2xyzPlane(m, 0, 0, 1, -grabHeight);
        if (q != null)
          p.position = new Point3d(q.x, q.y, at.z);
        break;
      }
      case X_LINE:
        if (onSurface != null)
          p.position = new Point3d(at.x, onSurface.y, at.z);
        break;
      case Y_LINE:
        if (onSurface != null)
          p.position = new Point3d(onSurface.x, at.y, at.z);
        break;
      case SPIN:
        if (atHeight != null)
          p.orientation.spin(Math.atan2(atHeight.y - at.y, atHeight.x - at.x));
        break;
      case TILT: {
        // the vertical plane through the gripper containing the spin direction
        double nx = -Math.sin(s), ny = Math.cos(s);
        Point3D q = cam.uv2xyzPlane(m, nx, ny, 0, -(nx * at.x + ny * at.y));
        if (q != null) {
          Vector3D v = q.sub(at);
          double along = v.x * Math.cos(s) + v.y * Math.sin(s);
          p.orientation.tilt(Math.atan2(along, v.z));
        }
        break;
      }
      default:
        break;
    }
    movePose(p);
    e.consume();
  }

  public void mouseReleased(MouseEvent e)
  {
    dragging = Drag.NONE;
    cursor = Cursor.getDefaultCursor();
    for (OverlaidComponent p : parts)
      p.setFocus(false);
    tellObservers();
  }

  static final long serialVersionUID = 1L;
}
