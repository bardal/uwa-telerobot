package au.edu.uwa.mech.shared;

import java.io.*;
import java.util.*;
import java.net.*;
import au.edu.uwa.mech.io.*;

public class Result {

  public int MajorCode(){return _MajorCode;}
  public long MinorCode(){return _MinorCode;}
  public String Text(){return _Text;}

  public boolean Error()
  {
    if (_MajorCode==scError)
      return true;
    else
      return false;
  }

  public static Result newOK(String Text)
  {
    Result R=new Result();
    R.SetOK(Text);
    return R;
  } 

  public static Result newFail(long minorcode, String Text)
  {
    Result R=new Result();
    R.SetFail(minorcode, Text);
    return R;
  } 



  public boolean OK()
  {
    return !Error();
  }

  public void SetOK(String Message)
  {
    _MajorCode=scOK;
    _MinorCode=0;
    _Text=new String(Message);
  }

  
  public void SetFail(long nMinorCode, String nMessage)
  {
    _MajorCode=scError;
    _MinorCode=nMinorCode;
    _Text=new String(nMessage);
  }

  public void read(String s) throws IOException
  {
    ASCIIInputStream ais=new ASCIIInputStream(new ByteArrayInputStream(s.getBytes()));
    read(ais);
  }
  
  
  public void read(ASCIIInputStream is) throws IOException
  {
    //ASCIIInputStream ais = new ASCIIInputStream(is);
    _MajorCode=is.readInt();
    _MinorCode=is.readLong();
    _Text=is.readQuotedString();
  }

  public static Result ReadResult(ASCIIInputStream ai) throws IOException
  {
    Result R=new Result();
    R.read(ai);
    //    System.out.println("Read result");
    return R;
  }


  public void write(ASCIIOutputStream os) throws IOException
  {
    os.writeInt(_MajorCode);
    os.writeSpace();
    os.writeLong(_MinorCode);
    os.writeSpace();
    os.writeQuotedString(_Text);
    os.writeSpace();
  }

  public String getText(){
    return _Text;
  }
  

  public String toString(){
    return new String(_MajorCode+" "+_MinorCode+" \""+_Text+"\"");
  }
  
  
  protected int _MajorCode;
  protected long _MinorCode;
  protected String _Text;

  public static final  int scOK=1, scIntermediate=2, scWarning=3, scError=4;

}
