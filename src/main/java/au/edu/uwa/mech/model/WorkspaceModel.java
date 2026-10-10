//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: WorkspaceModel.java,v 1.2 2000/01/27 11:50:44 bdalton Exp $ 
// Time-stamp: "2000-01-27 16:14:10 barney"

// {{{ GNU General Public License
//  This program is free software; you can redistribute it and/or
//  modify it under the terms of the GNU General Public License
//  as published by the Free Software Foundation; either version 2
//  of the License, or (at your option) any later version.

//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.

//  You should have received a copy of the GNU General Public License
//  along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
// }}}

package au.edu.uwa.mech.model;



import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;

import au.edu.uwa.mech.gui.overlaid.Camera;
import au.edu.uwa.mech.gui.overlaid.Line;
import au.edu.uwa.mech.gui.overlaid.OverlaidObject;

import au.edu.uwa.mech.gui.overlaid.*;
import au.edu.uwa.mech.maths.*;
import au.edu.uwa.mech.util.*;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.maths.geom.*;


//quick hack
//prbably should have a base class that is just the model, with no graphical coupling

public class WorkspaceModel implements Overlay3D, MouseListener, Observer
{
  //
  protected Hashtable objects = new Hashtable();

  public void loadFromFile(String filename)
  {
    try{
    FileInputStream fIn = new FileInputStream(filename);
    ObjectInputStream oIn = new ObjectInputStream(fIn);
    objects=(Hashtable)oIn.readObject();
    oIn.close();
    } catch (IOException e){
      e.printStackTrace();
    } catch (ClassNotFoundException e){
      e.printStackTrace();
    }
    if(objects ==null)
      objects = new Hashtable();
    //need to run a addObserver(this) on each object
  }

  public void saveToFile(String filename)
  {
    try{
      FileOutputStream fOut = new FileOutputStream(filename);
      ObjectOutputStream oOut = new ObjectOutputStream(fOut);
      oOut.writeObject(objects);
      oOut.close();
    }
    catch (IOException e){
      e.printStackTrace();
    }
  }

  public void addObject(String name, WorkspaceObject object){
    objects.put(name, object);
    object.overlay.observable().addObserver(this);
    tellObservers();
  }
  
  public void deleteObject(String name){
    Object oo=objects.remove(name);
    if(oo !=null)
      ((WorkspaceObject)oo).overlay.observable().deleteObserver(this);
    tellObservers();
  }

  public WorkspaceObject get(String name){
    return (WorkspaceObject)objects.get(name);
  }

  public Enumeration objectNames(){
    return objects.keys();
  }

  boolean visible=true;
  public void setVisible(boolean visible){
    if (this.visible != visible){
      this.visible = visible;
      tellObservers();
    }
  }
  
  
  public InteractiveOverlay3D getSelected(){
    return previousSelected;
  }
  
  
  InteractiveOverlay3D previousSelected;
  
  public void mouseClicked(MouseEvent e){
    if (e.getSource() instanceof CalibratedBackground)
    {
      //System.out.println("PointMeasure got click from calibratedcanvas");
      CalibratedBackground cc = (CalibratedBackground)e.getSource();
      Camera camera=new Camera(cc.getCalibrationMatrix().getArray(), new Point(0,0), 1);
      for  (Enumeration en=objects.keys();en.hasMoreElements();)
      {
        String key = (String)en.nextElement();
        Overlay3D oo=get(key).overlay;
        if (oo instanceof InteractiveOverlay3D)
        {
          InteractiveOverlay3D overlay =  (InteractiveOverlay3D)oo;
          overlay.mouseClicked(e);
          if(overlay.getSelected()){
            if ((previousSelected !=null) && (previousSelected != overlay))
              previousSelected.setSelected(false);
            previousSelected = overlay;
          }
          else
            if(overlay == previousSelected){
              //previously selected object is now not selected
              //so we have nothing selected
              previousSelected = null;
            }
          
          
          if (e.isConsumed()){
            break;
          }
        }
      }    
    }
  }
  


  public void projectOnTo(CalibratedBackground cb, Graphics g)
  {
    if(visible)
      for  (Enumeration e=objects.keys();e.hasMoreElements();){
        String key = (String)e.nextElement();
        Overlay3D oo=(Overlay3D)get(key).overlay;
        oo.projectOnTo(cb,g);
      }
  }

  public void tellObservers()
  {
    observable.setChanged();
    observable.notifyObservers(this);
  }

  public void update(Observable o, Object arg){
    tellObservers();
  }
  
  
  public void mouseDragged(MouseEvent e) {;}
  public void mouseMoved (MouseEvent e ) {;}

  public void mouseExited(MouseEvent e) {;}
  public void mousePressed(MouseEvent e) {;}
  public void mouseReleased(MouseEvent e) {;}
  public void mouseEntered(MouseEvent e){;}
  ContainedObservable observable = new ContainedObservable();
  public Observable observable() {return observable;}
  
}

