import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.InstanceStore;
import com.frimer.launcher.core.ServerInstance;
import com.frimer.launcher.ui.CrashingDialog;
import com.frimer.launcher.ui.EditInstanceDialog;
import com.frimer.launcher.ui.MainFrame;
import com.frimer.launcher.ui.ModsDialog;
import com.frimer.launcher.ui.NewInstanceDialog;
import com.frimer.launcher.ui.NewServerDialog;
import com.frimer.launcher.ui.ServerConsoleDialog;
import com.frimer.launcher.ui.SettingsDialog;
import com.frimer.launcher.ui.SkinDialog;
import com.frimer.launcher.ui.Theme;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;

/**
 * Рендерит окна лаунчера в build/ui: окно печатается прямо в картинку.
 * Снимок делается сразу после показа (пока оконный менеджер не ужал окно),
 * плюс отдельный снимок после паузы — проверка поведения в узком окне.
 * Запуск: javac -cp build/classes -d build/test tools/UiShots.java
 *          java -cp "build/classes;build/test" UiShots
 */
public final class UiShots {

    private static File outDir;

    public static void main(String[] args) throws Exception {
        outDir = new File("build/ui");
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IllegalStateException("не могу создать " + outDir);
        }
        System.out.println("SCREEN=" + Toolkit.getDefaultToolkit().getScreenSize()
                + " MAX=" + GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        Theme theme = Theme.DARK;

        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            holder[0] = frame;
        });
        MainFrame frame = holder[0];
        shot(frame, "01-main", 1040, 700);
        Thread.sleep(1600);
        shot(frame, "01b-main", 760, 660);

        SwingUtilities.invokeAndWait(() -> {
            try {
                var method = MainFrame.class.getDeclaredMethod("switchTab", boolean.class);
                method.setAccessible(true);
                method.invoke(frame, true);
            } catch (ReflectiveOperationException e) {
                e.printStackTrace();
            }
        });
        shot(frame, "02-servers", 1040, 700);

        List<Instance> instances = InstanceStore.loadAll();
        Instance instance = instances.isEmpty() ? null : instances.get(0);

        capture(new NewInstanceDialog(frame, theme), "03-new-instance", 420, 430);
        if (instance != null) {
            capture(new EditInstanceDialog(frame, theme, instance), "04-edit-instance", 420, 330);
        }
        capture(new SettingsDialog(frame, theme), "05-settings", 440, 430);
        capture(new NewServerDialog(frame, theme), "06-new-server", 440, 520);
        if (instance != null) {
            capture(new ModsDialog(frame, theme, instance), "07-mods", 700, 520);
        }
        capture(new SkinDialog(frame, theme, instances), "08-skins", 760, 520);
        capture(new CrashingDialog(frame, theme,
                "Игра (NeoForge 1.21.1) завершилась с ошибкой, код 1.\n"
                        + "Причина: java.lang.OutOfMemoryError: Java heap space\n"
                        + "Подсказка: Не хватает оперативной памяти. Увеличь RAM у сборки.",
                "java.lang.OutOfMemoryError: Java heap space\n\tat net.minecraft.client.Minecraft.run(...)\n"),
                "09-crash", 760, 520);
        List<ServerInstance> servers = ServerInstance.loadAll();
        if (!servers.isEmpty()) {
            capture(new ServerConsoleDialog(frame, theme, servers.get(0), null, null),
                    "10-server-console", 760, 520);
        }

        SwingUtilities.invokeAndWait(frame::dispose);
        System.out.println("Готово: " + outDir.getAbsolutePath());
        System.exit(0);
    }

    private static void capture(JDialog dialog, String name, int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            dialog.setModal(false);
            dialog.setVisible(true);
        });
        Thread.sleep(300);
        shot(dialog, name, width, height);
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    private static void shot(java.awt.Window window, String name, int width, int height) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            window.setSize(width, height);
        });
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> {
            window.validate();
            java.awt.Dimension size = window.getSize();
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    Math.max(1, size.width), Math.max(1, size.height),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            window.printAll(g);
            g.dispose();
            try {
                ImageIO.write(image, "png", new File(outDir, name + ".png"));
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
            System.out.println("shot " + name + " " + size.width + "x" + size.height);
        });
    }
}
