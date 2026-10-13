
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: CameraClient.java,v 1.15 2000/02/02 17:30:47 bdalton Exp $
// Time-stamp: "2000-02-02 16:41:30 barney"

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
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.mom.Channel;
import au.edu.uwa.mech.mom.SessionRoot;
import au.edu.uwa.mech.mom.Context;
import au.edu.uwa.mech.mom.ContextURL;
import au.edu.uwa.mech.mom.Peer;
import au.edu.uwa.mech.mom.MOMPacket;
import au.edu.uwa.mech.mom.MOMException;
import au.edu.uwa.mech.mom.MOMIOException;
import au.edu.uwa.mech.mom.MOMPermissionException;
import au.edu.uwa.mech.mom.MOMNotFoundException;
import au.edu.uwa.mech.mom.event.LoginEventListener;
import au.edu.uwa.mech.mom.event.TokenEventListener;
import au.edu.uwa.mech.mom.event.PeerEventListener;
import au.edu.uwa.mech.mom.event.MessageEventListener;
import au.edu.uwa.mech.mom.event.ReplyListener;

import au.edu.uwa.mech.maths.Point3d;


public class CameraClient implements MessageEventListener, PeerEventListener
                                     ,Camera
{
  //channel we are subscribed too
  Channel channel;
  ImageData currentImage;
  CameraSettings cameraSettings;
  Vector cameraListeners = new Vector();
  Vector imageListeners = new Vector();
  
  public CameraClient(Channel channel){
    this.channel=channel;
    channel.addMessageEventListener(this);
    channel.addPeerEventListener(this);
    setOn(true);
  }

  public String getName(){
    return channel.getContextURL().getContextName();
  }

  boolean on=false;
  public void setOn(boolean on)
  {
    if (on != this.on)
    {
      this.on=on;
      if (on){
        try{channel.join();} catch (MOMException e){e.printStackTrace();}
      } else //off
        try{channel.leave();} catch (MOMException e){e.printStackTrace();}
    }
  }

  public boolean getOn(){
    return on;
  }
  
  
  public void joined(Context c){
    on=true;
  }
  
  public void left(Context c){
    on=false;
  }
  
  public void peerJoined(Context c, Peer p){;}
  public void peerLeft(Context c, Peer p){;}
  
  
  public void setSettings(CameraSettings newSettings) 
  {
    try{
      //serialiaze settings
      MOMPacket message = channel.getPacketFactory().message();
      CameraCommand command = new SettingsCommand(newSettings, SettingsCommand.SET);
      TheLog.println("Sending new settings command "+command);
      message.setArgSerialized(command);
      channel.sendMessage(message); //get reply???
    } catch (MOMIOException ex) {
      TheLog.logException(ex);
      TheConsole.println("Problem setting new settings "+ex.getMessage());
    }
  }
  
  
  public CameraSettings getSettings()
  {
    if(cameraSettings==null)
    {
      try{
        //serialiaze settings
        MOMPacket message = channel.getPacketFactory().message();
        CameraCommand command = new SettingsCommand();
        message.setArgSerialized(command);
        MOMPacket reply= (MOMPacket)channel.sendAndReceive(message);
        //TheLog.println("settings reply "+reply.getArgDeserialized());
        cameraSettings=(CameraSettings)reply.getArgDeserialized();
        
      } catch (MOMIOException ex) {
        TheLog.logException(ex);
        TheConsole.println("Problem getting settings "+ex.getMessage());
      } catch (ClassCastException ex){
        TheLog.logException(ex);
        TheConsole.println("Problem getting settings "+ex.getMessage());
      }
      
    }
    
    //TheLog.println("settings hopefully aren't null "+cameraSettings);
    //cache and have a new settings event?
    return cameraSettings;
    //cache or get from server?
    //return null;
  }

  public ImageData lastPhoto(){
    return currentImage;
  }
  
  
  //new images are received via the ImageListener Interface
  public ImageData takePhoto() throws CameraException
  {
    try{
      if (on){
        MOMPacket message = channel.getPacketFactory().message();
        message.setArgSerialized(new TakePhotoCommand());
        channel.sendMessage(message);
      } else{
        TheConsole.println("Can't takePhoto The camera is off");
      }
      
    } catch (MOMIOException ex) {
      throw new CameraException("Problem camera - "+ex.getMessage());
      //report back somehow
      //ex.printStackTrace ();
    }
    return null;
  }

  public void setCentre(Point3d point){
    throw new RuntimeException("Not implemented for camera client");
  }
  
  
  public ImageData takePhoto(String filename){
    throw new RuntimeException("Client's can't choose filenames for photos, take what you're given");
  }
  
 
  public void addImageListener(ImageListener l){
    TheLog.println("CameraClient adding imagelistener "+l);
    imageListeners.addElement(l);
  }
  
  public void removeImageListener(ImageListener l){
    imageListeners.removeElement(l);
  }
  
  
  public void message (Context c, MOMPacket packet) 
  {
    //TheLog.println("CameraClient received "+packet+"\n");

    try{
      Object body=packet.getArgDeserialized();
      //TheLog.println("Body is "+body);
      if (body instanceof ImageData)
      {
        currentImage = (ImageData)body;
        //TheLog.println("NEW image "+currentImage+" sending to "+imageListeners.size()+" listeners");
        for (Enumeration en=imageListeners.elements(); en.hasMoreElements();)
          ((ImageListener)en.nextElement()).newImage(this, currentImage);      
      }
      else if (body instanceof CameraSettings)
      {
        cameraSettings = (CameraSettings)body;
        //TheLog.println("have new settings "+cameraSettings);
        TheConsole.println("New settings for camera "+getName());
        for (Enumeration en=cameraListeners.elements(); en.hasMoreElements();)
          ((CameraListener)en.nextElement()).newSettings(this, cameraSettings); 
      }
      else if (body instanceof String){
        TheConsole.println(body);
      }
        
    } catch (MOMIOException ex) {
      TheLog.logException(ex);
    }
    
  }

  //set camera settings

  public void addCameraListener(CameraListener l){
    cameraListeners.addElement(l);
  }
  
  public void removeCameraListener(CameraListener l){
    cameraListeners.removeElement(l);
  }
  
  
}





