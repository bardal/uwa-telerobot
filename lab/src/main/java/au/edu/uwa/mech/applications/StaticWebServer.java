package au.edu.uwa.mech.applications;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serves the applet page and jar (applets may only connect back to the
 * host they were loaded from, so this must run on the MOM host). POSTs to
 * /log - the applet's usage logger, a CGI script in 2000 - are appended to
 * logs/applet-usage.log.
 *
 * <pre>java au.edu.uwa.mech.applications.StaticWebServer [port [directory]]</pre>
 */
public class StaticWebServer
{
  public static void main(String[] args) throws IOException
  {
    int port = args.length > 0 ? Integer.parseInt(args[0]) : 18080;
    final File root = new File(args.length > 1 ? args[1] : "web").getCanonicalFile();
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", ex -> {
      try {
        if (ex.getRequestMethod().equals("POST"))
          log(ex);
        else
          serve(ex, root);
      } catch (IOException e) {
        System.err.println("StaticWebServer: " + e);
      } finally {
        ex.close();
      }
    });
    server.start();
    System.out.println("StaticWebServer: serving " + root + " on http://localhost:" + port + "/");
  }

  private static void serve(HttpExchange ex, File root) throws IOException
  {
    String path = ex.getRequestURI().getPath();
    if (path.endsWith("/"))
      path += "index.html";
    File f = new File(root, path).getCanonicalFile();
    if (!f.getPath().startsWith(root.getPath()) || !f.isFile()) {
      byte[] msg = "not found".getBytes("UTF-8");
      ex.sendResponseHeaders(404, msg.length);
      ex.getResponseBody().write(msg);
      return;
    }
    ex.getResponseHeaders().set("Content-Type", type(f.getName()));
    ex.sendResponseHeaders(200, f.length());
    try (OutputStream out = ex.getResponseBody()) {
      Files.copy(f.toPath(), out);
    }
  }

  private static void log(HttpExchange ex) throws IOException
  {
    new File("logs").mkdirs();
    try (InputStream in = ex.getRequestBody();
         OutputStream out = new FileOutputStream("logs/applet-usage.log", true)) {
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0)
        out.write(buf, 0, n);
      out.write('\n');
    }
    ex.sendResponseHeaders(200, -1);
  }

  private static String type(String name)
  {
    String n = name.toLowerCase();
    if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
    if (n.endsWith(".jar")) return "application/java-archive";
    if (n.endsWith(".css")) return "text/css";
    if (n.endsWith(".png")) return "image/png";
    if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
    if (n.endsWith(".gif")) return "image/gif";
    return "application/octet-stream";
  }
}
