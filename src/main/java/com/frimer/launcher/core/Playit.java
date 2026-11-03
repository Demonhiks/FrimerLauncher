package com.frimer.launcher.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Агент playit.gg: поиск локальной установки, автозагрузка,
 * claim-код (постоянный адрес) и local-webport API для создания туннелей.
 */
public final class Playit {

    private Playit() {
    }

    /** Порт local-webport API агента (https://local.playit/webport). */
    public static final int WEBPORT_API_PORT = 10056;

    /** Claim-код агента: с ним адрес playit.gg не меняется между перезапусками. */
    public static String claimCode() {
        try {
            for (Path p : credentialsCandidates()) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                Map<String, Object> creds = Json.readFile(p);
                if (creds == null) {
                    continue;
                }
                String code = Json.str(creds.get("code"), "");
                if (!code.isEmpty()) {
                    return code;
                }
            }
        } catch (Exception ignored) {
            // нет файла учётки — значит агент ещё не заявлен
        }
        return "";
    }

    private static List<Path> credentialsCandidates() {
        List<Path> out = new ArrayList<>();
        String appdata = Procs.env("APPDATA", "");
        if (!appdata.isEmpty()) {
            out.add(Path.of(appdata).resolve("playit_gg").resolve("playit-credentials.json"));
        }
        out.add(Dirs.HOME.resolve(".config").resolve("playit-gg")
                .resolve("playit-credentials.json"));
        out.add(Dirs.GAME_DIR.resolve("playit").resolve("playit-credentials.json"));
        return out;
    }

    /** Создаёт tunnel "minecraft" через local-webport API агента (если он запущен). */
    public static boolean ensureTunnel(int port, Consumer<String> log) {
        Consumer<String> logger = log == null ? msg -> {
        } : log;
        try {
            Map<String, Object> resp = webportRequest("/api/tunnels");
            Object tunnelsObj = resp.get("tunnels");
            if (tunnelsObj instanceof List<?> list && hasMinecraft(list, port)) {
                logger.accept("[playit] Туннель на порт " + port + " уже есть");
                return true;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("connector", "localhost:" + port);
            body.put("type", "notls");
            body.put("name", "minecraft");
            body.put("remote", "joinmc.link");
            webportRequest("/api/new", body);
            logger.accept("[playit] Создал постоянный туннель minecraft -> localhost:" + port);
            return true;
        } catch (Exception e) {
            logger.accept("[playit] Local API недоступен (" + e.getMessage()
                    + ") — туннель создастся сам по умолчанию");
            return false;
        }
    }

    private static boolean hasMinecraft(List<?> tunnels, int port) {
        for (Object t : tunnels) {
            Map<String, Object> map = Json.obj(t);
            if (map == null) {
                continue;
            }
            String name = Json.str(map.get("name"), "");
            String connector = Json.str(map.get("connector"), "");
            if ("minecraft".equals(name) || connector.endsWith(":" + port)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> webportRequest(String path) throws IOException {
        return webportRequest(path, null);
    }

    private static Map<String, Object> webportRequest(String path, Map<String, Object> body)
            throws IOException {
        String token = readWebportToken();
        StringBuilder req = new StringBuilder();
        req.append(body == null ? "GET " : "POST ").append(path)
                .append(" HTTP/1.1\r\nHost: local.playit\r\nAccept: application/json\r\n");
        if (body != null) {
            req.append("Content-Type: application/json\r\n");
        }
        if (!token.isEmpty()) {
            req.append("Authorization: Basic ").append(token).append("\r\n");
        }
        byte[] payload = body == null ? new byte[0]
                : Json.write(body).getBytes(StandardCharsets.UTF_8);
        req.append("Connection: close\r\nContent-Length: ").append(payload.length)
                .append("\r\n\r\n");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", WEBPORT_API_PORT), 3000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(
                    req.toString().getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
            String text = new String(socket.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int split = text.indexOf("\r\n\r\n");
            String head = split < 0 ? text : text.substring(0, split);
            String rest = split < 0 ? "" : text.substring(split + 4);
            if (!head.contains(" 200")) {
                throw new IOException("ответ " + head.split("\r\n", 2)[0]);
            }
            return Json.parseObject(rest);
        }
    }

    /** Базовый токен из config.yml агента (файл может быть в кодировке UTF-16). */
    private static String readWebportToken() {
        for (Path cfg : configCandidates()) {
            for (Charset enc : new Charset[]{StandardCharsets.UTF_8,
                    Charset.forName("UTF-16LE")}) {
                try {
                    if (!Files.isRegularFile(cfg)) {
                        continue;
                    }
                    String text = new String(Files.readAllBytes(cfg), enc);
                    for (String line : text.split("\\R")) {
                        String s = line.trim();
                        if (s.startsWith("base64_auth:")) {
                            return s.substring("base64_auth:".length()).trim()
                                    .replaceAll("^\"|\"$", "");
                        }
                    }
                } catch (Exception ignored) {
                    // пробуем следующую кодировку/кандидата
                }
            }
        }
        return "";
    }

    private static List<Path> configCandidates() {
        List<Path> out = new ArrayList<>();
        String appdata = Procs.env("APPDATA", "");
        if (!appdata.isEmpty()) {
            out.add(Path.of(appdata).resolve("playit_gg").resolve("config.yml"));
        }
        String programData = Procs.env("ProgramData", "C:/ProgramData");
        out.add(Path.of(programData).resolve("playit_gg").resolve("config.yml"));
        out.add(Dirs.GAME_DIR.resolve("playit").resolve("config.yml"));
        return out;
    }

    /** Случайное имя для нового туннеля. */
    public static String randomName() {
        String letters = "abcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder("frimer-");
        for (int i = 0; i < 6; i++) {
            sb.append(letters.charAt(rnd.nextInt(letters.length())));
        }
        return sb.toString();
    }

    public static final String DOWNLOAD_URL =
            "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-windows-x86-signed.exe";

    public static List<Path> candidates() {
        List<Path> out = new ArrayList<>();
        String localAppData = Procs.env("LOCALAPPDATA", "");
        if (!localAppData.isEmpty()) {
            out.add(Path.of(localAppData).resolve("playit_gg").resolve("bin").resolve("playit.exe"));
        }
        out.add(Path.of("C:/Program Files/playit_gg/bin/playit.exe"));
        out.add(Path.of("C:/Program Files (x86)/playit_gg/bin/playit.exe"));
        out.add(Path.of(Procs.env("ProgramFiles", "C:/Program Files"))
                .resolve("playit_gg").resolve("playit.exe"));
        out.add(Path.of(Procs.env("ProgramFiles(x86)", "C:/Program Files (x86)"))
                .resolve("playit_gg").resolve("playit.exe"));
        out.add(Dirs.GAME_DIR.resolve("playit").resolve("playit.exe"));
        return out;
    }

    public static Path find() {
        for (Path p : candidates()) {
            try {
                if (Files.isRegularFile(p)) {
                    return p;
                }
            } catch (RuntimeException ignored) {
                // недоступный путь - пропускаем
            }
        }
        return null;
    }

    public static Path ensure(Consumer<String> log, ProgressListener progress) {
        Path found = find();
        if (found != null) {
            return found;
        }
        Consumer<String> logger = log == null ? msg -> {
        } : log;
        Path dest = Dirs.GAME_DIR.resolve("playit").resolve("playit.exe");
        logger.accept("playit.gg не найден - скачиваю агент...");
        Downloader.download(DOWNLOAD_URL, dest, null, null, logger, progress);
        logger.accept("playit.gg скачан");
        return dest;
    }
}
