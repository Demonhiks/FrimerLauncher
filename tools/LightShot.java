import com.frimer.launcher.ui.MainFrame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Рендер главного окна в светлой теме: проверка второй палитры. */
public final class LightShot {
    public static void main(String[] a) throws Exception {
        Preferences prefs = Preferences.userRoot().node("com/frimer/launcher");
        String old = prefs.get("theme", "dark");
        prefs.put("theme", "light");
        MainFrame[] h = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame f = new MainFrame();
            f.setVisible(true);
            h[0] = f;
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> {
            MainFrame f = h[0];
            f.setSize(1040, 700);
            f.validate();
            BufferedImage img = new BufferedImage(1040, 700, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            f.printAll(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", new File("build/ui/20-light-main.png"));
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
            f.dispose();
        });
        prefs.put("theme", old);
        System.out.println("shot 20-light-main");
        System.exit(0);
    }
}