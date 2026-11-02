package com.frimer.launcher.core;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Рисование превью скина (вид спереди) из PNG 64x32 / 64x64. */
public final class SkinRenderer {

    private SkinRenderer() {
    }

    public static BufferedImage render(byte[] skin, int size) {
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(skin));
        } catch (IOException e) {
            return null;
        }
        if (src == null || src.getWidth() < 64) {
            return null;
        }
        boolean legacy = src.getHeight() <= 32;
        int scale = Math.max(1, size / 32);

        BufferedImage canvas = new BufferedImage(16 * scale, 32 * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // голова, тело, руки, ноги
        draw(g, src, 8, 8, 8, 8, 4 * scale, 0, 8 * scale, 8 * scale);
        draw(g, src, 20, 20, 8, 12, 4 * scale, 8 * scale, 8 * scale, 12 * scale);
        draw(g, src, 44, 20, 4, 12, 0, 8 * scale, 4 * scale, 12 * scale);
        draw(g, src, 4, 20, 4, 12, 4 * scale, 20 * scale, 4 * scale, 12 * scale);
        if (legacy) {
            drawMirrored(g, src, 44, 20, 4, 12, 12 * scale, 8 * scale, 4 * scale, 12 * scale);
            drawMirrored(g, src, 4, 20, 4, 12, 8 * scale, 20 * scale, 4 * scale, 12 * scale);
        } else {
            draw(g, src, 36, 52, 4, 12, 12 * scale, 8 * scale, 4 * scale, 12 * scale);
            draw(g, src, 20, 52, 4, 12, 8 * scale, 20 * scale, 4 * scale, 12 * scale);
            // оверлеи (шапка, куртка, штаны) для формата 64x64
            draw(g, src, 40, 8, 8, 8, 4 * scale, 0, 8 * scale, 8 * scale);
            draw(g, src, 20, 36, 8, 12, 4 * scale, 8 * scale, 8 * scale, 12 * scale);
            draw(g, src, 52, 52, 4, 12, 0, 8 * scale, 4 * scale, 12 * scale);
            draw(g, src, 44, 36, 4, 12, 12 * scale, 8 * scale, 4 * scale, 12 * scale);
            draw(g, src, 4, 52, 4, 12, 4 * scale, 20 * scale, 4 * scale, 12 * scale);
            draw(g, src, 4, 36, 4, 12, 8 * scale, 20 * scale, 4 * scale, 12 * scale);
        }
        g.dispose();
        return canvas;
    }

    private static void draw(Graphics2D g, BufferedImage src, int sx, int sy, int sw, int sh,
                             int dx, int dy, int dw, int dh) {
        if (sx + sw > src.getWidth() || sy + sh > src.getHeight()) {
            return;
        }
        BufferedImage part = src.getSubimage(sx, sy, sw, sh);
        g.drawImage(part, dx, dy, dw, dh, null);
    }

    private static void drawMirrored(Graphics2D g, BufferedImage src, int sx, int sy, int sw, int sh,
                                     int dx, int dy, int dw, int dh) {
        if (sx + sw > src.getWidth() || sy + sh > src.getHeight()) {
            return;
        }
        BufferedImage part = src.getSubimage(sx, sy, sw, sh);
        g.drawImage(part, dx + dw, dy, -dw, dh, null);
    }

    /** Плоская картинка скина для списка (масштабированная копия). */
    public static BufferedImage flat(byte[] skin, int size) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(skin));
            if (src == null) {
                return null;
            }
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(src, 0, 0, size, size, null);
            g.dispose();
            return out;
        } catch (IOException e) {
            return null;
        }
    }
}
