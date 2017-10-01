
package chatty.util;

import java.util.*;
import java.lang.*;
import java.io.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.net.URL;
import org.xml.sax.SAXException;
import java.net.MalformedURLException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 01.10.17
 * @author 23rd
 */
public class YoutubeUtil {

    public static boolean SHOW_TITLE = true;

    public static boolean isItYoutubeUrl(String url) {
        Pattern pattern = Pattern.compile("(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/ ]{11})");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return true;
        }
        return false;
    }
    
    public static String getTitleYoutube(String url) {
        try {
            url = "https://www.youtube.com/oembed?url=" + url + "&format=xml";
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new URL(url).openStream());
            Element rootElement = doc.getDocumentElement();

            String requestQueueName = getString("title", rootElement);
            return requestQueueName;
        } catch (ParserConfigurationException e) {

        } catch (MalformedURLException e) {

        } catch (IOException e) {

        } catch (SAXException e) {

        }
        return "";
    }

    protected static String getString(String tagName, Element element) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) {
            NodeList subList = list.item(0).getChildNodes();

            if (subList != null && subList.getLength() > 0) {
                return subList.item(0).getNodeValue();
            }
        }

        return null;
    }
    
}
