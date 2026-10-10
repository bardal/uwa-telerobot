
import java.io.*;
import java.net.*;


class HTTPGet 
{
  public static void main (String[] args) throws Exception
  {
    URL url = new URL(args[0]);
    Socket s = new Socket(url.getHost(), 80);
    OutputStream out = s.getOutputStream();
    OutputStreamWriter outw = new OutputStreamWriter(out);
    outw.write("GET "+url.getFile()+" HTTP/1.0\r\n\r\n");
    outw.flush();
    System.out.println("done get");
    
    DataInputStream inStream = new
      DataInputStream(s.getInputStream());
    String inputLine;
    
    while ((inputLine = inStream.readLine()) != null) {
      System.err.println(inputLine);
    }
    inStream.close();

    s.close();
  } // end of main ()
  
  
}
