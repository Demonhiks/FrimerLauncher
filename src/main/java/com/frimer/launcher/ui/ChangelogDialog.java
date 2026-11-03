package com.frimer.launcher.ui;

import com.frimer.launcher.core.AppSettings;
import com.frimer.launcher.core.Changelog;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

/** Окно «Что нового»: список изменений по версиям, свежие сверху. */
public final class ChangelogDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private static final int TEXT_WIDTH = 408;

    public ChangelogDialog(JFrame owner, Theme theme) {
        super(owner, "Что нового", true, theme);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel list = new JPanel();
        list.setBackground(theme.bg);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(2, 2, 2, 2));

        for (Changelog.Entry entry : Changelog.entries()) {
            list.add(versionCard(theme, entry));
            list.add(Box.createVerticalStrut(10));
        }

        JScrollPane scroll = Ui.borderlessScroll(list, theme);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hint = new JLabel("Версия: " + AppSettings.APP_VERSION);
        hint.setFont(Theme.uiFont(Font.PLAIN, 11));
        hint.setForeground(theme.muted);

        JButton close = Ui.primaryButton("Понятно", theme);
        close.addActionListener(e -> dispose());

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(hint, BorderLayout.WEST);
        bottom.add(close, BorderLayout.EAST);

        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(560, 550));
        setLocationRelativeTo(owner);
    }

    public void showDialog() {
        setVisible(true);
    }

    private static JPanel versionCard(Theme theme, Changelog.Entry entry) {
        boolean current = entry.version().equals(AppSettings.APP_VERSION);
        Ui.Card card = new Ui.Card(theme.card, current ? theme.accent : theme.border);
        card.setRadius(12);
        card.setGradient(theme.cardTop(), theme.cardBottom());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel head = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        head.setOpaque(false);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel version = new JLabel("v" + entry.version());
        version.setFont(Theme.titleFont(Font.BOLD, 15));
        version.setForeground(current ? theme.accent : theme.text);
        head.add(version);
        if (current) {
            head.add(Ui.accentChip("текущая", theme));
        }
        head.add(Ui.chip(entry.date(), theme));
        card.add(head);
        card.add(Box.createVerticalStrut(6));

        for (String change : entry.changes()) {
            JLabel line = new JLabel("<html><div style='width:" + TEXT_WIDTH + "px'>"
                    + "&#8226;&nbsp;&nbsp;" + escape(change) + "</div></html>");
            line.setFont(Theme.uiFont(Font.PLAIN, 12));
            line.setForeground(theme.muted);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            line.setBorder(new EmptyBorder(2, 2, 2, 2));
            card.add(line);
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(card, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return wrap;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Все версии для тестов. */
    public static List<Changelog.Entry> versions() {
        return Changelog.entries();
    }

    /** Заголовок карточки версии (для тестов). */
    public static JLabel badge(String text, Theme theme) {
        JLabel label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder());
        return label;
    }
}
