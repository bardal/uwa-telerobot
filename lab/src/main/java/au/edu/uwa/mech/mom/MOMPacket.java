
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: MOMPacket.java,v 1.11 2000/01/23 13:02:31 bdalton Exp $
// Time-stamp: "2000-01-23 18:30:55 barney"

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

package au.edu.uwa.mech.mom;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import java.util.Vector;

import au.edu.uwa.mech.util.TheLog;

public class MOMPacket
{
  //MOMPacket() {packet=new Packet();}
  
  public MOMPacket(Packet packet) {
    this.packet=packet;
  }
  
  
  
  Packet packet;
  
  /**
     * Get the value of packet.
     * @return Value of packet.
     */
  public Packet getPacket() {return packet;}
  
  /**
     * Set the value of packet.
     * @param v  Value to assign to packet.
     */
  public void setPacket(Packet  v) {this.packet = v;}
  
  public void setFamily(String family)
  {try{packet.setProperty(Keys.FAMILY, family);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}

  public String getFamily()
  {return (String)packet.getProperty(Keys.FAMILY);}

  public void setSender(Peer peer)
  {try{packet.setProperty(Keys.SENDER, peer);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}
  
  public  Peer getSender()
  {return (Peer)packet.getProperty(Keys.SENDER);}
  
  public  boolean isRequest()
  {return packet.getProperty(Keys.FAMILY).equals(Values.REQUEST);}
    
  public  boolean isReply()
  {return packet.getProperty(Keys.FAMILY).equals(Values.REPLY);}
  
  public  boolean isEvent()
  {return packet.getProperty(Keys.FAMILY).equals(Values.EVENT);}

  public  boolean isError()
  {return packet.getProperty(Keys.NAME).equals(Values.ERROR);}
  
  //public  Object getResult()
  //{return packet.getProperty(Keys.RESULT);}

  public  ContextURL getContextURL()
  {return (ContextURL)packet.getProperty(Keys.CONTEXTURL);}

  public  void setContextURL(ContextURL context)
  {try{packet.setProperty(Keys.CONTEXTURL, context);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}

  public  void setTimestamp(Long time)
  {try{packet.setPropertyReadOnly(Keys.TIMESTAMP, time);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}

  public Long getTimestamp()
  {return (Long)packet.getProperty(Keys.TIMESTAMP) ;}


  public  void setUnimportant(boolean unimportant){
    setBoolean(Keys.UNIMPORTANT, unimportant);
  }

  public  boolean  unimportant(){
    return getBoolean(Keys.UNIMPORTANT);
  }

  public  void setBulk(boolean bulk){
    setBoolean(Keys.BULK, bulk);
  }

  public  boolean isBulk(){
    return getBoolean(Keys.BULK);
  }
  
  
  public  void setBoolean(String name, boolean val)
  {
    try{
      if (val){
        packet.setProperty(name, new Boolean(true));
      }
      else
        packet.unsetProperty(name);
    }
    catch (MOMReadOnlyException e) {TheLog.logException(e);}
  }
  
  public boolean getBoolean(String name)
  {return packet.isPropertySet(name);}
  
  public void setTO(Peer to)
  {
    Vector v = new Vector();
    v.addElement(to);
    setTO(v);
  }
  
  
  public void setTO(Vector to)
  {try{packet.setProperty(Keys.TO, to);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}
  
  public  Vector getTO()
  {return (Vector)packet.getProperty(Keys.TO);}

   
  public void setExclude(Peer exclude)
  {
    {try{packet.setProperty(Keys.EXCLUDE, exclude);}
    catch (MOMReadOnlyException e) {e.printStackTrace();}}
  }
  
  public  Peer getExclude()
  {return (Peer)packet.getProperty(Keys.EXCLUDE);}
  
  
  public void setID(Integer ID)
  {try{packet.setPropertyReadOnly(Keys.ID, ID);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}
  
  public  Integer getID()
  {return (Integer)packet.getProperty(Keys.ID);}
  
  public void setRef(Integer ID)
  {try{packet.setPropertyReadOnly(Keys.REF, ID);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}
  
  public Integer getRef()
  {return (Integer)packet.getProperty(Keys.REF);}
  
  //public  String getEventName()
  //{return (String)packet.getProperty(Keys.EVENT_NAME);}

  public void setName(String Name)
  {try{packet.setProperty(Keys.NAME, Name);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}
  
  public  String getName()
  {return (String)packet.getProperty(Keys.NAME);}
  
   public  Object getArg()
  {return packet.getProperty(Keys.ARG);}

  public  void setArg(Object arg)
  {try{packet.setProperty(Keys.ARG, arg);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}

  public  void unsetArg()
  {try{packet.unsetProperty(Keys.ARG);}
  catch (MOMReadOnlyException e) {e.printStackTrace();}}
  
  //serialize arg before including it in packet
  public void  setArgSerialized(Object arg) throws MOMIOException
  {
    try{

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ObjectOutputStream outO = new ObjectOutputStream(out);
      outO.writeObject(arg);
      setArg(out.toByteArray());
      
    } catch (IOException e){
      e.printStackTrace();
      throw new MOMIOException(e.toString());
    }
   
  }

  public Object getArgDeserialized() throws MOMIOException
  {
    try{

      ByteArrayInputStream in = new ByteArrayInputStream((byte [])getArg());
      ObjectInputStream inO = new ObjectInputStream(in);
      return inO.readObject();

    } catch (IOException e){
      e.printStackTrace();
      throw new MOMIOException(e.toString());
    }  catch (ClassNotFoundException e){
      e.printStackTrace();
      throw new MOMIOException(e.toString());
    }  catch (ClassCastException e){
      throw new MOMIOException("Arg wasn't an serialized object");
    }

  }
  
  
  
  public String toString()
  {
    return getPacket().toString();
  }
  
  
  public static class Keys
  {
    public static final String FAMILY="Family";
    public static final String NAME="Name";
    //static final String REQUEST_NAME="RequestName";
    //static final String EVENT_NAME="EventName";
    public static final String USERNAME="Username";
    public static final String PASSWORD="Password";
    public static final String NICKNAME="Nickname";
    public static final String MANAGER="Manager";
    public static final String CONTEXTURL="Context";
    public static final String TIMESTAMP="Time";
    public static final String SENDER="From";
    public static final String TO="To";
    public static final String EXCLUDE="Exclude";
    //static final String RESULT="Result";
    public static final String ID="ID";
    public static final String REF="RE";
    public static final String SUB_TYPE="Subtype";
    public static final String BULK="Bulk";
    public static final String UNIMPORTANT="Unimportant";
    public static final String ARG="Arg";
    //static final String BODY="Body";

  }

  public static class Values
  {
    // PACKET_FAMILY
    public static final String REQUEST = "Request";
    public static final String REPLY   = "Reply";
    public static final String EVENT   = "Event";

    //NAMES
    public static final String NO_NAME = "Unamed";
    public static final String ERROR   = "Error";
    
    // REQUEST_NAME
    public static final String LOGIN   = "Login";
    public static final String LOGOUT  = "Logout";
    public static final String CREATE  = "Create";
    public static final String EXISTS  = "Exists";
    public static final String JOIN    = "Join";
    public static final String LEAVE   = "Leave";
    public static final String GRAB    = "Grab";
    public static final String RELEASE = "Release";
    public static final String GIVE    = "Give";

    // EVENT_NAME
    public static final String CHILD_CREATED = "ChildCreated";
    public static final String CHILD_DELETED = "ChildDeleted";
    public static final String PEER_JOINED   = "PeerJoined";
    public static final String PEER_LEFT     = "PeerLeft";
    public static final String MESSAGE       = "Message";
    public static final String GRABBED       = "Grabbed";
    public static final String RELEASED      = "Released";
  }


  public static void main (String[] args)
  {
    Packet p = new Packet();
    MOMPacket mp = new MOMPacket(p);
    mp.setUnimportant(true);
    System.out.println(mp);
    System.out.println(mp.unimportant());
    System.out.println(MOMPacket.Keys.UNIMPORTANT);
    System.out.println(p.properties.containsKey(MOMPacket.Keys.UNIMPORTANT));
  } // end of main ()
  
    
  
}



