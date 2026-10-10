
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: CameraManagerClient.java,v 1.4 2000/02/02 17:30:47 bdalton Exp $
// Time-stamp: "2000-02-02 16:33:09 barney"

// {{{ GNU General Public License
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as
// published by the Free Software Foundation; either version 2 of the
// License, or (at your option) any later version. This program is
// distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
// License for more details.  You should have received a copy of the
// GNU General Public License along with this program; if not, write
// to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
// Boston, MA 02111-1307, USA.
// }}}

package au.edu.uwa.mech.devices.camera;

import java.util.Enumeration;
import java.util.Vector;
import java.util.Hashtable;

import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.mom.Session;
import au.edu.uwa.mech.mom.SessionRoot;
import au.edu.uwa.mech.mom.Channel;
import au.edu.uwa.mech.mom.Context;
import au.edu.uwa.mech.mom.ContextURL;
import au.edu.uwa.mech.mom.MOMException;
import au.edu.uwa.mech.mom.event.ChildEventListener;
import au.edu.uwa.mech.mom.event.MessageEventListener;
import au.edu.uwa.mech.mom.MOMPacket;
import au.edu.uwa.mech.mom.MOMIOException;

public class CameraManagerClient implements ChildEventListener, MessageEventListener, CameraManager
{

  SessionRoot root;
  
  ContextURL cameraSessionURL=new ContextURL("cameras");
  Session camerasSession;
  CameraSettings cameraSettings;
  Vector cameraManagerListeners=new Vector();
  Hashtable cameras = new Hashtable();
  
  public CameraManagerClient(){
  }

  public CameraManagerClient(SessionRoot root){
    setRoot(root);
  }
  
  public void setRoot(SessionRoot root)
  {
    this.root=root;
    root.addChildEventListener(this);
  }
  

  public void childCreated(Context c, Context child){
    TheLog.println("------child created"+c+" "+child);
    if ((c==root) && child.getContextURL().equals(cameraSessionURL)){
      camerasSession=(Session)child;
      camerasSession.addChildEventListener(this);
      try{camerasSession.join();} catch (MOMException e){e.printStackTrace();}
    }
    else if ((c==camerasSession) && (child instanceof Channel)){
      Channel cameraChannel = (Channel)child;
      createCamera(cameraChannel);
    }
  }
  
  public void childDeleted(Context c, ContextURL childURL){
    TheLog.println("child deleted"+c+childURL);
    // if (childURL.equals(robotSessionURL))
    //  releaseContexts();
  }

  public void message (Context c, MOMPacket packet) 
  {
    TheLog.println("CameraManagerClient received "+packet+"\n");

    try{
      Object body=packet.getArgDeserialized();
      if (body instanceof ImageData)
      {
        ImageData currentImage = (ImageData)body;
        TheLog.println("NEW image "+currentImage);
        //for (Enumeration en=cameraListeners.elements(); en.hasMoreElements();)
        //  ((CameraListener)en.nextElement()).newImage(this, currentImage);      
      }
      else if (body instanceof CameraSettings)
      {
        cameraSettings = (CameraSettings)body;
      }
      
        
    } catch (MOMIOException ex) {
      ex.printStackTrace ();
    }
    
  }

  protected Session getCamerasSession(){
    if (camerasSession == null){
      try{
        camerasSession = (Session)root.lookup(cameraSessionURL);
      } catch (MOMException e){
        e.printStackTrace();
      }
    }
    return camerasSession;
  }
  
  public java.util.Enumeration cameras(){
    return cameras.elements();
  }
  
  
  public Camera getCamera(String cameraName) throws CameraException{
    Camera camera;
    if((camera=(Camera)cameras.get(cameraName))!=null)
      return camera;
    else{
      try{
        Channel cameraChannel=(Channel)getCamerasSession().lookup(ContextURL.simpleContext(cameraName, Channel.TYPE_NAME));
        camera=createCamera(cameraChannel);
      } catch (MOMException e){
        e.printStackTrace();
        throw new CameraException("Camera "+cameraName+" not found, did you get the name right");
      }
    }
    return camera;
  }

  protected Camera createCamera(Channel cameraChannel){
    //create from factory
    Camera camera=(Camera)cameras.get(cameraChannel.getContextURL().getContextName());
    if (camera == null)
    {
      camera = new CameraClient((Channel)cameraChannel);
      cameras.put(camera.getName(), camera);
      
      for (Enumeration en=cameraManagerListeners.elements(); en.hasMoreElements();)
        ((CameraManagerListener)en.nextElement()).newCamera(this, camera);
    }
    
    return camera;
  }
  

  
  
  public void addCameraManagerListener(CameraManagerListener cameraManagerListener){
    cameraManagerListeners.addElement(cameraManagerListener);
  }
  
  
  
  public static void main (String[] args)
  {
    try{
    CameraManagerClient cmc=new CameraManagerClient();
    SessionRoot root = new SessionRoot("moms://barney:3058:barney@localhost:4444");
    root.login();
    root.join();
    //root.create(new ContextURL("testerssssssss"));
    //TheLog.println("%%%%%%%%%%%done join");
    Thread.currentThread().sleep(5000);
    cmc.setRoot(root);
    }
    catch (Exception e){
      e.printStackTrace();
    }
    
  } // end of main ()
  
  
}



