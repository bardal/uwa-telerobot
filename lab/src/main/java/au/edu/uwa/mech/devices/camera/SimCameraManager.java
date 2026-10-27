package au.edu.uwa.mech.devices.camera;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import au.edu.uwa.mech.maths.Point3d;
import au.edu.uwa.mech.sim.Renderer;
import au.edu.uwa.mech.sim.VirtualCamera;
import au.edu.uwa.mech.sim.World;

/**
 * Cameras that photograph the simulated workcell instead of grabbing
 * frames: each photo is rendered from a {@link VirtualCamera}, JPEG encoded
 * and delivered with that camera's exact calibration matrix.
 */
public class SimCameraManager implements CameraManager
{
  private final Map<String, SimCamera> cameras = new LinkedHashMap<String, SimCamera>();
  private final Vector<CameraManagerListener> listeners = new Vector<CameraManagerListener>();

  /**
   * @param imageDirectory if not null, each photo is also written there as a JPEG
   */
  public SimCameraManager(World world, List<VirtualCamera> virtualCameras, String imageDirectory)
  {
    Renderer renderer = new Renderer();
    for (VirtualCamera vc : virtualCameras)
      cameras.put(vc.name, new SimCamera(world, vc, renderer, imageDirectory));
  }

  public void addCameraManagerListener(CameraManagerListener l)
  {
    listeners.addElement(l);
    for (SimCamera c : cameras.values())
      l.newCamera(this, c);
  }

  public Camera getCamera(String name) throws CameraException
  {
    Camera c = cameras.get(name);
    if (c == null)
      throw new CameraException("no camera called " + name);
    return c;
  }

  public Enumeration cameras() { return new Vector<Camera>(cameras.values()).elements(); }

  static class SimCamera implements Camera
  {
    static final int MAX_WIDTH = 768, MAX_HEIGHT = 576;
    private final World world;
    private final VirtualCamera vc;
    private final Renderer renderer;
    private final String imageDirectory;
    private final Vector<CameraListener> cameraListeners = new Vector<CameraListener>();
    private final Vector<ImageListener> imageListeners = new Vector<ImageListener>();
    private ImageData last;
    private int quality = 40;  // 1..50, as the framegrabber cameras (40 = JPEG 0.83)
    private float zoom = 1;
    private boolean fixed = true;
    private int count;

    SimCamera(World world, VirtualCamera vc, Renderer renderer, String imageDirectory)
    {
      this.world = world;
      this.vc = vc;
      this.renderer = renderer;
      this.imageDirectory = imageDirectory;
    }

    public String getName() { return vc.name; }

    public ImageData takePhoto() throws CameraException
    {
      count = (count + 1) % 100;
      return takePhoto(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + count + vc.name + ".jpg");
    }

    public synchronized ImageData takePhoto(String filename) throws CameraException
    {
      World.Snapshot snap = world.snapshot();
      BufferedImage img = renderer.render(vc, snap, snap.moving ? "moving" : null);
      ImageData data = new ImageData();
      try {
        data.pixels = jpeg(img, 0.35f + 0.6f * quality / 50f);
        if (imageDirectory != null) {
          File f = new File(imageDirectory, filename);
          try (OutputStream out = new FileOutputStream(f)) {
            out.write(data.pixels);
          }
          data.filename = f.getPath();
        } else
          data.filename = filename;
      } catch (IOException e) {
        throw new CameraException("could not encode image: " + e);
      }
      data.url = filename;
      data.width = img.getWidth();
      data.height = img.getHeight();
      data.calibrationMatrix = vc.calibrationMatrix();
      last = data;
      for (ImageListener l : new Vector<ImageListener>(imageListeners))
        l.newImage(this, data);
      return data;
    }

    private static byte[] jpeg(BufferedImage img, float q) throws IOException
    {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
      ImageWriter w = it.next();
      try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
        w.setOutput(out);
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(Math.max(0.05f, Math.min(1f, q)));
        w.write(null, new IIOImage(img, null, null), p);
      } finally {
        w.dispose();
      }
      return bytes.toByteArray();
    }

    public ImageData lastPhoto() { return last; }

    public synchronized CameraSettings getSettings()
    {
      CameraSettings s = new CameraSettings(MAX_WIDTH, MAX_HEIGHT);
      s.setWidth(vc.getWidth());
      s.setHeight(vc.getHeight());
      s.setQuality(quality);
      s.setZoom(zoom);
      s.setFixed(fixed);
      return s;
    }

    public void setSettings(CameraSettings s)
    {
      synchronized (this) {
        int w = Math.max(80, Math.min(MAX_WIDTH, s.getWidth()));
        int h = Math.max(60, Math.min(MAX_HEIGHT, s.getHeight()));
        vc.setSize(w, h);
        quality = Math.max(1, Math.min(50, s.getQuality()));
        zoom = s.getZoom();
        fixed = s.getFixed();
      }
      CameraSettings now = getSettings();
      for (CameraListener l : new Vector<CameraListener>(cameraListeners))
        l.newSettings(this, now);
    }

    /** The real cameras could pan to follow the robot; these are fixed. */
    public void setCentre(Point3d point) {}

    public void setOn(boolean on) {}

    public boolean getOn() { return true; }

    public void addCameraListener(CameraListener l) { cameraListeners.addElement(l); }

    public void removeCameraListener(CameraListener l) { cameraListeners.removeElement(l); }

    public void addImageListener(ImageListener l) { imageListeners.addElement(l); }

    public void removeImageListener(ImageListener l) { imageListeners.removeElement(l); }

    public String toString() { return "[SimCamera " + vc.name + " " + getSettings() + "]"; }
  }
}
