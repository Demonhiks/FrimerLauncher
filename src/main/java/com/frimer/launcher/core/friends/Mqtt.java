package com.frimer.launcher.core.friends;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Минимальный клиент MQTT 3.1.1 (только то, что нужно друзьям): CONNECT, SUBSCRIBE,
 * PUBLISH QoS0, PINGREQ. Без внешних зависимостей.
 *
 * <p>Читает пакеты в отдельном потоке и отдаёт их слушателю; переподключение — забота
 * вызывающего кода.
 */
public final class Mqtt implements Closeable {

    /** Слушатель событий соединения. */
    public interface Listener {
        default void onConnected() {
        }

        default void onDisconnected(Throwable cause) {
        }

        default void onMessage(String topic, byte[] payload) {
        }
    }

    private static final int KEEPALIVE_SECONDS = 45;
    private static final int PUBLISH = 0x30;
    private static final int PINGREQ = 0xC0;
    private static final int PINGRESP = 0xD0;

    private final String host;
    private final int port;
    private final String clientId;
    private final Listener listener;
    private final AtomicInteger packetId = new AtomicInteger(1);
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile boolean connected;

    public Mqtt(String host, int port, String clientId, Listener listener) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /** Подключиться и дождаться CONNACK. */
    public void connect(int timeoutMs) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), timeoutMs);
        s.setSoTimeout(0);
        s.setTcpNoDelay(true);
        socket = s;
        out = s.getOutputStream();

        byte[] connect = connectPacket();
        out.write(connect);
        out.flush();

        // ждём CONNACK
        InputStream in = s.getInputStream();
        int type = in.read();
        if (type < 0) {
            throw new EOFException("брокер закрыл соединение");
        }
        readRemainingLength(in);
        if ((type & 0xF0) != 0x20) {
            throw new IOException("брокер ответил не CONNACK: " + Integer.toHexString(type));
        }
        int ackFlags = in.read();
        int code = in.read();
        if (code != 0) {
            throw new IOException("брокер отказал, код " + code);
        }
        connected = true;
        listener.onConnected();

        Thread reader = new Thread(() -> readLoop(in), "frimer-mqtt-reader");
        reader.setDaemon(true);
        reader.start();

        Thread pinger = new Thread(this::pingLoop, "frimer-mqtt-ping");
        pinger.setDaemon(true);
        pinger.start();
    }

    /** Подписаться на топик (QoS0). */
    public void subscribe(String topicFilter) throws IOException {
        byte[] id = new byte[2];
        int pid = packetId.getAndIncrement() & 0xFFFF;
        id[0] = (byte) (pid >> 8);
        id[1] = (byte) pid;

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(id);
        writeString(body, topicFilter);
        body.write(0); // QoS 0

        synchronized (this) {
            writePacket(0x82, body.toByteArray());
        }
    }

    /** Отправить сообщение (QoS0). */
    public void publish(String topic, byte[] payload) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeString(body, topic);
        body.write(payload);
        synchronized (this) {
            writePacket(PUBLISH, body.toByteArray());
        }
    }

    @Override
    public void close() {
        closed.set(true);
        connected = false;
        try {
            if (out != null) {
                synchronized (this) {
                    writePacket(0xE0, new byte[0]);
                }
            }
        } catch (IOException ignored) {
            // уже отключены
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // нечего закрывать
        }
    }

    // ---------------------------------------------------------------- пакеты

    private byte[] connectPacket() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeString(body, "MQTT");
        body.write(4);                 // уровень протокола
        body.write(0x02);              // clean session
        body.write(KEEPALIVE_SECONDS >> 8);
        body.write(KEEPALIVE_SECONDS & 0xFF);
        writeString(body, clientId);
        byte[] payload = body.toByteArray();

        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(0x10);
        writeRemainingLength(packet, payload.length);
        packet.write(payload, 0, payload.length);
        return packet.toByteArray();
    }

    private void writePacket(int header, byte[] body) throws IOException {
        OutputStream o = out;
        if (o == null) {
            throw new IOException("нет соединения");
        }
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(header);
        writeRemainingLength(packet, body.length);
        packet.write(body, 0, body.length);
        o.write(packet.toByteArray());
        o.flush();
    }

    private void readLoop(InputStream in) {
        try {
            while (!closed.get()) {
                int header = in.read();
                if (header < 0) {
                    break;
                }
                int length = readRemainingLength(in);
                byte[] body = in.readNBytes(length);
                if (body.length != length) {
                    break;
                }
                if ((header & 0xF0) == 0x30) {
                    int topicLength = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
                    String topic = new String(body, 2, topicLength, StandardCharsets.UTF_8);
                    byte[] payload = new byte[body.length - 2 - topicLength];
                    System.arraycopy(body, 2 + topicLength, payload, 0, payload.length);
                    listener.onMessage(topic, payload);
                }
                // SUBACK/PINGRESP игнорируем: QoS0 не требует подтверждений
            }
        } catch (IOException e) {
            if (!closed.get()) {
                connected = false;
                listener.onDisconnected(e);
            }
        } finally {
            connected = false;
        }
    }

    private void pingLoop() {
        while (!closed.get()) {
            try {
                Thread.sleep(KEEPALIVE_SECONDS * 1000L / 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (closed.get()) {
                return;
            }
            try {
                synchronized (this) {
                    writePacket(PINGREQ, new byte[0]);
                }
            } catch (IOException e) {
                connected = false;
                listener.onDisconnected(e);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- утилиты

    private static void writeString(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.write(bytes.length >> 8);
        out.write(bytes.length & 0xFF);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeRemainingLength(ByteArrayOutputStream out, int length) {
        do {
            int digit = length % 128;
            length /= 128;
            if (length > 0) {
                digit |= 0x80;
            }
            out.write(digit);
        } while (length > 0);
    }

    private static int readRemainingLength(InputStream in) throws IOException {
        int multiplier = 1;
        int value = 0;
        int digit;
        do {
            digit = in.read();
            if (digit < 0) {
                throw new EOFException("соединение закрыто");
            }
            value += (digit & 0x7F) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128) {
                throw new IOException("битый remaining length");
            }
        } while ((digit & 0x80) != 0);
        return value;
    }
}
