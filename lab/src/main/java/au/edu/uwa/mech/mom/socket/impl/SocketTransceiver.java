
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: SocketTransceiver.java,v 1.5 2000/01/28 11:20:54 bdalton Exp $
// Time-stamp: "2000-02-05 16:11:27 barney"

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

import java.net.Socket;
import java.net.InetAddress;
import java.io.*;

import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.mom.*;
import au.edu.uwa.mech.net.URLHelper;

//used by both the MOMServer and MOM peers

//should have two seperate classes, that both inherit from a single parent


public class SocketTransceiver
  implements Transceiver, ManageableTransceiver
{

  // used by MOMConnectionFactory clients
  public SocketTransceiver() {
    translator=new JavaTranslator();
  }  

  
  //    public MOMConnectionSocketImpl(String protocol) {
  //      if (protocol.equals(XML))
  //        translator=new MIMETranslator(); // one transceiver per
  //                                         // connection
  //      else if (protocol.equals(JAVA))
  //        translator=new JavaTranslator();
  //    }


  
                                // used by the server - all
                                // connections share a
                                // MIMETranslator, this constructor
                                // is called after an accept 
  public SocketTransceiver(Translator t){
    this.translator=t;
  }
  
  public void setManager(TransceiverManager tm){
    this.manager=tm;
  }
      
  public void close()
  {
    try{
      if (connected) {
        sender.stop();
        // 2026: the socket is closed before waiting for the receiver. It is blocked reading, which an
        // interrupt does not end, so stop() used to wait out its full second first (on each of a
        // connection's two sockets). Closing ends the read as it did before, just without the wait.
        receiver.stayActive=false;
        try {
          s.close();
        } finally {
          receiver.stop();
        }
      }
    } catch (IOException e){
      TheLog.logException(e);
    } finally {
      upstream = null;
      connected=false;
    }
  }

  public void putqDownstream(Object source, Object arg) throws MOMIOException
  {
    try{
      //debug("SocketTransceiver send("+arg+")");
      //if (debug)
      //  mimeTranslator.writeObject(arg, TheLog);

      //IMPORTANT
      //if we get the translator to write directly to the output
      //stream, this may block in write mode for slow clients. If this
      //blocks while accessing synchronized stuff in arg, then any
      //other trheads trying to do stuff synchronize don arg will
      //block. Therefor need to writeObjecto to a bytearray
      //outputstream, which should never block. We can then block
      //writing this to the socket without any problems of holding the monitor

      // we probably do this a lot for the same object so would be
      // good to cache the results. 
      
      ByteArrayOutputStream memoryOut = new ByteArrayOutputStream();
      translator.writeObject(arg, memoryOut);
      //could block here, but that shouldn't matter to anyone
      memoryOut.writeTo(socketOut);

      //bo.flush(); - done by flusher now
      // 2026: and flushed here as well. Left to the flusher, every message waited up to its 250 ms
      // sendWait, a quarter of a second on each hop (a join took 1.5 s, a command 0.5 s).
      synchronized(socketOut){
        socketOut.flush();
      }
    } catch (java.net.SocketException e){
      throw new MOMConnectionLostException("MOMConnectionImpl::send Lost SOCKET connection");
    } catch (IOException e){
      throw new MOMIOException("Exception in MOMConnectionImpl::send :"+e.getMessage());
    } catch (ConversionException e){
      throw  new MOMIOException("Translator Failed to serialize object "+e.getMessage());
    }
  }

                                
  //boolean stayActive;            // flag to tell us to keep receiving data
  //Thread receiveThread;
  //Object stoppedReceiving=new Object();  // synchronisation lock
  
  //called by MOMListenerImpl too
  public synchronized void startTransceiving(Socket s) throws IOException
  {
    if(connected){
      throw new RuntimeException("Programming Error, call close() first");
    }
    
    connected=true;
    this.s =s;
    //s.setTcpNoDelay(true);
    socketIn = new BufferedInputStream(s.getInputStream());
    socketOut = new BufferedOutputStream(s.getOutputStream());
    remoteHost=s.getInetAddress().getHostAddress();

    
    receiver = new Receiver();
    Thread receiveThread=new Thread(receiver, name+":receive");
    receiveThread.setDaemon(true);
    receiveThread.start();

    debug("about to start sender");
    sender = new Sender();
    Thread senderThread = new Thread(sender, name+":sender");
    senderThread.setDaemon(true);
    senderThread.start();
  }

  public String toString(){
    return "SocketTransceiver to :"+remoteHost;
  }
  

  class Receiver implements Runnable
  {
    Thread active;
    boolean stayActive=true;

    String name="receiver";
    
    void setName(String name){
      this.name=name;
      if (active != null)
        active.setName(name+":receiver");
    }
    
    
    public void stop(){
      stayActive=false;
      if(active != null){
        active.interrupt();
        try{active.join(1000);}
        catch(InterruptedException e){;}
      }
    }

    
    public void run () {
      active = Thread.currentThread();
      stayActive=true;
      debug("Listening for data from "+remoteHost);
      try {
        while (stayActive) {
          Object readObject=translator.readObject(socketIn);
          //TheLog.println("MOMConnectionSocketImpl read object "+readObject);
                                // forward to eventHandler
          if(upstream != null)
            upstream.putqUpstream(this, readObject);
          else
            debug("Nowhere to send object "+readObject);
        } 
      }
      catch (IOException e){
        propogateException(e);
      }
      catch (MOMIOException e){
        propogateException(e);
      }
      catch (ConversionException e){
        propogateException(e);
      }
    
      debug("stopping active receive for "+remoteHost);
    }
  }

  
  class Sender implements Runnable{

    Thread active;
    boolean stayActive=true;
    String name="sender";
    
    void setName(String name){
      this.name=name;
      if (active != null)
        active.setName(name+":sender");
    }
    
    
    public void stop(){
      stayActive=false;
      if(active != null){
        active.interrupt();
        try{active.join(1000);}
        catch(InterruptedException e){;}
      }
    }
    
    public void run(){
      try{
        active = Thread.currentThread();
        debug("in sender "+stayActive);
        
        while(stayActive){
          active.sleep(sendWait);

          if (socketOut != null){
            synchronized(socketOut){
              socketOut.flush();
              //debug("flushing");
            }
          }
        }    
      } catch (IOException e){
        propogateException(e);
      } catch (InterruptedException e){
        propogateException(e);
      }
      debug("stopping active send for "+remoteHost);
    }
  }

  
  void propogateException(Exception e)
  {
    try{
      if (manager !=null)
        manager.handleError(this, e, UPSTREAM);
      else{
        debug("HELP no manager ! Exception in MOMConnectionImpl::run");
        e.printStackTrace();
      }
    }
    
    catch (Exception e2){
      debug("HELP!! exception progating previous Exception!!!");
      e2.printStackTrace();
    }
      
  }
  
  public void putqUpstream(Object source, Object arg)
  {;} //throw an exception here 
  

                                //this should be at the bottom of a stream 
  public void addDownstream(Downstream downstream){;} 
  public void removeDownstream(Downstream downstream){;}
  
  public final void addUpstream(Upstream upstream){
    this.upstream=upstream;
  }
  
  public void removeUpstream(Upstream upstream){
    this.upstream=null;
  }

  
  String name="";
  
  /**
     * Get the value of name.
     * @return Value of name.
     */
  public String getName() {return name;}
  
  /**
     * Set the value of name.
     * @param v  Value to assign to name.
     */
  public void setName(String  v) {
    this.name = v;
    if (receiver != null)
      receiver.setName(v);
    if (sender != null)
      sender.setName(v);
  }
  
  
  
  protected TransceiverManager manager;
  protected boolean connected = false;
  protected Upstream upstream;
  protected Socket s;
  protected BufferedInputStream socketIn;
  protected BufferedOutputStream socketOut;
  
  protected Translator translator;  
  protected String remoteHost;
  
  static final String XML="XML";
  static final String JAVA="JAVA";

  long sendWait=250;
  Sender sender;
  Receiver receiver;
  
  static final protected boolean debug=true;


  protected void finalize(){
    close();
  }
  
  
  protected void debug(String s)
  {
    if (debug)
      TheLog.println(name+":"+s);
  }

  //  public static void main (String[] args)
//    {
//      MOMConnection m= new MOMConnectionSocketImpl();
//      //m.setURL(args[0]);
//      //ConnectionParameters cp=new ConnectionParameters(args[0], Integer.parseInt (args[1]));
//      try{
//        m.connect();
//        //m.login("barney", "3058");
//        System.in.read();
//        m.disconnect();
//      } catch (au.edu.uwa.mech.mom.MOMException e) {
//        System.err.println(e.toString());
//      } catch (IOException e) {
//        System.err.println(e.toString());
//      }
    
  //  } // end of main ()
    
}
