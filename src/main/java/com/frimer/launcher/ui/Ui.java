package com.frimer.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Мелкие визуальные помощники: кнопки, карточки, бейджи, прокрутка. */
public final class Ui {

    private Ui() {
    }

    /** Панель со скруглённым фоном и рамкой. */
    public static class Card extends JPanel {
        private Color fill;
        private Color stroke;
        private int radius = 10;

        public Card(Color fill, Color stroke) {
            this.fill = fill;
            this.stroke = stroke;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        public void setFill(Color fill) {
            this.fill = fill;
            repaint();
        }

        public void setStroke(Color stroke) {
            this.stroke = stroke;
            repaint();
        }

        public void setRadius(int radius) {
            this.radius = radius;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Скруглённая плоская кнопка с наведением. */
    public static class FlatButton extends JButton {
        private final Color base;
        private final Color hover;
        private final Color fg;
        private final Color stroke;
        private final int radius;

        public FlatButton(String text, Color base, Color hover, Color fg, Color stroke, int radius) {
            super(text);
            this.base = base;
            this.hover = hover;
            this.fg = fg;
            this.stroke = stroke;
            this.radius = radius;
            setForeground(fg);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 16, 8, 16));
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = getModel().isRollover() && isEnabled() ? hover : base;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            setForeground(isEnabled() ? fg : new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 110));
            super.paintComponent(g);
        }
    }

    public static JButton button(String text, Theme theme) {
        return new FlatButton(text, theme.card, theme.hover, theme.text, theme.border, 6);
    }

    public static JButton primaryButton(String text, Theme theme) {
        return new FlatButton(text, theme.accent, theme.accentLight, Theme.rgb("#241703"), null, 6);
    }

    public static JButton miniButton(String text, Theme theme) {
        JButton b = new FlatButton(text, new Color(0, 0, 0, 0), theme.hover, theme.muted, theme.border, 6);
        b.setBorder(new EmptyBorder(4, 10, 4, 10));
        b.setFont(Theme.uiFont(Font.PLAIN, 13));
        return b;
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    public static JLabel muted(String text, Theme theme) {
        return label(text, Theme.uiFont(Font.PLAIN, 12), theme.muted);
    }

    /** Однотонная картинка-бейдж с буквой (Vanilla/Fabric/...). */
    public static ImageIcon badge(String letter, Color bg, Color fg, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillRoundRect(0, 0, size - 1, size - 1, 8, 8);
        g.setColor(fg);
        g.setFont(Theme.titleFont(Font.BOLD, size * 0.42f));
        int width = g.getFontMetrics().stringWidth(letter);
        int ascent = g.getFontMetrics().getAscent();
        g.drawString(letter, (size - width) / 2f, (size + ascent) / 2f - 1);
        g.dispose();
        return new ImageIcon(img);
    }

    public static ImageIcon loaderBadge(String loader, Theme theme, int size) {
        return badge(theme.loaderLetter(loader), theme.loaderBg(loader), theme.loaderFg(loader), size);
    }

    public static ImageIcon imageIcon(byte[] data, int size) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) {
                return null;
            }
            double scale = Math.min((double) size / src.getWidth(), (double) size / src.getHeight());
            int w = Math.max(1, (int) (src.getWidth() * scale));
            int h = Math.max(1, (int) (src.getHeight() * scale));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            return new ImageIcon(out);
        } catch (IOException e) {
            return null;
        }
    }

    /** Панель лога: моноширинный текст, не редактируется, автопрокрутка. */
    public static JTextArea logArea(Theme theme) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        area.setFont(Theme.monoFont(Font.PLAIN, 12));
        area.setBackground(theme.bg);
        area.setForeground(theme.text);
        area.setCaretColor(theme.accent);
        area.setBorder(new EmptyBorder(8, 8, 8, 8));
        return area;
    }

    public static JScrollPane scroll(JComponent view, Theme theme) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createLineBorder(theme.border));
        pane.setBackground(theme.bg);
        pane.getViewport().setBackground(theme.bg);
        styleScrollBar(pane.getVerticalScrollBar(), theme);
        styleScrollBar(pane.getHorizontalScrollBar(), theme);
        return pane;
    }

    public static JScrollPane borderlessScroll(JComponent view, Theme theme) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(theme.bg);
        pane.getViewport().setBackground(theme.bg);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollBar(pane.getVerticalScrollBar(), theme);
        styleScrollBar(pane.getHorizontalScrollBar(), theme);
        return pane;
    }

    private static void styleScrollBar(JScrollBar bar, Theme theme) {
        bar.setBackground(theme.bg);
        bar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = theme.border;
                trackColor = theme.bg;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroButton();
            }

            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle rect) {
                if (rect.isEmpty()) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isThumbRollover() ? theme.muted : theme.border);
                g2.fillRoundRect(rect.x + 2, rect.y + 2, rect.width - 4, rect.height - 4, 6, 6);
                g2.dispose();
            }
        });
        bar.setPreferredSize(new Dimension(10, 10));
    }

    public static JPanel row(int gap) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBorder(new EmptyBorder(gap, 0, 0, 0));
        return p;
    }

    public static JPanel column(int gap) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(gap, 0, 0, 0));
        return p;
    }

    public static JLabel spacer(int width, int height) {
        JLabel l = new JLabel();
        l.setPreferredSize(new Dimension(width, height));
        l.setMinimumSize(new Dimension(width, height));
        return l;
    }

    public static void onHover(JComponent c, Runnable enter, Runnable exit) {
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (enter != null) {
                    enter.run();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (exit != null) {
                    exit.run();
                }
            }
        });
    }

    public static void openFolder(Path folder) {
        try {
            if (com.frimer.launcher.core.Dirs.isWindows()) {
                new ProcessBuilder("explorer", folder.toString()).start();
            } else if (com.frimer.launcher.core.Dirs.osName().equals("osx")) {
                new ProcessBuilder("open", folder.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", folder.toString()).start();
            }
        } catch (IOException ignored) {
            // не смогли открыть проводник
        }
    }

    public static JLabel centered(String text, Theme theme) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(Theme.uiFont(Font.PLAIN, 13));
        l.setForeground(theme.muted);
        return l;
    }

    /** Кнопка-вкладка: залитая плашка, когда активна. */
    public static class SegmentButton extends JButton {

        private static final long serialVersionUID = 1L;

        private final Theme theme;
        private boolean active;

        public SegmentButton(String text, Theme theme) {
            super(text);
            this.theme = theme;
            setFont(Theme.uiFont(Font.BOLD, 13));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 14, 7, 14));
        }

        public void setActive(boolean active) {
            this.active = active;
            setForeground(active ? theme.accent : theme.muted);
            repaint();
        }

        public boolean isActive() {
            return active;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover();
            g2.setColor(active ? theme.cardSel : (hover ? theme.hover : theme.sidebar));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setColor(active ? theme.accent : theme.border);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Тонкий индикатор прогресса: своя отрисовка вместо системного L&F. */
    public static class MiniBar extends JComponent {

        private static final long serialVersionUID = 1L;

        private Theme theme;
        private int value;
        private boolean indeterminate;
        private int sweep;
        private javax.swing.Timer timer;

        public MiniBar(Theme theme) {
            this.theme = theme;
            setOpaque(false);
            setPreferredSize(new Dimension(120, 6));
            setMinimumSize(new Dimension(40, 6));
        }

        public void setTheme(Theme theme) {
            this.theme = theme;
            repaint();
        }

        public void setValue(int value) {
            this.value = Math.max(0, Math.min(100, value));
            repaint();
        }

        public int getValue() {
            return value;
        }

        public void setIndeterminate(boolean indeterminate) {
            this.indeterminate = indeterminate;
            if (indeterminate) {
                if (timer == null) {
                    timer = new javax.swing.Timer(40, e -> {
                        sweep = (sweep + 4) % 120;
                        repaint();
                    });
                }
                timer.start();
            } else {
                if (timer != null) {
                    timer.stop();
                }
                sweep = 0;
            }
            repaint();
        }

        public boolean isIndeterminate() {
            return indeterminate;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = Math.max(4, getHeight());
            int y = (getHeight() - h) / 2;
            g2.setColor(theme.border);
            g2.fillRoundRect(0, y, w, h, h, h);
            g2.setColor(theme.accent);
            if (indeterminate) {
                int blockWidth = Math.max(24, w / 4);
                int x = (int) ((w + blockWidth) * (sweep / 120.0)) - blockWidth;
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, y, w, h, h, h));
                g2.fillRoundRect(x, y, blockWidth, h, h, h);
                g2.setClip(null);
            } else if (value > 0) {
                int fill = Math.max(h, (int) (w * (value / 100.0)));
                g2.fillRoundRect(0, y, fill, h, h, h);
            }
            g2.dispose();
        }
    }

    /** Треугольник-шеврон (замена глифу ▾, которого нет в некоторых шрифтах). */
    public static ImageIcon chevron(Theme theme, boolean up) {
        int w = 10;
        int h = 6;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(theme.muted);
        java.awt.Polygon p = up
                ? new java.awt.Polygon(new int[]{0, w / 2, w}, new int[]{h - 1, 0, h - 1}, 3)
                : new java.awt.Polygon(new int[]{0, w / 2, w}, new int[]{0, h - 1, 0}, 3);
        g.fillPolygon(p);
        g.dispose();
        return new ImageIcon(img);
    }

    /** Зафиксировать высоту компонента в BoxLayout (иначе он растягивается). */
    public static <T extends JComponent> T compact(T component, int height) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setMinimumSize(new Dimension(0, height));
        component.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return component;
    }

    /**
     * Разложить содержимое колонки по левому краю и на всю ширину.
     * Без этого BoxLayout центрирует поля и они «висят» посередине диалога.
     */
    public static void alignLeft(JPanel root) {
        for (java.awt.Component c : root.getComponents()) {
            if (!(c instanceof JComponent jc)) {
                continue;
            }
            jc.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            if (c instanceof javax.swing.Box.Filler) {
                continue; // распорки трогать нельзя
            }
            int height = jc.getPreferredSize().height;
            if (height <= 0) {
                height = 30;
            }
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        }
    }

    /** Слайдер в цветах темы. */
    public static javax.swing.JSlider slider(Theme theme, int min, int max, int value) {
        javax.swing.JSlider slider = new javax.swing.JSlider(min, max, value);
        slider.setOpaque(false);
        slider.setForeground(theme.accent);
        slider.setBackground(theme.bg);
        slider.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return slider;
    }

    /** Ряд кнопок, который переносится на новую строку при нехватке ширины. */
    public static JPanel buttonRow(JComponent... buttons) {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        for (JComponent b : buttons) {
            row.add(b);
        }
        return row;
    }
}
