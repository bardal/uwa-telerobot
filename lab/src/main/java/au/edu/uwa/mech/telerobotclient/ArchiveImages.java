//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id$ 
// Time-stamp: "2000-02-02 16:38:12 barney"

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

package au.edu.uwa.mech.telerobotclient;

import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.util.*;
import java.io.*;
import au.edu.uwa.mech.util.*;
import au.edu.uwa.mech.net.*;
import au.edu.uwa.mech.gui.TheConsole;

public class ArchiveImages  extends Dialog implements  ActionListener
{
  URL postURL;
  Button archive;
  String nickname;
  Vector images;
  TextField name;
  TextArea comment;
  
  
  public ArchiveImages(URL postURL, Frame parent, String nickname, Vector images)
  {
    super(parent, "Archive Images", true);
    this.postURL = postURL;
    this.images = images;
    this.nickname = nickname;
    
    setLayout(new BorderLayout());

    Panel p = new Panel(new BorderLayout());
    p.add(new Label("name of your creation"), "North");
    p.add(name = new TextField(20), "South");
    add(p,"North");

    p = new Panel(new BorderLayout());
    p.add(new Label("comments?"), "North");
    p.add(comment = new TextArea(5,30), "South");
    add(p,"Center");

    p = new Panel();
    archive = new Button("archive");
    archive.addActionListener(this);
    Button cancel = new Button("cancel");
    cancel.addActionListener(this);

    p.add(archive);
    p.add(cancel);

    add(p,"South");
    
    
    pack();
    setVisible(true);
  }  

  public void actionPerformed(ActionEvent event)
  {
    setVisible(false);
    dispose();
    
    if (event.getSource() == archive){
      //System.out.println("archinving to "+postURL);
      Hashtable postPairs = new Hashtable();
      postPairs.put("image1", images.elementAt(0));
      postPairs.put("image2", images.elementAt(1));
      postPairs.put("image3", images.elementAt(2));
      postPairs.put("nickname", nickname);
      postPairs.put("name", name.getText());
      postPairs.put("comment", comment.getText());
      try{
        String result = CGIPost.post(postURL, postPairs);
        TheConsole.println(result);
      } catch (IOException e){
        e.printStackTrace();
      }
    }
  }
  
  

  public static void main (String[] args) throws Exception
  {

    Frame f = new Frame();
    Vector images = new Vector();
    images.addElement("c:/mom/htdocs/images/temp/2000012510403490Colour.jpg");
    images.addElement("c:/mom/htdocs/images/temp/2000012510403645YCam.jpg");
    images.addElement("c:/mom/htdocs/images/temp/2000012510403734ZCam.jpg");
    ArchiveImages ai = new ArchiveImages(new URL("http://localhost:400/cgi-shl/ArchiveCreation.pl"),
                                       f, "barney", images);

  } // end of main ()
  
}

