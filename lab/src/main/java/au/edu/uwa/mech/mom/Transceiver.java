
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: Transceiver.java,v 1.4 2000/01/13 13:08:14 bdalton Exp $
// Time-stamp: "2000-01-13 18:34:14 barney"

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


//interface for classes that send and receive objects

//the sender passes objects downstrean
//the receiver passes objects upstream

package au.edu.uwa.mech.mom;

//if transceivers are managed then they should throw exceptions via
//this emanager?

public interface Transceiver extends Upstream, Downstream
{

  //transciever should clean up any resources it uses, and remove all
  //upstream/downstream transcievers from its memory. It SHOULD NOT
  //call the close() method of other transceivers. This should be done
  //by a TransceiverManager.
  public void close();
  
  //to deal with errors, and close down properly
  //sendError(int upstreamOrDownstream, Exception e);

  //public void setManager(TransceiverManager manager);
  
  
  public static final int DOWNSTREAM=1;
  public static final int UPSTREAM=2;
  
}



