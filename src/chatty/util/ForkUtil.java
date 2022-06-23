
package chatty.util;

import chatty.Chatty;
import chatty.User;
import chatty.Helper;
import chatty.util.irc.MsgTags;
import chatty.util.irc.IrcBadges;

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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;

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

    public static boolean NOT_STRIKE = false;
    public static Color COLOR_BANNED_HIGHLIGHT_MESSAGE = new Color(50, 50, 50);

    public static boolean REPLACEMENT_UNDERLINE = false;
    public static boolean PRINT_FULL_FILTERED = true;

    public static String FILTER_FORK_PREFIX = "ByFork";

    public static String USER_AGENT = String.join(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ",
        "AppleWebKit/537.36 (KHTML, like Gecko) ",
        "Chrome/86.0.4240.198 Safari/537.36");

    public static Map<String, String> completionLangs = new HashMap<>();
    static {
        // "Ж" and "ж" are swaped because "search" text is in lower case.
        completionLangs.put("ru", "йцукенгшщзхъ\\фывапролдЖэячсмитьбю.ёЙЦУКЕНГШЩЗХЪ/ФЫВАПРОЛДжЭЯЧСМИТЬБЮ,Ё!\"№;%:?*()_+");
        completionLangs.put("en", "qwertyuiop[]\\asdfghjkl;\"zxcvbnm,./`QWERTYUIOP{}|ASDFGHJKL:\"ZXCVBNM<>?~!@#$%^&*()_+");
    }

    public static String replaceWrongLanguage(String text, String fromLang, String toLang) {
        String s = completionLangs.get(fromLang);
        String en = completionLangs.get(toLang);
        String newString = "";

        for (int i = 0; i < text.length(); i++) {
            int index = s.indexOf(text.charAt(i));
            newString += index < 0 ? "" : en.charAt(index);
        }
        return newString;
    }

    public static String removeSharpFromTitle(Channel channel) {
        return (channel.getType() == Channel.Type.CHANNEL)
            ? removeSharpFromTitle(channel.getName())
            : channel.getName();
    }

    public static String removeSharpFromTitle(String title) {
        return (REMOVE_SHARP && title.startsWith("#"))
            ? title.substring(1)
            : title;
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
            JSONObject json = (JSONObject) parser.parse(getUrl(taks));
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
            System.out.println(urlId);
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(getUrl(urlId));
            String id = (Long)json.get("_id") + "";
            System.out.println(id);
            return id;
        } catch (Exception e) {
        }
        return "";
    }

    public static List<String> getRecentMessages(String channel) {
        try {
            // String channelId = getIdChannel(channel);
            // Old API.
            // String urlId = "https://tmi.twitch.tv/api/rooms/" + channelId + "/recent_messages?client_id=" + Chatty.CLIENT_ID;
            // New custom API from RAnders00.
            String urlId = "https://recent-messages.robotty.de/"
                + "api/v2/recent-messages/"
                + channel.substring(1)
                + "?clearchatToNotice=true";
            // System.out.println(urlId);
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(getUrl(urlId));
            JSONArray msg = (JSONArray) json.get("messages");

            List<String> messages = new ArrayList<String>();

            Iterator<String> iterator = msg.iterator();
            while (iterator.hasNext()) {
                //System.out.println(iterator.next());
                messages.add(iterator.next());
            }

            //System.out.println(id);
            return messages;
        } catch (Exception e) {
        }
        return new ArrayList<String>();
    }

    public static void updateUserFromTags(User user, MsgTags tags) {
        // From TwitchConnection.
        if (tags.isEmpty()) {
            return;
        }
        /**
         * Any and all tag values may be null, so account for that when
         * checking against them.
         */
        // Whether anything in the user changed to warrant an update
        boolean changed = false;

        IrcBadges badges = IrcBadges.parse(tags.get("badges"));
        if (user.setTwitchBadges(badges)) {
            changed = true;
        }

        IrcBadges badgeInfo = IrcBadges.parse(tags.get("badge-info"));
        String subMonths = badgeInfo.get("subscriber");
        if (subMonths == null) {
            subMonths = badgeInfo.get("founder");
        }
        if (subMonths != null) {
            user.setSubMonths(Helper.parseShort(subMonths, (short)0));
        }

        // if (settings.getBoolean("ircv3CapitalizedNames")) {
            if (user.setDisplayNick(StringUtil.trim(tags.get("display-name")))) {
                changed = true;
            }
        // }

        // Update color
        String color = tags.get("color");
        if (color != null && !color.isEmpty()) {
            user.setColor(color);
        }

        // Update user status
        boolean turbo = tags.isTrue("turbo") || badges.hasId("turbo") || badges.hasId("premium");
        if (user.setTurbo(turbo)) {
            changed = true;
        }
        boolean subscriber = badges.hasId("subscriber") || badges.hasId("founder");
        if (user.setSubscriber(subscriber)) {
            changed = true;
        }
        if (user.setVip(badges.hasId("vip"))) {
            changed = true;
        }
        if (user.setModerator(badges.hasId("moderator"))) {
            changed = true;
        }
        if (user.setAdmin(badges.hasId("admin"))) {
            changed = true;
        }
        if (user.setStaff(badges.hasId("staff"))) {
            changed = true;
        }

        user.setId(tags.get("user-id"));
    }

    private static String getUrl(String targetUrl) {
        Charset charset = Charset.forName("UTF-8");
        URL url;
        HttpURLConnection connection = null;

        try {
            url = new URL(targetUrl);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);

            // Read response
            InputStream input = connection.getInputStream();

            StringBuilder response;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, charset))) {
                String line;
                response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            return response.toString();
        } catch (SocketTimeoutException ex) {
            System.out.println(ex.toString());
            return null;
        } catch (IOException ex) {
            System.out.println(ex.toString());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static String safeSubstring(final String str, final int start, final int end) {
        return str.substring(
                Math.max(0, start),
                Math.min(end, str.length()));
    }

}
