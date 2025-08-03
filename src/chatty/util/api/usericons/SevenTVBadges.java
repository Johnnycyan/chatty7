package chatty.util.api.usericons;

import chatty.util.JSONUtil;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.json.simple.JSONObject;

/**
 * Manages 7TV badge assignments and registrations
 * 
 * @author tduva
 */
public class SevenTVBadges {
    
    private static final Logger LOGGER = Logger.getLogger(SevenTVBadges.class.getName());
    
    // Map of user ID to set of badge IDs they have
    private final Map<String, Set<String>> userBadges = new ConcurrentHashMap<>();
    
    // Map of badge ID to badge info
    private final Map<String, BadgeInfo> knownBadges = new ConcurrentHashMap<>();
    
    // Cache of created Usericon objects to avoid recreating them
    private final Map<String, Usericon> usericonCache = new ConcurrentHashMap<>();
    
    private static class BadgeInfo {
        final String name;
        final String tooltip;
        final String imageUrl1x;
        final String imageUrl2x;
        
        BadgeInfo(String id, String name, String tooltip, String imageUrl1x, String imageUrl2x, String imageUrl3x) {
            this.name = name;
            this.tooltip = tooltip;
            this.imageUrl1x = imageUrl1x;
            this.imageUrl2x = imageUrl2x;
            // imageUrl3x stored for future use but not currently needed
        }
    }
    
    /**
     * Get a 7TV badge for the given badge ID
     */
    public Usericon getBadge(String badgeId) {
        System.out.println("DEBUG: getBadge called for: " + badgeId);
        
        // Check if we already have a cached Usericon for this badge
        Usericon cachedIcon = usericonCache.get(badgeId);
        if (cachedIcon != null) {
            System.out.println("DEBUG: Returning cached badge for: " + badgeId);
            return cachedIcon;
        }
        
        BadgeInfo info = knownBadges.get(badgeId);
        if (info == null) {
            System.out.println("DEBUG: No badge info found for: " + badgeId);
            return null;
        }
        
        System.out.println("DEBUG: Creating badge for: " + badgeId + " name: " + info.name);
        Usericon icon = UsericonFactory.createSevenTV(
            badgeId, 
            info.name, 
            info.imageUrl1x, 
            info.imageUrl2x, 
            info.tooltip
        );
        
        // Cache the created Usericon
        usericonCache.put(badgeId, icon);
        System.out.println("DEBUG: Cached badge for: " + badgeId);
        
        return icon;
    }
    
    /**
     * Assign a badge to a user
     */
    public void assignBadgeToUser(String userId, String badgeId) {
        System.out.println("DEBUG: Assigning badge " + badgeId + " to user " + userId);
        userBadges.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(badgeId);
    }
    
    /**
     * Clear a badge from a user  
     */
    public void clearBadgeFromUser(String userId, String badgeId) {
        System.out.println("DEBUG: Clearing badge " + badgeId + " from user " + userId);
        Set<String> badges = userBadges.get(userId);
        if (badges != null) {
            badges.remove(badgeId);
            if (badges.isEmpty()) {
                userBadges.remove(userId);
            }
        }
    }
    
    /**
     * Get all badge IDs for a user
     */
    public Set<String> getUserBadges(String userId) {
        Set<String> badges = userBadges.get(userId);
        Set<String> result = badges != null ? new HashSet<>(badges) : Collections.emptySet();
        System.out.println("DEBUG: getUserBadges for " + userId + " returning: " + result);
        return result;
    }
    
    /**
     * Get all badge Usericons for a user
     */
    public List<Usericon> getUserBadgeIcons(String userId) {
        Set<String> badgeIds = getUserBadges(userId);
        List<Usericon> icons = new ArrayList<>();
        
        for (String badgeId : badgeIds) {
            Usericon icon = getBadge(badgeId);
            if (icon != null) {
                icons.add(icon);
            }
        }
        
        return icons;
    }
    
    /**
     * Check if a user has a specific badge
     */
    public boolean userHasBadge(String userId, String badgeId) {
        Set<String> badges = userBadges.get(userId);
        return badges != null && badges.contains(badgeId);
    }
    
    /**
     * Register a badge from JSON data
     */
    public void registerBadge(JSONObject badgeData) {
        System.out.println("DEBUG: registerBadge called with data: " + badgeData);
        try {
            String id = JSONUtil.getString(badgeData, "id");
            String name = JSONUtil.getString(badgeData, "name");
            String tooltip = JSONUtil.getString(badgeData, "tooltip");
            
            System.out.println("DEBUG: Registering badge - id: " + id + ", name: " + name);
            
            // For GraphQL response, construct URLs using 7TV CDN pattern
            String imageUrl1x = null;
            String imageUrl2x = null;
            String imageUrl3x = null;
            
            if (id != null) {
                // 7TV badge URLs follow pattern: https://cdn.7tv.app/badge/{id}/1x.webp
                imageUrl1x = "https://cdn.7tv.app/badge/" + id + "/1x.webp";
                imageUrl2x = "https://cdn.7tv.app/badge/" + id + "/2x.webp";
                imageUrl3x = "https://cdn.7tv.app/badge/" + id + "/3x.webp";
                System.out.println("DEBUG: Constructed badge URLs - 1x: " + imageUrl1x + ", 2x: " + imageUrl2x + ", 3x: " + imageUrl3x);
            }
            
            if (id != null && name != null && imageUrl1x != null) {
                BadgeInfo info = new BadgeInfo(id, name, tooltip, imageUrl1x, imageUrl2x, imageUrl3x);
                knownBadges.put(id, info);
                System.out.println("DEBUG: Successfully registered badge: " + name + " (" + id + ") with URLs: " + imageUrl1x + ", " + imageUrl2x);
                LOGGER.info("Registered 7TV badge: " + name + " (" + id + ")");
            } else {
                System.out.println("DEBUG: Failed to register badge - missing required fields. id: " + id + ", name: " + name + ", imageUrl1x: " + imageUrl1x);
            }
            
        } catch (Exception ex) {
            System.out.println("DEBUG: Failed to register badge: " + ex.getMessage());
            LOGGER.warning("Failed to register 7TV badge: " + ex.getMessage());
        }
    }
    
    /**
     * Clear all badge data
     */
    public void clear() {
        userBadges.clear();
        knownBadges.clear();
        usericonCache.clear();
    }
    
    /**
     * Get the number of registered badges
     */
    public int getBadgeCount() {
        return knownBadges.size();
    }
    
    /**
     * Get the number of users with badges
     */
    public int getUsersWithBadgesCount() {
        return userBadges.size();
    }
}
