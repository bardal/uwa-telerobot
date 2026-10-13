
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Factory.java,v 1.4 2000/01/20 11:49:18 bdalton Exp $
// Time-stamp: "2000-01-19 21:48:36 barney"

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

package au.edu.uwa.mech.util;


import java.util.Hashtable;


public class Factory
{
  Hashtable classes=new Hashtable();
  
  public Object build(String Type) throws BuildException 
  {
    try { 
      //debug("building "+Type);//+" "+classes.get(Type));
      return (Class.forName((String)classes.get(Type))).newInstance();
    } catch (java.lang.NullPointerException e) {
      throw new BuildException("Type "+Type+" not registered with Factory");
    } catch (java.lang.InstantiationException e){
      throw new BuildException("Factory Configuration Error for "
                               +Type+
                               ". "+(String)classes.get(Type)+" is Abstract, an Interface, or has no default constructor"); 
    } catch (java.lang.IllegalAccessException e){
      throw new BuildException("Factory Configuration Error for "
                               +Type+
                               ". Registered class is not accessible");
    }
    catch (java.lang.ClassNotFoundException e){
       throw new BuildException("Factory Configuration Error for "
                                +Type+
                                ". Registered class cannot be found");
    }
    
  }
  
  public void register(String name, String classname)
  {
    //debug("registering "+name);
    classes.put(name, classname);
    //debug("registered "+name);
  }

  static protected boolean debug=true;
  static protected void debug(String s)
  {
    if (debug)
      TheLog.println(s);
  }
  
}



