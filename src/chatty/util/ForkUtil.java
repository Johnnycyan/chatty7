
package chatty.util;

import chatty.Chatty;
import chatty.User;
import chatty.util.irc.MsgTags;

import java.util.ArrayList;

import java.util.*;
import java.lang.*;
import java.io.*;
import org.w3c.dom.*;
import java.net.URL;
import java.net.MalformedURLException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.net.URLEncoder;
import java.util.regex.*;
import java.awt.Color;

import chatty.gui.components.Channel;

/**
 * 01.10.17
 * @author 23rd
 */
public class ForkUtil {

    public static boolean SHOW_TITLE = true;
    public static boolean REMOVE_SHARP = false;
    public static String MENTION_NICK = "normal";
    public static boolean EMOTE_CODE = true;

    public static Color COLOR_HIGHLIGHT_MESSAGE = new Color(200, 200, 200);
    public static boolean USE_HIGHLIGHT2 = false;

    public static boolean NOT_STRIKE = false;
    public static Color COLOR_BANNED_HIGHLIGHT_MESSAGE = new Color(50, 50, 50);

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
            taks = taks.replaceAll("\\+", "");
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

    public static String getIdChannel(String channel) {
        try {
            String urlId = "https://api.twitch.tv/kraken/channels/" + channel.substring(1) + "?client_id=" + Chatty.CLIENT_ID;
            System.out.println("HEH!");
            System.out.println(urlId);
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(readUrl(urlId));
            String id = (Long)json.get("_id") + "";
            System.out.println(id);
            return id;
        } catch (org.json.simple.parser.ParseException | java.io.UnsupportedEncodingException ee) {
        } catch (Exception e) {
        }
        return "";
    }

    public static List<String> getRecentMessages(String channel) {
        try {
            String channelId = getIdChannel(channel);
            String urlId = "https://tmi.twitch.tv/api/rooms/" + channelId + "/recent_messages?client_id=" + Chatty.CLIENT_ID;
            System.out.println("HEH!");
            System.out.println(urlId);
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(readUrl(urlId));
            JSONArray msg = (JSONArray) json.get("messages");

            List<String> messages = new ArrayList<String>();

            Iterator<String> iterator = msg.iterator();
            while (iterator.hasNext()) {
                //System.out.println(iterator.next());
                messages.add(iterator.next());
            }

            //System.out.println(id);
            return messages;
        } catch (org.json.simple.parser.ParseException | java.io.UnsupportedEncodingException ee) {
        } catch (Exception e) {
        }
        return new ArrayList<String>();
    }

    public static void updateUserFromTags(User user, MsgTags tags) {
        //From TwitchConnection
        if (tags.isEmpty()) {
            return;
        }
        boolean changed = false;
        
        Map<String, String> badges = chatty.Helper.parseBadges(tags.get("badges"));
        if (user.setTwitchBadges(badges)) {
            changed = true;
        }        
        // Update color
        String color = tags.get("color");
        if (color != null && !color.isEmpty()) {
            user.setColor(color);
        }        
        // Update user status
        boolean turbo = tags.isTrue("turbo") || badges.containsKey("turbo") || badges.containsKey("premium");
        if (user.setTurbo(turbo)) {
            changed = true;
        }
        if (user.setSubscriber(tags.isTrue("subscriber"))) {
            changed = true;
        }
        
        // Temporarily check both for containing a value as Twitch is
        // changing it
        String userType = tags.get("user-type");
        if (user.setModerator("mod".equals(userType))) {
            changed = true;
        }
        if (user.setStaff("staff".equals(userType))) {
            changed = true;
        }
        if (user.setAdmin("admin".equals(userType))) {
            changed = true;
        }
        if (user.setGlobalMod("global_mod".equals(userType))) {
            changed = true;
        }
        
        user.setId(tags.get("user-id"));
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
