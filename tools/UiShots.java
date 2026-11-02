import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.InstanceStore;
import com.frimer.launcher.core.ServerInstance;
import java.nio.file.Path;
import com.frimer.launcher.ui.CrashingDialog;
import com.frimer.launcher.ui.EditInstanceDialog;
import com.frimer.launcher.ui.MainFrame;
import com.frimer.launcher.ui.ModrinthDialog;
import com.frimer.launcher.ui.ModsDialog;
import com.frimer.launcher.ui.NewInstanceDialog;
import com.frimer.launcher.ui.NewServerDialog;
import com.frimer.launcher.ui.ServerConsoleDialog;
import com.frimer.launcher.ui.ScreenshotsDialog;
import com.frimer.launcher.ui.SettingsDialog;
import com.frimer.launcher.ui.SkinDialog;
import com.frimer.launcher.ui.Theme;
import com.frimer.launcher.ui.Ui;
import com.frimer.launcher.ui.WorldsDialog;
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

        capture(new NewInstanceDialog(frame, theme), "03-new-instance", 430, 500);
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

        var menuItems = java.util.List.of(
                Ui.MenuItemSpec.of("Играть", null),
                Ui.MenuItemSpec.separator(),
                Ui.MenuItemSpec.of("Моды (149)", null),
                Ui.MenuItemSpec.of("Изменить", null),
                Ui.MenuItemSpec.of("Дублировать", null),
                Ui.MenuItemSpec.of("Открыть папку", null),
                Ui.MenuItemSpec.separator(),
                Ui.MenuItemSpec.of("Удалить", null));
        javax.swing.JWindow menuWindow = new javax.swing.JWindow();
        SwingUtilities.invokeAndWait(() -> {
            menuWindow.setContentPane(Ui.menuPanel(theme, menuItems, null));
            menuWindow.pack();
            menuWindow.setVisible(true);
        });
        Thread.sleep(500);
        shot(menuWindow, "11-menu", menuWindow.getWidth(), menuWindow.getHeight());
        SwingUtilities.invokeAndWait(menuWindow::dispose);

        if (instance != null) {
            Path sample = makeSampleInstance();
            Instance probe = new Instance(sample, "Тест", "Fabric", "1.20.1", 4096, "0.15.0");
            capture(new WorldsDialog(frame, theme, probe), "14-worlds", 680, 520, 700);
            capture(new ScreenshotsDialog(frame, theme, probe), "15-screenshots", 820, 580, 1800);
            cleanSample(sample);
            ModrinthDialog modrinth = new ModrinthDialog(frame, theme, instance,
                    com.frimer.launcher.core.Modrinth.MOD, instance.folder.resolve("mods"));
            SwingUtilities.invokeAndWait(() -> {
                modrinth.setModal(false);
                modrinth.setVisible(true);
            });
            Thread.sleep(4000);
            shot(modrinth, "16-modrinth", 780, 580);
            SwingUtilities.invokeAndWait(modrinth::dispose);
        }

        SettingsDialog settingsDialog = new SettingsDialog(frame, theme);
        SwingUtilities.invokeAndWait(() -> {
            settingsDialog.setModal(false);
            settingsDialog.setVisible(true);
        });
        Thread.sleep(500);
        javax.swing.JComboBox<?> themeCombo = findCombo(settingsDialog.getContentPane());
        if (themeCombo != null) {
            SwingUtilities.invokeAndWait(() -> themeCombo.setPopupVisible(true));
            Thread.sleep(600);
            shot(settingsDialog, "13-settings-combo-open", 440, 430);
            SwingUtilities.invokeAndWait(() -> themeCombo.setPopupVisible(false));
        }
        SwingUtilities.invokeAndWait(settingsDialog::dispose);

        NewServerDialog comboDialog = new NewServerDialog(frame, theme);
        SwingUtilities.invokeAndWait(() -> {
            comboDialog.setModal(false);
            comboDialog.setVisible(true);
        });
        Thread.sleep(600);
        javax.swing.JComboBox<?> combo = findCombo(comboDialog.getContentPane());
        System.out.println("combo found: " + (combo != null));
        if (combo != null) {
            SwingUtilities.invokeAndWait(() -> combo.setPopupVisible(true));
            Thread.sleep(700);
            shot(comboDialog, "12-combo-open", 440, 520);
            SwingUtilities.invokeAndWait(() -> combo.setPopupVisible(false));
        }
        SwingUtilities.invokeAndWait(comboDialog::dispose);

        SwingUtilities.invokeAndWait(frame::dispose);
        System.out.println("Готово: " + outDir.getAbsolutePath());
        System.exit(0);
    }

    private static void capture(JDialog dialog, String name, int width, int height) throws Exception {
        capture(dialog, name, width, height, 300);
    }

    private static void capture(JDialog dialog, String name, int width, int height, int waitMs)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            dialog.setModal(false);
            dialog.setVisible(true);
        });
        Thread.sleep(waitMs);
        shot(dialog, name, width, height);
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    /** Временный инстанс с мирами и скриншотами — чтобы проверить списки с данными. */
    private static Path makeSampleInstance() throws Exception {
        Path root = new File("build/ui-sample").toPath();
        cleanSample(root);
        java.nio.file.Files.createDirectories(root.resolve("saves").resolve("Мир-1"));
        java.nio.file.Files.createDirectories(root.resolve("saves").resolve("Hardcore-world"));
        java.nio.file.Files.createDirectories(root.resolve("screenshots"));
        java.nio.file.Files.writeString(root.resolve("saves").resolve("Мир-1")
                .resolve("level.dat"), "x".repeat(400_000));
        java.nio.file.Files.writeString(root.resolve("saves").resolve("Hardcore-world")
                .resolve("level.dat"), "y".repeat(120_000));
        for (int i = 1; i <= 3; i++) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    480, 270, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(new java.awt.Color(30 + i * 40, 60, 120 - i * 20));
            g.fillRect(0, 0, 480, 270);
            g.setColor(java.awt.Color.WHITE);
            g.drawString("screenshot " + i, 200, 140);
            g.dispose();
            ImageIO.write(img, "png", root.resolve("screenshots")
                    .resolve("2026-11-0" + i + "_12.00.00.png").toFile());
        }
        return root;
    }

    private static void cleanSample(Path root) {
        try {
            if (root != null && java.nio.file.Files.exists(root)) {
                try (var stream = java.nio.file.Files.walk(root)) {
                    stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            java.nio.file.Files.deleteIfExists(p);
                        } catch (java.io.IOException ignored) {
                            // занятый файл
                        }
                    });
                }
            }
        } catch (java.io.IOException ignored) {
            // не критично
        }
    }

    private static javax.swing.JComboBox<?> findCombo(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JComboBox<?> combo) {
                return combo;
            }
            if (child instanceof java.awt.Container nested) {
                javax.swing.JComboBox<?> found = findCombo(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void shotPanel(javax.swing.JPanel panel, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Dimension size = panel.getPreferredSize();
            panel.setSize(size);
            panel.doLayout();
            panel.validate();
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    size.width, size.height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = image.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            panel.printAll(g);
            g.dispose();
            try {
                ImageIO.write(image, "png", new File(outDir, name + ".png"));
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
            System.out.println("shot " + name + " " + size.width + "x" + size.height);
        });
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
