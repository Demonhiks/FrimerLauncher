package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Integrity;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Проверка файлов сборки и починка: библиотеки, клиент, индекс ассетов. */
public final class IntegrityDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final Instance instance;
    private final JLabel status = new JLabel(" ");
    private final Ui.MiniBar bar;
    private final JTextArea log;
    private final JButton checkButton;
    private final JButton repairButton;

    private Integrity.Report report;

    public IntegrityDialog(JFrame owner, Theme theme, Instance instance) {
        super(owner, "Проверка файлов — " + instance.name, true, theme);
        this.theme = theme;
        this.instance = instance;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel title = new JLabel("Файлы сборки «" + instance.name + "»");
        title.setFont(Theme.titleFont(Font.BOLD, 16));
        title.setForeground(theme.text);

        JPanel chips = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        chips.setOpaque(false);
        chips.add(Ui.accentChip(instance.loader + " " + instance.mcVersion, theme));
        chips.add(Ui.chip("папка: " + instance.folder.getFileName(), theme));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(title);
        head.add(Box.createVerticalStrut(6));
        head.add(chips);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);

        log = Ui.logArea(theme);
        log.setFont(Theme.uiFont(Font.PLAIN, 12));
        log.setText("Нажми «Проверить», чтобы сверить клиент, библиотеки и ассеты.\n");
        JScrollPane scroll = Ui.scroll(log, theme);
        scroll.setPreferredSize(new Dimension(560, 260));

        bar = new Ui.MiniBar(theme);
        bar.setPreferredSize(new Dimension(0, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));

        status.setFont(Theme.uiFont(Font.PLAIN, 12));
        status.setForeground(theme.muted);

        checkButton = Ui.primaryButton("Проверить", theme);
        checkButton.addActionListener(e -> runCheck());
        repairButton = Ui.button("Починить", theme);
        repairButton.setEnabled(false);
        repairButton.addActionListener(e -> runRepair());
        JButton close = Ui.miniButton("Закрыть", theme);
        close.addActionListener(e -> dispose());

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(Ui.buttonRow(checkButton, repairButton), BorderLayout.WEST);
        actions.add(close, BorderLayout.EAST);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(status);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(bar);
        bottom.add(Box.createVerticalStrut(8));
        bottom.add(actions);

        JPanel headWrap = new JPanel(new java.awt.BorderLayout());
        headWrap.setOpaque(false);
        headWrap.add(head, java.awt.BorderLayout.WEST);
        root.add(headWrap, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(620, 480));
        setLocationRelativeTo(owner);
        runCheck();
    }

    public void showDialog() {
        setVisible(true);
    }

    private void runCheck() {
        checkButton.setEnabled(false);
        repairButton.setEnabled(false);
        status.setText("Проверяю файлы...");
        append("— проверка —");
        new Thread(() -> {
            try {
                report = Integrity.check(instance, line -> SwingUtilities.invokeLater(() -> append(line)),
                        (done, total) -> SwingUtilities.invokeLater(() -> {
                            int percent = total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
                            bar.setValue(percent);
                        }));
                SwingUtilities.invokeLater(() -> {
                    bar.setValue(100);
                    if (report.ok()) {
                        status.setText("Всё на месте — проверено файлов: " + report.checked());
                        append("проверено: " + report.checked() + ", проблем нет");
                    } else {
                        status.setText("Найдено проблем: " + report.issues().size()
                                + " из " + report.checked());
                        append("проблем: " + report.issues().size());
                        repairButton.setEnabled(true);
                    }
                    checkButton.setEnabled(true);
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    status.setText("Ошибка проверки");
                    append("ошибка: " + e.getMessage());
                    checkButton.setEnabled(true);
                });
            }
        }, "frimer-integrity").start();
    }

    private void runRepair() {
        if (report == null || report.ok()) {
            return;
        }
        repairButton.setEnabled(false);
        checkButton.setEnabled(false);
        status.setText("Восстанавливаю файлы...");
        append("— починка —");
        new Thread(() -> {
            try {
                int fixed = Integrity.repair(report, line -> SwingUtilities.invokeLater(() -> append(line)),
                        (done, total) -> SwingUtilities.invokeLater(() -> {
                            int percent = total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
                            bar.setValue(percent);
                        }));
                SwingUtilities.invokeLater(() -> {
                    append("восстановлено файлов: " + fixed);
                    status.setText("Восстановлено: " + fixed + ". Проверяю ещё раз...");
                    runCheck();
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    status.setText("Ошибка починки");
                    append("ошибка: " + e.getMessage());
                    repairButton.setEnabled(true);
                    checkButton.setEnabled(true);
                });
            }
        }, "frimer-repair").start();
    }

    private void append(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    /** Краткий отчёт (для отладки и тестов). */
    public String summary() {
        return report == null ? "нет данных"
                : Utils.shortText("проверено " + report.checked() + ", проблем "
                + report.issues().size(), 80);
    }
}
