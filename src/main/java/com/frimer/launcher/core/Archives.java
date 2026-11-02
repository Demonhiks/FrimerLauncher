package com.frimer.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Распаковка zip и tar.gz (для архивов Java от Adoptium). */
public final class Archives {

    private Archives() {
    }

    public static void extractZip(Path archive, Path target) throws IOException {
        Files.createDirectories(target);
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path dest = Utils.safeJoin(target, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                    continue;
                }
                Files.createDirectories(dest.getParent());
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                in.closeEntry();
            }
        }
    }

    /** Минимальный tar-распаковщик поверх gzip (ustar + pax + GNU long name). */
    public static void extractTarGz(Path archive, Path target) throws IOException {
        Files.createDirectories(target);
        try (InputStream raw = Files.newInputStream(archive);
             GZIPInputStream gz = new GZIPInputStream(raw, 1 << 16)) {
            byte[] header = new byte[512];
            String pendingName = null;
            while (true) {
                int read = readFully(gz, header, 512);
                if (read < 512) {
                    break;
                }
                if (isZeroBlock(header)) {
                    break; // конец архива
                }
                String name = cString(header, 0, 100);
                String prefix = cString(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                long size = parseOctal(header, 124, 12);
                char type = (char) header[156];

                if (type == 'L') { // GNU long name: имя лежит в данных
                    byte[] data = new byte[(int) Math.min(size, 1 << 20)];
                    readFully(gz, data, data.length);
                    pendingName = cString(data, 0, data.length);
                    skipPadding(gz, size);
                    continue;
                }
                if (type == 'x') { // pax extended header
                    byte[] data = new byte[(int) Math.min(size, 1 << 20)];
                    readFully(gz, data, data.length);
                    String paxName = paxPath(data);
                    if (paxName != null) {
                        pendingName = paxName;
                    }
                    skipPadding(gz, size);
                    continue;
                }

                if (pendingName != null) {
                    name = pendingName;
                    pendingName = null;
                }

                if (type == '5') { // каталог
                    Files.createDirectories(Utils.safeJoin(target, name));
                    skipPadding(gz, size);
                    continue;
                }
                if (type == '0' || type == '\0' || type == '7') {
                    Path dest = Utils.safeJoin(target, name);
                    Files.createDirectories(dest.getParent());
                    try (OutputStream out = Files.newOutputStream(dest)) {
                        copyExact(gz, out, size);
                    }
                    long pad = (512 - (size % 512)) % 512;
                    skipBytes(gz, pad);
                    // восстановим права исполнения, если они были
                    long mode = parseOctal(header, 100, 8);
                    if ((mode & 0b001_000_000) != 0) {
                        restrictPermissions(dest);
                    }
                    continue;
                }
                // прочие типы (симлинки и т.п.) пропускаем вместе с данными
                skipBytes(gz, size + (512 - (size % 512)) % 512);
            }
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            if (!Dirs.isWindows()) {
                Files.setPosixFilePermissions(file,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // не критично
        }
    }

    private static String paxPath(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        for (String record : text.split("\n")) {
            int space = record.indexOf(' ');
            if (space <= 0) {
                continue;
            }
            String kv = record.substring(space + 1);
            if (kv.startsWith("path=")) {
                return kv.substring(5);
            }
        }
        return null;
    }

    private static void copyExact(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buf = new byte[1 << 16];
        long left = size;
        while (left > 0) {
            int want = (int) Math.min(buf.length, left);
            int read = in.read(buf, 0, want);
            if (read <= 0) {
                throw new IOException("Неожиданный конец архива");
            }
            out.write(buf, 0, read);
            left -= read;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long pad = (512 - (size % 512)) % 512;
        skipBytes(in, pad);
    }

    private static void skipBytes(InputStream in, long count) throws IOException {
        long left = count;
        byte[] buf = new byte[8192];
        while (left > 0) {
            int read = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (read <= 0) {
                return;
            }
            left -= read;
        }
    }

    private static int readFully(InputStream in, byte[] buf, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int read = in.read(buf, total, len - total);
            if (read <= 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String cString(byte[] data, int offset, int length) {
        int end = offset;
        int limit = Math.min(data.length, offset + length);
        while (end < limit && data[end] != 0) {
            end++;
        }
        return new String(data, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long parseOctal(byte[] data, int offset, int length) {
        String s = cString(data, offset, length).trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
