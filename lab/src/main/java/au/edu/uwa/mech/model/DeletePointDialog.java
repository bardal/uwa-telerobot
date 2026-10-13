//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: DeletePointDialog.java,v 1.1 2000/01/26 13:35:53 bdalton Exp $ 
// Time-stamp: "2000-01-26 21:28:38 barney"

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

public class DeletePointDialog extends Dialog implements ActionListener {
  
  TextField nameF;
  Button delete,cancel;
  //gotta be a nicer way - but I'm out of time
  WorkspaceClient client;
  
  public DeletePointDialog(Frame parent, WorkspaceClient client) {
    super(parent, "Delete Point", true);

    setLayout(new FlowLayout());
    this.client=client;

    nameF = new TextField(20);
    add(new Label("name of point"));
    add(nameF);
    
    delete = new Button("Delete");
    delete.addActionListener(this);
    cancel  = new Button("Cancel");
    cancel.addActionListener(this);
    add(delete);
    add(cancel);

    pack();
    setVisible(true);
  }


  public void actionPerformed(ActionEvent event) {
    setVisible(false);
    dispose();
    if (event.getSource() == delete)
    {
      if((nameF.getText() != null) && (nameF.getText().length()>0))
      {
        String name = nameF.getText().trim();
        try{client.deleteObject(name);}
        catch(MOMIOException e){
          TheConsole.println(e.getMessage());
        }
        
      }
      else
        TheConsole.println("Delete point failed - no name specified");
    }
    
  }
}
