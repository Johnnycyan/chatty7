
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
            System.out.println("ReplyManager: Added listener, now have " + listeners.size() + " listeners");
        }
    }
    
    public static void removeListener(ReplyListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            System.out.println("ReplyManager: Removed listener, now have " + listeners.size() + " listeners");
        }
    }
    
    private static void notifyReplyAdded(String parentMsgId, Reply reply) {
        System.out.println("ReplyManager: Notifying " + listeners.size() + " listeners of reply " + reply.msgId + " to parent " + parentMsgId);
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
    
    public synchronized static void addReply(String parentMsgId, String msgId, String userMsg, String parentUserMsg, 
                                           chatty.util.irc.MsgTags tags, chatty.User user, 
                                           chatty.util.irc.MsgTags parentTags, chatty.User parentUser) {
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
