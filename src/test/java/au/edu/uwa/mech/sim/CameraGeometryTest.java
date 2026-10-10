package au.edu.uwa.mech.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import Jama.Matrix;
import au.edu.uwa.mech.applications.calibration.CalibrationFile;
import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.maths.geom.Line3D;
import au.edu.uwa.mech.maths.geom.Point3D;

/** The overlay camera model, the virtual cameras and the DLT solver must all agree. */
public class CameraGeometryTest
{
  @Test
  public void overlayCameraProjectsLikeTheRenderer()
  {
    for (VirtualCamera vc : VirtualLab.defaultCameras()) {
      Camera cam = new Camera(vc.calibrationMatrix().getArray(), new Point(0, 0), 1);
      double[][] pts = {{0, 0, 0}, {500, 0, 0}, {250, 250, 100}, {120, 150, 15}, {400, 500, 300}};
      for (double[] p : pts) {
        double[] c = vc.toCamera(p[0], p[1], p[2]);
        double u = vc.focal() * c[0] / c[2] + vc.centreU(), v = vc.focal() * c[1] / c[2] + vc.centreV();
        double[] uv = cam.project(p[0], p[1], p[2]);
        assertEquals(vc.name + " u", u, uv[0], 1e-6);
        assertEquals(vc.name + " v", v, uv[1], 1e-6);
      }
    }
  }

  @Test
  public void backProjectionFindsTheTablePoint()
  {
    VirtualCamera vc = VirtualLab.defaultCameras().get(0);
    Camera cam = new Camera(vc.calibrationMatrix().getArray(), new Point(0, 0), 1);
    Point uv = cam.xyz2uv(new Point3D(200, 300, 0));
    Point3D back = cam.uv2xyzSurface(uv);
    assertEquals(200, back.x, 3);  // within a pixel's worth of mm
    assertEquals(300, back.y, 3);
    assertEquals(0, back.z, 1e-6);
  }

  @Test
  public void twoRaysTriangulate()
  {
    List<VirtualCamera> cams = VirtualLab.defaultCameras();
    Point3D target = new Point3D(321, 123, 77);
    Line3D[] rays = new Line3D[2];
    for (int i = 0; i < 2; i++) {
      Camera cam = new Camera(cams.get(i).calibrationMatrix().getArray(), new Point(0, 0), 1);
      rays[i] = cam.uv2line(cam.xyz2uv(target));
    }
    Point3D p = rays[0].midPoint(rays[1]);
    assertTrue("triangulated " + p, p.distance(target) < 5);
  }

  @Test
  public void dltRecoversTheCalibration() throws Exception
  {
    VirtualCamera vc = VirtualLab.defaultCameras().get(1);
    Matrix truth = vc.calibrationMatrix();
    Camera cam = new Camera(truth.getArray(), new Point(0, 0), 1);
    Random r = new Random(1);
    List<double[]> xyz = new ArrayList<double[]>(), uv = new ArrayList<double[]>();
    for (int i = 0; i < 12; i++) {
      double[] w = {r.nextDouble() * 500, r.nextDouble() * 500, r.nextDouble() * 200};
      xyz.add(w);
      double[] p = cam.project(w[0], w[1], w[2]);
      uv.add(new double[] {p[0], p[1]});
    }
    Matrix solved = CalibrationFile.solve(xyz, uv);
    assertEquals(0, CalibrationFile.rmsError(solved, xyz, uv), 1e-6);

    File f = File.createTempFile("cal", ".txt");
    f.deleteOnExit();
    CalibrationFile.write(solved, f.getPath(), "test");
    Matrix read = CalibrationFile.read(f.getPath());
    for (int i = 0; i < 3; i++)
      for (int j = 0; j < 4; j++)
        assertEquals(truth.get(i, j), read.get(i, j), Math.abs(truth.get(i, j)) * 1e-5 + 1e-9);
  }
}
