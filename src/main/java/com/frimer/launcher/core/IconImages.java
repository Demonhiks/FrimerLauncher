package com.frimer.launcher.core;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * Загрузка иконок из сети.
 *
 * <p>Modrinth отдаёт часть картинок в WebP, а стандартный ImageIO его не читает — такие
 * файлы конвертируем в PNG через бесплатный прокси и кэшируем результат.
 */
public final class IconImages {

    private IconImages() {
    }

    /** Скачать картинку, при необходимости приведя её к формату, который понимает ImageIO. */
    public static byte[] fetch(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        byte[] raw = Downloader.fetchBytes(url);
        if (readable(raw)) {
            return raw;
        }
        byte[] converted = Downloader.fetchBytes(proxy(url));
        if (readable(converted)) {
            return converted;
        }
        return raw;
    }

    /** Понятен ли формат картинки текущей JVM. */
    public static boolean readable(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            BufferedImage image = ImageIO.read(in);
            return image != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Адрес прокси, отдающего PNG. */
    static String proxy(String url) {
        String encoded = URLEncoder.encode(url, StandardCharsets.UTF_8);
        return "https://wsrv.nl/?url=" + encoded + "&output=png&w=128&h=128&fit=cover";
    }
}
