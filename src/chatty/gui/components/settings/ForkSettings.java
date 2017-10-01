
package chatty.gui.components.settings;

import chatty.Chatty;
import java.awt.GridBagConstraints;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

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
                "youtubeTitle",
                "Show title youtube urls.",
                "You can see title of youtube video when you click on youtube urls.");
        fork.add(versionCheck,
                d.makeGbc(0, 3, 3, 1, GridBagConstraints.WEST));
        
    }
    
}
