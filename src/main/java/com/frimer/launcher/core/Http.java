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
