package com.frimer.launcher;

import com.frimer.launcher.core.AppSettings;
import com.frimer.launcher.core.Dirs;
import com.frimer.launcher.ui.MainFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Точка входа Frimer Launcher. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // останется кросс-платформенный вид
            }
            Dirs.ensureAll();
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.afterShow();
            AppSettings.flush();
        });
    }
}
