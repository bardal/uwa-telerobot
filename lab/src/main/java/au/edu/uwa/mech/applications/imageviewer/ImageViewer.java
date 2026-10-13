package au.edu.uwa.mech.applications.imageviewer; // Generated package name

/**
 * ImageViewer.java
 *
 *
 * Created: Wed Mar 17 16:11:24 1999
 *
 * @author Barney Dalton
 * @version
 */

//usage
//cat cam1images.txt | java au.edu.uwa.mech.applications.imageviewer.ImageViewer 
// list of images | app > point file


import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import au.edu.uwa.mech.maths.Point3i;

public class ImageViewer extends JApplet {

  static Vector points= new Vector();
  static Vector clickedpoints= new Vector();
  JLabel label;
  ImagePanel imagepanel;
  String sourcefilename="cam1images.txt";
  static String WorkingDir="Data";
  Container container;
  
  public static void main(String[] args) {

    ImageViewer iv = new ImageViewer();
    
    //get command line args
    //if (args.length >0)
    //  iv.sourcefilename=args[0];

    //can't chdir int jdk 1.1/1.2 ?&*$%@!!!!!!
    //String separator=System.getProperty("file.separator");
    //System.setProperty("user.dir", System.getProperty("user.dir")+separator+WorkingDir);
    //System.err.println("user dir"+System.getProperty("user.dir"));
    
    JFrame f = new JFrame("ImageDisplayer");
  
    iv.buildUI(f.getContentPane());
    
    f.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {System.exit(0);}
    });

    f.pack();
    f.setVisible(true);
      
  }

  protected void buildUI(Container c)
  {
    this.container=c;
    container.setLayout(new BoxLayout(container,
                                      BoxLayout.Y_AXIS));

    imagepanel = new ImagePanel();

    try{
      //FileReader r = new FileReader(sourcefilename);
      InputStreamReader r=new InputStreamReader(System.in);
      BufferedReader br=new BufferedReader(r);
      imagepanel.readImageFilenames(br);
    
      r.close();
    }
    catch (IOException ioe) {
      System.err.println("IO Error"+ioe.toString());
    }

    imagepanel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e)
        {
          int n = JOptionPane.showConfirmDialog
            (imagepanel,
             "Add "+e.getPoint()+" to list and goto to next image?",
             "Confirm Mouse Click",
             JOptionPane.YES_NO_OPTION);

          if (n==JOptionPane.YES_OPTION)
          {
            System.err.println(e.getPoint());
            clickedpoints.add(e.getPoint());
            System.out.println("imagepoint "+(imagepanel.currentimage+1)
                               +" "+ e.getPoint().x+" "+e.getPoint().y);
            imagepanel.nextImage();
          } // end of if (n==YES_OPTION)
        
        }
    });

    imagepanel.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e)
        {
           label.setText("Current Image "+
                         imagepanel.currentImageFile()
                         +"Mouse coordinates ("
                         + e.getPoint().x + ", "
                         + e.getPoint().y + ")."); 
        }
    });

    imagepanel.addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent evt) {
        System.err.println("image panel resized!");
        //((JFrame)container.getParent()).pack();
      }});
    
    container.add(imagepanel);

    label = new JLabel("Mouse coordinates");
    container.add(label);

    //Align the left edges of the components.
    imagepanel.setAlignmentX(LEFT_ALIGNMENT);
    label.setAlignmentX(LEFT_ALIGNMENT); //unnecessary, but doesn't hurt

    imagepanel.showFirstImage();
    
  }


   
  
} // ImageViewer


class ImagePanel extends JPanel {
  Image image;
  int currentimage=-1;
  Vector images;
  boolean imageloaderror=false;
  //Dimension preferredSize = new Dimension(400,400);
  
  public ImagePanel()//String[] images) {
  {
    //  this.images=images;
    //image =
    //  Toolkit.getDefaultToolkit().getImage(images[currentimage]);


    setFixedSize(new Dimension(400,300));
    
    Border raisedBevel = BorderFactory.createRaisedBevelBorder();
    Border loweredBevel = BorderFactory.createLoweredBevelBorder();
    Border compound = BorderFactory.createCompoundBorder
                              (raisedBevel, loweredBevel);
    setBorder(compound);
    //nextImage();
    //this.image = images[currentimage];
  }

  public String currentImageFile()
  {
    return (String)images.get(currentimage);
  }

  
  
  public void paintComponent(Graphics g) {
    super.paintComponent(g); //paint background
    //Draw image at its natural size first.
    if ((!imageloaderror))
      g.drawImage(image, 0, 0, this);
    else
      g.drawString("failed to load image "+currentImageFile(), 20, 40);
    
  }
  
  public void readImageFilenames(BufferedReader br) throws IOException
  {
    images=new Vector();
    String line;
      while ((line=br.readLine()) != null)
        images.add(line);
  }
  
  //public Dimension getPreferredSize() {
  //  return preferredSize;
  // }

  public void showFirstImage()
  {
    currentimage=-1;
    nextImage();
  }
  
  public void nextImage()
  {
    imageloaderror=false;
    System.err.println("nextImage "+currentimage);
    currentimage++;
    if (currentimage==images.size())
    {
       System.exit(0);
    } // end of if (currentimage==images.length)
    
    image = Toolkit.getDefaultToolkit().createImage(currentImageFile());
    //System.err.println("Current Image "+currentimage+" "+images[currentimage]);

    image.getHeight(this);
    image.getWidth(this);
    //if (h>-1 && w>-1)
    //{
    //   setPreferredSize(new Dimension(w,h));
    //} // end of if (h>-1 && w>-1)
    
    
    repaint();
  }

  public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height)
  {

    if ((infoflags & ERROR) !=0)
    {
       imageloaderror=true;
    } // end of if ((infoflags & ERROR) !=0)
    
    if (((infoflags & WIDTH) !=0)
        &&((infoflags & HEIGHT) !=0))
    {
      System.err.println("Image size is "+width+","+height);
      setFixedSize(new Dimension(width, height));
    } // end of if ((infoflags & WIDTH) && (infoflags & HEIGHT))
    return super.imageUpdate(img, infoflags, x, y, width, height);
  }



  protected void setFixedSize(Dimension d)
  {
    setPreferredSize(d);
    setMinimumSize(d);
    setMaximumSize(d);
    setSize(d);
    //((JFrame)getTopLevelAncestor()).pack();
  }


}
