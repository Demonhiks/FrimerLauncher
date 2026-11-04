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
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** Плитка сборки — маленький квадрат с иконкой загрузчика и названием. */
public final class InstanceTile extends Ui.Card {

    private static final long serialVersionUID = 1L;

    /** Куда сообщать о действиях пользователя. */
    public interface Handler {
        void onSelect(Instance instance);

        void onActivate(Instance instance);

        void onMenu(Instance instance, java.awt.Point point);
    }

    /** Сторона квадрата: плитки встают в сетку как в классическом лаунчере. */
    public static final int SIDE = 116;
    public static final Dimension SIZE = new Dimension(SIDE, SIDE);

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
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 6, 8, 6));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(SIZE);
        setMinimumSize(SIZE);
        setMaximumSize(SIZE);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new javax.swing.BoxLayout(column, javax.swing.BoxLayout.Y_AXIS));

        JLabel icon = new JLabel(iconFor(instance, theme, 46));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setHorizontalAlignment(SwingConstants.CENTER);

        nameLabel = new JLabel(shortName(instance.name));
        nameLabel.setFont(Theme.uiFont(Font.BOLD, 12));
        nameLabel.setForeground(theme.text);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);

        int mods = Utils.countMods(instance.folder);
        subLabel = new JLabel(instance.loader + " " + instance.mcVersion
                + (mods > 0 ? " · " + mods : ""));
        subLabel.setFont(Theme.uiFont(Font.PLAIN, 10));
        subLabel.setForeground(theme.muted);
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subLabel.setHorizontalAlignment(SwingConstants.CENTER);

        column.add(Box.createVerticalGlue());
        column.add(icon);
        column.add(Box.createVerticalStrut(6));
        column.add(nameLabel);
        column.add(Box.createVerticalStrut(2));
        column.add(subLabel);
        column.add(Box.createVerticalGlue());
        add(column, BorderLayout.CENTER);

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
        g2.fillOval(getWidth() - 18, 10, 8, 8);
        g2.setColor(theme.bg);
        g2.drawOval(getWidth() - 18, 10, 8, 8);
        g2.dispose();
    }

    public void setDisplayName(String name) {
        nameLabel.setText(shortName(name));
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

    /** Длинные названия режем, чтобы влезли в квадрат. */
    private static String shortName(String name) {
        if (name == null) {
            return "";
        }
        return name.length() <= 15 ? name : name.substring(0, 14) + "…";
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
