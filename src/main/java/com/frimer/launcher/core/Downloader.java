package com.frimer.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.function.Consumer;

/** Скачивание файлов с докачкой (.part), проверкой SHA-1/SHA-512 и прогрессом. */
public final class Downloader {

    private Downloader() {
    }

    public static Path download(String url, Path dest) {
        return download(url, dest, null, null, null, null);
    }

    /**
     * @param sha1     ожидаемый SHA-1 (или null)
     * @param sha512   ожидаемый SHA-512 (или null)
     * @param log      куда писать сообщения (может быть null)
     * @param progress колбэк прогресса в байтах (может быть null)
     */
    public static Path download(String url, Path dest, String sha1, String sha512,
                                Consumer<String> log, ProgressListener progress) {
        Consumer<String> logger = log == null ? msg -> {
        } : log;
        ProgressListener prog = progress == null ? ProgressListener.NONE : progress;

        try {
            if (dest.getParent() != null) {
                Files.createDirectories(dest.getParent());
            }
            if (Files.exists(dest)) {
                if (sha1 != null && sha1.equalsIgnoreCase(Utils.sha1(dest))) {
                    return dest;
                }
                if (sha512 != null && sha512.equalsIgnoreCase(Utils.sha512(dest))) {
                    return dest;
                }
                if (sha1 == null && sha512 == null) {
                    return dest;
                }
            }

            Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
            long have = Files.exists(tmp) ? Files.size(tmp) : 0;

            HttpURLConnection conn = open(url, have > 0 ? have : -1);
            int code = conn.getResponseCode();
            if (code == 416) {
                conn.disconnect();
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                if (code < 200 || code >= 300) {
                    conn.disconnect();
                    throw new RuntimeException("Скачивание не удалось: " + url + " (HTTP " + code + ")");
                }
                boolean append = code == 206 && have > 0;
                long start = append ? have : 0;
                long length = parseLong(conn.getHeaderField("Content-Length"));
                long total = length > 0 ? start + length : 0;
                try (InputStream in = conn.getInputStream();
                     OutputStream out = Files.newOutputStream(tmp,
                             append ? java.nio.file.StandardOpenOption.APPEND
                                     : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                             java.nio.file.StandardOpenOption.CREATE)) {
                    byte[] buf = new byte[1 << 16];
                    long done = start;
                    int read;
                    while ((read = in.read(buf)) > 0) {
                        out.write(buf, 0, read);
                        done += read;
                        if (total > 0) {
                            prog.update(done, total);
                        }
                    }
                } finally {
                    conn.disconnect();
                }
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
            }

            if (sha1 != null && !sha1.equalsIgnoreCase(Utils.sha1(dest))) {
                Files.deleteIfExists(dest);
                throw new RuntimeException("SHA1 не совпал: " + dest.getFileName());
            }
            if (sha512 != null && !sha512.equalsIgnoreCase(Utils.sha512(dest))) {
                Files.deleteIfExists(dest);
                throw new RuntimeException("SHA512 не совпал: " + dest.getFileName());
            }
            logger.accept("Скачано: " + dest.getFileName());
            return dest;
        } catch (IOException e) {
            throw new RuntimeException("Скачивание не удалось: " + url + " (" + e.getMessage() + ")", e);
        }
    }

    private static HttpURLConnection open(String url, long rangeFrom) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", Http.UA);
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (rangeFrom > 0) {
            conn.setRequestProperty("Range", "bytes=" + rangeFrom + "-");
        }
        return conn;
    }

    private static long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Скачать в память (для иконок модов и картинок). */
    public static byte[] fetchBytes(String url) {
        try {
            return Http.getBytes(url);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String humanRate(long bytesPerSec) {
        return Utils.fmtSize(bytesPerSec) + "/s";
    }

    public static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
