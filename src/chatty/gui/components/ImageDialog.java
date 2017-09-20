
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
import java.net.URLConnection;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.Graphics;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent ;
import java.awt.event.MouseAdapter;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.net.HttpURLConnection;


/**
 * Dialog to see image in the chat.
 * 
 * @author 23rd
 */
public class ImageDialog extends JDialog {

    private ImageIcon iconOrigin;
    private ImageIcon icon;

    public static void showImageDialog(Window owner, String url) {
        String type = getURLType(url);
        if (type.indexOf("image/") >= 0) {
            ImageDialog dialog;
            dialog = new ImageDialog(owner, url, type);
            dialog.setLocationRelativeTo(owner);
            GuiUtil.installEscapeCloseOperation(dialog);
            dialog.setVisible(true);
        }
    }
        
    public ImageDialog(final Window owner, String url, String type) {
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

        try {
            URL thisurl = new URL(url);
            URLConnection connection = thisurl.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.135 Safari/537.36");
            Image image = null;
            image = ImageIO.read(connection.getInputStream());

            iconOrigin = new ImageIcon(image);
            icon = new ImageIcon(iconOrigin.getImage());
            Dimension dimension = getScaledDimension(iconOrigin, owner.getBounds());
            icon.setImage(getScaledImage(iconOrigin.getImage(), dimension.width, dimension.height));

            JLabel thumb = new JLabel();
            thumb.setIcon(icon);
            add(thumb, gbc);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException exx) {
            exx.printStackTrace();
        }

        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                Dimension dimension = getScaledDimension(iconOrigin, getBounds());
                icon.setImage(getScaledImage(iconOrigin.getImage(), dimension.width, dimension.height));
            }
        });

        addMouseListener(new MouseAdapter() {
            public void mousePressed (MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dispose();
                }

                if (SwingUtilities.isRightMouseButton(e)) {
                    String ext = "jpg";
                    int typeInt = BufferedImage.TYPE_INT_RGB;
                    if (type.equals("image/png")) {
                        typeInt = BufferedImage.TYPE_INT_ARGB;
                        ext = "png";
                    }

                    BufferedImage bi = new BufferedImage(iconOrigin.getIconWidth(), iconOrigin.getIconHeight(), typeInt);
                    Graphics g = bi.createGraphics();
                    iconOrigin.paintIcon(null, g, 0,0);
                    g.dispose();

                    JFileChooser chooser = new JFileChooser();
                    FileNameExtensionFilter filter = new FileNameExtensionFilter("JPG & PNG Images", "jpg", "png");
                    chooser.setFileFilter(filter);
                    int returnVal = chooser.showSaveDialog(owner);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        try {
                            File f = chooser.getSelectedFile();
                            
                            String test = f.getAbsolutePath() + "." + ext;
                            ImageIO.write(bi, ext, new File(test));
                        } catch(IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        });

        pack();
        setVisible(true);
    }

    private static String getURLType(String url) {
        try {
          HttpURLConnection.setFollowRedirects(false);
          HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
          con.setRequestMethod("HEAD");
          return con.getHeaderFields().get("Content-Type").get(0);
        } catch (Exception e) {
           e.printStackTrace();
           return "";
        }
    }

    private Image getScaledImage(Image srcImg, int w, int h){
        if (w <= 0 || h <= 0) {
            dispose();
            return srcImg;
        }
        BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImg.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(srcImg, 0, 0, w, h, null);
        g2.dispose();

        return resizedImg;
    }

    public Dimension getScaledDimension(ImageIcon img, Rectangle boundary) {
        int titleHeight = 35;
        int original_width = img.getIconWidth();
        int original_height = img.getIconHeight();
        int bound_width = boundary.width - 10;
        int bound_height = boundary.height - titleHeight;
        int new_width = original_width;
        int new_height = original_height;

        if (original_width > bound_width) {
            new_width = bound_width;
            new_height = (new_width * original_height) / original_width;
        }

        if (new_height > bound_height) {
            new_height = bound_height;
            new_width = (new_height * original_width) / original_height;
        }

        return new Dimension(new_width, new_height);
    }
    
}
