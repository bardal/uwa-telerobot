package au.edu.uwa.mech.util;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A {@link ConfigFile} read from XML of the form
 * <pre>
 * &lt;config&gt;
 *   &lt;section&gt;&lt;name&gt;value&lt;/name&gt;&lt;/section&gt;
 * &lt;/config&gt;
 * </pre>
 * Lookups use the first element with the section's tag name anywhere under
 * the root, then the first element with the entry's tag name inside it.
 */
public class XMLConfigFile implements ConfigFile
{
  private final Element root;

  public XMLConfigFile(String xmlConfigFile) throws Exception
  {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setExpandEntityReferences(false);
    root = f.newDocumentBuilder().parse(new File(xmlConfigFile)).getDocumentElement();
  }

  /** The parser class argument is ignored; the JDK's JAXP parser is always used. */
  public XMLConfigFile(String xmlConfigFile, String parserClass) throws Exception
  {
    this(xmlConfigFile);
  }

  public String getString(String section, String name, String defValue)
  {
    Element s = first(root, section);
    Element e = s == null ? null : first(s, name);
    if (e == null)
      return defValue;
    String text = e.getTextContent();
    return text == null ? defValue : text.trim();
  }

  public int getInt(String section, String name, int defValue)
  {
    String s = getString(section, name, null);
    if (s == null)
      return defValue;
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException ex) {
      return defValue;
    }
  }

  private static Element first(Element parent, String tag)
  {
    NodeList nl = parent.getElementsByTagName(tag);
    for (int i = 0; i < nl.getLength(); i++)
      if (nl.item(i).getNodeType() == Node.ELEMENT_NODE)
        return (Element) nl.item(i);
    return null;
  }
}
