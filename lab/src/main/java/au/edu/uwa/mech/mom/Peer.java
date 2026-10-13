
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Peer.java,v 1.11 2000/01/23 13:02:31 bdalton Exp $
// Time-stamp: "2000-01-23 19:08:04 barney"

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

import java.util.Date;
import java.text.SimpleDateFormat;


public class Peer implements java.io.Serializable
{
  public Peer() {
    sessionID=generateSessionID();
  }
  
  
  public Peer(String username, String nickname){
    this.username = username;
    this.nickname = nickname;
    sessionID=generateSessionID();
    if (this.nickname == null || this.nickname.length()==0)
      this.nickname = sessionID;
  }


  protected static synchronized String generateSessionID()
  {
    //add a couple of numbers at end of sessionID to make sure each
    //session is unique 
    if (count>99)
      count=10;
    
    return sessionFormatter.format(new Date())+count++;
  }
  
  
  public boolean equals(Object obj)
  {
    //System.out.println("Peer equals "+this+" "+obj+" "+this.hashCode()+" "+obj.hashCode());
    if (!(obj instanceof Peer))
      return false;
    else
      return hashCode() == obj.hashCode();
  }

  public int hashCode()
  {
    return nickname.hashCode();
  }
  
  public String toString()
  {
    return nickname;
  }

  //public String getUserID(){return null;]
  // public void setUserID(String s){;}
  public String getUsername(){
    return username;
  }
    
  /**
     * Get the value of sessionID.
     * @return Value of sessionID.
     */
  public String getSessionID() {return sessionID;}
  
  /**
     * Set the value of sessionID.
     * @param v  Value to assign to sessionID.
     */
  public void setSessionID(String  v) {this.sessionID = v;}
  
  
  /**
     * Get the value of nickname.
     * @return Value of nickname.
     */
  public String getNickname() {return nickname;}
  
  /**
     * Set the value of nickname.
     * @param v  Value to assign to nickname.
     */
  public void setNickname(String  v) {this.nickname = v;}
  
  
  /**
     * Get the value of userLevel.
     * @return Value of userLevel.
     */
  public int getUserLevel() {return userLevel;}
  
  /**
     * Set the value of userLevel.
     * @param v  Value to assign to userLevel.
     */
  public void setUserLevel(int  v) {this.userLevel = v;}
  
  protected int userLevel;
  protected String username;
  protected String nickname;
  protected String sessionID;
  protected static SimpleDateFormat sessionFormatter
    = new SimpleDateFormat ("yyyyMMddHHmmss");
  protected static int count=10;
  static final long serialVersionUID = 3860460215624136240L;

  public static void main (String[] args)
  {
    Peer p =new Peer("barney", "barney1");
    Peer q =new Peer("barney", "purple");
    count=99;
    Peer r =new Peer("barney", "loop");
    Peer s =new Peer("barney", "rangooon");
    System.err.println(p+"\n"+q+"\n"+r+"\n"+s);
    
    
  } // end of main ()

  
}




