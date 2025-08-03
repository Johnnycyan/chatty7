
package chatty.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.swing.Timer;

/**
 * Save replies for a while for easier lookup for display and finding thread
 * ids.
 * 
 * @author tduva
 */
public class ReplyManager {
    
    private static final long DELETE_TIME = TimeUnit.HOURS.toMillis(12);
//    private static final long DELETE_TIME = TimeUnit.MINUTES.toMillis(2);
    
    private static final Map<String, List<Reply>> data = new HashMap<>();
    private static final Map<String, Long> lastAdded = new HashMap<>();
    private static final Set<ReplyListener> listeners = new HashSet<>();
    
    public interface ReplyListener {
        void replyAdded(String parentMsgId, Reply reply);
    }
    
    public static void addListener(ReplyListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    public static void removeListener(ReplyListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    private static void notifyReplyAdded(String parentMsgId, Reply reply) {
        synchronized (listeners) {
            for (ReplyListener listener : listeners) {
                listener.replyAdded(parentMsgId, reply);
            }
        }
    }
    
    static {
        Timer timer = new Timer((int)DELETE_TIME, e -> removeOld());
        timer.setRepeats(true);
        timer.start();
    }

    public synchronized static void addReply(String parentMsgId, String msgId, String userMsg, String parentUserMsg) {
        addReply(parentMsgId, msgId, userMsg, parentUserMsg, null, null, null, null);
    }
    
    private static final Map<String, String> msgIdMappings = new HashMap<>(); // Maps server msgId to thread parent
    
    public synchronized static void addReply(String parentMsgId, String msgId, String userMsg, String parentUserMsg, 
                                           chatty.util.irc.MsgTags tags, chatty.User user, 
                                           chatty.util.irc.MsgTags parentTags, chatty.User parentUser) {
        
        System.out.println("DEBUG: addReply called - parentMsgId: " + parentMsgId + ", msgId: " + msgId);
        
        // Special case: Check if this is a server confirmation of a local echo
        // If msgId is not null and we have a reply with null msgId and matching userMsg,
        // this might be the server confirmation
        if (msgId != null) {
            System.out.println("DEBUG: Checking for local echo match with userMsg: " + userMsg);
            for (Map.Entry<String, List<Reply>> entry : data.entrySet()) {
                List<Reply> replies = entry.getValue();
                for (int i = 0; i < replies.size(); i++) {
                    Reply reply = replies.get(i);
                    System.out.println("DEBUG: Comparing with reply[" + i + "] msgId: " + reply.msgId + ", userMsg: " + reply.userMsg);
                    if (reply.msgId == null && reply.userMsg.equals(userMsg)) {
                        // Found a matching local echo, update it with the server msgId
                        System.out.println("DEBUG: Found matching local echo, updating msgId from null to " + msgId);
                        Reply updatedReply = new Reply(msgId, userMsg, tags, user);
                        replies.set(i, updatedReply);
                        
                        // Create a mapping so we can find this message later
                        msgIdMappings.put(msgId, entry.getKey());
                        
                        // If this was supposed to create a new thread but we found it's actually
                        // an update to an existing reply, don't create a new thread
                        notifyReplyAdded(entry.getKey(), updatedReply);
                        return;
                    }
                }
            }
            System.out.println("DEBUG: No matching local echo found");
        }
        
        // Check if we're trying to reply to a message that we have a mapping for
        String mappedParent = msgIdMappings.get(parentMsgId);
        if (mappedParent != null) {
            System.out.println("DEBUG: Found mapping for parentMsgId " + parentMsgId + " -> " + mappedParent);
            parentMsgId = mappedParent;
        } else {
            // If no direct mapping and parentMsgId not found in any thread,
            // check if there's a recent reply with null msgId that this could be
            // This handles the case where someone replies to a server-confirmed message
            // that was originally stored as a local echo with null msgId
            boolean foundNullReply = false;
            String potentialThread = null;
            
            for (Map.Entry<String, List<Reply>> entry : data.entrySet()) {
                List<Reply> replies = entry.getValue();
                for (Reply reply : replies) {
                    if (reply.msgId == null) {
                        foundNullReply = true;
                        potentialThread = entry.getKey();
                        break; // Take the first one we find for now
                    }
                }
                if (foundNullReply) break;
            }
            
            if (foundNullReply && potentialThread != null) {
                System.out.println("DEBUG: Could not find parentMsgId " + parentMsgId + " in any thread, but found thread " + potentialThread + " with null msgId reply - treating as same thread");
                parentMsgId = potentialThread;
            }
        }
        
        // Find the root parent - if the parentMsgId is already part of another thread,
        // we should add to the root thread instead
        String rootParentMsgId = findRootParent(parentMsgId);
        if (rootParentMsgId != null) {
            System.out.println("DEBUG: Redirecting from parent " + parentMsgId + " to root parent " + rootParentMsgId);
            parentMsgId = rootParentMsgId;
        }
        
        if (!data.containsKey(parentMsgId)) {
            data.put(parentMsgId, new ArrayList<>());
            // Should only be null if reply is already added anyway (e.g. locally switching to parent to reply)
            if (parentUserMsg != null) {
                Reply parentReply = new Reply(parentMsgId, parentUserMsg, parentTags, parentUser);
                data.get(parentMsgId).add(parentReply);
                notifyReplyAdded(parentMsgId, parentReply);
            }
        }
        Reply reply = new Reply(msgId, userMsg, tags, user);
        data.get(parentMsgId).add(reply);
        lastAdded.put(parentMsgId, System.currentTimeMillis());
        
        // Only notify if we have a valid msgId (not null for local echoes)
        if (msgId != null) {
            notifyReplyAdded(parentMsgId, reply);
        }
    }
    
    /**
     * Find the root parent of a thread. If the given msgId is already part of a thread
     * as a reply, return the root parent. Otherwise return null.
     */
    private synchronized static String findRootParent(String msgId) {
        if (msgId == null) {
            return null;
        }
        
        System.out.println("DEBUG: findRootParent searching for msgId: " + msgId);
        System.out.println("DEBUG: Current thread data structure:");
        for (Map.Entry<String, List<Reply>> entry : data.entrySet()) {
            System.out.println("DEBUG:   Thread " + entry.getKey() + " has " + entry.getValue().size() + " replies:");
            for (int i = 0; i < entry.getValue().size(); i++) {
                Reply reply = entry.getValue().get(i);
                System.out.println("DEBUG:     [" + i + "] msgId: " + reply.msgId + ", userMsg: " + reply.userMsg);
            }
        }
        
        // Check if this msgId is already a reply in some thread
        for (Map.Entry<String, List<Reply>> entry : data.entrySet()) {
            String threadParent = entry.getKey();
            List<Reply> replies = entry.getValue();
            
            // Check all replies in this thread
            for (int i = 0; i < replies.size(); i++) {
                Reply reply = replies.get(i);
                if (msgId.equals(reply.msgId)) {
                    // If this is the first reply and its msgId equals the thread parent,
                    // then this msgId IS the thread parent, not a reply in the thread
                    if (i == 0 && msgId.equals(threadParent)) {
                        System.out.println("DEBUG: msgId " + msgId + " is the thread parent itself, skipping");
                        continue; // This is the thread parent itself, keep looking
                    }
                    
                    // Found msgId as a genuine reply in this thread
                    System.out.println("DEBUG: Found msgId " + msgId + " as reply in thread " + threadParent + ", redirecting to root parent");
                    return threadParent;
                }
                
                // Special case: if reply has null msgId, it might be a local echo that
                // this msgId is the server confirmation for
                if (reply.msgId == null) {
                    System.out.println("DEBUG: Found reply with null msgId, might be related to " + msgId);
                    // We could potentially update this reply with the server msgId
                    // but for now, let's treat this as a potential match
                    // if this is the only null msgId reply in recent threads
                }
            }
        }
        System.out.println("DEBUG: msgId " + msgId + " not found in any thread");
        return null;
    }
    
    public synchronized static String getParentMsgId(String msgId) {
        if (msgId == null) {
            return null;
        }
        for (Map.Entry<String, List<Reply>> entry : data.entrySet()) {
            for (Reply reply : entry.getValue()) {
                if (msgId.equals(reply.msgId)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
    
    public synchronized static List<Reply> getReplies(String parentMsgId) {
        List<Reply> list = data.get(parentMsgId);
        if (list != null) {
            return new ArrayList<>(list);
        }
        return null;
    }
    
    public synchronized static String getFirstUserMsg(String parentMsgId) {
        List<Reply> list = data.get(parentMsgId);
        if (list != null && !list.isEmpty()) {
            return list.get(0).userMsg;
        }
        return null;
    }
    
    private synchronized static void removeOld() {
        Iterator<Map.Entry<String, Long>> it = lastAdded.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (System.currentTimeMillis() - entry.getValue() > DELETE_TIME) {
                it.remove();
                data.remove(entry.getKey());
            }
        }
    }
    
    public static class Reply {
        
        public final String msgId;
        public final String userMsg;
        public final chatty.util.irc.MsgTags tags;
        public final String username;
        public final String displayName;
        public final String color;
        
        private Reply(String msgId, String userMsg) {
            this(msgId, userMsg, null, null);
        }
        
        private Reply(String msgId, String userMsg, chatty.util.irc.MsgTags tags, chatty.User user) {
            this.msgId = msgId;
            this.userMsg = userMsg;
            this.tags = tags;
            this.username = user != null ? user.getName() : null;
            this.displayName = user != null ? user.getDisplayNick() : null;
            this.color = (tags != null && tags.hasValue("color")) ? tags.get("color") : null;
        }
        
    }
    
}
