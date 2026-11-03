package com.frimer.launcher.core.friends;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Коды друзей и шифрование переписки.
 *
 * <p>Код друга — это и адрес, и секрет: топик брокера и ключ AES выводятся из него,
 * поэтому без кода чужие сообщения не прочитать и не подделать. Аккаунтов нет.
 */
public final class Codes {

    /** Без похожих символов (0/O, 1/I), чтобы код было легко продиктовать. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LENGTH = 10;

    private Codes() {
    }

    /** Новый код вида ABCDE-FGHJK. */
    public static String random() {
        StringBuilder out = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == 5) {
                out.append('-');
            }
            out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    /** Привести введённый код к виду ABCDE-FGHJK (убрать пробелы, привести регистр). */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String letters = raw.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (letters.length() != LENGTH) {
            return letters;
        }
        return letters.substring(0, 5) + "-" + letters.substring(5);
    }

    /** Код без дефиса — для топиков и ключей. */
    public static String plain(String code) {
        return code == null ? "" : code.replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }

    public static boolean valid(String code) {
        return normalize(code).length() == LENGTH + 1;
    }

    /** Топик входящих сообщений: из кода выводится, угадать без него нельзя. */
    public static String inbox(String code) {
        return "frimer/1/i/" + hex(sha256("frimer-inbox|" + plain(code)), 10);
    }

    /** Ключ AES для сообщений этому коду. */
    public static SecretKeySpec key(String code) {
        return new SecretKeySpec(sha256("frimer-key|" + plain(code)), "AES");
    }

    /** Зашифровать: iv(12) + ciphertext. */
    public static byte[] encrypt(SecretKeySpec key, byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plain);
        byte[] out = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
        return out;
    }

    /** Расшифровать; при неверном ключе вернёт null, а не исключение. */
    public static byte[] decrypt(SecretKeySpec key, byte[] data) {
        try {
            if (data == null || data.length < 13) {
                return null;
            }
            byte[] iv = new byte[12];
            System.arraycopy(data, 0, iv, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return cipher.doFinal(data, 12, data.length - 12);
        } catch (Exception e) {
            return null;
        }
    }

    public static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] unbase64(String text) {
        try {
            return Base64.getDecoder().decode(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("нет SHA-256", e);
        }
    }

    private static String hex(byte[] bytes, int length) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < length && i < bytes.length; i++) {
            out.append(String.format("%02x", bytes[i]));
        }
        return out.toString();
    }
}
