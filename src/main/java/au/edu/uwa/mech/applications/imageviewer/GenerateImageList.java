
/**
 * ImageViewer.java
 *
 *
 * Created: Wed Mar 17 16:11:24 1999
 *
 * @author Barney Dalton
 * @version
 */

package au.edu.uwa.mech.applications.imageviewer; //inserted by perl script 'checkPackages'

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import au.edu.uwa.mech.maths.Point3i;

//usage
//java GenerateImageList Right >data/cam2images.txt
// app prefix | imagefilenamesfile
// hardcoded to read xyz.txt for input 

public class GenerateImageList extends JApplet {

  public static void main(String[] args) {

    String prefix="";
    //get command line args
    if (args.length >0)
    {
      prefix=args[0];
    } // end of if (args.length >0)
    

    
    StringTokenizer sto;
    try{
      
      FileReader fr = new FileReader("data/xyz.txt");
      BufferedReader br=new BufferedReader(fr);
      String line;
      while ((line=br.readLine()) != null)
      {
        sto= new StringTokenizer(line);
        sto.nextToken();
        sto.nextToken();
      

        System.out.println(prefix+"#"+Integer.parseInt(sto.nextToken())+"#"+
                           Integer.parseInt(sto.nextToken())+"#"+
                           Integer.parseInt(sto.nextToken())+".gif");
      } // end of while ((line=br.readLine()) !=null)
      
      fr.close();
      
    }
    catch (IOException ioe)
    {
      System.err.println("IO Error"+ioe.toString());
    }
    
    
  } // ImageViewer

}
