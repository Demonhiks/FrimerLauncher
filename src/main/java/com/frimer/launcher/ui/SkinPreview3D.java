package com.frimer.launcher.ui;

import com.frimer.launcher.core.SkinRenderer3D;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/** Живое 3D-превью скина: крутится мышью, кэширует кадр по размеру и углу. */
public final class SkinPreview3D extends JComponent {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private byte[] skin;
    private double yaw = -28;
    private BufferedImage frame;
    private double frameYaw = Double.NaN;
    private byte[] frameSkin;
    private int dragX = -1;

    public SkinPreview3D(Theme theme) {
        this.theme = theme;
        setOpaque(false);
        setPreferredSize(new Dimension(220, 300));
        setToolTipText("Потяните мышью, чтобы повернуть");
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragX = e.getX();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragX = -1;
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragX < 0) {
                    return;
                }
                yaw += (e.getX() - dragX) * 0.9;
                dragX = e.getX();
                frame = null;
                repaint();
            }
        });
    }

    public void setSkin(byte[] data) {
        this.skin = data;
        this.frame = null;
        this.yaw = -28;
        repaint();
    }

    public double yaw() {
        return yaw;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(theme.input);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

        if (skin == null) {
            g2.setColor(theme.muted);
            g2.setFont(Theme.uiFont(java.awt.Font.PLAIN, 12));
            g2.drawString("выбери скин", 16, 26);
            g2.dispose();
            return;
        }

        if (frame == null || frameYaw != yaw || frameSkin != skin
                || frame.getWidth() != getWidth() || frame.getHeight() != getHeight()) {
            frame = SkinRenderer3D.render(skin, Math.max(1, getWidth()),
                    Math.max(1, getHeight()), yaw);
            frameYaw = yaw;
            frameSkin = skin;
        }
        if (frame != null) {
            g2.drawImage(frame, 0, 0, null);
        }
        g2.dispose();
    }
}
