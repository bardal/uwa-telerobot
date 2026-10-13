
// Copyright (C) 1999 Barney Dalton

// Author        : Barney Dalton <barney@mech.uwa.edu.au>
// Created       : Wed Sep 08 10:03:37 1999
// Version       : $Id: FileViewer.java,v 1.1 1999/09/08 22:33:44 bdalton Exp $ 
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

import javax.swing.*;
import javax.swing.text.*;
import java.awt.event.*;
import java.awt.*;
import java.io.*;
import java.net.URL;

public class FileViewer extends JFrame {

  public FileViewer(int x, int y)
  {
    super("File Viewer");
    setSize(x,y);
    setup();
  }
  
  
  public FileViewer(int x, int y, String startingUrl)
  {
    super("File Viewer");
    setSize(x,y);
    setup();
    setURL(startingUrl);
  }
  
  
  public FileViewer(String startingUrl) {
    super("File Viewer");
    setup();
    setURL(startingUrl);
  }

  private void setup()
  {

    JPanel urlPanel = new JPanel();
    urlPanel.setLayout(new BorderLayout());
    urlPanel.add(new JLabel("File: "), BorderLayout.WEST);
    urlPanel.add(urlField, BorderLayout.CENTER);
    ta.setEditable(false);


    JScrollPane jsp = new JScrollPane(ta); 

    // and get the GUI components onto our content pane
    getContentPane().add(jsp, BorderLayout.CENTER);
    getContentPane().add(urlPanel, BorderLayout.NORTH);
    getContentPane().add(statusBar, BorderLayout.SOUTH);


    urlField.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ae) {
          setURL(ae.getActionCommand());
        }
      });
  }

  public void setURL(String url){
    String furl=url;
    try{ furl = (new File(url)).toURL().toString();}
    catch(java.net.MalformedURLException e){}
    urlField.setText(url);
    
    if(dosetURL(url))
      return;
    else if (dosetURL(furl))
      return;
    else
      statusBar.setText("Could not open "+ url+" Using a blank.");
  }
  
  
  private boolean dosetURL(String url)
  {
    try {
      System.out.println("Setting page to "+url);
      ta.setText(readURL(url));
      ta.setCaretPosition(0);
      //ta.setContentType("text/plain");
      return true;
    }
    catch(Exception e) {
      return false;
    }
  }

  protected String readURL(String surl) throws Exception {
      
    URL  url = new URL(surl);
    CharArrayWriter chars=new CharArrayWriter();
    BufferedWriter bw = new BufferedWriter(chars);
    BufferedReader in =
      new BufferedReader(new InputStreamReader(url.openStream()));
    int inI;
    while ((inI = in.read()) != -1)
      bw.write(inI);
      
    in.close();
    bw.close();
    return chars.toString();
  }
  
  
  final JTextArea ta=new JTextArea();
  final JLabel statusBar = new JLabel(" ");
  JTextField urlField = new JTextField();
  
  public static void main(String args[]) {
    File tester=new File("FileViewer.java");
    try{
    System.out.println(tester+"\n"+tester.getAbsolutePath()+" "+tester.getCanonicalPath());
    }catch(java.io.IOException e){
      e.printStackTrace();
    }
    
    
    FileViewer fv= new FileViewer(args.length > 0  ? args[0]: "file:///c:/tmp/test.html");
    fv.setVisible(true);
    fv.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent we) {
	we.getWindow().setVisible(false);
        System.exit(0);
      }
    });

  
  }
}




