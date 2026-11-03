package com.frimer.launcher.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Псевдо-3D превью скина: кубы головы, тела, рук и ног проецируются с поворотом
 * вокруг вертикальной оси. Грани заливаются нужными участками текстуры,
 * рисуются от дальних к ближним и затеняются по нормали.
 */
public final class SkinRenderer3D {

    private SkinRenderer3D() {
    }

    /** Кусок модели: положение, размеры и UV каждой грани (перед, зад, право, лево, верх, низ). */
    private record Part(double x, double y, double z, double w, double h, double d,
                        double[][] uvs) {
    }

    /** Грань после проекции: участок текстуры, вершины на экране и глубина. */
    private record ProjectedFace(double[] uv, double[][] screen, double depth) {
    }

    private static final double OVERLAP = 0.35;

    /** Части стандартной модели 64x64 (порядок: голова, тело, руки, ноги). */
    private static List<Part> parts() {
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(0, 0, 0, 8, 8, 8, new double[][]{
                {8, 8, 8, 8}, {24, 8, 8, 8}, {0, 8, 8, 8}, {16, 8, 8, 8},
                {8, 0, 8, 8}, {16, 0, 8, 8}}));
        parts.add(new Part(0, 8, 0, 8, 12, 4, new double[][]{
                {20, 20, 8, 12}, {32, 20, 8, 12}, {16, 20, 4, 12}, {28, 20, 4, 12},
                {20, 16, 8, 4}, {28, 16, 8, 4}}));
        parts.add(new Part(-4, 8, 0, 4, 12, 4, new double[][]{
                {44, 20, 4, 12}, {52, 20, 4, 12}, {40, 20, 4, 12}, {48, 20, 4, 12},
                {44, 16, 4, 4}, {48, 16, 4, 4}}));
        parts.add(new Part(8, 8, 0, 4, 12, 4, new double[][]{
                {36, 52, 4, 12}, {44, 52, 4, 12}, {32, 52, 4, 12}, {40, 52, 4, 12},
                {36, 48, 4, 4}, {40, 48, 4, 4}}));
        parts.add(new Part(0, 20, 0, 4, 12, 4, new double[][]{
                {4, 20, 4, 12}, {12, 20, 4, 12}, {0, 20, 4, 12}, {8, 20, 4, 12},
                {4, 16, 4, 4}, {8, 16, 4, 4}}));
        parts.add(new Part(4, 20, 0, 4, 12, 4, new double[][]{
                {20, 52, 4, 12}, {28, 52, 4, 12}, {16, 52, 4, 12}, {24, 52, 4, 12},
                {20, 48, 4, 4}, {24, 48, 4, 4}}));
        return parts;
    }

    /** Отрисовать скин: yaw — поворот вокруг вертикали в градусах. */
    public static BufferedImage render(byte[] skinData, int width, int height, double yaw) {
        BufferedImage texture = decode(skinData);
        if (texture == null) {
            return null;
        }
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        // 1) проецируем всё без масштаба, чтобы посчитать габариты
        List<ProjectedFace> faces = new ArrayList<>();
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minDepth = Double.MAX_VALUE;
        double maxDepth = -Double.MAX_VALUE;
        for (Part part : parts()) {
            for (int f = 0; f < 6; f++) {
                double[][] corners = corners(part, f);
                double[][] rotated = new double[4][];
                double depth = 0;
                for (int i = 0; i < 4; i++) {
                    double dx = corners[i][0] - 4;
                    double dy = corners[i][1] - 16;
                    double dz = corners[i][2] - 2;
                    double rx = dx * cos - dz * sin;
                    // смотрим с -Z: ближе тот, у кого больше depth
                    double viewDepth = -(dx * sin + dz * cos);
                    rotated[i] = new double[]{rx, dy - viewDepth * OVERLAP};
                    depth += viewDepth;
                    minX = Math.min(minX, rotated[i][0]);
                    maxX = Math.max(maxX, rotated[i][0]);
                    minY = Math.min(minY, rotated[i][1]);
                    maxY = Math.max(maxY, rotated[i][1]);
                    minDepth = Math.min(minDepth, viewDepth);
                    maxDepth = Math.max(maxDepth, viewDepth);
                }
                faces.add(new ProjectedFace(part.uvs()[f], rotated, depth / 4.0));
            }
        }
        if (faces.isEmpty()) {
            g.dispose();
            return out;
        }

        // 2) масштаб и центрирование по габаритам
        double spanX = Math.max(1e-3, maxX - minX);
        double spanY = Math.max(1e-3, maxY - minY);
        double scale = Math.min((w - 16) / spanX, (h - 16) / spanY);
        double offsetX = w / 2.0 - (minX + maxX) / 2.0 * scale;
        double offsetY = h / 2.0 - (minY + maxY) / 2.0 * scale;

        // 3) дальние грани раньше ближних
        faces.sort((a, b) -> Double.compare(a.depth(), b.depth()));
        for (ProjectedFace face : faces) {
            double[][] screen = new double[4][2];
            for (int i = 0; i < 4; i++) {
                screen[i][0] = offsetX + face.screen()[i][0] * scale;
                screen[i][1] = offsetY + face.screen()[i][1] * scale;
            }
            Path2D path = new Path2D.Double();
            path.moveTo(screen[0][0], screen[0][1]);
            for (int i = 1; i < 4; i++) {
                path.lineTo(screen[i][0], screen[i][1]);
            }
            path.closePath();

            double u0 = face.uv()[0];
            double v0 = face.uv()[1];
            double uw = face.uv()[2];
            double vh = face.uv()[3];
            double ux = (screen[1][0] - screen[0][0]) / uw;
            double uy = (screen[1][1] - screen[0][1]) / uw;
            double vx = (screen[3][0] - screen[0][0]) / vh;
            double vy = (screen[3][1] - screen[0][1]) / vh;
            AffineTransform transform = new AffineTransform(ux, uy, vx, vy,
                    screen[0][0] - u0 * ux - v0 * vx,
                    screen[0][1] - u0 * uy - v0 * vy);

            java.awt.Shape oldClip = g.getClip();
            g.setClip(path);
            g.drawImage(texture, transform, null);
            double shade = shade(face.depth(), minDepth, maxDepth);
            if (shade > 8) {
                g.setColor(new Color(0, 0, 0, (int) Math.min(120, shade)));
                g.fill(path);
            }
            g.setClip(oldClip);
        }
        g.dispose();
        return out;
    }

    /** Затемнение по глубине: дальние части чуть темнее, ближние светлее. */
    private static double shade(double depth, double minDepth, double maxDepth) {
        double span = Math.max(1e-3, maxDepth - minDepth);
        double t = (depth - minDepth) / span;
        return 55 - 45 * Math.min(1, Math.max(0, t));
    }

    /** Углы грани f для части: 0 перед, 1 зад, 2 право, 3 лево, 4 верх, 5 низ. */
    private static double[][] corners(Part part, int face) {
        double x = part.x();
        double y = part.y();
        double z = part.z();
        double w = part.w();
        double h = part.h();
        double d = part.d();
        return switch (face) {
            case 0 -> new double[][]{{x, y, z}, {x + w, y, z}, {x + w, y + h, z}, {x, y + h, z}};
            case 1 -> new double[][]{{x + w, y, z + d}, {x, y, z + d}, {x, y + h, z + d},
                    {x + w, y + h, z + d}};
            case 2 -> new double[][]{{x + w, y, z + d}, {x + w, y, z}, {x + w, y + h, z},
                    {x + w, y + h, z + d}};
            case 3 -> new double[][]{{x, y, z}, {x, y, z + d}, {x, y + h, z + d}, {x, y + h, z}};
            case 4 -> new double[][]{{x, y, z}, {x + w, y, z}, {x + w, y, z + d}, {x, y, z + d}};
            default -> new double[][]{{x, y + h, z + d}, {x + w, y + h, z + d}, {x + w, y + h, z},
                    {x, y + h, z}};
        };
    }

    /** Декодировать PNG скина; поддержать 64x32 (legacy) растяжением до 64x64. */
    private static BufferedImage decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null || image.getWidth() < 64) {
                return null;
            }
            if (image.getHeight() >= 64) {
                return image;
            }
            BufferedImage scaled = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, 64, 64, null);
            g.dispose();
            return scaled;
        } catch (IOException e) {
            return null;
        }
    }
}
