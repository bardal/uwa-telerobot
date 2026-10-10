



class Vector
{
  
  public static void main (String[] args)
  {
    StringBuffer a= new StringBuffer("barney");
    java.util.Vector v1 = new java.util.Vector();
    v1.addElement(a);
    java.util.Vector v2 = (java.util.Vector)v1.clone();
    System.out.println(v2);
    a.append(" is in deep shit with his Phd");
    v1.removeElement(a);
    System.out.println(v2);
    System.out.println(v1);
  
  } // end of main ()
}
