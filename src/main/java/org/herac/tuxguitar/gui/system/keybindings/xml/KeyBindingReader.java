package org.herac.tuxguitar.gui.system.keybindings.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.herac.tuxguitar.gui.editors.chord.ChordSelector;
import org.herac.tuxguitar.gui.system.keybindings.KeyBinding;
import org.herac.tuxguitar.gui.system.keybindings.KeyBindingAction;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class KeyBindingReader {
  private static final String SHORTCUT_ATTRIBUTE_ACTION = "action";
  private static final String SHORTCUT_ATTRIBUTE_KEY = "key";
  private static final String SHORTCUT_ATTRIBUTE_MASK = "mask";
  private static final String SHORTCUT_TAG = "shortcut";

  /**
   * Read shortcuts from xml file
   * 
   * @param shortcutsNode
   * @return
   */
  private static List<KeyBindingAction> getBindings(Node shortcutsNode) {
    List<KeyBindingAction> list = new ArrayList<KeyBindingAction>();

    NodeList nodeList = shortcutsNode.getChildNodes();
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node child = nodeList.item(i);
      String nodeName = child.getNodeName();

      if (nodeName.equals(SHORTCUT_TAG)) {
        NamedNodeMap params = child.getAttributes();

        Node nodeKey = params.getNamedItem(SHORTCUT_ATTRIBUTE_KEY);
        Node nodeMask = params.getNamedItem(SHORTCUT_ATTRIBUTE_MASK);
        Node nodeAction = params.getNamedItem(SHORTCUT_ATTRIBUTE_ACTION);
        if (nodeKey != null && nodeMask != null && nodeAction != null) {
          String key = nodeKey.getNodeValue();
          String mask = nodeMask.getNodeValue();
          String action = nodeAction.getNodeValue();

          if (key != null && mask != null && action != null) {
            list.add(new KeyBindingAction(action, new KeyBinding(Integer
                .parseInt(key), Integer.parseInt(mask))));
          }
        }
      }
    }
    return list;
  }

  private static Document getDocument(File file) {
    Document document = null;
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      document = builder.parse(file);
    } catch (SAXException sxe) {
      LOG.error(sxe);
    } catch (ParserConfigurationException pce) {
      LOG.error(pce);
    } catch (IOException ioe) {
      LOG.error(ioe);
    }
    return document;
  }

  /** The Logger for this class. */
  public static final transient Logger LOG = Logger
      .getLogger(KeyBindingReader.class);
  
  private static Document getDocument(InputStream is) {
    Document document = null;
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    try {
      DocumentBuilder builder = factory.newDocumentBuilder();
      document = builder.parse(is);
    } catch (SAXException sxe) {
      LOG.error(sxe);
    } catch (ParserConfigurationException pce) {
      LOG.error(pce);
    } catch (IOException ioe) {
      LOG.error(ioe);
    }
    return document;
  }

  public static List<KeyBindingAction> getKeyBindings(InputStream is) {
    try {
      if (is != null) {
        return getBindings(getDocument(is).getFirstChild());
      }
    } catch (Throwable throwable) {
      LOG.error(throwable);
    }
    return null;
  }

  public static List<KeyBindingAction> getKeyBindings(String fileName) {
    try {
      File file = new File(fileName);
      if (file.exists()) {
        return getBindings(getDocument(file).getFirstChild());
      }
    } catch (Throwable throwable) {
      LOG.error(throwable);
    }
    return null;
  }
}
