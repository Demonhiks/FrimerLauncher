package com.frimer.launcher.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Окно с разбором краша: причина, подсказки и текст отчёта. */
public final class CrashingDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    public CrashingDialog(JFrame owner, Theme theme, String summary, String body) {
        super(owner, "Игра упала", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(16, 18, 14, 18));

        JLabel title = new JLabel("Что случилось");
        title.setFont(Theme.titleFont(Font.BOLD, 16));
        title.setForeground(theme.err);

        JLabel summaryLabel = new JLabel("<html><body style='width:640px'>"
                + summary.replace("\n", "<br>") + "</body></html>");
        summaryLabel.setFont(Theme.uiFont(Font.PLAIN, 13));
        summaryLabel.setForeground(theme.text);

        javax.swing.JTextArea area = Ui.logArea(theme);
        area.setText(body);
        area.setCaretPosition(0);
        javax.swing.JScrollPane scroll = Ui.scroll(area, theme);
        scroll.setPreferredSize(new Dimension(700, 300));

        JButton copy = Ui.miniButton("Копировать отчёт", theme);
        copy.addActionListener(e -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(summary + "\n\n" + body), null));
        JButton close = Ui.primaryButton("Закрыть", theme);
        close.addActionListener(e -> dispose());

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(title);
        top.add(Box.createVerticalStrut(6));
        top.add(summaryLabel);

        JPanel buttons = new JPanel(new BorderLayout(8, 0));
        buttons.setOpaque(false);
        buttons.add(copy, BorderLayout.WEST);
        buttons.add(close, BorderLayout.EAST);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(760, 520));
        setLocationRelativeTo(owner);
    }

    public void showDialog() {
        setVisible(true);
    }
}
