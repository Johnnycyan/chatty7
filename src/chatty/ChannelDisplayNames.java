package chatty;

import chatty.util.Debugging;
import chatty.util.StringUtil;
import chatty.util.settings.SettingChangeListener;
import chatty.util.settings.Settings;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages custom display names for channel tabs.
 * 
 * @author tduva
 */
public class ChannelDisplayNames {
    
    private final static String SETTING_NAME = "channelDisplayNames";
    
    private final Set<ChannelDisplayNamesListener> listeners = new HashSet<>();
    
    private final Settings settings;
    
    /**
     * The current original names. Used to detect removed names when changed
     * through the settings dialog.
     */
    private final Set<String> origNames = new HashSet<>();
    
    public ChannelDisplayNames(Settings settings) {
        this.settings = settings;
        updateOrigNames();
        settings.addSettingChangeListener(new SettingChangeListener() {

            @Override
            public void settingChanged(String setting, int type, Object value) {
                /**
                 * This is only called when not changed through command, since
                 * mapPut() and mapRemove() don't trigger this.
                 */
                if (setting.equals(SETTING_NAME)) {
                    informListenersAllChanged();
                }
            }
        });
    }
    
    public void setChannelDisplayName(String channel, String displayName) {
        if (channel == null) {
            return;
        }
        channel = StringUtil.toLowerCase(channel);
        if (displayName == null || displayName.trim().isEmpty()) {
            settings.mapRemove(SETTING_NAME, channel);
        } else {
            settings.mapPut(SETTING_NAME, channel, displayName.trim());
        }
        informListeners(channel, displayName);
        updateOrigNames();
    }
    
    public String getChannelDisplayName(String channel) {
        if (channel == null) {
            return null;
        }
        channel = StringUtil.toLowerCase(channel);
        return (String)settings.mapGet(SETTING_NAME, channel);
    }
    
    /**
     * Get the effective display name for a channel. Returns the custom display
     * name if set, otherwise returns the original channel name.
     * 
     * @param channel The channel name
     * @return The display name to use for this channel
     */
    public String getEffectiveDisplayName(String channel) {
        String customName = getChannelDisplayName(channel);
        return customName != null ? customName : channel;
    }
    
    private void informListeners(String channel, String displayName) {
        Debugging.println("channelDisplayNames", "%s => %s", channel, displayName);
        for (ChannelDisplayNamesListener listener : getListeners()) {
            listener.setChannelDisplayName(channel, displayName);
        }
    }
    
    private void informListenersAllChanged() {
        @SuppressWarnings("unchecked")
        Map<String, String> channelDisplayNames = settings.getMap(SETTING_NAME);
        fillRemovedNames(channelDisplayNames);
        for (String channel : channelDisplayNames.keySet()) {
            informListeners(channel, channelDisplayNames.get(channel));
        }
        updateOrigNames();
    }
    
    public synchronized void addListener(ChannelDisplayNamesListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }
    
    private synchronized Set<ChannelDisplayNamesListener> getListeners() {
        return new HashSet<>(listeners);
    }
    
    private synchronized void updateOrigNames() {
        @SuppressWarnings("unchecked")
        Map<String, String> channelDisplayNames = settings.getMap(SETTING_NAME);
        origNames.clear();
        origNames.addAll(channelDisplayNames.keySet());
        Debugging.println("channelDisplayNames", "OrigNames: %s", origNames);
    }
    
    /**
     * Add previously available names as removed, which when removed through the
     * settings dialog wouldn't otherwise cause a notification.
     * 
     * @param names 
     */
    private synchronized void fillRemovedNames(Map<String, String> names) {
        for (String name : origNames) {
            if (!names.containsKey(name)) {
                names.put(name, null);
            }
        }
    }
    
    /**
     * Listener that can be implemented by classes that want to be informed
     * about changes in channel display names.
     */
    public static interface ChannelDisplayNamesListener {
        
        /**
         * When a channel display name got added, changed or removed.
         * 
         * @param channel All lowercase channel name
         * @param displayName The custom display name, null if custom name removed
         */
        public void setChannelDisplayName(String channel, String displayName);
    }
}
