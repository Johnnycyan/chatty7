
package chatty.gui.components;

import chatty.gui.HtmlColors;
import chatty.gui.components.AutoCompletionServer.CompletionItems;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

/**
 * Provides the feature to complete text when the user performs a certain action
 * (e.g. pressing TAB, although that is controlled from outside this class).
 * 
 * This is probably only fit for shorter texts, because it always works on the
 * whole text. Might have bad performance in huge documents if it where modified
 * to work in another context.
 * 
 * If this is not used anymore, you should call {@link cleanUp()} to make sure
 * it can get gargabe collected.
 * 
 * @author tduva
 */
public class EditBoxPopup {

    public static int MAX_SYMBOLS_FOR_SHOWING_POPUP = 100;

    /**
     * The JTextField the completion is performed in.
     */
    private final JTextField textField;

    // Settings
    private boolean showPopup = true;
    private boolean completeToCommonPrefix = true;

    // GUI elements for info display
    private JWindow infoWindow;
    private JLabel infoLabel;
    
    private final ComponentListener componentListener;
    private Window containingWindow;

    /**
     * Creates a new auto completion object bound to the given JTextField.
     *
     * @param textField The JTextField to perform the completion on
     */
    public EditBoxPopup(JTextField textField) {
        this.textField = textField;
        
        /**
         * Hide and show the info popup depending on whether the textfield has
         * focus.
         */
        textField.addFocusListener(new FocusListener() {

            @Override
            public void focusGained(FocusEvent e) {
                reshowInfoWindow();
            }

            @Override
            public void focusLost(FocusEvent e) {
                hideInfoWindow();
            }
        });
        
        /**
         * Listener to attach to the textField and the main containing window,
         * so when any of that moves or gets resized, the info window is hidden.
         * 
         * The componentShown() and componentHidden() methods may not do
         * anything depending on the specific use, but keeping them there just
         * in case.
         */
        componentListener = new ComponentListener() {

            @Override
            public void componentResized(ComponentEvent e) {
                infoWindow.setVisible(false);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                infoWindow.setVisible(false);
            }

            @Override
            public void componentShown(ComponentEvent e) {
                infoWindow.setVisible(false);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                infoWindow.setVisible(false);
            }
        };
    }

    private Point prevCaretLocation;

    public void updateCaretSymbolPosition() {
        if (infoLabel == null) {
            return;
        }
        
        String str = textField.getText();
        int index = textField.getCaret().getDot();
        str = str.substring(0, index) + "|" + str.substring(index);
        infoLabel.setText("<html><table style='table-layout:fixed;width:" + textField.getWidth() / 2 + "px;'><tr><th>" + str + "</th></tr></table></html>");
    }

    private int lastHeightWindow = 0;

    /**
     * Position the info popup according to the current caret location and show
     * it.
     *
     * @param infoText The info text to show
     * @param newPosition
     */
    public void showInfoWindow(String infoText, boolean newPosition) {
        if (infoWindow == null) {
            createInfoWindow();
        }
        Point location = prevCaretLocation;
        if (location == null || newPosition) {
            location = textField.getCaret().getMagicCaretPosition();
        }

        // No location found, so don't show window
        if (location == null) {
            return;
        }

        // Save a copy, because location is modified in-place
        prevCaretLocation = new Point(location);

        // Get size before setting new values
        int prevHeight = infoWindow.getHeight();
        int prevWidth = infoWindow.getWidth();

        /*String str = infoText;
        int index = str.indexOf("<tr><th>") + "<tr><th>".length() + textField.getCaret().getDot();
        infoLabel.setText(str.substring(0, index) + "|" + str.substring(index));*/

        // Get new size
        updateCaretSymbolPosition();
        Dimension preferredSize = infoWindow.getPreferredSize();

        // Set size depending on previous size
        if (prevWidth > preferredSize.width && !newPosition) {
            infoWindow.setSize(prevWidth, preferredSize.height);
        } else {
            if (preferredSize.width > textField.getWidth()) {
                infoWindow.setSize(textField.getWidth(), preferredSize.height);
            } else {
                infoWindow.setSize(preferredSize);
            }
        }

        // If height of the window changed, need to reposition it
        if (prevHeight != infoWindow.getHeight()
                || prevWidth != infoWindow.getWidth()) {
            newPosition = true;
        }

        if (!infoWindow.isVisible() || lastHeightWindow != infoWindow.getHeight()) {
            lastHeightWindow = infoWindow.getHeight();
            // Determine and set new position
            location.x -= infoWindow.getWidth() / 4;
            if (location.x + infoWindow.getWidth() > textField.getWidth()) {
                location.x = textField.getWidth() - infoWindow.getWidth();
            } else if (location.x < 8) {
                location.x = 8;
            }
            location.y -= infoWindow.getHeight();
            SwingUtilities.convertPointToScreen(location, textField);
            infoWindow.setLocation(location);
        }
        reshowInfoWindow();
    }

    /**
     * Creates the window for the info popup. This should only be run once and
     * then reused, only changing the text and size.
     */
    private void createInfoWindow() {
        infoWindow = new JWindow(SwingUtilities.getWindowAncestor(textField));
        infoLabel = new JLabel();
        infoWindow.add(infoLabel);
        JPanel contentPane = (JPanel) infoWindow.getContentPane();
        Border border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(2, 4, 2, 4));
        contentPane.setBorder(border);
        contentPane.setBackground(HtmlColors.decode("#EEEEEE"));
        infoLabel.setFont(textField.getFont());
        infoWindow.setOpacity(0.7f);

        lastHeightWindow = infoWindow.getHeight();

        /**
         * Hide the info popup if the textfield or containing window is changed
         * in any way.
         */
        containingWindow = SwingUtilities.getWindowAncestor(textField);
        if (containingWindow != null) {
            containingWindow.addComponentListener(componentListener);
        }
        textField.addComponentListener(componentListener);
    }

    public void reshowInfoWindow() {
        if (infoWindow != null && !infoWindow.isVisible() && textField.getText().length() > MAX_SYMBOLS_FOR_SHOWING_POPUP) {
            infoWindow.setVisible(true);
        }
    }

    public void hideInfoWindow() {
        if (infoWindow != null && infoWindow.isVisible()) {
            infoWindow.setVisible(false);
        }
    }

    /**
     * This should be called when the AutoCompletion is no longer used, so it
     * can be gargabe collected.
     */
    public void cleanUp() {
        if (containingWindow != null) {
            containingWindow.removeComponentListener(componentListener);
        }
        infoWindow = null;
    }

}
