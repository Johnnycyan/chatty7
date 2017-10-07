
package chatty.util;

import java.util.*;
import java.lang.*;
import java.io.*;
import org.w3c.dom.*;
import java.net.URL;
import java.net.MalformedURLException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.net.URLEncoder;
import java.util.regex.*;

import chatty.gui.components.Channel;

/**
 * 01.10.17
 * @author 23rd
 */
public class ForkUtil {

    public static boolean SHOW_TITLE = true;
    public static boolean REMOVE_SHARP = false;

    public static String removeSharpFromTitle(Channel channel) {
        if (channel.getType() == Channel.Type.CHANNEL && REMOVE_SHARP) {
            return channel.getName().substring(1);
        }
        return channel.getName();
    }

    public static boolean isItYoutubeUrl(String url) {
        Pattern pattern = Pattern.compile("(?:youtube\\.com\\/(?:[^\\/]+\\/.+\\/|(?:v|e(?:mbed)?)\\/|.*[?&]v=)|youtu\\.be\\/)([^\"&?\\/ ]{11})");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return true;
        }
        return false;
    }

    public static String getTooltip(String url) {
        try {
            String taks = "https://api.betterttv.net/2/link_resolver/" + URLEncoder.encode(url, "ISO-8859-1");
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(readUrl(taks));
            String tooltip = (String)json.get("tooltip");
            tooltip = tooltip.replaceAll("\n", "<br />");
            tooltip = chatty.Helper.htmlspecialchars_decode(tooltip);
            return tooltip;
        } catch (org.json.simple.parser.ParseException | java.io.UnsupportedEncodingException ee) {
        } catch (Exception e) {
        }
        return "";
    }

    private static String readUrl(String urlString) throws Exception {
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
            StringBuffer buffer = new StringBuffer();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1)
                buffer.append(chars, 0, read); 

            return buffer.toString();
        } finally {
            if (reader != null)
                reader.close();
        }
    }
    
}
