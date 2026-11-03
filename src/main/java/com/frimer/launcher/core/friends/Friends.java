package com.frimer.launcher.core.friends;

import com.frimer.launcher.core.AppSettings;
import com.frimer.launcher.core.Json;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Друзья поверх публичного MQTT-брокера: присутствие, чат, обмен сборками и приглашения.
 *
 * <p>Аккаунтов нет: у каждого свой код (он же адрес), сообщения шифруются AES-GCM ключом,
 * выведенным из кода получателя.
 */
public final class Friends {

    /** Одно сообщение чата. */
    public record Message(String friendCode, String nick, boolean mine, long time, String text) {
    }

    /** События для интерфейса (приходят из сетевого потока). */
    public interface Listener {
        default void onStatus(String text) {
        }

        default void onContactsChanged() {
        }

        default void onChat(Message message, boolean unread) {
        }

        default void onIncomingRequest(String code, String nick) {
        }

        default void onBuild(String friendCode, Map<String, Object> build) {
        }

        default void onInvite(String friendCode, String address, String serverName) {
        }
    }

    /** Публичные брокеры: первый доступен, второй — запасной. */
    private static final String[][] BROKERS = {
            {"broker.hivemq.com", "1883"},
            {"broker.emqx.io", "1883"},
    };

    private final FriendStore store;
    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Mqtt mqtt;
    private volatile String status = "офлайн";
    private volatile long lastHelloAt;

    public Friends(Listener listener) {
        this.listener = listener == null ? new Listener() {
        } : listener;
        this.store = FriendStore.load();
        this.store.nick = AppSettings.nickname();
        this.store.save();
    }

    public FriendStore store() {
        return store;
    }

    public String status() {
        return status;
    }

    public boolean online() {
        Mqtt client = mqtt;
        return client != null && client.isConnected();
    }

    /** Запустить подключение (не блокирует). */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Thread thread = new Thread(this::connectionLoop, "frimer-friends");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running.set(false);
        Mqtt client = mqtt;
        mqtt = null;
        if (client != null) {
            client.close();
        }
        setStatus("офлайн");
    }

    // ------------------------------------------------------------- соединение

    private void connectionLoop() {
        int attempt = 0;
        while (running.get()) {
            String host = BROKERS[attempt % BROKERS.length][0];
            int port = Integer.parseInt(BROKERS[attempt % BROKERS.length][1]);
            attempt++;
            setStatus("подключаюсь…");
            Mqtt client = new Mqtt(host, port, "frimer-" + Codes.plain(store.code),
                    new Mqtt.Listener() {
                        @Override
                        public void onMessage(String topic, byte[] payload) {
                            handle(payload);
                        }

                        @Override
                        public void onDisconnected(Throwable cause) {
                            setStatus("связь потеряна");
                        }
                    });
            try {
                client.connect(10_000);
                mqtt = client;
                client.subscribe(Codes.inbox(store.code));
                setStatus("онлайн");
                lastHelloAt = 0;
                helloLoop(client);
            } catch (Exception e) {
                setStatus("нет связи: " + shortError(e));
            } finally {
                if (mqtt == client) {
                    mqtt = null;
                }
                client.close();
            }
            if (!running.get()) {
                return;
            }
            sleep(5_000L * Math.min(attempt, 6));
        }
    }

    /** Пока соединение живо — раз в 25 секунд напоминаем друзьям, что мы тут. */
    private void helloLoop(Mqtt client) {
        while (running.get() && client.isConnected()) {
            sendHello();
            sleep(25_000);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------- отправка

    public void sendHello() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("t", "hello");
        message.put("c", store.code);
        message.put("n", store.nick);
        for (FriendStore.Friend friend : snapshotFriends()) {
            try {
                publish(friend.code, message);
            } catch (RuntimeException ignored) {
                // присутствие — «best effort»
            }
        }
        lastHelloAt = System.currentTimeMillis();
    }

    /** Запрос в друзья по коду. */
    public void requestFriend(String code, String nick) {
        String normalized = Codes.normalize(code);
        if (!Codes.valid(normalized)) {
            throw new IllegalArgumentException("Код друга выглядит как ABCDE-FGHJK");
        }
        if (normalized.equalsIgnoreCase(store.code)) {
            throw new IllegalArgumentException("Это ваш собственный код");
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("t", "req");
        message.put("c", store.code);
        message.put("n", nick == null || nick.isBlank() ? store.nick : nick);
        publish(normalized, message);
    }

    /** Принять входящий запрос: добавляем человека и сообщаем ему об этом. */
    public void acceptFriend(String code) {
        FriendStore.Friend pending = store.findIncoming(code);
        if (pending == null) {
            return;
        }
        store.addFriend(pending.code, pending.nick);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("t", "acc");
        message.put("c", store.code);
        message.put("n", store.nick);
        publish(pending.code, message);
        listener.onContactsChanged();
    }

    public void declineFriend(String code) {
        store.incoming.removeIf(f -> f.code.equals(Codes.normalize(code)));
        store.save();
        listener.onContactsChanged();
    }

    public void removeFriend(String code) {
        store.removeFriend(code);
        listener.onContactsChanged();
    }

    public void sendText(String friendCode, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("t", "msg");
        message.put("c", store.code);
        message.put("n", store.nick);
        message.put("x", text.trim());
        message.put("ts", System.currentTimeMillis());
        publish(Codes.normalize(friendCode), message);
    }

    /** Поделиться сборкой: имя, версия, загрузчик и число модов. */
    public void sendBuild(String friendCode, String name, String mcVersion, String loader, int mods) {
        Map<String, Object> build = new LinkedHashMap<>();
        build.put("name", name);
        build.put("mc", mcVersion);
        build.put("loader", loader);
        build.put("mods", mods);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("t", "build");
        message.put("c", store.code);
        message.put("n", store.nick);
        message.put("b", build);
        publish(Codes.normalize(friendCode), message);
    }

    /** Позвать на сервер: адрес и название. */
    public void sendInvite(String friendCode, String address, String serverName) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("t", "invite");
        message.put("c", store.code);
        message.put("n", store.nick);
        message.put("a", address);
        message.put("s", serverName);
        publish(Codes.normalize(friendCode), message);
    }

    private void publish(String toCode, Map<String, Object> message) {
        Mqtt client = mqtt;
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("Нет связи с брокером — сообщение не отправлено");
        }
        try {
            byte[] plain = Json.write(message).getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = Codes.encrypt(Codes.key(toCode), plain);
            client.publish(Codes.inbox(toCode), encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось отправить: " + shortError(e));
        }
    }

    // ------------------------------------------------------------- приём

    private void handle(byte[] payload) {
        byte[] plain = Codes.decrypt(Codes.key(store.code), payload);
        if (plain == null) {
            return; // чужое или испорченное сообщение
        }
        Map<String, Object> message;
        try {
            message = Json.parseObject(new String(plain, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return;
        }
        String type = Json.str(message.get("t"), "");
        String from = Codes.normalize(Json.str(message.get("c"), ""));
        String nick = Json.str(message.get("n"), "Друг");
        if (!Codes.valid(from) || from.equalsIgnoreCase(store.code)) {
            return;
        }
        FriendStore.Friend friend = store.find(from);

        switch (type) {
            case "hello" -> {
                if (friend != null) {
                    friend.lastSeen = System.currentTimeMillis();
                    if (System.currentTimeMillis() - lastHelloAt > 20_000) {
                        sendHello();
                    }
                    listener.onContactsChanged();
                }
            }
            case "req" -> {
                if (friend == null && store.findIncoming(from) == null) {
                    store.incoming.add(new FriendStore.Friend(from, nick, 0L));
                    store.save();
                    listener.onIncomingRequest(from, nick);
                    listener.onContactsChanged();
                }
            }
            case "acc" -> {
                if (friend == null) {
                    store.addFriend(from, nick);
                } else {
                    friend.lastSeen = System.currentTimeMillis();
                }
                listener.onContactsChanged();
                sendHello();
            }
            case "msg" -> {
                if (friend != null) {
                    friend.lastSeen = System.currentTimeMillis();
                    String text = Json.str(message.get("x"), "");
                    long ts = Json.lng(message.get("ts"), System.currentTimeMillis());
                    listener.onChat(new Message(friend.code, nick, false, ts, text), false);
                }
            }
            case "build" -> {
                if (friend != null) {
                    Map<String, Object> build = Json.obj(message.get("b"));
                    if (build != null && !build.isEmpty()) {
                        listener.onBuild(friend.code, build);
                    }
                }
            }
            case "invite" -> {
                if (friend != null) {
                    listener.onInvite(friend.code, Json.str(message.get("a"), ""),
                            Json.str(message.get("s"), "Сервер"));
                }
            }
            default -> {
                // неизвестный тип — молча игнорируем
            }
        }
    }

    private java.util.List<FriendStore.Friend> snapshotFriends() {
        synchronized (store) {
            return new java.util.ArrayList<>(store.friends);
        }
    }

    private void setStatus(String text) {
        status = text;
        listener.onStatus(text);
    }

    private static String shortError(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        }
        return message.length() > 60 ? message.substring(0, 60) + "…" : message;
    }
}
