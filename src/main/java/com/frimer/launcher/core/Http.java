package com.frimer.launcher.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Тонкая обёртка над java.net.http: GET текста и байтов с понятными ошибками. */
public final class Http {

    private Http() {
    }

    public static final String UA = "Frimer-Launcher/2.1";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static class ApiException extends RuntimeException {
        public final int status;

        public ApiException(String message, int status) {
            super(message);
            this.status = status;
        }
    }

    public static String get(String url) {
        return get(url, Map.of());
    }

    public static String get(String url, Map<String, String> headers) {
        HttpResponse<String> resp = send(url, headers, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new ApiException("HTTP " + resp.statusCode() + " для " + url, resp.statusCode());
        }
        return resp.body();
    }

    public static byte[] getBytes(String url) {
        HttpResponse<byte[]> resp = send(url, Map.of(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new ApiException("HTTP " + resp.statusCode() + " для " + url, resp.statusCode());
        }
        return resp.body();
    }

    /**
     * GET с ручным проходом по редиректам, включая https → http.
     *
     * <p>Java намеренно не следует «понижающим» редиректам ({@link HttpClient.Redirect#NORMAL}),
     * а Ely.by отдаёт скины именно так: 301 с https на http-хранилище.
     */
    public static byte[] getBytesFollowing(String url) {
        String current = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpResponse<byte[]> resp = send(current, Map.of(),
                    HttpResponse.BodyHandlers.ofByteArray());
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                return resp.body();
            }
            if (code < 300 || code >= 400) {
                throw new ApiException("HTTP " + code + " для " + url, code);
            }
            String location = resp.headers().firstValue("Location").orElse("");
            if (location.isBlank()) {
                throw new ApiException("HTTP " + code + " без Location для " + url, code);
            }
            current = URI.create(current).resolve(location).toString();
        }
        throw new ApiException("Слишком много редиректов для " + url, 0);
    }

    /** POST с телом (JSON или form-urlencoded). */
    public static String post(String url, String body, String contentType,
                              Map<String, String> headers) {
        HttpRequest.Builder b;
        try {
            b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        } catch (IllegalArgumentException e) {
            throw new ApiException("Некорректный URL: " + url, 0);
        }
        b.header("User-Agent", UA);
        b.header("Content-Type", contentType);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        b.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> resp = CLIENT.send(b.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new ApiException("HTTP " + resp.statusCode() + " для " + url + ": "
                        + resp.body(), resp.statusCode());
            }
            return resp.body();
        } catch (IOException e) {
            throw new ApiException("Не могу подключиться к " + url + ": " + e.getMessage(), 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Запрос прерван: " + url, 0);
        }
    }

    /** Текст или null, если запрос не удался (без исключений). */
    public static String getQuiet(String url) {
        try {
            return get(url);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static <T> HttpResponse<T> send(String url, Map<String, String> headers,
                                            HttpResponse.BodyHandler<T> handler) {
        HttpRequest.Builder b;
        try {
            b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60));
        } catch (IllegalArgumentException e) {
            throw new ApiException("Некорректный URL: " + url, 0);
        }
        b.header("User-Agent", UA);
        for (Map.Entry<String, String> e : headers.entrySet()) {
            b.header(e.getKey(), e.getValue());
        }
        b.GET();
        try {
            return CLIENT.send(b.build(), handler);
        } catch (IOException e) {
            throw new ApiException("Не могу подключиться к " + url + ": " + e.getMessage(), 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Запрос прерван: " + url, 0);
        }
    }

    /** Скачать файл в память с проверкой статуса (нужно для картинок). */
    public static byte[] fetchQuiet(String url) {
        try {
            return getBytes(url);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
