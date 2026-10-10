package au.edu.uwa.mech.gui.overlaid;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;

import au.edu.uwa.mech.maths.Pose3d;

/**
 * Something drawn over a calibrated camera image that follows a 3D pose:
 * it is given a camera, moved to poses and asked to draw itself.
 */
public abstract class OverlaidComponent
{
  protected Camera cam;
  protected Pose3d currentPose = new Pose3d();
  protected boolean visible = true;
  protected boolean focus = false;
  protected Color colorFocus = Color.red;
  protected Color colorNoFocus = Color.yellow;

  public abstract void draw(Graphics g);

  /** True if p (display coordinates) is on a draggable part of this component. */
  public boolean contains(Point p) { return false; }

  public void setCamera(Camera c) { cam = c; }

  public void movePose(Pose3d pose) { currentPose = pose; }

  public void setVisible(boolean v) { visible = v; }

  public boolean isVisible() { return visible; }

  public void setFocus(boolean f) { focus = f; }

  public boolean hasFocus() { return focus; }

  public void setColorFocus(Color c) { colorFocus = c; }

  public void setColorNoFocus(Color c) { colorNoFocus = c; }

  protected Color currentColor() { return focus ? colorFocus : colorNoFocus; }
}
