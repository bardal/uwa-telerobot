
package au.edu.uwa.mech.gui.overlaid;

import java.awt.event.*;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Color;
import au.edu.uwa.mech.util.*;
import java.util.*;
import au.edu.uwa.mech.gui.overlaid.OverlaidComponent;
import au.edu.uwa.mech.gui.overlaid.Line;
//import au.edu.uwa.mech.gui.StickCursor;
import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.maths.Pose3d;
import au.edu.uwa.mech.maths.Block3d;
import au.edu.uwa.mech.maths.geom.*;
import au.edu.uwa.mech.util.Debug;

public class OverlaidBlock extends OverlaidObject {

  int tolerance=10;

  public OverlaidBlock(){}

  public OverlaidBlock(Vector3D size)
  {
    theBlock.setSize(size);
  }
  
  public void draw (Graphics g)
  {
    if (hasFocus())
      cursor.draw(g);
    else
    {
      g.setColor(color);
      //draw a blob in the middle
      g.fillArc(centreRect.x, centreRect.y, centreRect.width, centreRect.height, 0, 360);
    }
    
    //Debug.println("block Draw called");
    for (Enumeration e= projectedEdges.elements();e.hasMoreElements();)
      ((Line)e.nextElement()).draw(g, Color.black);

  }

  public void movePose (Pose3d p)
  {
    moveBlock(p);
    //System.out.println("done projection");
    cursor.movePose(p);
  }

  public void moveBlock(Pose3d p)
  {
    theBlock.move(p);
    //System.out.println("done move");
    calculateProjectedLines();
  }
  
  public void setCamera(Camera  v)
  {
    super.setCamera(v);
    cursor.setCamera(v);
    cursor.pc.setBaselinesVisible(false);
  }
  
  public void calculateProjectedLines()
  {
    projectedEdges=new Vector();
    for (int i=0; i<theBlock.edges().size(); i++)
    {
      projectedEdges.addElement(camera.projectLine((Line3D)theBlock.edges().elementAt(i)));
    }

    projectedCentre=camera.xyz2uv(theBlock.getPose().position);
    centreRect=new Rectangle(projectedCentre.x-(tolerance/2), projectedCentre.y-(tolerance/2), tolerance, tolerance);
  }

  public void mousePressed(MouseEvent e)
  {
    //Debug.println("block mouse event");
    if (!hasFocus() && centreRect.contains(e.getPoint()))
    {
      //Debug.println("setting focus");
      setFocus(true);
    }
    else if (hasFocus() && cursor.contains(e.getPoint()))
    {
      cursor.mousePressed(e);
    }
    else
      setFocus(false);
    
  }

  public void mouseReleased(MouseEvent e) {
    //if (cursor.contains(e.getPoint())
    if (hasFocus())
      cursor.mouseReleased(e);
  }

  public void mouseDragged(MouseEvent e) {
    if (hasFocus())
    {
      cursor.mouseDragged(e);
      moveBlock(cursor.pose);
    }
  }
  

  public boolean contains(Point p)
  {
    return (centreRect.contains(p) || (hasFocus() && cursor.contains(p)));
  }
  

  protected void lostFocusActions()
  {
    //Debug.println("lost focus actions");
    color=colorNoFocus;
    tellObservers();
  }
  
  protected void gotFocusActions () 
  {
    //Debug.println("got focus actions");
    color=colorFocus;
    //Debug.println("color now "+color);
    tellObservers();
  }

  protected StickCursor cursor=new StickCursor(); 
  protected Color color;
  public Vector projectedEdges;
  public Point projectedCentre;
  public Rectangle centreRect;
  public Block3d theBlock=new Block3d();
  
 
   
  
}
