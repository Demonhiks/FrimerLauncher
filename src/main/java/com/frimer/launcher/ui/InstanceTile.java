package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** Плитка сборки: бейдж загрузчика, название, версия и число модов. */
public final class InstanceTile extends Ui.Card {

    private static final long serialVersionUID = 1L;

    /** Куда сообщать о действиях пользователя. */
    public interface Handler {
        void onSelect(Instance instance);

        void onActivate(Instance instance);

        void onMenu(Instance instance, java.awt.Point point);
    }

    public static final Dimension SIZE = new Dimension(286, 74);

    private final Instance instance;
    private final Theme theme;
    private final JLabel nameLabel;
    private final JLabel subLabel;
    private boolean selected;
    private boolean runningState;

    public InstanceTile(Instance instance, Theme theme, Handler handler) {
        super(theme.card, theme.border);
        this.instance = instance;
        this.theme = theme;
        setRadius(Theme.RADIUS_TILE);
        setGradient(theme.cardTop(), theme.cardBottom());
        setLayout(new BorderLayout(14, 0));
        setBorder(new EmptyBorder(10, 12, 10, 12));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(SIZE);
        setMinimumSize(new Dimension(220, SIZE.height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, SIZE.height));

        JLabel icon = new JLabel(iconFor(instance, theme, 46));
        icon.setPreferredSize(new Dimension(46, 46));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        add(icon, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        text.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        nameLabel = new JLabel(instance.name);
        nameLabel.setFont(Theme.titleFont(Font.BOLD, 14));
        nameLabel.setForeground(theme.text);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        int mods = Utils.countMods(instance.folder);
        subLabel = new JLabel(instance.loader + " " + instance.mcVersion
                + (mods > 0 ? "  ·  " + mods + " мод" + plural(mods) : ""));
        subLabel.setFont(Theme.uiFont(Font.PLAIN, 11));
        subLabel.setForeground(theme.muted);
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        text.add(javax.swing.Box.createVerticalGlue());
        text.add(nameLabel);
        text.add(javax.swing.Box.createVerticalStrut(4));
        text.add(subLabel);
        text.add(javax.swing.Box.createVerticalGlue());
        add(text, BorderLayout.CENTER);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    handler.onMenu(instance, e.getPoint());
                } else {
                    handler.onSelect(instance);
                    if (e.getClickCount() == 2) {
                        handler.onActivate(instance);
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!selected) {
                    setGradient(theme.hover, theme.card);
                    setStroke(theme.muted);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!selected) {
                    setGradient(theme.cardTop(), theme.cardBottom());
                    setStroke(theme.border);
                }
            }
        });
    }

    public Instance instance() {
        return instance;
    }

    /** Показать зелёную точку: сборка сейчас запущена. */
    public void setRunning(boolean running) {
        this.runningState = running;
        repaint();
    }

    public boolean isRunningState() {
        return runningState;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (!runningState) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(theme.ok);
        g2.fillOval(getWidth() - 20, 12, 8, 8);
        g2.setColor(theme.bg);
        g2.drawOval(getWidth() - 20, 12, 8, 8);
        g2.dispose();
    }

    public void setDisplayName(String name) {
        nameLabel.setText(name);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) {
            setGradient(theme.cardSel, theme.card);
            setStroke(theme.accent);
            setShadow(Ui.shadow(theme));
            nameLabel.setForeground(theme.accent);
            subLabel.setForeground(theme.text);
        } else {
            setGradient(theme.cardTop(), theme.cardBottom());
            setStroke(theme.border);
            setShadow(null);
            nameLabel.setForeground(theme.text);
            subLabel.setForeground(theme.muted);
        }
    }

    private static String plural(int mods) {
        int mod100 = mods % 100;
        int mod10 = mods % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "ов";
        }
        if (mod10 == 1) {
            return "";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "а";
        }
        return "ов";
    }

    /** Иконка сборки: своя icon.png либо бейдж загрузчика. */
    public static Icon iconFor(Instance instance, Theme theme) {
        return iconFor(instance, theme, 44);
    }

    /** Иконка сборки нужного размера. */
    public static Icon iconFor(Instance instance, Theme theme, int size) {
        Path custom = instance.folder.resolve("icon.png");
        if (Files.isRegularFile(custom)) {
            try {
                Icon icon = Ui.imageIcon(Files.readAllBytes(custom), size);
                if (icon != null) {
                    return icon;
                }
            } catch (IOException ignored) {
                // падаем в бейдж загрузчика
            }
        }
        return Ui.loaderBadge(instance.loader, theme, size);
    }
}
