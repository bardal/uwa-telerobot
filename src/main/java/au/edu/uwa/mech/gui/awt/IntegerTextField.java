
//  Copyright (C) 1999, 2000 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: IntegerTextField.java,v 1.3 2000/01/18 19:16:42 bdalton Exp $
// Time-stamp: "2000-01-19 02:26:17 barney"

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

package au.edu.uwa.mech.gui.awt;

import java.awt.*;
import java.awt.event.*;

public class IntegerTextField extends TextField implements TextListener 
{

  public IntegerTextField(int initialValue, int width){
    super(Integer.toString(initialValue), width);
    lastNumber=initialValue;
    //addKeyListener(this);
    addTextListener(this);
  }
  
  public IntegerTextField(){
    addTextListener(this);
  }

  public int getValue()
  {
    try{
      return Integer.parseInt(getText());
    } catch ( NumberFormatException e) {
      return lastNumber;
    }
  }

  public void setValue(int value){
    setText(Integer.toString(value));
    lastNumber=value;
  }
  
  
  public void textValueChanged(TextEvent event) {
    String s = this.getText();
    int caret = getCaretPosition();
    if (s.length() >0){

      if((s.length()==1) && (s.charAt(0)=='-'))
        return;
      
      try{ lastNumber = Integer.parseInt(s);}
      catch ( NumberFormatException e) {
        setText(Integer.toString(lastNumber));
        setCaretPosition(caret);
      }
    }
  }

  int lastNumber;
  
//    public void keyTyped(KeyEvent e){
    
//    }


//    public void keyPressed(KeyEvent e) {
//      System.out.println(e);
//      e=null;
//    }


//    public void keyReleased(KeyEvent e) {
    
//    }
  
  
//    /* this function tests input to see if it is a number.
//       It allows for no input to be a Zero */
  
//    public boolean isNumber()
//    {
//      try {
//        tmp = new Integer(Integer.valueOf(this.getText()).integerValue());
//      }
//      catch (NumberFormatException e) {
//        if (this.getText().length() > 0)
//          return false;
//        else
//          tmp = new Integer(0);
//      }
//      return true;
//    } /* end isNumber procedure */
  
//    public integer getInteger()
//    {
//      if(tmp != null) {
//        this.isNumber();
//        return tmp.integerValue();
//      }
//      else 
//        return tmp.integerValue();
//    } /* end getFloat */
} /* end numberTextField class */





