
package chatty.gui.components.settings;

import chatty.Chatty;
import java.awt.GridBagConstraints;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.HashMap;
import java.util.Map;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Fork settings..
 * 
 * @author 23rd
 */
public class ForkSettings extends SettingsPanel {
    
    public ForkSettings(SettingsDialog d) {
        
        JPanel fork = addTitledPanel("Fork Settings", 0);
        
        //---------------
        // Other settings
        //---------------
        JCheckBox versionCheck = d.addSimpleBooleanSetting(
                "urlTitleDescription",
                "Show link title and description (through BetterTTV API).",
                "You can see title and description of website when you click on any url.");
        fork.add(versionCheck,
                d.makeGbc(0, 3, 3, 1, GridBagConstraints.WEST));

        JCheckBox removeSharp = d.addSimpleBooleanSetting(
                "removeSharp",
                "Remove # from title and tabs.",
                "Why you need this symbol?");
        fork.add(removeSharp,
                d.makeGbc(0, 1, 3, 1, GridBagConstraints.WEST));

        JCheckBox removeW = d.addSimpleBooleanSetting(
                "removeWFromTitle",
                "Remove [W] from title.",
                "[W] is just showing your whisper status. Is it really needed?");
        fork.add(removeW,
                d.makeGbc(0, 2, 3, 1, GridBagConstraints.WEST));

        JCheckBox printCheck = d.addSimpleBooleanSetting(
                "printAboutCheckingVersion",
                "Print about checking new version.",
                "Print 'Checking for new version...'.");
        fork.add(printCheck,
                d.makeGbc(0, 10, 3, 1, GridBagConstraints.WEST));

        fork.add(new JLabel("Path of videoplayer:"),
                d.makeGbc(0, 7, 1, 1, GridBagConstraints.WEST));
        
        fork.add(d.addSimpleStringSetting(
                "playerPath", 20, true),
                d.makeGbc(1, 7, 2, 1, GridBagConstraints.WEST));

        fork.add(new JLabel("Check new version every "),
                d.makeGbc(0, 5, 1, 1, GridBagConstraints.WEST));
        
        fork.add(d.addSimpleLongSetting("checkVersionInterval", 3, true),
                d.makeGbc(1, 5, 1, 1, GridBagConstraints.WEST));

        fork.add(new JLabel(" hours. (Max 500 hours.)"),
                d.makeGbc(2, 5, 1, 1, GridBagConstraints.WEST));

        fork.add(new JLabel("Color of tab title when channel has new message:"),
                d.makeGbc(0, 8, 1, 1, GridBagConstraints.WEST));
        
        fork.add(d.addSimpleStringSetting(
                "colorNewMessage", 10, true),
                d.makeGbc(1, 8, 2, 1, GridBagConstraints.WEST));

        fork.add(new JLabel("Color of tab title when you was mentioned:"),
                d.makeGbc(0, 9, 1, 1, GridBagConstraints.WEST));
        
        fork.add(d.addSimpleStringSetting(
                "colorNewHighlightedMessage", 10, true),
                d.makeGbc(1, 9, 2, 1, GridBagConstraints.WEST));

        Map<String, String> mentionNicknameOptions = new HashMap<>();
        mentionNicknameOptions.put("normal", "Localized nickname");
        mentionNicknameOptions.put("real", "Real nickname");
        mentionNicknameOptions.put("customReal", "Custom (or real) nickname");
        mentionNicknameOptions.put("custom", "Custom (or localized) nickname");
        ComboStringSetting mentionNicknameSetting = new ComboStringSetting(mentionNicknameOptions);
        d.addStringSetting("mentionByNickname", mentionNicknameSetting);
        fork.add(new JLabel("Mention with middle click by"), d.makeGbc(0, 11, 1, 1, GridBagConstraints.WEST));
        fork.add(mentionNicknameSetting,
            d.makeGbc(1, 11, 2, 1, GridBagConstraints.WEST)
        );

        JCheckBox emoteCode = d.addSimpleBooleanSetting(
                "emoteCodeInDialog",
                "Show code of emotes in Emoticons Dialog.",
                "Reboot is needed.");
        fork.add(emoteCode,
                d.makeGbc(0, 12, 3, 1, GridBagConstraints.WEST));


        JCheckBox notStrike = d.addSimpleBooleanSetting(
                "useNotStrike",
                "Highlight banned message in specific color instead of strike.",
                "...");
        fork.add(notStrike,
                d.makeGbc(0, 15, 3, 1, GridBagConstraints.WEST));

        JLabel notStrike_l = new JLabel("Color of highlight banned messages:");
        fork.add(notStrike_l, d.makeGbc(0, 16, 1, 1, GridBagConstraints.WEST));
        
        JTextField notStrike_t = d.addSimpleStringSetting("colorBannedHighlightedMessage", 10, true);
        fork.add(notStrike_t, d.makeGbc(1, 16, 2, 1, GridBagConstraints.WEST));

        notStrike.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                notStrike_l.setEnabled(notStrike.isSelected());
                notStrike_t.setEnabled(notStrike.isSelected());
            }
        });

        notStrike_l.setEnabled(notStrike.isSelected());
        notStrike_t.setEnabled(notStrike.isSelected());

        
    }
    
}
