package com.frimer.launcher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Клиент RCON (Source RCON) — управление сервером Minecraft командами по сети. */
public final class Rcon implements AutoCloseable {

    private static final int TYPE_AUTH = 3;
    private static final int TYPE_COMMAND = 2;
    private static final int TYPE_AUTH_RESPONSE = 2;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private int requestId = 1;

    public Rcon(String host, int port, String password) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setSoTimeout(8000);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        int id = requestId++;
        write(id, TYPE_AUTH, password);
        String[] answer = read();
        if (answer == null || Integer.parseInt(answer[0]) == -1) {
            close();
            throw new IOException("RCON отклонил пароль");
        }
    }

    /** Выполнить команду и вернуть ответ сервера. */
    public String command(String command) throws IOException {
        write(requestId++, TYPE_COMMAND, command);
        String[] answer = read();
        return answer == null ? "" : answer[1];
    }

    private void write(int id, int type, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(14 + payload.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(10 + payload.length);
        buffer.putInt(id);
        buffer.putInt(type);
        buffer.put(payload);
        buffer.put((byte) 0).put((byte) 0);
        out.write(buffer.array());
        out.flush();
    }

    /** Прочитать один пакет: [id, body]. */
    private String[] read() throws IOException {
        byte[] lengthBytes = readExactly(4);
        if (lengthBytes == null) {
            return null;
        }
        int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (length < 10 || length > 4 * 1024 * 1024) {
            throw new IOException("Некорректный пакет RCON");
        }
        byte[] packet = readExactly(length);
        if (packet == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        int id = buffer.getInt();
        buffer.getInt(); // тип
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            if (b == 0) {
                continue;
            }
            body.write(b);
        }
        return new String[]{Integer.toString(id), body.toString(StandardCharsets.UTF_8)};
    }

    private byte[] readExactly(int count) throws IOException {
        byte[] data = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = in.read(data, offset, count - offset);
            if (read < 0) {
                return null;
            }
            offset += read;
        }
        return data;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // уже закрыт
        }
    }

    /** Проверить соединение (для UI): вернуть true, если RCON отвечает. */
    public static boolean probe(String host, int port, String password) {
        try (Rcon rcon = new Rcon(host, port, password)) {
            rcon.command("list");
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
