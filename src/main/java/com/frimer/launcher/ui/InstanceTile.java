package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** Плитка сборки в списке: бейдж загрузчика, название, версия, моды. */
public final class InstanceTile extends Ui.Card {

    private static final long serialVersionUID = 1L;

    /** Куда сообщать о действиях пользователя. */
    public interface Handler {
        void onSelect(Instance instance);

        void onActivate(Instance instance);

        void onMenu(Instance instance, java.awt.Point point);
    }

    public static final Dimension SIZE = new Dimension(268, 66);

    private final Instance instance;
    private final Theme theme;
    private final JLabel nameLabel;
    private boolean selected;

    public InstanceTile(Instance instance, Theme theme, Handler handler) {
        super(theme.card, theme.border);
        this.instance = instance;
        this.theme = theme;
        setRadius(8);
        setLayout(new BorderLayout(12, 0));
        setBorder(new EmptyBorder(8, 10, 8, 10));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(SIZE);
        setMinimumSize(SIZE);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, SIZE.height));

        JLabel icon = new JLabel(iconFor(instance, theme));
        icon.setPreferredSize(new Dimension(44, 44));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        add(icon, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        text.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        nameLabel = new JLabel(instance.name);
        nameLabel.setFont(Theme.titleFont(Font.BOLD, 14));
        nameLabel.setForeground(theme.text);
        nameLabel.setAlignmentX(0f);

        int mods = Utils.countMods(instance.folder);
        String sub = instance.loader + " " + instance.mcVersion;
        if (mods > 0) {
            sub += " · " + mods + " мод" + plural(mods);
        }
        JLabel subLabel = new JLabel(sub);
        subLabel.setFont(Theme.uiFont(Font.PLAIN, 11));
        subLabel.setForeground(theme.muted);
        subLabel.setAlignmentX(0f);

        text.add(nameLabel);
        text.add(javax.swing.Box.createVerticalStrut(3));
        text.add(subLabel);
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
                    setFill(theme.hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!selected) {
                    setFill(theme.card);
                }
            }
        });
        Ui.onHover(this, null, null);
    }

    public Instance instance() {
        return instance;
    }

    public void setDisplayName(String name) {
        nameLabel.setText(name);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        setFill(selected ? theme.cardSel : theme.card);
        setStroke(selected ? theme.accent : theme.border);
        nameLabel.setForeground(selected ? theme.accent : theme.text);
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
        Path custom = instance.folder.resolve("icon.png");
        if (Files.isRegularFile(custom)) {
            try {
                Icon icon = Ui.imageIcon(Files.readAllBytes(custom), 44);
                if (icon != null) {
                    return icon;
                }
            } catch (java.io.IOException ignored) {
                // падаем в бейдж загрузчика
            }
        }
        return Ui.loaderBadge(instance.loader, theme, 44);
    }

    public static Color accentFor(Instance instance, Theme theme) {
        return theme.loaderBg(instance.loader);
    }
}
