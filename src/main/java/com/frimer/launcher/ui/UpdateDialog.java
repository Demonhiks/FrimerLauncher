package com.frimer.launcher.ui;

import com.frimer.launcher.core.AppSettings;
import com.frimer.launcher.core.Dirs;
import com.frimer.launcher.core.Updater;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Окно обновления: что нового в новой версии и кнопка «скачать и перезапустить». */
public final class UpdateDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final Updater.Info info;
    private final JLabel status = new JLabel(" ");
    private final Ui.MiniBar bar;
    private final JButton install;
    private Path downloaded;

    public UpdateDialog(JFrame owner, Theme theme, Updater.Info info) {
        super(owner, "Обновление лаунчера", true, theme);
        this.theme = theme;
        this.info = info;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel title = new JLabel("Доступна версия " + info.version());
        title.setFont(Theme.titleFont(Font.BOLD, 18));
        title.setForeground(theme.text);

        JPanel versions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        versions.setOpaque(false);
        versions.add(Ui.chip("сейчас: " + AppSettings.APP_VERSION, theme));
        versions.add(Ui.accentChip("новая: " + info.version(), theme));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(title);
        head.add(Box.createVerticalStrut(6));
        head.add(versions);

        JTextArea notes = Ui.logArea(theme);
        notes.setFont(Theme.uiFont(Font.PLAIN, 12));
        notes.setText(info.notes().isBlank() ? "Описание не указано." : info.notes());
        notes.setCaretPosition(0);
        JScrollPane scroll = Ui.scroll(notes, theme);
        scroll.setPreferredSize(new Dimension(520, 210));

        bar = new Ui.MiniBar(theme);
        bar.setPreferredSize(new Dimension(0, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));

        status.setFont(Theme.uiFont(Font.PLAIN, 12));
        status.setForeground(theme.muted);

        install = Ui.primaryButton("Скачать и перезапустить", theme);
        install.addActionListener(e -> startDownload());
        JButton page = Ui.button("Страница релиза", theme);
        page.addActionListener(e -> ModrinthOpen.open(info.pageUrl()));
        JButton later = Ui.miniButton("Позже", theme);
        later.addActionListener(e -> dispose());

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(Ui.buttonRow(install, page), BorderLayout.WEST);
        actions.add(later, BorderLayout.EAST);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(status);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(bar);
        bottom.add(Box.createVerticalStrut(8));
        bottom.add(actions);

        root.add(head, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(600, 430));
        setLocationRelativeTo(owner);
    }

    public void showDialog() {
        setVisible(true);
    }

    private void startDownload() {
        install.setEnabled(false);
        status.setText("Скачиваю " + info.version() + "...");
        Path target = targetDir();
        new Thread(() -> {
            try {
                Path jar = Updater.download(info, target, (done, total) -> SwingUtilities.invokeLater(() -> {
                    int percent = total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
                    bar.setValue(percent);
                    status.setText("Скачиваю: " + percent + "%");
                }));
                SwingUtilities.invokeLater(() -> {
                    downloaded = jar;
                    bar.setValue(100);
                    status.setText("Готово: " + jar.getFileName());
                    install.setEnabled(true);
                    install.setText("Перезапустить");
                    for (java.awt.event.ActionListener listener : install.getActionListeners()) {
                        install.removeActionListener(listener);
                    }
                    install.addActionListener(e -> relaunch());
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    status.setText("Не скачалось");
                    install.setEnabled(true);
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка обновления",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }, "frimer-update").start();
    }

    private void relaunch() {
        if (downloaded == null) {
            return;
        }
        try {
            status.setText("Перезапускаю...");
            Updater.relaunch(downloaded);
            AppSettings.flush();
            System.exit(0);
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this,
                    e.getMessage() + "\n\nФайл лежит здесь:\n" + downloaded,
                    "Не удалось перезапустить", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Куда качать: рядом с текущим jar, если папка доступна на запись. */
    private static Path targetDir() {
        try {
            Path jar = Path.of(UpdateDialog.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            Path parent = jar.getParent();
            if (parent != null && Files.isWritable(parent)) {
                return parent;
            }
        } catch (Exception ignored) {
            // падаем в папку игры
        }
        return Dirs.GAME_DIR.resolve("update");
    }

    /** Все ссылки, которые упомянуты в описании (для тестов). */
    public static List<String> releaseLinks(Updater.Info info) {
        List<String> out = new ArrayList<>();
        if (!info.pageUrl().isBlank()) {
            out.add(info.pageUrl());
        }
        return out;
    }

    /** Строка статуса (для отладки). */
    public String statusText() {
        return Utils.shortText(status.getText(), 80);
    }

    /** Небольшой помощник: открыть ссылку в браузере. */
    private static final class ModrinthOpen {
        private ModrinthOpen() {
        }

        static void open(String url) {
            com.frimer.launcher.core.Modrinth.openPage(url);
        }
    }

    /** Пустая рамка-разделитель для единообразия (используется в тестах). */
    static javax.swing.border.Border spacer() {
        return BorderFactory.createEmptyBorder();
    }
}
