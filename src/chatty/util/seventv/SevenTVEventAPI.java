package chatty.util.seventv;

import chatty.util.JSONUtil;
import chatty.util.jws.JWSClient;
import chatty.util.jws.MessageHandler;
import chatty.util.api.usericons.SevenTVBadges;
import chatty.TwitchClient;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * 7TV WebSocket Event API client for receiving real-time cosmetic updates
 * 
 * @author tduva
 */
public class SevenTVEventAPI implements MessageHandler {
    
    private static final Logger LOGGER = Logger.getLogger(SevenTVEventAPI.class.getName());
    
    private static final String EVENT_API_URL = "wss://events.7tv.io/v3?app=chatty&version=0.28.0";
    private static final String API_BASE_URL = "https://7tv.io/v3";
    
    // API endpoints - matching your working web chat GraphQL format
    private static final String API_URL_USER_BY_TWITCH = API_BASE_URL + "/users/twitch/%s";
    private static final String API_URL_GRAPHQL = API_BASE_URL + "/gql";
    private static final String API_URL_PRESENCES = API_BASE_URL + "/users/%s/presences";
    
    private final JWSClient client;
    private final JSONParser parser = new JSONParser();
    private final SevenTVBadges badgeManager;
    private final TwitchClient twitchClient;
    private final ScheduledExecutorService executor;
    
    // Track subscribed channels and users
    private final Set<String> subscribedChannels = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedUsers = ConcurrentHashMap.newKeySet();
    
    // User and presence management
    private String sevenTVUserId;
    private String sessionId;
    private long lastPresenceUpdate = 0;
    private static final long PRESENCE_COOLDOWN = 10000; // 10 seconds
    
    // Heartbeat management
    private volatile long heartbeatInterval = 25000; // Default 25 seconds
    private volatile boolean isConnected = false;
    
    public SevenTVEventAPI(SevenTVBadges badgeManager, TwitchClient twitchClient) {
        this.badgeManager = badgeManager;
        this.twitchClient = twitchClient;
        this.executor = Executors.newScheduledThreadPool(1);
        try {
            this.client = new JWSClient(new URI(EVENT_API_URL), this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create 7TV EventAPI client", e);
        }
    }
    
    /**
     * Start the WebSocket connection
     */
    public void connect() {
        LOGGER.info("Connecting to 7TV Event API: " + EVENT_API_URL);
        client.reconnect();
        
        // Fetch current user's 7TV data
        fetchCurrentUserData();
    }
    
    /**
     * Fetch the current Twitch user's 7TV data
     */
    private void fetchCurrentUserData() {
        System.out.println("DEBUG: fetchCurrentUserData called");
        executor.execute(() -> {
            try {
                System.out.println("DEBUG: fetchCurrentUserData executor running");
                String username = twitchClient.getUsername();
                System.out.println("DEBUG: Current username: " + username);
                if (username != null && !username.isEmpty()) {
                    // Use the TwitchApi from TwitchClient to get user ID
                    twitchClient.api.getUserIdAsap(result -> {
                        System.out.println("DEBUG: getUserIdAsap callback called");
                        String twitchUserId = result.getData().get(username);
                        System.out.println("DEBUG: Current user Twitch ID: " + twitchUserId);
                        if (twitchUserId != null) {
                            System.out.println("DEBUG: Fetching 7TV user data for current user: " + twitchUserId);
                            // Fetch user data directly using Twitch ID (like Chatterino7)
                            fetchUserDataByTwitchId(twitchUserId);
                        }
                    }, username);
                }
            } catch (Exception e) {
                System.out.println("DEBUG: Exception in fetchCurrentUserData: " + e.getMessage());
                LOGGER.warning("Failed to fetch 7TV user data: " + e.getMessage());
            }
        });
    }
    
    /**
     * Fetch 7TV user cosmetic data using GraphQL - matching your web chat format
     */
    private void fetchUserDataByTwitchId(String twitchUserId) {
        try {
            System.out.println("DEBUG: Fetching 7TV user cosmetic data for Twitch ID: " + twitchUserId);
            
            // GraphQL query to get user's badge_id and paint_id - exactly like your web chat
            String query = String.format(
                "query MyQuery { userByConnection(id: \"%s\", platform: TWITCH) { style { badge_id paint_id } roles } }",
                twitchUserId
            );
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("query", query);
            
            URL url = new URL(API_URL_GRAPHQL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-7TV-Platform", "chatty");
            conn.setRequestProperty("X-7TV-Version", "0.28.0");
            conn.setDoOutput(true);
            
            // Send GraphQL query
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    System.out.println("DEBUG: 7TV GraphQL response: " + response.toString());
                    
                    JSONObject responseData = (JSONObject) parser.parse(response.toString());
                    JSONObject data = (JSONObject) responseData.get("data");
                    
                    if (data != null) {
                        JSONObject userByConnection = (JSONObject) data.get("userByConnection");
                        if (userByConnection != null) {
                            // Process user's style cosmetics (badge_id, paint_id)
                            JSONObject style = (JSONObject) userByConnection.get("style");
                            if (style != null) {
                                System.out.println("DEBUG: Found style object: " + style.toString());
                                
                                String badgeId = JSONUtil.getString(style, "badge_id");
                                String paintId = JSONUtil.getString(style, "paint_id");
                                
                                // Collect cosmetic IDs for batch fetch
                                java.util.List<String> cosmeticIds = new java.util.ArrayList<>();
                                if (badgeId != null) {
                                    cosmeticIds.add(badgeId);
                                    System.out.println("DEBUG: Found badge_id in style: " + badgeId);
                                }
                                if (paintId != null) {
                                    cosmeticIds.add(paintId);
                                    System.out.println("DEBUG: Found paint_id in style: " + paintId);
                                }
                                
                                if (!cosmeticIds.isEmpty()) {
                                    // Fetch cosmetic details using GraphQL like your web chat
                                    fetchCosmeticDetails(cosmeticIds, twitchUserId, badgeId, paintId);
                                } else {
                                    System.out.println("DEBUG: No cosmetic IDs found in style");
                                }
                            } else {
                                System.out.println("DEBUG: No style object found for user");
                            }
                        } else {
                            System.out.println("DEBUG: No userByConnection found in response");
                        }
                    } else {
                        System.out.println("DEBUG: No data object found in GraphQL response");
                    }
                }
            } else {
                System.out.println("DEBUG: Failed to fetch 7TV user data: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Exception fetching 7TV user data: " + e.getMessage());
            LOGGER.warning("Failed to fetch 7TV user data for Twitch user " + twitchUserId + ": " + e.getMessage());
        }
    }
    
    /**
     * Fetch cosmetic details using GraphQL - exactly like your web chat's getCosmeticDetails
     */
    @SuppressWarnings("unchecked")
    private void fetchCosmeticDetails(java.util.List<String> cosmeticIds, String twitchUserId, String badgeId, String paintId) {
        try {
            System.out.println("DEBUG: Fetching cosmetic details for IDs: " + cosmeticIds);
            
            // Build the GraphQL query exactly like your web chat
            String query = "query GetCosmestics($list: [ObjectID!]) { " +
                          "cosmetics(list: $list) { " +
                          "paints { id kind name function color angle shape image_url repeat " +
                          "stops { at color __typename } " +
                          "shadows { x_offset y_offset radius color __typename } __typename } " +
                          "badges { id kind name tooltip tag __typename } __typename } }";
            
            // Build variables object
            JSONObject variables = new JSONObject();
            JSONArray list = new JSONArray();
            for (String id : cosmeticIds) {
                list.add(id);
            }
            variables.put("list", list);
            
            // Build request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("operationName", "GetCosmestics");
            requestBody.put("variables", variables);
            requestBody.put("query", query);
            
            URL url = new URL(API_URL_GRAPHQL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-7TV-Platform", "chatty");
            conn.setRequestProperty("X-7TV-Version", "0.28.0");
            conn.setDoOutput(true);
            
            // Send GraphQL query
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    System.out.println("DEBUG: Cosmetic details GraphQL response: " + response.toString());
                    
                    JSONObject responseData = (JSONObject) parser.parse(response.toString());
                    JSONObject data = (JSONObject) responseData.get("data");
                    
                    if (data != null) {
                        JSONObject cosmetics = (JSONObject) data.get("cosmetics");
                        if (cosmetics != null) {
                            // Process badges
                            JSONArray badges = (JSONArray) cosmetics.get("badges");
                            if (badges != null && badgeId != null) {
                                for (Object badgeObj : badges) {
                                    JSONObject badge = (JSONObject) badgeObj;
                                    String id = JSONUtil.getString(badge, "id");
                                    if (badgeId.equals(id)) {
                                        System.out.println("DEBUG: Found badge details: " + badge.toString());
                                        
                                        // Register the badge definition
                                        badgeManager.registerBadge(badge);
                                        
                                        // Assign it to the user
                                        badgeManager.assignBadgeToUser(twitchUserId, badgeId);
                                        
                                        System.out.println("DEBUG: Successfully registered and assigned badge " + badgeId + " to user " + twitchUserId);
                                        break;
                                    }
                                }
                            }
                            
                            // Process paints (for future use)
                            JSONArray paints = (JSONArray) cosmetics.get("paints");
                            if (paints != null && paintId != null) {
                                for (Object paintObj : paints) {
                                    JSONObject paint = (JSONObject) paintObj;
                                    String id = JSONUtil.getString(paint, "id");
                                    if (paintId.equals(id)) {
                                        System.out.println("DEBUG: Found paint details: " + paint.toString());
                                        // Paint processing can be added here later
                                        break;
                                    }
                                }
                            }
                        } else {
                            System.out.println("DEBUG: No cosmetics object found in response");
                        }
                    } else {
                        System.out.println("DEBUG: No data object found in GraphQL response");
                    }
                }
            } else {
                System.out.println("DEBUG: Failed to fetch cosmetic details: " + conn.getResponseCode());
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Exception fetching cosmetic details: " + e.getMessage());
            LOGGER.warning("Failed to fetch 7TV cosmetic details: " + e.getMessage());
        }
    }
    
    /**
     * Fetch badges for a specific user (called when user appears in chat)
     */
    public void fetchUserBadges(String twitchUserId) {
        if (twitchUserId == null || twitchUserId.isEmpty()) {
            return;
        }
        
        System.out.println("DEBUG: fetchUserBadges called for Twitch user: " + twitchUserId);
        executor.execute(() -> {
            fetchUserDataByTwitchId(twitchUserId);
        });
    }
    
    /**
     * Send presence update to 7TV
     */
    public void sendPresence(String twitchChannelId, boolean self) {
        if (sevenTVUserId == null || twitchChannelId == null) {
            return;
        }
        
        // Rate limit presence updates
        long now = System.currentTimeMillis();
        if (now - lastPresenceUpdate < PRESENCE_COOLDOWN) {
            return;
        }
        
        lastPresenceUpdate = now;
        
        executor.execute(() -> {
            try {
                updateUserPresences(sevenTVUserId, twitchChannelId, self);
            } catch (Exception e) {
                LOGGER.warning("Failed to send 7TV presence: " + e.getMessage());
            }
        });
    }
    
    /**
     * Update user presences via 7TV API
     */
    @SuppressWarnings("unchecked")
    private void updateUserPresences(String userId, String channelId, boolean self) {
        try {
            URL url = new URL(String.format(API_URL_PRESENCES, userId));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-7TV-Platform", "chatty");
            conn.setRequestProperty("X-7TV-Version", "0.28.0");
            conn.setDoOutput(true);
            
            JSONObject presenceData = new JSONObject();
            presenceData.put("kind", 1);
            presenceData.put("passive", self);
            if (self && sessionId != null) {
                presenceData.put("session_id", sessionId);
            }
            
            JSONObject data = new JSONObject();
            data.put("platform", "TWITCH");
            data.put("id", channelId);
            presenceData.put("data", data);
            
            try (OutputStream out = conn.getOutputStream()) {
                out.write(presenceData.toJSONString().getBytes(StandardCharsets.UTF_8));
            }
            
            if (conn.getResponseCode() == 200) {
                LOGGER.fine("Sent 7TV presence for channel: " + channelId);
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to update 7TV presence: " + e.getMessage());
        }
    }
    
    /**
     * Stop the WebSocket connection
     */
    public void disconnect() {
        LOGGER.info("Disconnecting from 7TV Event API");
        client.disconnect();
        isConnected = false;
    }
    
    /**
     * Subscribe to cosmetic updates for a Twitch channel
     * 
     * @param twitchChannelId The Twitch channel ID to subscribe to
     */
    public void subscribeToChannel(String twitchChannelId) {
        if (subscribedChannels.add(twitchChannelId)) {
            // Subscribe to various event types based on FFZ 7TV addon implementation
            sendSubscription("emote_set.*", "ctx", "channel", "id", twitchChannelId, "platform", "TWITCH");
            sendSubscription("cosmetic.*", "ctx", "channel", "id", twitchChannelId, "platform", "TWITCH");
            sendSubscription("entitlement.*", "ctx", "channel", "id", twitchChannelId, "platform", "TWITCH");
            LOGGER.info("Subscribed to 7TV events for channel: " + twitchChannelId);
        }
    }
    
    /**
     * Unsubscribe from cosmetic updates for a Twitch channel
     * 
     * @param twitchChannelId The Twitch channel ID to unsubscribe from
     */
    public void unsubscribeFromChannel(String twitchChannelId) {
        if (subscribedChannels.remove(twitchChannelId)) {
            sendUnsubscription("emote_set.*", "ctx", "channel", "id", twitchChannelId, "platform", "TWITCH");
            sendUnsubscription("cosmetic.*", "ctx", "channel", "id", twitchChannelId, "platform", "TWITCH");
            sendUnsubscription("entitlement.*", "ctx", "channel", "id", twitchChannelId, "platform", "TWITCH");
            LOGGER.info("Unsubscribed from 7TV events for channel: " + twitchChannelId);
        }
    }
    
    /**
     * Subscribe to updates for a specific 7TV user
     * 
     * @param userId The 7TV user ID to subscribe to
     */
    public void subscribeToUser(String userId) {
        if (subscribedUsers.add(userId)) {
            sendSubscription("user.*", "object_id", userId);
            LOGGER.info("Subscribed to 7TV user updates: " + userId);
        }
    }
    
    /**
     * Unsubscribe from updates for a specific 7TV user
     * 
     * @param userId The 7TV user ID to unsubscribe from
     */
    public void unsubscribeFromUser(String userId) {
        if (subscribedUsers.remove(userId)) {
            sendUnsubscription("user.*", "object_id", userId);
            LOGGER.info("Unsubscribed from 7TV user updates: " + userId);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void sendSubscription(String type, String... conditionPairs) {
        if (!isConnected || conditionPairs.length % 2 != 0) {
            return;
        }
        
        JSONObject subscription = new JSONObject();
        subscription.put("op", 35); // SUBSCRIBE opcode
        
        JSONObject data = new JSONObject();
        data.put("type", type);
        
        JSONObject condition = new JSONObject();
        for (int i = 0; i < conditionPairs.length; i += 2) {
            condition.put(conditionPairs[i], conditionPairs[i + 1]);
        }
        data.put("condition", condition);
        
        subscription.put("d", data);
        
        client.sendMessage(subscription.toJSONString());
    }
    
    @SuppressWarnings("unchecked")
    private void sendUnsubscription(String type, String... conditionPairs) {
        if (!isConnected || conditionPairs.length % 2 != 0) {
            return;
        }
        
        JSONObject unsubscription = new JSONObject();
        unsubscription.put("op", 36); // UNSUBSCRIBE opcode
        
        JSONObject data = new JSONObject();
        data.put("type", type);
        
        JSONObject condition = new JSONObject();
        for (int i = 0; i < conditionPairs.length; i += 2) {
            condition.put(conditionPairs[i], conditionPairs[i + 1]);
        }
        data.put("condition", condition);
        
        unsubscription.put("d", data);
        
        client.sendMessage(unsubscription.toJSONString());
    }
    
    @SuppressWarnings("unchecked")
    private void sendHeartbeat() {
        if (!isConnected) {
            return;
        }
        
        JSONObject heartbeat = new JSONObject();
        heartbeat.put("op", 2); // HEARTBEAT opcode
        client.sendMessage(heartbeat.toJSONString());
    }
    
    @Override
    public void handleConnect(JWSClient c) {
        System.out.println("DEBUG: Connected to 7TV Event API");
        LOGGER.info("Connected to 7TV Event API");
        isConnected = true;
    }
    
    @Override
    public void handleDisconnect(int code) {
        System.out.println("DEBUG: Disconnected from 7TV Event API: " + code);
        LOGGER.info("Disconnected from 7TV Event API: " + code);
        isConnected = false;
    }
    
    @Override
    public void handleReceived(String message) {
        System.out.println("DEBUG: Received 7TV message: " + message);
        try {
            JSONObject json = (JSONObject) parser.parse(message);
            handleMessage(json);
        } catch (ParseException e) {
            LOGGER.warning("Failed to parse 7TV EventAPI message: " + message);
        }
    }
    
    @Override
    public void handleSent(String message) {
        System.out.println("DEBUG: Sent 7TV message: " + message);
        // Optional: Log sent messages for debugging
        LOGGER.fine("Sent 7TV EventAPI message: " + message);
    }
    
    private void handleMessage(JSONObject message) {
        Object opObj = message.get("op");
        if (!(opObj instanceof Number)) {
            return;
        }
        
        int op = ((Number) opObj).intValue();
        Object dataObj = message.get("d");
        
        switch (op) {
            case 1: // HELLO
                handleHello(dataObj);
                break;
            case 0: // DISPATCH
                handleDispatch(dataObj);
                break;
            case 2: // HEARTBEAT
                // Server requesting heartbeat
                sendHeartbeat();
                break;
            case 4: // RECONNECT
                LOGGER.info("7TV Event API requesting reconnect");
                client.forceReconnect();
                break;
            case 5: // ACK
                // Subscription acknowledged
                break;
            case 6: // ERROR
                LOGGER.warning("7TV Event API error: " + message.toJSONString());
                break;
            default:
                LOGGER.info("Unknown 7TV Event API opcode: " + op);
                break;
        }
    }
    
    private void handleHello(Object data) {
        if (data instanceof JSONObject) {
            JSONObject helloData = (JSONObject) data;
            Object intervalObj = helloData.get("heartbeat_interval");
            if (intervalObj instanceof Number) {
                heartbeatInterval = ((Number) intervalObj).longValue();
                LOGGER.info("7TV Event API heartbeat interval: " + heartbeatInterval + "ms");
            }
            
            // Capture session ID for presence updates
            String sessionIdStr = JSONUtil.getString(helloData, "session_id");
            if (sessionIdStr != null) {
                sessionId = sessionIdStr;
                LOGGER.info("7TV Event API session ID: " + sessionId);
            }
        }
    }
    
    private void handleDispatch(Object data) {
        if (!(data instanceof JSONObject)) {
            return;
        }
        
        JSONObject dispatch = (JSONObject) data;
        String type = JSONUtil.getString(dispatch, "type");
        if (type == null) {
            return;
        }
        
        System.out.println("DEBUG: 7TV dispatch received - type: " + type + ", data: " + dispatch.toJSONString());
        
        switch (type) {
            case "cosmetic.create":
                handleCosmeticCreate(dispatch);
                break;
            case "entitlement.create":
                handleEntitlementCreate(dispatch);
                break;
            case "entitlement.delete":
                handleEntitlementDelete(dispatch);
                break;
            default:
                LOGGER.fine("Unhandled 7TV dispatch type: " + type);
                break;
        }
    }
    
    private void handleCosmeticCreate(JSONObject dispatch) {
        JSONObject body = (JSONObject) dispatch.get("body");
        if (body == null) {
            return;
        }
        
        JSONObject object = (JSONObject) body.get("object");
        if (object == null) {
            return;
        }
        
        String kind = JSONUtil.getString(object, "kind");
        if ("BADGE".equals(kind)) {
            JSONObject data = (JSONObject) object.get("data");
            if (data != null) {
                badgeManager.registerBadge(data);
                LOGGER.info("Registered new 7TV badge via WebSocket");
            }
        }
    }
    
    private void handleEntitlementCreate(JSONObject dispatch) {
        System.out.println("DEBUG: handleEntitlementCreate called with: " + dispatch.toJSONString());
        JSONObject body = (JSONObject) dispatch.get("body");
        if (body == null) {
            return;
        }
        
        JSONObject object = (JSONObject) body.get("object");
        if (object == null) {
            return;
        }
        
        String kind = JSONUtil.getString(object, "kind");
        System.out.println("DEBUG: Entitlement kind: " + kind);
        if ("BADGE".equals(kind)) {
            String refId = JSONUtil.getString(object, "ref_id");
            JSONObject user = (JSONObject) object.get("user");
            
            System.out.println("DEBUG: Badge entitlement - refId: " + refId + ", user: " + (user != null ? "present" : "null"));
            
            if (refId != null && user != null) {
                // Find Twitch connection
                Object connectionsObj = user.get("connections");
                if (connectionsObj instanceof JSONArray) {
                    JSONArray connections = (JSONArray) connectionsObj;
                    for (Object conn : connections) {
                        if (conn instanceof JSONObject) {
                            JSONObject connection = (JSONObject) conn;
                            String platform = JSONUtil.getString(connection, "platform");
                            if ("TWITCH".equals(platform)) {
                                String userId = JSONUtil.getString(connection, "id");
                                if (userId != null) {
                                    System.out.println("DEBUG: Assigning badge " + refId + " to Twitch user " + userId);
                                    badgeManager.assignBadgeToUser(userId, refId);
                                    LOGGER.info("Assigned 7TV badge " + refId + " to user " + userId + " via WebSocket");
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void handleEntitlementDelete(JSONObject dispatch) {
        JSONObject body = (JSONObject) dispatch.get("body");
        if (body == null) {
            return;
        }
        
        JSONObject object = (JSONObject) body.get("object");
        if (object == null) {
            return;
        }
        
        String kind = JSONUtil.getString(object, "kind");
        if ("BADGE".equals(kind)) {
            String refId = JSONUtil.getString(object, "ref_id");
            JSONObject user = (JSONObject) object.get("user");
            
            if (refId != null && user != null) {
                // Find Twitch connection
                Object connectionsObj = user.get("connections");
                if (connectionsObj instanceof JSONArray) {
                    JSONArray connections = (JSONArray) connectionsObj;
                    for (Object conn : connections) {
                        if (conn instanceof JSONObject) {
                            JSONObject connection = (JSONObject) conn;
                            String platform = JSONUtil.getString(connection, "platform");
                            if ("TWITCH".equals(platform)) {
                                String userId = JSONUtil.getString(connection, "id");
                                if (userId != null) {
                                    badgeManager.clearBadgeFromUser(userId, refId);
                                    LOGGER.info("Removed 7TV badge " + refId + " from user " + userId + " via WebSocket");
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Get the current connection status
     * 
     * @return true if connected to the 7TV Event API
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Get the set of subscribed channel IDs
     * 
     * @return A copy of the subscribed channels set
     */
    public Set<String> getSubscribedChannels() {
        return new HashSet<>(subscribedChannels);
    }
    
    /**
     * Get the set of subscribed user IDs
     * 
     * @return A copy of the subscribed users set
     */
    public Set<String> getSubscribedUsers() {
        return new HashSet<>(subscribedUsers);
    }
}
