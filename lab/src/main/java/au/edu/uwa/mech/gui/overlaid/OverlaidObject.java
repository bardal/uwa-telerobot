
package au.edu.uwa.mech.gui.overlaid;

import java.awt.event.*;
import au.edu.uwa.mech.util.*;
import java.util.Observable;
import au.edu.uwa.mech.gui.overlaid.OverlaidComponent;
import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.maths.Pose3d;

//this will be overlaid component one day - once we get rid of
//multiple versions of the applet

public abstract class OverlaidObject
  extends
  OverlaidComponent
  implements
  MouseMotionListener,
  MouseListener, 
  ObservableI
{ 

  protected ContainedObservable _observable= new ContainedObservable();
  public Observable observable() {
    return _observable;
  }
  
  public void tellObservers()
  {
    _observable.setChanged();
    _observable.notifyObservers(this);
  }
  

  
  public void mouseDragged(MouseEvent e) {;}
  public void mouseMoved (MouseEvent e ) {;}
  public void mouseClicked(MouseEvent e){;}
  public void mouseExited(MouseEvent e) {;}
  public void mousePressed(MouseEvent e) {;}
  public void mouseReleased(MouseEvent e) {;}
  public void mouseEntered(MouseEvent e){;}

  protected void gotFocusActions(){;}
  protected void  lostFocusActions(){;}
  
  Pose3d pose;
  
  Camera camera;
  
  /**
     * Get the value of camera.
     * @return Value of camera.
     */
  public Camera getCamera() {return camera;}
  
  /**
     * Set the value of camera.
     * @param v  Value to assign to camera.
     */
  public void setCamera(Camera  v) {this.camera = v;}
  

}
