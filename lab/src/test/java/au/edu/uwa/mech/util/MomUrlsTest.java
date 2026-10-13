package au.edu.uwa.mech.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MomUrlsTest
{
  @Test
  public void thePinIsMasked()
  {
    assertEquals("moms://robot:****:robot@localhost:4444/", MomUrls.masked("moms://robot:robot-local:robot@localhost:4444/"));
    assertEquals("moms://camera:****:camera@127.0.0.1:50123/",
        MomUrls.masked("moms://camera:0123456789abcdef:camera@127.0.0.1:50123/"));
    assertEquals("rmi://guest:****:ann@host/", MomUrls.masked("rmi://guest:-1:ann@host/"));
  }

  @Test
  public void anEmptyPinIsStillMasked()
  {
    assertEquals("moms://robot:****:robot@h:1/", MomUrls.masked("moms://robot::robot@h:1/"));
  }

  @Test
  public void urlsWithoutAPinAreUnchanged()
  {
    assertEquals("moms://localhost:4444/", MomUrls.masked("moms://localhost:4444/"));
    assertEquals("not a url", MomUrls.masked("not a url"));
    assertEquals("null", MomUrls.masked(null));
  }
}
