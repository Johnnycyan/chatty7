
package chatty.gui.components;

import chatty.gui.GuiUtil;

import chatty.util.ForkUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.lang.OutOfMemoryError;


/**
 * Dialog to see image in the chat.
 * 
 * @author Johnnycyan
 */
public class ImageDialog extends JDialog {

    private ImageIcon iconOrigin;
    private ImageIcon icon;

    private int counterResize = 0;

    // 0 - size of owner
    // 1 - size of owner x2
    // 2 - original size
    private int currentStateOfImage = 0;

    public static void showImageDialog(Window owner, String url) {
        String type = getURLType(url);
        if (type.indexOf("image/") >= 0) {
            ImageDialog dialog;
            dialog = new ImageDialog(owner, url, type);
            dialog.setLocationRelativeTo(owner);
            GuiUtil.installEscapeCloseOperation(dialog);
            //dialog.setVisible(true);
        }
    }
        
    public ImageDialog(final Window owner, String url, String type) {
        super(owner);
        setTitle(url);
        setResizable(false);
        setMinimumSize(new Dimension(100, 100));
        setLayout(new GridBagLayout());
        getContentPane().setBackground(new Color(35, 35, 35));
 
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(3,3,3,3);
        gbc.anchor = GridBagConstraints.WEST;

        try {
            URL thisurl = new URL(url);
            URLConnection connection = thisurl.openConnection();
            connection.setRequestProperty("User-Agent", ForkUtil.USER_AGENT);
            Image image = null;
            image = ImageIO.read(connection.getInputStream());
            if (image == null) {
                return;
            }

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
        } catch (OutOfMemoryError exxx) {
            exxx.printStackTrace();
        }

        getContentPane().addMouseListener(new MouseAdapter() {
            public void mouseReleased (MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    dispose();
                }

                String ext = "jpg";
                int typeInt = BufferedImage.TYPE_INT_RGB;
                if (type.equals("image/png")) {
                    typeInt = BufferedImage.TYPE_INT_ARGB;
                    ext = "png";
                }

                try {
                    BufferedImage bi = new BufferedImage(iconOrigin.getIconWidth(), iconOrigin.getIconHeight(), typeInt);
                    Graphics g = bi.createGraphics();
                    iconOrigin.paintIcon(null, g, 0,0);
                    g.dispose();

                    if (SwingUtilities.isMiddleMouseButton(e)) {
                        TransferableImage trans = new TransferableImage(bi);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(trans, null);
                    }


                    if (SwingUtilities.isLeftMouseButton(e)) {
                        currentStateOfImage++;
                        if (currentStateOfImage > 2) {
                            currentStateOfImage = 0;
                        }

                        int widthOffset = 15;
                        int heightOffset = 35;
                        Dimension dimension = owner.getBounds().getSize();

                        if (currentStateOfImage == 0) {
                            dimension = getScaledDimension(iconOrigin, owner.getBounds());
                            icon.setImage(getScaledImage(iconOrigin.getImage(), dimension.width, dimension.height));
                        } else if (currentStateOfImage == 1) {
                            dimension = getScaledDimension(iconOrigin, owner.getBounds());
                            dimension.width *= 2;
                            dimension.height *= 2;
                            icon.setImage(getScaledImage(iconOrigin.getImage(), dimension.width, dimension.height));
                        } else if (currentStateOfImage == 2) {
                            dimension = new Dimension(iconOrigin.getIconWidth(), iconOrigin.getIconHeight());
                            icon.setImage(iconOrigin.getImage());
                        }
                        dimension.width += widthOffset;
                        dimension.height += heightOffset;
                        setSize(dimension);

                    }
                } catch (OutOfMemoryError exx) {
                    dispose();
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
          if (con.getResponseCode() == 200) {
            return con.getHeaderFields().get("Content-Type").get(0);
          } else {
            return "image/jpg";
          }
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

    private class TransferableImage implements Transferable {

        Image i;

        public TransferableImage( Image i ) {
            this.i = i;
        }

        public Object getTransferData( DataFlavor flavor )
        throws UnsupportedFlavorException, IOException {
            if ( flavor.equals( DataFlavor.imageFlavor ) && i != null ) {
                return i;
            }
            else {
                throw new UnsupportedFlavorException( flavor );
            }
        }

        public DataFlavor[] getTransferDataFlavors() {
            DataFlavor[] flavors = new DataFlavor[ 1 ];
            flavors[ 0 ] = DataFlavor.imageFlavor;
            return flavors;
        }

        public boolean isDataFlavorSupported( DataFlavor flavor ) {
            DataFlavor[] flavors = getTransferDataFlavors();
            for ( int i = 0; i < flavors.length; i++ ) {
                if ( flavor.equals( flavors[ i ] ) ) {
                    return true;
                }
            }

            return false;
        }
    }
    
}
