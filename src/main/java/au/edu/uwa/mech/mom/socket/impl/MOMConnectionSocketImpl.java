
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMConnectionSocketImpl.java,v 1.22 2000/02/02 17:30:47 bdalton Exp $
// Time-stamp: "2000-02-05 01:34:33 barney"

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

package au.edu.uwa.mech.mom.socket.impl;

import java.net.*;
import java.io.*;

import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.net.URLHelper;
import au.edu.uwa.mech.net.Hostsfile;

public class MOMConnectionSocketImpl
  implements MOMConnection, ManageableTransceiver
{

  public MOMConnectionSocketImpl(){
    bulkTransceiver.addUpstream(this);
    controlTransceiver.addUpstream(this);
  }
  
  
  // MOMConnection Interface fns
  public void setURL(String url)
  {
    URLHelper urlh=new URLHelper(url);
    host=urlh.getHost();
    port=urlh.getPort();
  }

  public void setManager(TransceiverManager tm){
    this.manager=tm;
    bulkTransceiver.setManager(tm);
    controlTransceiver.setManager(tm);
  }
      
  public void connect() throws MOMIOException
  {
    try {
      if (!connected)
      {
        debug("connecting to "+host);
        debug("connecting to control port "+port);
        Socket sControl;
        try{sControl = new Socket (host,port);}
        catch(UnknownHostException e){
          TheLog.logException(e);
          host = Hostsfile.lookup(host);
          TheLog.println("Trying ip address "+host);
          sControl = new Socket (host,port);
        }
        

        //read in port for second bulk connection
        DataInputStream dis = new DataInputStream(sControl.getInputStream());
        int bulkPort = dis.readInt();
        //int bulkPort = 4445;
        debug("Connecting to bulk port "+bulkPort);
        Socket sBulk = new Socket(host, bulkPort);
        //connect to remote port
        //connect up transceivers
        //create a socket transceiver for both connections
        controlTransceiver.startTransceiving(sControl);
        bulkTransceiver.startTransceiving(sBulk);
        connected=true;
      }
      else
        debug("Already connected!!!");
    } catch (IOException e) {
      throw new MOMIOException("Failed to connect to "+host+":"+port+" reason: "+e);
    }
  }

  public void disconnect() 
  {
    if (connected) {
      controlTransceiver.close();
      bulkTransceiver.close();
      connected=false;
      upstream = null;
    }    
  }

  public void close(){
    disconnect();
  }
  
  public boolean connected() {return connected;}  

  public void putqDownstream(Object source, Object arg) throws MOMIOException
  {
    if (arg instanceof MOMPacket){
      MOMPacket p = (MOMPacket)arg;
      Packet data = p.getPacket();
      if (!p.isBulk())
        controlTransceiver.putqDownstream(this,data);
      else
        bulkTransceiver.putqDownstream(this,data);
    } else //bulk
      bulkTransceiver.putqDownstream(this,arg);
    
  }

  
  void propogateException(Exception e)
  {
    if (manager !=null)
      manager.handleError(this, e, UPSTREAM);
    else{
      debug("HELP no manager ! Exception in MOMConnectionImpl::run");
      e.printStackTrace();
    }
  }

  //called by bulk and control transceivers
  public void putqUpstream(Object source, Object arg) throws MOMIOException{
    upstream.putqUpstream(this, arg);
  } 
  

     
  public void addDownstream(Downstream downstream){;} 
  public void removeDownstream(Downstream downstream){;}
  
  public final void addUpstream(Upstream upstream){
    this.upstream=upstream;
  }
  
  public void removeUpstream(Upstream upstream){
    this.upstream=null;
  }
  
  protected SocketTransceiver bulkTransceiver = new SocketTransceiver();
  protected SocketTransceiver controlTransceiver = new SocketTransceiver();
  
  protected TransceiverManager manager;
  protected boolean connected = false;
  protected Upstream upstream;

  protected int port;
  protected String host;

  public void setName(String name){
    this.name=name;
  }

  public String toString(){
    return new String("MOMConnectionSocketImpl:"+name);
  }
  

  String name="";
  
  static final protected boolean debug=true;

  protected void debug(String s)
  {
    if (debug)
      TheLog.println(s);
  }

  public static void main (String[] args)
  {
    MOMConnection m= new MOMConnectionSocketImpl();
    m.setURL(args[0]);
    //ConnectionParameters cp=new ConnectionParameters(args[0], Integer.parseInt (args[1]));
    try{
      m.connect();
      //m.login("barney", "3058");
      System.in.read();
      m.disconnect();
    } catch (au.edu.uwa.mech.mom.MOMException e) {
      System.err.println(e.toString());
    } catch (IOException e) {
      System.err.println(e.toString());
    }
    
  } // end of main ()
    
}
