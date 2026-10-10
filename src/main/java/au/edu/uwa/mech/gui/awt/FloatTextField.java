
//  Copyright (C) 1999 Barney Dalton

// Author : Barney Dalton <barney@mech.uwa.edu.au>   
// Version: $Id: FloatTextField.java,v 1.3 1999/09/02 18:17:19 bdalton Exp $
// Time-stamp: "1999-09-02 19:59:13 student"

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

public class FloatTextField extends TextField implements TextListener 
{

  public FloatTextField(float initialValue, int width){
    super(Float.toString(initialValue), width);
    lastNumber=initialValue;
    //addKeyListener(this);
    addTextListener(this);
  }
  
  public FloatTextField(){
    addTextListener(this);
  }

  public float getValue()
  {
    try{
      return Float.valueOf(getText()).floatValue();
    } catch ( NumberFormatException e) {
      return lastNumber;
    } //catch (NoSuchMethodError e){
      //not much we can do, just let the user carry on
    //}
    
  }

  public void setValue(float value){
    setText(Float.toString(value));
    lastNumber=value;
  }
  
  
  
  public void textValueChanged(TextEvent event) {
    String s = this.getText();
    int caret = getCaretPosition();
    if (s.length() >0){

      if((s.length()==1) && (s.charAt(0)=='-'))
        return;
      
      try{ lastNumber = Float.valueOf(s).floatValue();}
      catch ( NumberFormatException e) {
        setText(Float.toString(lastNumber));
        setCaretPosition(caret);}
      //catch (NoSuchMethodError e){
        //not much we can do, just let the user carry on
      //}
    }
  }

  float lastNumber;
} /* end numberTextField class */





