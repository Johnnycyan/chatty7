
package chatty.gui.components;

import chatty.gui.GuiUtil;
import chatty.gui.MainGui;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.Dimension;

/**
 * Dialog to see image in the chat.
 * 
 * @author 23rd
 */
public class ImageDialog extends JDialog {

    private ImageIcon iconOrigin;
    private ImageIcon icon;

    public static void showImageDialog(Window owner, String url) {
        if (url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".bmp")) {
            ImageDialog dialog;
            dialog = new ImageDialog(owner, url);
            dialog.setLocationRelativeTo(owner);
            GuiUtil.installEscapeCloseOperation(dialog);
            dialog.setVisible(true);
        }
    }
        
    public ImageDialog(final Window owner, String url) {
        super(owner);
        setTitle(url);
        setResizable(true);
        setMinimumSize(new Dimension(100, 100));
        setLayout(new GridBagLayout());
        getContentPane().setBackground(new Color(35, 35, 35));
 
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(5,5,5,5);
        gbc.anchor = GridBagConstraints.WEST;

        int titleHeight = 35;

        try {
            iconOrigin = new ImageIcon(new URL(url));
            icon = new ImageIcon(iconOrigin.getImage());
            if (icon.getIconWidth() > icon.getIconHeight() && icon.getIconWidth() > owner.getBounds().width) {
                int hh = Math.round((float)owner.getBounds().width / icon.getIconWidth() * icon.getIconHeight()) - titleHeight;
                icon.setImage(getScaledImage(icon.getImage(), owner.getBounds().width - titleHeight, hh));
            } else {
                if (icon.getIconHeight() > owner.getBounds().height) {
                    int hh = Math.round((float)owner.getBounds().height / icon.getIconHeight() * icon.getIconWidth()) - titleHeight;
                    icon.setImage(getScaledImage(icon.getImage(), hh, owner.getBounds().height - titleHeight));
                }
            }
            JLabel thumb = new JLabel();
            thumb.setIcon(icon);
            add(thumb, gbc);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                if (getBounds().height > getBounds().width) {
                    int hh = Math.round((float)getBounds().width / iconOrigin.getIconWidth() * iconOrigin.getIconHeight()) - titleHeight;
                    icon.setImage(getScaledImage(iconOrigin.getImage(), getBounds().width - titleHeight, hh));
                } else {
                    int hh = Math.round((float)getBounds().height / iconOrigin.getIconHeight() * iconOrigin.getIconWidth()) - titleHeight;
                    icon.setImage(getScaledImage(iconOrigin.getImage(), hh, getBounds().height - titleHeight));
                }
            }
        });

        pack();
        setVisible(true);
    }

    private Image getScaledImage(Image srcImg, int w, int h){
        BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImg.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(srcImg, 0, 0, w, h, null);
        g2.dispose();

        return resizedImg;
    }
    
}
