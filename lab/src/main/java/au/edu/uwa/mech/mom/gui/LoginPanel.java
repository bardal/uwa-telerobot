
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: LoginPanel.java,v 1.14 2000/01/16 10:01:50 bdalton Exp $
// Time-stamp: "2000-01-15 18:19:29 barney"

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


package au.edu.uwa.mech.mom.gui; //inserted by perl script 'checkPackages'

import java.awt.TextField;
import java.awt.Button;
import java.awt.*;
import java.awt.event.*;

import au.edu.uwa.mech.util.TheLog;
import au.edu.uwa.mech.mom.LoginHandler;
import au.edu.uwa.mech.mom.Peer;
import au.edu.uwa.mech.mom.MOMLoginException;
import au.edu.uwa.mech.mom.MOMIOException;


//import au.edu.uwa.mech.mom.impl.*;
import au.edu.uwa.mech.gui.TheConsole;


public class LoginPanel extends java.awt.Panel implements ActionListener
{
  protected TextField loginField = new TextField("guest", 7);
  protected TextField passwordField = new TextField("", 3);
  protected TextField nicknameField = new TextField("", 10);
  protected Button    loginButton = new Button(LOGIN);

  public LoginPanel()
  {
    //setLayout(new BorderLayout());
    add(new Label("username", Label.RIGHT));
    add(loginField);
    add(new Label("password", Label.RIGHT));
    add(passwordField);
    add(new Label("nickname", Label.RIGHT));
    add(nicknameField);
    add(loginButton);

    passwordField.setEchoChar('*');
    
    loginButton.setEnabled(false);
    //listen for login button being pressed
    loginButton.addActionListener(this);
  }


  public void setLoginHandler(LoginHandler lh)
  {
    this.loginHandler = lh;
    loginButton.setEnabled(true);
  }

  public void actionPerformed(ActionEvent e) {
    debug("loginhandler "+loginHandler);
    //debug("Got event "+e.getActionCommand());
    //debug("c:"+connection+" cp:"+connectionParameters);
    try{
      if (!loginHandler.loggedIn())
      {
        TheConsole.println("Logging in... please wait.....");
        loginButton.setEnabled(false);
        Thread lt=new Loginthread();
        debug("starting thread");
        lt.start();             // use a seperate thread so we don't
                                // hang the GUI
      }
      else
      {
        loginHandler.logout();
        loginButton.setLabel(LOGIN);
        TheConsole.println("Logged out bye....");
      } // end of else
      
    } catch (MOMIOException ex) {
      TheConsole.println(ex.toString());
      ex.printStackTrace(System.err);
    } catch (MOMLoginException ex) {
      TheConsole.println(ex.toString());
      ex.printStackTrace(System.err);
    }

  }

  class Loginthread extends Thread 
  {
    public void run()
    {
      try{
        Peer us=loginHandler.login(loginField.getText(),
                           passwordField.getText(), nicknameField.getText());
        loginButton.setLabel(LOGOUT);
        TheConsole.println("Logged in OK as "+us);
      } catch (MOMIOException ex) {
        TheConsole.println(ex.toString());
        TheLog.logException(ex);
      } catch (MOMLoginException ex) {
        TheConsole.println(ex.toString());
        TheLog.logException(ex);
      } 
      loginButton.setEnabled(true);
    }
  }  
  
    
  protected static String LOGIN="login";
  protected static String LOGOUT="logout";
    
  
  protected LoginHandler loginHandler;  

  static public void debug(String s)
  {
    if (debug)
      TheLog.println(s);
  }
  static protected boolean debug=true;
}



