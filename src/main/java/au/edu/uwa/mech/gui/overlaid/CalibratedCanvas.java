
// Copyright (C) 1999, 2000 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Wed Sep 08 16:59:31 1999
// Version       : $Id: CalibratedCanvas.java,v 1.5 2000/01/25 15:42:40 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

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


package au.edu.uwa.mech.gui.overlaid;

import java.awt.Graphics;
import java.util.Vector;
import java.util.Enumeration;
import au.edu.uwa.mech.gui.awt.ImageCanvas;
import java.util.Observer;
import Jama.Matrix;

public class CalibratedCanvas extends ImageCanvas
  implements Background, CalibratedBackground, java.util.Observer
{

  Vector overlays = new Vector();
  Matrix calibrationMatrix;
  
  public CalibratedCanvas(int w, int h){
    super(w, h);
  }

  public void setCalibrationMatrix(Matrix cm){
    this.calibrationMatrix=cm;
  }

  public Matrix getCalibrationMatrix(){
    return calibrationMatrix;
  }
  
  
  public void doPaint (Graphics g) {
    //System.out.println("Calibrated canvas doPaint called with "+g);
    super.doPaint(g);
    for (Enumeration e=overlays.elements();e.hasMoreElements();)
    {
      Object toDraw = e.nextElement();
      if(toDraw instanceof Overlay)
        ((Overlay)toDraw).overlay(g);
      else if (calibrationMatrix !=null)
        ((Overlay3D)toDraw).projectOnTo(this, g);
    }
  }

  public void update(java.util.Observable o, Object arg){
    //System.out.println("Update called from "+o+" for CalibratedCanvas "+this);
    invalidate();
    repaint();
  }
  
  
  public void addOverlay(Overlay o){
    overlays.addElement(o);
    repaint();
  }

  public void removeOverlay(Overlay o){
    overlays.removeElement(o);
    repaint();
  }

  public void addOverlay(Overlay3D o){
    overlays.addElement(o);
    o.observable().addObserver(this);
    repaint();
  }

  public void removeOverlay(Overlay3D o){
    overlays.removeElement(o);
    o.observable().deleteObserver(this);
    repaint();
  }
}



