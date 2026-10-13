package au.edu.uwa.mech.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MOM login URLs ({@code moms://user:PIN:nick@host:port/}) made safe to print: the PIN is
 * replaced by {@code ****}, so logs and console output never carry a password.
 */
public final class MomUrls
{
  /** scheme://user: then the PIN (no : @ or /), then :nick@ */
  private static final Pattern PIN = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*://[^:@/]*:)[^:@/]*(:[^@/]*@)");

  private MomUrls() {}

  /** The URL with its PIN masked; a URL without a user:PIN:nick part is returned unchanged. */
  public static String masked(String url)
  {
    if (url == null)
      return "null";
    Matcher m = PIN.matcher(url);
    if (!m.find())
      return url;
    return m.group(1) + "****" + m.group(2) + url.substring(m.end());
  }
}
