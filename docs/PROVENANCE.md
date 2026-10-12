# Provenance

This repository was made in 2026 from Barney Dalton's archive of the
1999–2000 telerobot work. The archive also held third-party libraries, other
people's code, live data and native code. This file records what was kept,
what was left out and what was written new, so the release has only the
author's own code plus new code under the same licence.

## Kept

284 Java files from `au.edu.uwa.mech`, written by Barney Dalton, are included
with their GPL headers. They cover MOM (`mom.*`), the robot proxy and client
(`devices.robot`), the camera server and clients (`devices.camera`), the
applets, overlays, maths, I/O, XML and utilities. They are as written except
for:

* import changes to use the replacement classes listed below;
* removal of a startup check for `rmic`-generated RMI stubs (`MOMServer`),
  which hasn't been needed since Java 5;
* small API-drift fixes in `ABBDemoApplet`, `ImageApplet`, `UDPStats` (a typo),
  `MOMConnectionRMIImpl` (missing `setName`) and the file name of
  `mom/xml/Reflect.java`;
* `devices.camera.Server`, which now takes a `CameraManager` from its caller
  instead of building the native one, and waits for shutdown with a JVM
  shutdown hook;
* `Calibrate`, which solves for the camera matrix in Java instead of calling
  the native VIP library;
* example credentials in comments and test mains, replaced by placeholders;
* resizable camera views: `gui.awt.ImageCanvas` scales its image to fit,
  `CalibratedCanvas` gives overlays a calibration matrix that includes the
  display scale, `CameraPanel`, `CameraManagerPanel` and the `TA200001`
  layout let the cameras take the window's spare space, and `Calibrate`
  converts mouse clicks back to image pixels.

## Left out

| What | Why |
|---|---|
| `prominence.*` (Merlin Hughes, *Java Network Programming*), `com.friz.*` and Harald Friz's 1998 telerobot (`telerobot`, `telerobotclient`, `telerobotserver`), `com.afu.EasyIn`, `Jama` sources, IBM XML4J, Ælfred, JGL, JSDT, WebMacro, Swing tutorial samples and the other library trees under `java/src` and `java/lib` | Other people's code |
| `network.*`, `applets/RouterConnectorApplet`, `applets/Ushertwo`, `applets/BookmarkViewer` and `Test`, `telerobotclient/Applet9907` | The pre-MOM router system and applets, built on the excluded book code and on Friz's client classes |
| `io/ASCIIReader`, `util/Interrupt`, `util/cfgFile`, `util/Cloner`, `applications/imageviewer/parseCmdLine` and `Main` | Third-party or derived from third-party code (rewritten where needed; see below) |
| `gui/overlaid/StickCursor` (original) | Adapted from Friz's mouse handlers; rewritten |
| `servlets/*` | Built on book sample code, for a 1999 servlet API |
| `mom/MOMClient`, `mom/tests/*Client`, `TestPanels`, `TokenTest`, `mom/gui/ChannelPanel` and `SessionPanel`, `mom/rmi/impl/MOMConnectionBrokerRMIImpl`, `devices/robot/StateClient`, `CommandApplet`, `CommandQueue`, `gui/overlaid/Object3d` and `Block`, `maths/Polygon`, `io/ProgressInputStream`, `awt/PostscriptGraphics`, `applets/MOMClientApplet` and `Skeleton`, `devices/camera/Test`, `test/*` | Unfinished or stale: never part of a shipped build, and no longer compiled against the rest of the code |
| `devices/camera/NTCameraManager` | JNI layer over the native framegrabber DLL; replaced by `SimCameraManager` |
| C++ (`cpp/`): RobotServer, CameraManager, calibration DLL, ACE, NEWMAT, ABB RobLib/RAP headers | Needs MFC and ABB's proprietary libraries, and includes third-party code; `sim.VirtualRobotServer` stands in for RobotServer |
| User database, logs, HTTP logs, robot controller programs, camera calibration data, the website and its uploads | Personal data and other people's content |

## Written new (2026)

| Class | Replaces / purpose |
|---|---|
| `maths.geom.{Vector3D, Point3D, Line3D, Matrix4x4, VectorN}` | `com.friz.math`: only the operations this code uses |
| `util.Debug`, `util.ConsoleInput` | `com.friz.tools.Debug`, `com.afu.EasyIn` |
| `io.ASCIIReader`, `io.ASCIIInputStream`, `io.ASCIIOutputStream` | `prominence.io` and the old `ASCIIReader`: the whitespace/quoted text format |
| `util.XMLConfigFile` (body) | IBM XML4J; now JAXP |
| `mom.impl.IniUserDatabase` | `NativeUserDatabase` (JNI over `GetPrivateProfileString`) |
| `applications.calibration.CalibrationFile` | The native VIP calibration: least-squares DLT, plus the `.cal` file format |
| `gui.overlaid.{Camera, Line, OverlaidComponent, PlumblineCursor, SpinCursor, TiltCursor, StickCursor}` | Friz's camera model and cursor classes, and the cursor built from them |
| `devices.camera.SimCameraManager` | Simulated cameras for the camera server |
| `sim.*` | The virtual lab: world, robot server, path-script parser (a port of RobotServer's `PathScriptParser.cpp`), scene, virtual cameras, renderer |
| `applications.StaticWebServer` | Serves the applet; it was an NT web server and CGI scripts in 2000 |
| tests, `pom.xml`, scripts, configs, `web/` | Build, run and documentation |

Jama (public domain, NIST) and JUnit come from Maven Central at build time
and aren't part of this repository.
