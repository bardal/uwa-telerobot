//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: AddPointDialog.java,v 1.1 2000/01/26 13:35:53 bdalton Exp $ 
// Time-stamp: "2000-01-26 21:02:10 barney"

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

package au.edu.uwa.mech.model;

import java.awt.*;
import java.awt.event.*;

import au.edu.uwa.mech.gui.awt.IntegerTextField;
import au.edu.uwa.mech.gui.overlaid.OverlaidPoint;
import au.edu.uwa.mech.gui.TheConsole;

//YUCK............... the hack is getting worse
import au.edu.uwa.mech.mom.MOMIOException;

public class AddPointDialog extends Dialog implements ActionListener {

  IntegerTextField xf,yf,zf;
  TextField nameF;
  Button add,cancel;
  //gotta be a nicer way - but I'm out of time
  WorkspaceClient client;
  
  public AddPointDialog(Frame parent, WorkspaceClient client) {
    super(parent, "Add Point", true);

    this.client=client;
    
    xf=new IntegerTextField(0,5);
    yf=new IntegerTextField(0,5);
    zf=new IntegerTextField(0,5);
    nameF = new TextField("point");
    //setLayout(new FlowLayout());
    Panel top = new Panel();
    top.add(new Label("x"));
    top.add(xf);
    top.add(new Label("y"));
    top.add(yf);
    top.add(new Label("z"));
    top.add(zf);

    Panel bottom = new Panel();
    bottom.add(new Label("name of point"));
    bottom.add(nameF);
    
    add = new Button("Add");
    add.addActionListener(this);
    cancel  = new Button("Cancel");
    cancel.addActionListener(this);
    bottom.add(add);
    bottom.add(cancel);

    add(top,"North");
    add(bottom, "South");
    pack();
    setVisible(true);
  }


  public void actionPerformed(ActionEvent event) {
    setVisible(false);
    dispose();
    if (event.getSource() == add)
    {
      if((nameF.getText() != null) && (nameF.getText().length()>0))
      {
        OverlaidPoint op = new OverlaidPoint(xf.getValue(), yf.getValue(), zf.getValue());
        WorkspaceObject wo = new WorkspaceObject();
        wo.overlay=op;
        String name = nameF.getText().trim();
        wo.name=name;
        
        op.setName(name);
        try{client.addObject(wo);}
        catch(MOMIOException e){
          TheConsole.println(e.getMessage());
        }
        
      }
      else
        TheConsole.println("Add point failed - no name specified");
    }
    
  }
}
