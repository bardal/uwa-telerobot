//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: BorderPanel.java,v 1.1 1999/11/16 16:01:16 bdalton Exp $ 
// Time-stamp: "1999-11-16 22:57:15 barney"

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

package au.edu.uwa.mech.gui.awt;

import java.awt.*;



public class BorderPanel extends Panel
{
  Panel internal;
  int thickness=2;
  int gap=4;
  Color color=Color.black;
  String label=new String("");
  
  public BorderPanel(String label){
    this(label, new FlowLayout());
  }
  
  public BorderPanel(String label, LayoutManager layout){
    super(layout);
    this.label=label;
  }

  public Insets getInsets(){
    return new Insets(getFontMetrics(getFont()).getHeight()+2*gap, 1+thickness+gap*2, 
                      thickness+gap*2, thickness+gap*2);
  }
  
  public void paint(Graphics g){
    Dimension d=getSize();
    //Rectangle2D fontBox=g.getFontMetrics().getStringBounds(label, g);
    //int fontHeight = (int)fontBox.getHeight();
    FontMetrics fm = g.getFontMetrics();
    int fontHeight = fm.getAscent()+fm.getDescent();
    int wordWidth  = g.getFontMetrics().stringWidth(label);
    int fontMiddle = fontHeight/2;
    
    g.setColor(getForeground());
    
    for(int i=gap; i < thickness+gap; ++i)
      g.drawRect(i, i+fontMiddle,
                 d.width-(i*2)-1,
                 d.height-(i*2)-fontMiddle-1);

    g.setColor(getBackground());
    int labelStart=((2*gap)+thickness)*2;
    g.fillRect(labelStart-gap,gap, wordWidth+(2*gap), fontHeight);
      g.setColor(getForeground());
    g.drawString(label, labelStart,gap+fm.getAscent());
    super.paint(g);
  }

    
  public static void main (String[] args)
  {
    Frame f = new Frame();
    BorderPanel cp=new BorderPanel("A lott for labels here....", new BorderLayout());
    cp.add(new au.edu.uwa.mech.mom.gui.ChatPanel(), "Center");
    f.add(cp);
    f.pack();
    f.show();
    f.addWindowListener(new java.awt.event.WindowAdapter() {
        public void windowClosing(java.awt.event.WindowEvent e) {System.exit(0);}
      });
  } // end of main ()
  
  

  
//    public Component add(Component comp){
//      return internal.add(comp);
//    }

//    public Component add(String name, Component comp){
//      return internal.add(name, comp);
//    }

//    public Component add(Component comp, int index){
//      return internal.add(comp, index);
//    }

//    public void add(Component comp, Object constraints){
//      internal.add(comp, constraints);
//    }
  
//    public void add(Component comp, Object constraints, int index){
//       internal.add(comp, constraints, index);
//    }

//    public void remove(int index){
//      internal.remove(index);
//    }
//    public void remove(Component comp){
//      internal.remove(comp);
//    }
  
//    public void removeAll(){
//      internal.removeAll();
//    }
}
