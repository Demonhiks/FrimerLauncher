package com.frimer.launcher.core.presence;

import com.frimer.launcher.core.Dirs;
import com.frimer.launcher.core.Json;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Discord Rich Presence через локальный IPC-сокет Discord — свой клиент, без библиотек.
 *
 * <p>Windows: именованный пайп {@code \\?\pipe\discord-ipc-N}. Linux/macOS: unix-сокет
 * {@code $XDG_RUNTIME_DIR|$TMPDIR/discord-ipc-N}. Протокол — 8-байтный заголовок
 * (op, длина) и JSON.
 *
 * <p>Работает только когда запущен Discord; если его нет — все вызовы просто ничего не делают.
 */
public final class DiscordRpc {

    /** ID приложения Frimer Launcher в Discord Developer Portal. */
    public static final String APP_ID = "1550637763550904390";

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_PING = 3;

    private static volatile String appId = "";
    private static volatile String reqDetails = "";
    private static volatile String reqState = "";
    private static volatile long reqStart;
    private static volatile boolean pending;
    private static volatile boolean connected;
    private static volatile boolean started;
    private static final Object LOCK = new Object();

    private DiscordRpc() {
    }

    /** Application ID из Discord Developer Portal (число). Пустая строка — выключено. */
    public static void configure(String id) {
        String clean = id == null ? "" : id.trim();
        if (clean.equals(appId) && started) {
            return;
        }
        appId = valid(clean) ? clean : "";
        if (!appId.isEmpty()) {
            startWorker();
        }
    }

    public static boolean valid(String id) {
        return id != null && id.matches("\\d{15,25}");
    }

    public static boolean connected() {
        return connected;
    }

    /** Показать «играет»: строка-статус и подробности. */
    public static void playing(String details, String state) {
        reqDetails = shortText(details, 120);
        reqState = shortText(state, 120);
        if (reqStart == 0) {
            reqStart = System.currentTimeMillis() / 1000;
        }
        pending = true;
        synchronized (LOCK) {
            LOCK.notifyAll();
        }
    }

    /** Сбросить активность (лаунчер в простое). */
    public static void idle() {
        reqDetails = "";
        reqState = "";
        reqStart = 0;
        pending = true;
        synchronized (LOCK) {
            LOCK.notifyAll();
        }
    }

    private static void startWorker() {
        synchronized (LOCK) {
            if (started) {
                return;
            }
            started = true;
        }
        Thread thread = new Thread(DiscordRpc::loop, "frimer-discord-rpc");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop() {
        while (true) {
            if (appId.isEmpty()) {
                sleep(2000);
                continue;
            }
            Connection connection = null;
            try {
                connection = Connection.open(appId);
                connected = true;
                pending = true;
                long lastPing = System.currentTimeMillis();
                while (true) {
                    if (pending) {
                        pending = false;
                        connection.setActivity(appId, reqDetails, reqState, reqStart);
                    }
                    if (System.currentTimeMillis() - lastPing > 30_000) {
                        connection.write(OP_PING, "{}");
                        lastPing = System.currentTimeMillis();
                    }
                    synchronized (LOCK) {
                        if (!pending) {
                            LOCK.wait(1000);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                connected = false;
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
            sleep(5000);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    // ---------------------------------------------------------------- транспорт

    /** Соединение с Discord: пайп на Windows или unix-сокет на Linux/macOS. */
    private static final class Connection {

        private final RandomAccessFile pipe;
        private final SocketChannel channel;
        private final InputStream in;
        private final OutputStream out;

        private Connection(RandomAccessFile pipe, SocketChannel channel) {
            this.pipe = pipe;
            this.channel = channel;
            this.in = channel == null ? null : Channels.newInputStream(channel);
            this.out = channel == null ? null : Channels.newOutputStream(channel);
        }

        /** Перебрать discord-ipc-0..9 и вернуть первое соединение с успешным рукопожатием. */
        static Connection open(String appId) throws IOException {
            IOException last = null;
            for (int i = 0; i < 10; i++) {
                Connection connection = null;
                try {
                    connection = Dirs.isWindows() ? openPipe(i) : openSocket(i);
                    connection.handshake(appId);
                    return connection;
                } catch (IOException e) {
                    last = e;
                    if (connection != null) {
                        connection.close();
                    }
                }
            }
            throw new IOException("Discord не принял подключение (запущен ли он?)", last);
        }

        private static Connection openPipe(int index) throws IOException {
            String path = "\\\\.\\pipe\\discord-ipc-" + index;
            RandomAccessFile raf = new RandomAccessFile(path, "rw");
            return new Connection(raf, null);
        }

        private static Connection openSocket(int index) throws IOException {
            Path base = socketBase();
            Path path = base.resolve("discord-ipc-" + index);
            if (!Files.exists(path)) {
                throw new IOException("нет сокета " + path);
            }
            SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
            ch.connect(UnixDomainSocketAddress.of(path));
            return new Connection(null, ch);
        }

        private static Path socketBase() {
            String runtime = System.getenv("XDG_RUNTIME_DIR");
            if (runtime != null && !runtime.isBlank()) {
                return Path.of(runtime);
            }
            String tmp = System.getenv("TMPDIR");
            if (tmp != null && !tmp.isBlank()) {
                return Path.of(tmp);
            }
            return Path.of("/tmp");
        }

        void handshake(String id) throws IOException {
            Map<String, Object> hello = new LinkedHashMap<>();
            hello.put("v", 1);
            hello.put("client_id", id);
            write(OP_HANDSHAKE, Json.write(hello));
            String reply = read();
            if (reply == null || !reply.contains("READY")) {
                throw new IOException("Discord не подтвердил подключение: " + reply);
            }
        }

        void setActivity(String id, String details, String state, long startSeconds)
                throws IOException {
            Map<String, Object> activity = new LinkedHashMap<>();
            if (!details.isBlank()) {
                activity.put("details", details);
            }
            if (!state.isBlank()) {
                activity.put("state", state);
            }
            if (startSeconds > 0) {
                Map<String, Object> timestamps = new LinkedHashMap<>();
                timestamps.put("start", startSeconds);
                activity.put("timestamps", timestamps);
            }
            activity.put("instance", false);

            Map<String, Object> args = new LinkedHashMap<>();
            args.put("pid", ProcessHandle.current().pid());
            args.put("activity", activity);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cmd", "SET_ACTIVITY");
            payload.put("nonce", UUID.randomUUID().toString());
            payload.put("args", args);

            write(OP_FRAME, Json.write(payload));
        }

        synchronized void write(int op, String json) throws IOException {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(op);
            buffer.putInt(data.length);
            buffer.put(data);
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            if (pipe != null) {
                pipe.write(bytes);
            } else {
                out.write(bytes);
                out.flush();
            }
        }

        /** Прочитать один кадр; null — нет данных. */
        String read() throws IOException {
            byte[] header = readFully(8);
            if (header == null) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            buffer.getInt();
            int length = buffer.getInt();
            if (length < 0 || length > 1 << 20) {
                throw new IOException("битый кадр Discord: " + length);
            }
            byte[] payload = readFully(length);
            return payload == null ? null : new String(payload, StandardCharsets.UTF_8);
        }

        private byte[] readFully(int count) throws IOException {
            if (count == 0) {
                return new byte[0];
            }
            byte[] data = new byte[count];
            int offset = 0;
            while (offset < count) {
                int read;
                if (pipe != null) {
                    read = pipe.read(data, offset, count - offset);
                } else {
                    int n = in.read(data, offset, count - offset);
                    read = n;
                }
                if (read < 0) {
                    throw new EOFException("Discord закрыл соединение");
                }
                offset += read;
            }
            return data;
        }

        void close() {
            if (pipe != null) {
                try {
                    pipe.close();
                } catch (IOException ignored) {
                    // уже закрыт
                }
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                    // уже закрыт
                }
            }
        }
    }
}
