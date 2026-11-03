package com.frimer.launcher.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
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
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
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

    /** Панель со скруглённым фоном, рамкой, тенью и градиентом. */
    public static class Card extends JPanel {
        private Color fill;
        private Color stroke;
        private Color gradientTop;
        private Color gradientBottom;
        private Color shadowColor;
        private int radius = Theme.RADIUS_CARD;

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

        /** Вертикальный градиент вместо однотонной заливки. */
        public void setGradient(Color top, Color bottom) {
            this.gradientTop = top;
            this.gradientBottom = bottom;
            repaint();
        }

        /** Мягкая тень под карточкой (цвет с альфой). */
        public void setShadow(Color shadow) {
            this.shadowColor = shadow;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (shadowColor != null) {
                for (int i = 4; i >= 1; i--) {
                    int alpha = Math.max(4, shadowColor.getAlpha() / (i * 3));
                    g2.setColor(new Color(shadowColor.getRed(), shadowColor.getGreen(),
                            shadowColor.getBlue(), alpha));
                    g2.fillRoundRect(i, i + 2, getWidth() - i * 2 - 1, getHeight() - i * 2 - 1,
                            radius + 4, radius + 4);
                }
            }
            if (gradientTop != null && gradientBottom != null) {
                g2.setPaint(new java.awt.GradientPaint(0, 0, gradientTop, 0, getHeight(),
                        gradientBottom));
            } else {
                g2.setColor(fill);
            }
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

    /** Панель с вертикальным градиентом (сайдбар). */
    public static class GradientPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final Color top;
        private final Color bottom;
        private Color lineColor;

        public GradientPanel(Color top, Color bottom) {
            this.top = top;
            this.bottom = bottom;
            setOpaque(false);
        }

        /** Тонкая линия-разделитель по правому краю. */
        public void setEdgeLine(Color color) {
            this.lineColor = color;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new java.awt.GradientPaint(0, 0, top, 0, getHeight(), bottom));
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (lineColor != null) {
                g2.setColor(lineColor);
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Пилюля с подписью: память, моды, версия загрузчика. */
    public static class Chip extends JLabel {
        private static final long serialVersionUID = 1L;

        private final Color background;
        private final Color border;

        public Chip(String text, Theme theme, boolean accent) {
            super(text);
            this.background = accent ? theme.cardSel : theme.hover;
            this.border = accent ? theme.accent : theme.border;
            setOpaque(false);
            setFont(Theme.uiFont(Font.PLAIN, 11));
            setForeground(accent ? theme.accent : theme.muted);
            setBorder(new EmptyBorder(3, 9, 3, 9));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int radius = Math.min(getHeight() - 1, 16);
            g2.setColor(background);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static Chip chip(String text, Theme theme) {
        return new Chip(text, theme, false);
    }

    public static Chip accentChip(String text, Theme theme) {
        return new Chip(text, theme, true);
    }

    /** Заголовок секции: капс, разрядка, приглушённый цвет. */
    public static JLabel sectionLabel(String text, Theme theme) {
        JLabel label = new JLabel(text.toUpperCase(java.util.Locale.ROOT));
        java.util.Map<java.awt.font.TextAttribute, Object> attrs = java.util.Map.of(
                java.awt.font.TextAttribute.TRACKING, 0.16f);
        label.setFont(Theme.uiFont(Font.BOLD, 10).deriveFont(attrs));
        label.setForeground(theme.muted);
        return label;
    }

    /** Логотип: акцентный скруглённый квадрат с буквой. */
    public static ImageIcon logoMark(Theme theme, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new java.awt.GradientPaint(0, 0, theme.accentTop(), 0, size,
                theme.accentBottom()));
        g.fillRoundRect(0, 0, size - 1, size - 1, size / 3, size / 3);
        g.setColor(new Color(36, 23, 3, 230));
        g.setFont(Theme.titleFont(Font.BOLD, size * 0.58f));
        String letter = "F";
        int width = g.getFontMetrics().stringWidth(letter);
        g.drawString(letter, (size - width) / 2f, size * 0.73f);
        g.dispose();
        return new ImageIcon(img);
    }

    /** Цвет мягкой тени для карточек. */
    public static Color shadow(Theme theme) {
        return new Color(0, 0, 0, theme.shadowAlpha());
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
        return new FlatButton(text, theme.card, theme.hover, theme.text, theme.border, Theme.RADIUS_BUTTON);
    }

    public static JButton primaryButton(String text, Theme theme) {
        return new FlatButton(text, theme.accentTop(), theme.accentLight, Theme.rgb("#241703"), null, Theme.RADIUS_BUTTON);
    }

    public static JButton miniButton(String text, Theme theme) {
        JButton b = new FlatButton(text, new Color(0, 0, 0, 0), theme.hover, theme.muted, theme.border, Theme.RADIUS_BUTTON);
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
            g2.setPaint(new java.awt.GradientPaint(0, 0, theme.accentTop(), Math.max(1, w), 0,
                    theme.accentBottom()));
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
        styleSlider(slider, theme);
        return slider;
    }

    /** Перекрасить существующий слайдер (его нельзя создать с темой в поле класса). */
    public static void styleSlider(javax.swing.JSlider slider, Theme theme) {
        slider.setOpaque(false);
        slider.setUI(new ThemedSliderUI(slider, theme));
        slider.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
    }

    /** Слайдер со своей отрисовкой: иначе L&F рисует светлую дорожку и белый бегунок. */
    private static final class ThemedSliderUI extends javax.swing.plaf.basic.BasicSliderUI {

        private final Theme theme;

        ThemedSliderUI(javax.swing.JSlider slider, Theme theme) {
            super(slider);
            this.theme = theme;
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(12, 18);
        }

        @Override
        public void paintFocus(Graphics g) {
            // фокусную рамку не рисуем
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = trackRect.y + trackRect.height / 2 - 2;
            g2.setColor(theme.border);
            g2.fillRoundRect(trackRect.x, y, trackRect.width, 4, 4, 4);
            int filled = thumbRect.x + thumbRect.width / 2 - trackRect.x;
            if (filled > 0) {
                g2.setColor(theme.accent);
                g2.fillRoundRect(trackRect.x, y, Math.min(filled, trackRect.width), 4, 4, 4);
            }
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int height = Math.max(10, thumbRect.height - 4);
            int y = thumbRect.y + (thumbRect.height - height) / 2;
            g2.setColor(theme.accentLight);
            g2.fillRoundRect(thumbRect.x, y, thumbRect.width, height, 5, 5);
            g2.setColor(theme.accent);
            g2.drawRoundRect(thumbRect.x, y, thumbRect.width - 1, height - 1, 5, 5);
            g2.dispose();
        }
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

    /** Поле ввода со скруглённой рамкой в цветах темы. */
    public static class Field extends javax.swing.JTextField {
        private static final long serialVersionUID = 1L;

        private final Theme theme;

        public Field(Theme theme, String text) {
            super(text);
            this.theme = theme;
            setOpaque(false);
            setBorder(new EmptyBorder(7, 10, 7, 10));
            setForeground(theme.text);
            setCaretColor(theme.accent);
            setFont(Theme.uiFont(Font.PLAIN, 14));
            setSelectionColor(theme.cardSel);
            setSelectedTextColor(theme.accent);
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(theme.input);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(isFocusOwner() ? theme.accent : theme.border);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Кнопка-иконка без рамки: при наведении подложка, опционально точка-бейдж. */
    public static class IconButton extends JButton {

        private static final long serialVersionUID = 1L;

        private final Theme theme;
        private final Icon icon;
        private boolean badge;

        public IconButton(Theme theme, Icon icon) {
            this.theme = theme;
            this.icon = icon;
            setPreferredSize(new Dimension(28, 28));
            setMinimumSize(new Dimension(28, 28));
            setMaximumSize(new Dimension(28, 28));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        /** Показать акцентную точку (есть что-то новое). */
        public void setBadge(boolean badge) {
            this.badge = badge;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isRollover()) {
                g2.setColor(theme.hover);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
            if (icon != null) {
                icon.paintIcon(this, g2, (getWidth() - icon.getIconWidth()) / 2,
                        (getHeight() - icon.getIconHeight()) / 2);
            }
            if (badge) {
                g2.setColor(theme.accent);
                g2.fillOval(getWidth() - 8, 3, 6, 6);
                g2.setColor(theme.bg);
                g2.drawOval(getWidth() - 8, 3, 6, 6);
            }
            g2.dispose();
        }
    }

    /** Крестик закрытия. */
    public static ImageIcon closeIcon(Theme theme, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(theme.muted);
        g.setStroke(new BasicStroke(Math.max(1.4f, size / 10f), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        int pad = Math.max(3, size / 4);
        g.drawLine(pad, pad, size - pad, size - pad);
        g.drawLine(size - pad, pad, pad, size - pad);
        g.dispose();
        return new ImageIcon(img);
    }

    /** Иконка «что нового»: четырёхлучевая звёздочка. */
    public static ImageIcon sparkleIcon(Theme theme, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(theme.accent);
        double cx = size / 2.0;
        double cy = size / 2.0;
        double outer = size / 2.0 - 0.5;
        double inner = outer * 0.34;
        java.awt.geom.Path2D star = new java.awt.geom.Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double r = i % 2 == 0 ? outer : inner;
            double angle = Math.PI / 4 * i - Math.PI / 2;
            double x = cx + r * Math.cos(angle);
            double y = cy + r * Math.sin(angle);
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        g.fill(star);
        g.dispose();
        return new ImageIcon(img);
    }

    /**
     * Безрамочное окно с тёмной шапкой: системный заголовок Windows светлый
     * и выглядел белой полосой над тёмным содержимым.
     */
    public static class FramelessDialog extends javax.swing.JDialog {

        private static final long serialVersionUID = 1L;

        private final Theme theme;
        private final JPanel body;
        private final JPanel headerPanel;
        private java.awt.Point dragOffset;

        public FramelessDialog(java.awt.Window owner, String title, boolean modal, Theme theme) {
            super(owner, title, modal
                    ? java.awt.Dialog.ModalityType.APPLICATION_MODAL
                    : java.awt.Dialog.ModalityType.MODELESS);
            this.theme = theme;
            setUndecorated(true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            JPanel rootPanel = new JPanel(new BorderLayout()) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(theme.bg);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(theme.border);
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            rootPanel.setOpaque(false);

            headerPanel = new JPanel(new BorderLayout());
            JPanel header = headerPanel;
            header.setBackground(theme.sidebar);
            header.setBorder(new EmptyBorder(2, 6, 2, 6));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(Theme.uiFont(Font.BOLD, 13));
            titleLabel.setForeground(theme.text);
            titleLabel.setBorder(new EmptyBorder(8, 8, 8, 8));
            header.add(titleLabel, BorderLayout.CENTER);

            IconButton close = new IconButton(theme, closeIcon(theme, 14));
            close.setToolTipText("Закрыть");
            close.addActionListener(e -> dispose());
            header.add(close, BorderLayout.EAST);

            MouseAdapter dragger = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragOffset = e.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragOffset == null) {
                        return;
                    }
                    java.awt.Point onScreen = e.getLocationOnScreen();
                    setLocation(onScreen.x - dragOffset.x, onScreen.y - dragOffset.y);
                }
            };
            header.addMouseListener(dragger);
            header.addMouseMotionListener(dragger);
            titleLabel.addMouseListener(dragger);
            titleLabel.addMouseMotionListener(dragger);

            body = new JPanel(new BorderLayout());
            body.setOpaque(false);

            rootPanel.add(header, BorderLayout.NORTH);
            rootPanel.add(body, BorderLayout.CENTER);
            super.setContentPane(rootPanel);

            rootPanel.registerKeyboardAction(e -> dispose(),
                    javax.swing.KeyStroke.getKeyStroke("ESCAPE"),
                    JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        public Theme theme() {
            return theme;
        }



        /** Содержимое окна под шапкой. */
        @Override
        public void setContentPane(java.awt.Container content) {
            if (body == null) {
                super.setContentPane(content);
                return;
            }
            body.removeAll();
            body.add(content, java.awt.BorderLayout.CENTER);
            body.revalidate();
            body.repaint();
        }
    }

    // ------------------------------------------------------------- выпадающие меню

    /**
     * Пункт меню. Своё меню на JWindow нужно потому, что JPopupMenu
     * рисуется оконным L&F: получается белый фон с нечитаемым светлым текстом.
     */
    public static final class MenuItemSpec {

        private final String text;
        private final Runnable action;
        private final boolean separator;
        private final boolean enabled;

        private MenuItemSpec(String text, Runnable action, boolean separator, boolean enabled) {
            this.text = text;
            this.action = action;
            this.separator = separator;
            this.enabled = enabled;
        }

        public static MenuItemSpec of(String text, Runnable action) {
            return new MenuItemSpec(text, action, false, true);
        }

        public static MenuItemSpec disabled(String text) {
            return new MenuItemSpec(text, null, false, false);
        }

        public static MenuItemSpec separator() {
            return new MenuItemSpec(null, null, true, false);
        }
    }

    private static javax.swing.JWindow openMenu;
    private static java.awt.event.AWTEventListener openMenuListener;

    /** Собрать панель меню (её же можно отрендерить в картинку для проверки вёрстки). */
    public static JPanel menuPanel(Theme theme, java.util.List<MenuItemSpec> items, Runnable onClose) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(theme.card);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border),
                new EmptyBorder(4, 4, 4, 4)));

        java.util.List<JComponent> rows = new java.util.ArrayList<>();
        int width = 150;
        for (MenuItemSpec spec : items) {
            if (spec.separator) {
                JPanel line = new JPanel();
                line.setBackground(theme.border);
                line.setPreferredSize(new Dimension(10, 1));
                line.setMinimumSize(new Dimension(10, 1));
                line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                JPanel wrap = new JPanel(new java.awt.BorderLayout());
                wrap.setOpaque(false);
                wrap.setBorder(new EmptyBorder(5, 6, 5, 6));
                wrap.add(line, java.awt.BorderLayout.CENTER);
                rows.add(wrap);
                continue;
            }

            JPanel row = new JPanel(new java.awt.BorderLayout());
            row.setOpaque(true);
            row.setBackground(theme.card);
            row.setBorder(new EmptyBorder(7, 12, 7, 12));
            JLabel label = new JLabel(spec.text);
            label.setFont(Theme.uiFont(Font.PLAIN, 13));
            label.setForeground(spec.enabled ? theme.text : theme.disabledText);
            row.add(label, java.awt.BorderLayout.CENTER);
            width = Math.max(width, label.getPreferredSize().width + 40);

            if (spec.enabled) {
                row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                row.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        row.setBackground(theme.cardSel);
                        row.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(theme.accent),
                                new EmptyBorder(6, 11, 6, 11)));
                        label.setForeground(theme.accent);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        row.setBackground(theme.card);
                        row.setBorder(new EmptyBorder(7, 12, 7, 12));
                        label.setForeground(theme.text);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (onClose != null) {
                            onClose.run();
                        }
                        if (spec.action != null) {
                            spec.action.run();
                        }
                    }
                });
            }
            rows.add(row);
        }

        for (JComponent row : rows) {
            int height = Math.max(18, row.getPreferredSize().height);
            row.setPreferredSize(new Dimension(width, height));
            row.setMinimumSize(new Dimension(width, height));
            row.setMaximumSize(new Dimension(width, height));
            row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            panel.add(row);
        }
        return panel;
    }

    /** Показать меню рядом с компонентом (x, y — смещение от его левого верхнего угла). */
    public static void showMenu(java.awt.Component anchor, int offsetX, int offsetY, Theme theme,
                                java.util.List<MenuItemSpec> items) {
        closeOpenMenu();
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(anchor);
        javax.swing.JWindow window = new javax.swing.JWindow(owner);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(true);

        Runnable close = Ui::closeOpenMenu;
        window.setContentPane(menuPanel(theme, items, close));
        window.pack();

        java.awt.Point base = anchor.getLocationOnScreen();
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int x = base.x + offsetX;
        int y = base.y + offsetY;
        if (x + window.getWidth() > screen.x + screen.width) {
            x = Math.max(screen.x + 4, screen.x + screen.width - window.getWidth() - 4);
        }
        if (y + window.getHeight() > screen.y + screen.height) {
            y = Math.max(screen.y + 4, base.y - window.getHeight());
        }
        window.setLocation(x, y);

        final long openedAt = System.currentTimeMillis();
        openMenuListener = event -> {
            if (!(event instanceof java.awt.event.MouseEvent press)) {
                return;
            }
            // только нажатие: отпускание той же кнопки, которая открыла меню,
            // прилетает уже после показа и не должно его закрывать
            if (press.getID() != java.awt.event.MouseEvent.MOUSE_PRESSED) {
                return;
            }
            if (System.currentTimeMillis() - openedAt < 200) {
                return;
            }
            java.awt.Component source = press.getComponent();
            if (source != null && javax.swing.SwingUtilities.getWindowAncestor(source) == window) {
                return;
            }
            closeOpenMenu();
        };
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(openMenuListener,
                java.awt.AWTEvent.MOUSE_EVENT_MASK);
        window.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                closeOpenMenu();
            }
        });
        window.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    closeOpenMenu();
                }
            }
        });

        openMenu = window;
        window.setVisible(true);
        window.toFront();
    }

    /** Закрыть открытое меню, если оно есть. */
    public static void closeOpenMenu() {
        if (openMenuListener != null) {
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(openMenuListener);
            openMenuListener = null;
        }
        if (openMenu != null) {
            openMenu.setVisible(false);
            openMenu.dispose();
            openMenu = null;
        }
    }

    /** Список JComboBox в цветах темы: иначе выпадающий список белый с нечитаемым текстом. */
    public static void styleCombo(JComboBox<String> combo, Theme theme) {
        combo.setBackground(theme.input);
        combo.setForeground(theme.text);
        combo.setFont(Theme.uiFont(Font.PLAIN, 14));
        combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                                                                   Object value, int index,
                                                                   boolean isSelected,
                                                                   boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                label.setOpaque(true);
                label.setBorder(new EmptyBorder(5, 8, 5, 8));
                if (isSelected && index >= 0) {
                    label.setBackground(theme.cardSel);
                    label.setForeground(theme.accent);
                } else {
                    label.setBackground(index < 0 ? theme.input : theme.card);
                    label.setForeground(theme.text);
                }
                return label;
            }
        });
    }
}
