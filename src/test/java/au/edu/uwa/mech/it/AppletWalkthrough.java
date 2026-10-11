// Hosts the real TA200001 applet in this JVM and drives its own AWT widgets
// (fills in fields, fires buttons, menu items and mouse events), so the GUI
// can be exercised without synthesising OS mouse/keyboard input:
//   log in, check camera images arrive, take control, drag the 3D stick
//   cursor over the Colour camera image, turn it into a command with
//   "Comand from cursor", send it, then run a pick-and-place script.
// A screenshot of the applet window is saved after each step.
// Run against a stack started with start.ps1 / start.sh (NoApplet is fine):
//   java -cp target/telerobot.jar;target/test-classes au.edu.uwa.mech.it.AppletWalkthrough
//        <outdir> [user [pin [nick [script]]]]
// Logging in as "team" (PIN 1234 in config/users.ini) can take control from
// guests at once; a guest must wait until the last guest's control expires.

package au.edu.uwa.mech.it;

import java.applet.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

import au.edu.uwa.mech.gui.overlaid.CalibratedCanvas;
import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.maths.Pose3d;
import au.edu.uwa.mech.maths.geom.Point3D;

public class AppletWalkthrough
{
  static File outDir;
  static Map<String, String> params = new HashMap<String, String>();
  static int commandsSent = 0;

  public static void main(String[] args)
  {
    try {
      run(args);
    } catch (Throwable t) {
      t.printStackTrace();
      System.out.println("UAT FAIL: " + t);
      System.exit(1);
    }
  }

  static void run(String[] args) throws Exception
  {
    // the Direct3D pipeline leaves AWT canvases unpainted on some Windows setups
    System.setProperty("sun.java2d.d3d", "false");
    outDir = new File(args.length > 0 ? args[0] : ".");
    outDir.mkdirs();
    String user = args.length > 1 ? args[1] : "team";
    String pin = args.length > 2 ? args[2] : "1234";
    String nick = args.length > 3 ? args[3] : "walk" + (System.currentTimeMillis() / 1000 % 100000);
    String script = args.length > 4 ? args[4] : "x120,y150,z60,s-90,t0,g0;z15;g1;z80;x330,y120;z45;g0;z90";
    params.put("MOMURL", "moms://localhost:4444/");
    params.put("LogURL", "http://localhost:18080/log");
    params.put("fontsize", "11");
    params.put("imageheight", "300");
    params.put("imagewidth", "400");
    params.put("logouturl", "http://localhost:18080/");

    final Applet applet = (Applet) Class.forName("au.edu.uwa.mech.applets.TA200001").newInstance();
    applet.setStub(new Stub());
    Frame host = new Frame("applet host");
    host.add(applet);
    host.setSize(10, 10);
    host.setVisible(true);
    applet.init();
    applet.start();

    Frame gui = waitForFrame("Telerobot Control Applet", 15000);
    step(gui, "01-loaded");

    setText(gui, findTextFieldAfterLabel(gui, "username"), user);
    setText(gui, findTextFieldAfterLabel(gui, "password"), pin);
    setText(gui, findTextFieldAfterLabel(gui, "nickname"), nick);
    click(gui, findButton(gui, "login"));
    waitFor(gui, "login button says logout", () -> findButton(gui, "logout") != null, 15000);
    waitFor(gui, "camera images arrive", () -> imagesShowing(gui) > 0, 20000);
    System.out.println("UAT camera canvases with an image: " + imagesShowing(gui));
    waitFor(gui, "camera buttons", () -> findButton(gui, "Colour") != null, 20000);
    click(gui, findButton(gui, "Colour"));
    Thread.sleep(1500);
    step(gui, "02-colour-camera");

    click(gui, findButton(gui, "Control Robot"));
    waitFor(gui, "we are the controller", () -> labelText(gui, "Controller").contains(nick), 15000);
    step(gui, "03-in-control");

    // drag the stick cursor: base marker across the table, then the peak marker down
    CalibratedCanvas cc = showingCanvas(gui);
    Camera cam = new Camera(cc.getCalibrationMatrix().getArray(), new Point(0, 0), 1);
    Pose3d p0 = stickPose(gui);
    drag(cc, cam.xyz2uv(new Point3D(p0.position.x, p0.position.y, 0)), cam.xyz2uv(new Point3D(150, 350, 0)));
    Pose3d p1 = stickPose(gui);
    check("base drag moves the cursor in x and y", near(p1.position.x, 150, 6) && near(p1.position.y, 350, 6)
          && near(p1.position.z, p0.position.z, 0.5), p1);
    drag(cc, cam.xyz2uv(p1.position), cam.xyz2uv(new Point3D(p1.position.x, p1.position.y, 40)));
    Pose3d p2 = stickPose(gui);
    check("peak drag changes only the height", near(p2.position.z, 40, 6) && near(p2.position.x, p1.position.x, 0.5), p2);
    step(gui, "04-cursor-dragged");

    TextArea editor = (TextArea) findAfterLabel(gui, "Command editor", TextArea.class);
    setText(gui, editor, "");
    menu(gui, "Comand from cursor");
    System.out.println("UAT command from cursor: " + editor.getText().trim());
    runCommand(gui, "05-moved-to-cursor");
    Pose3d robot = robotPose(gui);
    check("robot went to the cursor", near(robot.position.x, p2.position.x, 2) && near(robot.position.y, p2.position.y, 2)
          && near(robot.position.z, p2.position.z, 2), robot);

    setText(gui, editor, script);
    runCommand(gui, "06-pick-and-place");
    saveCanvas(gui, "07-camera-after.png");

    System.out.println("UAT PASS");
    System.exit(0);
  }

  static void runCommand(Frame gui, String stepName) throws Exception
  {
    final int n = ++commandsSent;
    click(gui, findButton(gui, "Send command"));
    waitFor(gui, "command " + n + " finished", () ->
        labelText(gui, "Status").matches(".*Finished\\s+command " + n + "\\b.*")
        && labelText(gui, "Robot Position").contains("Stopped"), 90000);
    Thread.sleep(1500);   // let the last camera image arrive
    step(gui, stepName);
  }

  static boolean near(double a, double b, double tol) { return Math.abs(a - b) <= tol; }

  static void check(String what, boolean ok, Object detail)
  {
    System.out.println("UAT " + (ok ? "ok: " : "FAIL: ") + what + " (" + detail + ")");
    if (!ok)
      System.exit(1);
  }

  static CalibratedCanvas showingCanvas(Frame f)
  {
    for (Component c : all(f))
      if (c instanceof CalibratedCanvas && c.isShowing())
        return (CalibratedCanvas) c;
    throw new RuntimeException("no camera canvas showing");
  }

  static Pose3d stickPose(Frame gui) throws Exception
  {
    java.lang.reflect.Field f = gui.getClass().getDeclaredField("stickPose");
    f.setAccessible(true);
    return new Pose3d((Pose3d) f.get(gui));
  }

  static Pose3d robotPose(Frame gui) throws Exception
  {
    String t = labelText(gui, "Robot Position");
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("x=(-?\\d+) y=(-?\\d+) z=(-?\\d+)").matcher(t);
    if (!m.find())
      throw new RuntimeException("can't read robot position from '" + t + "'");
    return new Pose3d(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)), 0, 0);
  }

  /** Press, drag in small steps and release, as a mouse would. */
  static void drag(final Component c, Point from, Point to) throws Exception
  {
    final long t = System.currentTimeMillis();
    mouse(c, MouseEvent.MOUSE_PRESSED, from, t);
    for (int i = 1; i <= 10; i++)
      mouse(c, MouseEvent.MOUSE_DRAGGED, new Point(from.x + (to.x - from.x) * i / 10, from.y + (to.y - from.y) * i / 10), t + i * 20);
    mouse(c, MouseEvent.MOUSE_RELEASED, to, t + 250);
    Thread.sleep(300);
  }

  static void mouse(final Component c, final int id, final Point p, final long when) throws Exception
  {
    EventQueue.invokeAndWait(() -> c.dispatchEvent(
        new MouseEvent(c, id, when, InputEvent.BUTTON1_MASK, p.x, p.y, 1, false, MouseEvent.BUTTON1)));
  }

  static void menu(Frame gui, String label) throws Exception
  {
    MenuBar mb = gui.getMenuBar();
    for (int i = 0; i < mb.getMenuCount(); i++) {
      Menu m = mb.getMenu(i);
      for (int j = 0; j < m.getItemCount(); j++) {
        final MenuItem it = m.getItem(j);
        if (label.equals(it.getLabel())) {
          System.out.println("UAT menu " + m.getLabel() + " > " + label);
          EventQueue.invokeAndWait(() -> it.dispatchEvent(
              new ActionEvent(it, ActionEvent.ACTION_PERFORMED, it.getActionCommand())));
          Thread.sleep(300);
          return;
        }
      }
    }
    throw new RuntimeException("no menu item " + label);
  }

  /** Paint the showing camera canvas (image plus overlays) into a PNG. */
  static void saveCanvas(Frame gui, String name) throws Exception
  {
    final Component c = showingCanvas(gui);
    final BufferedImage bi = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
    EventQueue.invokeAndWait(() -> c.paint(bi.getGraphics()));
    ImageIO.write(bi, "png", new File(outDir, name));
  }

  interface Cond { boolean ok() throws Exception; }

  static void waitFor(Frame gui, String what, Cond c, long ms) throws Exception
  {
    long end = System.currentTimeMillis() + ms;
    while (System.currentTimeMillis() < end) {
      if (c.ok()) { System.out.println("UAT ok: " + what); return; }
      Thread.sleep(200);
    }
    step(gui, "FAILED");
    System.out.println("UAT FAIL: timed out waiting for " + what);
    System.exit(1);
  }

  static void step(Frame gui, String name) throws Exception
  {
    System.out.println("UAT step " + name);
    for (String l : new String[]{"Status", "Robot Position", "Controller"})
      System.out.println("   " + labelText(gui, l));
    for (Component c : all(gui))
      if (c instanceof TextArea && c.isShowing()) {
        String t = ((TextArea) c).getText().trim();
        if (t.length() > 0) {
          String[] lines = t.split("\n");
          System.out.println("   [textarea] " + lines[lines.length - 1]);
        }
      }
    gui.toFront();
    Thread.sleep(300);
    Rectangle r = gui.getBounds();
    BufferedImage img = new Robot().createScreenCapture(r);
    ImageIO.write(img, "png", new File(outDir, name + ".png"));
  }

  /** How many image canvases in the window have received an image. */
  static int imagesShowing(Frame f) throws Exception
  {
    java.lang.reflect.Field image = au.edu.uwa.mech.gui.awt.ImageCanvas.class.getDeclaredField("image");
    image.setAccessible(true);
    int n = 0;
    for (Component c : all(f))
      if (c instanceof au.edu.uwa.mech.gui.awt.ImageCanvas && image.get(c) != null)
        n++;
    return n;
  }

  static Frame waitForFrame(String prefix, long ms) throws Exception
  {
    long end = System.currentTimeMillis() + ms;
    while (System.currentTimeMillis() < end) {
      for (Frame f : Frame.getFrames())
        if (f.isShowing() && f.getTitle() != null && f.getTitle().startsWith(prefix)) return f;
      Thread.sleep(200);
    }
    throw new RuntimeException("no frame " + prefix);
  }

  static List<Component> all(Container c)
  {
    List<Component> out = new ArrayList<Component>();
    for (Component k : c.getComponents()) {
      out.add(k);
      if (k instanceof Container) out.addAll(all((Container) k));
    }
    return out;
  }

  static Button findButton(Frame f, String label)
  {
    for (Component c : all(f))
      if (c instanceof Button && label.equals(((Button) c).getLabel())) return (Button) c;
    return null;
  }

  // first component of the given type that follows the label in hierarchy order
  static Component findAfterLabel(Frame f, String labelPrefix, Class<?> type)
  {
    boolean seen = false;
    for (Component c : all(f)) {
      if (c instanceof Label && ((Label) c).getText().startsWith(labelPrefix)) seen = true;
      else if (seen && type.isInstance(c)) return c;
    }
    throw new RuntimeException("nothing of type " + type + " after label " + labelPrefix);
  }

  static TextField findTextFieldAfterLabel(Frame f, String label)
  {
    return (TextField) findAfterLabel(f, label, TextField.class);
  }

  static String labelText(Frame f, String prefix)
  {
    for (Component c : all(f))
      if (c instanceof Label && ((Label) c).getText().startsWith(prefix)) return ((Label) c).getText();
    return "";
  }

  static void setText(Frame f, final TextComponent t, final String s) throws Exception
  {
    EventQueue.invokeAndWait(() -> t.setText(s));
  }

  static void click(Frame f, final Button b) throws Exception
  {
    if (b == null) throw new RuntimeException("button not found");
    System.out.println("UAT click " + b.getLabel());
    EventQueue.invokeAndWait(() ->
        b.dispatchEvent(new ActionEvent(b, ActionEvent.ACTION_PERFORMED, b.getActionCommand())));
  }

  static class Stub implements AppletStub, AppletContext
  {
    URL base;
    Stub() { try { base = new URL("http://localhost:18080/index.html"); } catch (Exception e) { throw new RuntimeException(e); } }
    public boolean isActive() { return true; }
    public URL getDocumentBase() { return base; }
    public URL getCodeBase() { try { return new URL(base, "applets/"); } catch (Exception e) { return base; } }
    public String getParameter(String name)
    {
      for (Map.Entry<String, String> e : params.entrySet())
        if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
      return null;
    }
    public AppletContext getAppletContext() { return this; }
    public void appletResize(int w, int h) {}
    public AudioClip getAudioClip(URL u) { return null; }
    public Image getImage(URL u) { return Toolkit.getDefaultToolkit().getImage(u); }
    public Applet getApplet(String n) { return null; }
    public Enumeration<Applet> getApplets() { return new Vector<Applet>().elements(); }
    public void showDocument(URL u) { System.out.println("UAT showDocument " + u); }
    public void showDocument(URL u, String target) { System.out.println("UAT showDocument " + u + " " + target); }
    public void showStatus(String s) { System.out.println("UAT status " + s); }
    public void setStream(String k, InputStream s) {}
    public InputStream getStream(String k) { return null; }
    public Iterator<String> getStreamKeys() { return new ArrayList<String>().iterator(); }
  }
}
