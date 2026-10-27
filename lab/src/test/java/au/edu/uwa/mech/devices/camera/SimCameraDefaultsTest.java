package au.edu.uwa.mech.devices.camera;

import static org.junit.Assert.assertEquals;

import au.edu.uwa.mech.sim.VirtualCamera;
import au.edu.uwa.mech.sim.VirtualLab;
import au.edu.uwa.mech.sim.World;

import org.junit.Test;

/** The cameras start sharp enough to see the gripper's fingers: 640x480 at quality 40 (JPEG 0.83). */
public class SimCameraDefaultsTest
{
  @Test
  public void everyCameraStartsAt640x480Quality40() throws Exception
  {
    SimCameraManager m = new SimCameraManager(World.withDefaultBlocks(), VirtualLab.defaultCameras(), null);
    for (VirtualCamera vc : VirtualLab.defaultCameras()) {
      CameraSettings s = m.getCamera(vc.name).getSettings();
      assertEquals(vc.name + " width", 640, s.getWidth());
      assertEquals(vc.name + " height", 480, s.getHeight());
      assertEquals(vc.name + " quality", 40, s.getQuality());
    }
  }
}
