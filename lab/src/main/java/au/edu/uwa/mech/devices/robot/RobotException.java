//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: RobotException.java,v 1.2 1999/09/16 14:49:55 bdalton Exp $ 
// Time-stamp: "1999-09-16 13:27:00 student"

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

package  au.edu.uwa.mech.devices.robot;

public class RobotException extends Exception {
    /**
     * Constructs a <code>RobotException</code> with <tt>null</tt> 
     * as its error message string.
     */
    public RobotException() {
	super();
    }

    /**
     * Constructs a <code>RobotException</code>, saving a reference 
     * to the error message string <tt>s</tt> for later retrieval by the 
     * <tt>getMessage</tt> method.
     *
     * @param   s   the detail message.
     */
    public RobotException(String s) {
	super(s);
    }
}
