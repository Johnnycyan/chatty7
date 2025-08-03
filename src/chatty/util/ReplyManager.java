
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
        
        // Only process messages with real msgIds from the server
        if (msgId == null) {
            return;
        }
        
        // Check if we're trying to reply to a message that we have a mapping for
        String mappedParent = msgIdMappings.get(parentMsgId);
        if (mappedParent != null) {
            parentMsgId = mappedParent;
        }
        
        // Find the root parent - if the parentMsgId is already part of another thread,
        // we should add to the root thread instead
        String rootParentMsgId = findRootParent(parentMsgId);
        if (rootParentMsgId != null) {
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
        
        // Create a mapping so future replies to this msgId will be directed to the main thread
        msgIdMappings.put(msgId, parentMsgId);
        
        // Always notify for server messages
        notifyReplyAdded(parentMsgId, reply);
    }
    
    /**
     * Clean up any threads that should be merged into existing threads
    /**
     * Find the root parent of a thread. If the given msgId is already part of a thread
     * as a reply, return the root parent. Otherwise return null.
     */
    private synchronized static String findRootParent(String msgId) {
        if (msgId == null) {
            return null;
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
                        continue; // This is the thread parent itself, keep looking
                    }
                    
                    // Found msgId as a genuine reply in this thread
                    return threadParent;
                }
            }
        }
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
        // Check if this parentMsgId has been mapped to another thread
        String actualParentMsgId = msgIdMappings.get(parentMsgId);
        if (actualParentMsgId != null) {
            parentMsgId = actualParentMsgId;
        }
        
        List<Reply> list = data.get(parentMsgId);
        if (list != null) {
            return new ArrayList<>(list);
        }
        return null;
    }
    
    public synchronized static String getFirstUserMsg(String parentMsgId) {
        // Check if this parentMsgId has been mapped to another thread
        String actualParentMsgId = msgIdMappings.get(parentMsgId);
        if (actualParentMsgId != null) {
            parentMsgId = actualParentMsgId;
        }
        
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
