//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: TestImage.java,v 1.1 1999/12/17 02:21:58 bdalton Exp $ 
// Time-stamp: "1999-12-17 10:02:48 barney"

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




package au.edu.uwa.mech.telerobotclient;

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.net.URL;
import java.text.SimpleDateFormat;


/**
 * Applet to test dragging lines and crosshairs
 * over an image.
 */
public class TestImage extends Applet
{
  
  boolean useGetResource=false;

  public void init()
  {
    String useGR = getParameter("useGR");
    System.out.println("GR is "+useGR);
    useGetResource = Boolean.valueOf(useGR).booleanValue();
  }
  
  
  public void start() {
    showImage("/images/test2.jpg");
    showStatus("Loading image (wait even longer!!)...");
    
  }

  
  public  void paint (Graphics g) {
    if (bgIm != null)
      g.drawImage(bgIm,0,0,this);
    else
      g.drawString("NO Image yet", 0,20);
  }
  
  
  public void showImage (String strURL )
  {
    if(useGetResource){
      try{
        URL imageURL = this.getClass().getResource (strURL);
        System.out.println(imageURL);
        bgIm = getImage (imageURL);
      }
      catch(Exception e){e.printStackTrace();}
    }
    else
    {  
      InputStream is = this.getClass().getResourceAsStream(strURL); 

      if (is == null) 
        System.out.println(strURL +" Not found"); 
      ByteArrayOutputStream baos = new ByteArrayOutputStream(); 
      try 
      { 
        int c; 
        while((c = is.read()) >= 0) 
          baos.write(c); 
        bgIm = getToolkit().createImage(baos.toByteArray()); 
      } 
      catch(IOException e) 
      { 
        e.printStackTrace(); 
      } 
      
    }
    
    //bgIm = getImage(getCodeBase(), strURL);
    //bgIm = Toolkit.getDefaultToolkit().getImage(strURL);
    repaint();
  }

  
  Image bgIm = null;
}


