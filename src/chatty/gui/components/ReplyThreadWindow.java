package chatty.gui.components;

import chatty.gui.DockedDialogHelper;
import chatty.gui.DockedDialogManager;
import chatty.gui.MainGui;
import chatty.gui.StyleServer;
import chatty.gui.components.menus.ContextMenu;
import chatty.gui.components.textpane.ChannelTextPane;
import chatty.gui.components.textpane.UserMessage;
import chatty.Room;
import chatty.User;
import chatty.util.ReplyManager;
import chatty.util.ReplyManager.Reply;
import chatty.util.dnd.DockContent;
import chatty.util.irc.MsgTags;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Window showing a reply thread conversation with the ability to send replies.
 * 
 * @author tduva
 */
public class ReplyThreadWindow extends JDialog implements ReplyManager.ReplyListener {
    
    private final MainGui owner;
    private final TextPane messages;
    private final JTextField messageInput;
    private final JButton sendButton;
    private final DockedDialogHelper helper;
    private final DockContent content;
    private final JScrollPane scroll;
    private String parentMsgId;
    private String channelName;
    private java.util.Set<String> displayedReplies = new java.util.HashSet<>();
    
    public ReplyThreadWindow(MainGui owner, StyleServer styleServer, DockedDialogManager dockedDialogs) {
        super(owner);
        this.owner = owner;
        
        setTitle("Reply Thread");
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        
        // Create text pane for displaying thread messages
        messages = new TextPane(owner, styleServer, ChannelTextPane.Type.HIGHLIGHTS, null);
        
        scroll = new JScrollPane(messages);
        messages.setScrollPane(scroll);
        scroll.setPreferredSize(new Dimension(500, 300));
        
        // Create input panel
        JPanel inputPanel = new JPanel(new GridBagLayout());
        messageInput = new JTextField();
        sendButton = new JButton("Send Reply");
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        inputPanel.add(messageInput, gbc);
        
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        inputPanel.add(sendButton, gbc);
        
        add(scroll, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
        
        // Add action listeners
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendReply();
            }
        });
        
        messageInput.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendReply();
                }
            }
            
            @Override
            public void keyTyped(KeyEvent e) {}
            
            @Override
            public void keyReleased(KeyEvent e) {}
        });
        
        // Set up docking support
        content = dockedDialogs.createStyledContent(scroll, "Reply Thread", "-replythread-");
        helper = dockedDialogs.createHelper(new DockedDialogHelper.DockedDialog() {
            @Override
            public void setVisible(boolean visible) {
                ReplyThreadWindow.super.setVisible(visible);
            }

            @Override
            public boolean isVisible() {
                return ReplyThreadWindow.super.isVisible();
            }

            @Override
            public void addComponent(Component comp) {
                add(comp, BorderLayout.CENTER);
            }

            @Override
            public void removeComponent(Component comp) {
                remove(comp);
            }

            @Override
            public Window getWindow() {
                return ReplyThreadWindow.this;
            }

            @Override
            public DockContent getContent() {
                return content;
            }
        });
        
        pack();
        
        // Add window listener to clean up when window is closed
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cleanup();
            }
            
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                cleanup();
            }
        });
    }
    
    private void cleanup() {
        if (parentMsgId != null) {
            ReplyManager.removeListener(this);
            parentMsgId = null;
        }
    }
    
    /**
     * Show the reply thread for the given parent message ID.
     * 
     * @param parentMsgId The parent message ID of the thread
     * @param channelName The channel name where the thread is from
     */
    public void showThread(String parentMsgId, String channelName) {
        // Clean up previous thread listener if any
        cleanup();
        
        this.parentMsgId = parentMsgId;
        this.channelName = channelName;
        
        // Register listener for new replies to this thread
        ReplyManager.addListener(this);
        
        // Clear previous content
        messages.clear();
        displayedReplies.clear();
        
        // Load and display thread messages
        loadThreadMessages();
        
        // Update title
        setTitle("Reply Thread - " + channelName);
        helper.getContent().setLongTitle("Reply Thread - " + channelName);
        
        // Show window
        setVisible(true);
        
        // Focus input field
        SwingUtilities.invokeLater(() -> messageInput.requestFocus());
    }
    
    private void loadThreadMessages() {
        List<Reply> replies = ReplyManager.getReplies(parentMsgId);
        if (replies != null && !replies.isEmpty()) {
            for (Reply reply : replies) {
                String replyKey = reply.msgId != null ? reply.msgId : reply.userMsg;
                if (!displayedReplies.contains(replyKey)) {
                    addReplyToDisplay(reply);
                    displayedReplies.add(replyKey);
                }
            }
        } else {
            messages.printCompact("No replies found in this thread.");
        }
        
        // Scroll to bottom
        SwingUtilities.invokeLater(() -> {
            scroll.getVerticalScrollBar().setValue(
                scroll.getVerticalScrollBar().getMaximum());
        });
    }
    
    private void sendReply() {
        String text = messageInput.getText().trim();
        if (text.isEmpty() || parentMsgId == null || channelName == null) {
            return;
        }
        
        // Send the reply using the MainGui method
        owner.sendReplyToThread(channelName, parentMsgId, text);
        
        // Clear input field
        messageInput.setText("");
        
        // Refresh thread to show new reply after a small delay
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(500); // Give some time for the message to be processed
            } catch (InterruptedException e) {
                // Ignore
            }
            loadThreadMessages();
        });
    }
    
    @Override
    public void setVisible(boolean visible) {
        helper.setVisible(visible, true);
    }

    @Override
    public boolean isVisible() {
        return helper.isVisible();
    }
    
    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        if (helper != null) {
            helper.getContent().setLongTitle(title);
        }
    }
    
    /**
     * Custom text pane for the reply thread window.
     */
    static class TextPane extends ChannelTextPane {
        
        public TextPane(MainGui main, StyleServer styleServer, ChannelTextPane.Type type, Supplier<ContextMenu> contextMenuCreator) {
            super(main, styleServer, type);
            if (contextMenuCreator != null) {
                linkController.setContextMenuCreator(contextMenuCreator);
            }
        }
        
        public void clear() {
            setText("");
        }
        
        public void printCompact(String text) {
            try {
                getDocument().insertString(getDocument().getLength(), 
                    (getDocument().getLength() > 0 ? "\n" : "") + text, null);
            } catch (Exception e) {
                // Handle exception
                e.printStackTrace();
            }
        }
        
    }
    
    @Override
    public void replyAdded(String threadParentMsgId, ReplyManager.Reply reply) {
        // Only refresh if this is for our current thread and we haven't displayed this reply yet
        String replyKey = reply.msgId != null ? reply.msgId : reply.userMsg;
        if (parentMsgId != null && parentMsgId.equals(threadParentMsgId) && !displayedReplies.contains(replyKey)) {
            SwingUtilities.invokeLater(() -> {
                // Add only the new reply, not reload everything
                addReplyToDisplay(reply);
                displayedReplies.add(replyKey);
            });
        }
    }
    
    private void addReplyToDisplay(ReplyManager.Reply reply) {
        String replyText = reply.userMsg;
        String messageText = replyText;
        String username = reply.username;
        
        // Extract just the message text (remove "<username> " prefix if present)
        if (replyText.startsWith("<") && replyText.contains("> ")) {
            int usernameEnd = replyText.indexOf("> ");
            messageText = replyText.substring(usernameEnd + 2);
            // If we don't have stored username, extract it from the message
            if (username == null) {
                username = replyText.substring(1, usernameEnd);
            }
        }
        
        if (username == null) {
            // Fallback to simple text for malformed messages
            messages.printCompact("[Thread] " + replyText);
            return;
        }
        
        // Ensure channelName has the # prefix for getUser
        String userChannelName = channelName.startsWith("#") ? channelName : "#" + channelName;
        
        // Get the proper User object from MainGui - this has all the correct colors and context
        User user = owner.getUser(userChannelName, username);
        
        System.out.println("ReplyThreadWindow: Getting user " + username + " from channel " + userChannelName + " -> " + (user != null ? "found" : "null"));
        
        // Create tags with the stored information, but prioritize the original stored tags
        MsgTags tags = reply.tags;
        if (tags == null) {
            // Fallback: create basic tags if we don't have the originals
            java.util.Map<String, String> tagMap = new java.util.HashMap<>();
            if (reply.msgId != null) {
                tagMap.put("id", reply.msgId);
            }
            
            if (reply.displayName != null) {
                tagMap.put("display-name", reply.displayName);
            } else {
                tagMap.put("display-name", username);
            }
            
            if (reply.color != null) {
                tagMap.put("color", reply.color);
            }
            
            tags = new MsgTags(tagMap, null);
        }
        
        // Create and print a properly formatted UserMessage with the real User object
        UserMessage userMessage = new UserMessage(user, messageText, null, reply.msgId, 0, null, null, null, tags);
        messages.printMessage(userMessage);
        
        // Scroll to bottom to show new message
        SwingUtilities.invokeLater(() -> {
            scroll.getVerticalScrollBar().setValue(
                scroll.getVerticalScrollBar().getMaximum());
        });
    }
}
