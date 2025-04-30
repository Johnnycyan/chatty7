
package chatty.util.api;

import java.util.Set;


/**
 * Notify about changes in the stream status and about general stream info
 * updates.
 * 
 * @author tduva
 */
public interface StreamInfoListener {
    
    public void streamInfoUpdated(StreamInfo info);
    public void streamInfoStatusChanged(StreamInfo info, String newStatus);

    public Set<String> manualChannels();
    
}
