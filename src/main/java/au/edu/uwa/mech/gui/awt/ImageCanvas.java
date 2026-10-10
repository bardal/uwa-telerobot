//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ImageCanvas.java,v 1.11 2000/01/23 13:02:31 bdalton Exp $ 
// Time-stamp: "2000-01-23 20:10:56 barney"

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




package au.edu.uwa.mech.gui.awt;

import java.awt.Graphics;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Image;
import java.awt.Dimension;
import java.awt.MediaTracker;
import java.awt.Container;
import java.awt.Window;
import java.awt.Rectangle;

import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.util.TheLog;

//import java.awt.image.ImageObserver;

public class ImageCanvas extends Canvas {
  
  Image image, nextImage;
  Dimension size;
  int w, h;
  MediaTracker tracker;
  
  public ImageCanvas(int initialWidth, int initialHeight)
  {

    w = initialWidth;
    h = initialHeight;
    size = new Dimension(w,h);
    tracker = new MediaTracker(this);
  }
  
  public Dimension getPreferredSize() {
    return getMinimumSize();
  }

  public Dimension getMinimumSize() {
    return size;
  }

  public Dimension getMaximumSize(){
    return getMinimumSize();
  }
  
  
  public float getAlignmentX(){
    return CENTER_ALIGNMENT;
  }

  public float getAlignmentY(){
    return CENTER_ALIGNMENT;
  }

  
  public void setImage (Image newImage){

    if (newImage == null) {
      TheLog.println("ImageCanvas got invalid image object!");
      return;
    }

    if( image == null || tracker.checkID(0))
      startLoading(newImage);
    else{
      //wait till we've loaded the current one, discard any previous
      //nextImage's we just want the latest to be loaded next
      if(nextImage != null){
        nextImage.flush();
        nextImage=null;
        System.gc();
      }
      nextImage = newImage;
    }
    
  }

  
  private void startLoading(Image newImage){
    //TheLog.println("Starting to load "+newImage);
    if(image != null){
      tracker.removeImage(image);
      //tracker = null;
      image.flush();
      image = null;
      System.gc();
    }

    //tracker = new MediaTracker(this);
    image = newImage;
    fullyLoaded=false;
    nextImage=null;
    tracker.addImage(image, 0);
    repaint();
  }
  

  
  public void doResize(int width, int height)
  {      
    w = width;
    h = height;
    size = new Dimension(w,h);

    //TheLog.println("setting size to "+size);
    
    setSize(w, h);
    invalidate();
   //   Container parent=getParent();
//      while (parent.getParent() != null)
//        parent = parent.getParent();
    
//      if (parent instanceof Window)
//        ((Window)parent).pack();
              
//      parent.validate();        
  }
  
  //double buffering paint routines
  Dimension offDimension;
  Image offImage;
  Graphics offGraphics;
  boolean fullyLoaded=false;
  
  /**     * Paint the previous frame (if any).     */
  public void paint(Graphics g) {
    setClipRect(g);
    if (offImage == null)
      update(g);
    else
      g.drawImage(offImage, 0, 0, null);
  }

  public void update(Graphics g) {
    //memory leak problems
   //   if(offImage !=null){
//        offGraphics.dispose();
//        offImage.flush();
//        offGraphics=null;
//        offImage=null;
//      }

    setClipRect(g);
    Dimension d = size();
                                // Create the offscreen graphics
                                // context
    if ((offGraphics == null)
        || (d.width != offDimension.width)
        || (d.height != offDimension.height))
    {
      //TheLog.println("Allocating new offscreen image "+d);

      if(offImage !=null){
        offGraphics.dispose();
        offImage.flush();
        offGraphics=null;
        offImage=null;
        System.gc();
      }
      
      offDimension = d;
      offImage = createImage(d.width, d.height);
      offGraphics = offImage.getGraphics();
    }

    //set clipping rect so we don't paint outside image, even if
    //we're larger than the image
    //Rectangle clip=g.getClipBounds();
    //offGraphics.setClip(g.getClipBounds().intersection(new Rectangle(size)));
    offGraphics.setClip(new Rectangle(size));
    
    doPaint(offGraphics);	// Paint the image onto the offscreen
    g.drawImage(offImage, 0, 0, null);
  }
  
  public void doPaint (Graphics g) {
    //TheLog.println("ImageCanvas doPaint called");
    if (image != null) {
      //TheLog.println("Have an image so drawing it!");
      if (!fullyLoaded) {
        
        if (tracker.checkID(0, true)){
          //TheLog.println("Tracker reckons we're loaded "+image.getWidth(this)+","+image.getHeight(this));
          fullyLoaded = true;
          if (tracker.isErrorAny()){
            TheLog.println("Error loading image: "+ image);
            TheConsole.println("Error loading image, is the webserver running?");
          }
          
          if(nextImage != null){
            startLoading(nextImage);
          }
        }
        
        int imageWidth = image.getWidth(this);
        int imageHeight = image.getHeight(this);
        //TheLog.println("Not fullyloaded us "+w+","+h+" image"+imageWidth+","+imageHeight);
                        //Component-initiated resizing.
        if ((imageWidth > 0) && (imageHeight > 0) &&
            ((w != imageWidth) || (h != imageHeight)))
          doResize(imageWidth, imageHeight);
        
      }
      g.drawImage(image, 0, 0, this);
    }
    else
    {
      TheLog.println("No image so we're drawing a white background");
      Color c=g.getColor();
      g.setColor(Color.white);
      g.fillRect(0,0,w-1,h-1);
      g.setColor(c);
    }
    
    g.setColor(Color.black);
    g.drawRect(0, 0, w - 1, h - 1);
  }

  void setClipRect(Graphics g){
    g.setClip(new Rectangle(new Dimension(w,h)));
    //Rectangle us = new Rectangle(0,0,w,h);
    //Rectangle clip = g.getClipBounds().intersection(us);
    //g.setClip(clip.x, clip.y, clip.height, clip.width);
  }
  
  
}


  


//double buffering paint routines

//  public void paint (Graphics g){
//    /* test double buffer on off */
//    boolean buffering = true;
//    //TheLog.println("paint called"+g.getClipBounds());
      
//      if (buffering) {	

//        if (imDB==null)
//        {
//          imDB = createImage(getSize().width, getSize().height);
//          grDB = imDB.getGraphics();
//        }
        
//        grDB.setClip(g.getClip());
//        _paint(grDB); 
//        g.drawImage(imDB, 0, 0, this);
//      } else 
//        _paint(g);
//    }

    
//    protected void _paint (Graphics g) {

//      //TheLog.println("_paint called "+g.getClipBounds());
//      Rectangle clipRect=g.getClipBounds();
      
//      if (image != null) {

        
//        if (!trueSizeKnown) {
//          doResize(image.getWidth(this),  image.getHeight(this));
//        }

//        //g.drawImage (image, 0, 0, this);
//        int x1=clipRect.x;
//        int y1=clipRect.y;
//        int x2=x1+clipRect.width;
//        int y2=y1+clipRect.height;
        
        
//        g.drawImage (image, x1,y1,x2,y2,x1,y1,x2,y2, this);
//        g.setColor(Color.blue);
//        g.drawRect(0, 0, imageRect.width-1, imageRect.height-1);
//      } else {
//        g.setColor(Color.white);
//        g.fillRect(0,0,1000,1000);
//        g.setColor(Color.blue);
//        g.drawString("Waiting for image ...", 100, 100);
//      }	
//    }

//  }



