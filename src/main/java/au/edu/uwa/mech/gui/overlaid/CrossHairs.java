
package au.edu.uwa.mech.gui.overlaid;


import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class CrossHairs implements Overlay
{
  CrossHairs(int size){
    this.size=size;
  }
  
  public void overlay(java.awt.Graphics g)
  {
    Color c=g.getColor();
    g.setColor(Color.red);
    g.drawLine(point.x-size, point.y, point.x+size, point.y);
    g.drawLine(point.x, point.y-size, point.x, point.y+size);
    g.setColor(c);
  }
  Point point;
  int size;
}
