
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: ContextURL.java,v 1.11 1999/12/12 10:31:27 bdalton Exp $
// Time-stamp: "1999-12-12 17:53:42 barney"

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


package au.edu.uwa.mech.mom; //inserted by perl script 'checkPackages'

import java.util.StringTokenizer;
import au.edu.uwa.mech.util.TheLog;

public class ContextURL implements java.io.Serializable
{

  public ContextURL(String parentPath, String name, String type)
  {
    this.parentPath=(parentPath != null ? parentPath : "");
    contextName=(name != null ? name : "");
    contextType=(type != null ? type : "");
    
    debug("Created new ContextURL "+this.toString());
  }

  public ContextURL(ContextURL parentURL, ContextURL relativeURL){
    parsePath(parentURL.toString()+"/"+relativeURL.toString());
  }

  public ContextURL(ContextURL parentURL, String name, String type){
    parsePath(parentURL.toString()+"/"+name+"."+type);
  }

  public ContextURL(ContextURL curl){
    parsePath(curl.toString());
  }
  

  public ContextURL(String contextURL)
  {
    parsePath(contextURL);
  }

  //context with no parent
  public static ContextURL simpleContext(String name, String type){
    return new ContextURL("", name, type);
  }
  
  
  protected void parsePath(String contextPath)
  {
    //ignore root/
    if (contextPath.startsWith(ROOT+'/'))
      contextPath = contextPath.substring((ROOT+'/').length());
    
    StringTokenizer st=new StringTokenizer(contextPath,"/", true);
    parentPath="";
    while (st.countTokens() >2)
      parentPath+=st.nextToken();//+"/";

    if (st.countTokens() >1)
      st.nextToken();//ignore trailing slash

    //if (parentPath.equals(""))
    //  parentPath=ROOT;
    
    try {
      st=new StringTokenizer(st.nextToken(), ".");
      contextName=st.nextToken();
    } catch (java.util.NoSuchElementException e) {
      contextName=ROOT;
    }

    try {
      contextType=st.nextToken(); 
    } catch (java.util.NoSuchElementException e) {
      contextType=Session.TYPE_NAME;
    }    
  }

  public boolean equals(Object obj)
  {
    return toString().equals(obj.toString());
  }

  public int hashCode()
  {
    return toString().hashCode();
  }
  
  
  
  public String toString()
  {
    String asString="";
    if (getParentPath().length() > 0)
      asString += getParentPath()+"/";
    
    return asString+getContextFullname();
  }
  
  public String toDebugString()
  {
    return "Type="+getContextType()+
      "\nName="+getContextName()+
      "\nPath="+getParentPath();
  }
  
  public String getContextFullname()
  {
    String asString = getContextName();
    if (!getContextType().equals(Session.TYPE_NAME))
      asString += "."+getContextType();
   
    return asString;
  }
  

  public String getContextType()
  {
    return contextType;
  }
  
  public String getContextName()
  {
    return contextName;
  }

  public ContextURL getRelativeContextURL(){
    return new ContextURL(getContextFullname());
  }
  
  
  public boolean hasParent(){
    return parentPath.length() > 0;
  }
  
  
  //path to parent context
   //fot all contexts including a session this is the parent session
  public String getParentPath()
  {
    return parentPath;
  }
  
  public ContextURL  getParentContextURL()
  {
    return new ContextURL(parentPath);
  }

  //the path of any shildren of the this URL
  //sames as getParentPath for all contexts other than sessions
  public String getSessionPath()
  {
    if (contextType == Session.TYPE_NAME)
      return toString();
    else
      return parentPath;
  }
  
  
  protected String contextType;
  protected String contextName;
  protected String parentPath;
  

  public static void main (String[] args)
  {
    ContextURL a=new ContextURL("");
    ContextURL s=new ContextURL("telerobot");
    ContextURL r=new ContextURL("telerobot/robot.channel");
    ContextURL t=new ContextURL("telerobot/camera/hummer.token");
    ContextURL u=new ContextURL("root/telerobot/hello/there.token");
    System.err.println(a.toDebugString()+"\n>"+a.toString()+"<\n"+a.getParentContextURL().toString()+"\n");
    System.err.println(s.toDebugString()+"\n>"+s.toString()+"<\n"+s.getParentContextURL().toString()+"\n");
    System.err.println(r.toDebugString()+"\n>"+r.toString()+"<\n"+r.getParentContextURL().toString()+"\n");
    System.err.println(t.toDebugString()+"\n>"+t.toString()+"<\n"+t.getParentContextURL().toString()+"\n");
    System.err.println(u.toDebugString()+"\n>"+t.toString()+"<\n"+t.getParentContextURL().toString()+"\n");

    a=new ContextURL("root/cameras/Handycam.channel");
    TheLog.println(a.getParentContextURL());
    
    
  } // end of main ()

  static final long serialVersionUID = -6621079026322065213L;
  public static final String ROOT="root";

  static public void debug(String s)
  {
    if (debug)
      TheLog.println(s);
  }
  static protected boolean debug=true;
}











