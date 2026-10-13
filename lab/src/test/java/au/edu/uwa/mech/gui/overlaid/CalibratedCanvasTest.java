package au.edu.uwa.mech.gui.overlaid;

import static org.junit.Assert.assertEquals;

import java.awt.Point;

import org.junit.Test;

import Jama.Matrix;
import au.edu.uwa.mech.maths.geom.Point3D;

/** A canvas drawn larger than its image hands overlays a matching calibration. */
public class CalibratedCanvasTest
{
  static class Fitted extends CalibratedCanvas
  {
    Fitted(int w, int h) { super(w, h); }
    void layOut(int width, int height) { setSize(width, height); fit(); }
  }

  @Test
  public void overlaysAndMouseFollowTheScaledImage()
  {
    double[][] p = {{0.8, -0.1, 0.05, 200}, {0.02, 0.3, -0.9, 150}, {0.0001, 0.0005, 0.0002, 1}};
    Fitted c = new Fitted(400, 300);
    c.setCalibrationMatrix(new Matrix(p));
    c.layOut(1000, 600);                 // twice the height; letterboxed left and right
    assertEquals(2.0, c.getDisplayScale(), 1e-9);
    assertEquals(new Point(100, 0), c.getDisplayOffset());

    Point3D x = new Point3D(150, 350, 40);
    Point inImage = new Camera(p, new Point(0, 0), 1).xyz2uv(x);
    Point onCanvas = new Camera(c.getCalibrationMatrix().getArray(), new Point(0, 0), 1).xyz2uv(x);
    assertEquals(100 + 2 * inImage.x, onCanvas.x, 1);
    assertEquals(2 * inImage.y, onCanvas.y, 1);
    Point back = c.toImage(onCanvas);
    assertEquals(inImage.x, back.x, 1);
    assertEquals(inImage.y, back.y, 1);

    c.layOut(400, 300);                  // natural size: unchanged
    assertEquals(new Matrix(p).get(0, 3), c.getCalibrationMatrix().get(0, 3), 1e-9);
  }
}
