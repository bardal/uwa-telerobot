
// Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Sun Sep 05 22:12:47 1999
// Version       : $Id: SwingConsole.java,v 1.2 1999/09/08 22:33:44 bdalton Exp $ 
// Time-stamp    : "1999-09-04 17:32:06 student"

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



package au.edu.uwa.mech.gui.swing;

import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import au.edu.uwa.mech.gui.TheConsole;
import au.edu.uwa.mech.gui.Console;


public class SwingConsole extends JScrollPane implements Console 
{
  static protected String linefeed=System.getProperty("line.separator");

  public SwingConsole(){
    super();
    ta = new JTextArea();
    setupUI();
  }
  
  
  public SwingConsole(int rows, int cols){
    ta= new JTextArea(rows, cols);
    setupUI();
  }

  private void setupUI()
  {
    setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
    setViewportView(ta);
    ta.setEditable(false);
  }
  

  public void println(String text)
  {
    ta.append(text);
    ta.append(linefeed);
  }

  JTextArea ta;
} 
