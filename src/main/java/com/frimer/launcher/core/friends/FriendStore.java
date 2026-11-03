package com.frimer.launcher.core.friends;

import com.frimer.launcher.core.Dirs;
import com.frimer.launcher.core.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Список друзей и свой код: хранится в ~/.frimerlauncher/friends.json. */
public final class FriendStore {

    /** Друг: код, ник и время последнего «я тут». */
    public static final class Friend {
        public final String code;
        public final String nick;
        public volatile long lastSeen;

        public Friend(String code, String nick, long lastSeen) {
            this.code = code;
            this.nick = nick == null || nick.isBlank() ? "Друг" : nick;
            this.lastSeen = lastSeen;
        }

        public boolean online() {
            return System.currentTimeMillis() - lastSeen < 70_000L;
        }
    }

    public String code;
    public String nick;
    public final List<Friend> friends = new ArrayList<>();
    public final List<Friend> incoming = new ArrayList<>();

    public static Path file() {
        return Dirs.GAME_DIR.resolve("friends.json");
    }

    public static FriendStore load() {
        FriendStore store = new FriendStore();
        Map<String, Object> data = Files.exists(file()) ? Json.readFileQuiet(file()) : null;
        if (data == null) {
            store.code = Codes.random();
            store.save();
            return store;
        }
        Object rawCode = data.get("code");
        String saved = Json.str(rawCode, "");
        store.code = Codes.valid(saved) ? Codes.normalize(saved) : Codes.random();
        store.nick = Json.str(data.get("nick"), "Player");
        for (Object item : Json.arr(data.get("friends"))) {
            Map<String, Object> entry = Json.obj(item);
            String friendCode = Codes.normalize(Json.str(entry.get("code"), ""));
            if (Codes.valid(friendCode)) {
                store.friends.add(new Friend(friendCode, Json.str(entry.get("nick"), "Друг"),
                        Json.lng(entry.get("last_seen"), 0L)));
            }
        }
        for (Object item : Json.arr(data.get("incoming"))) {
            Map<String, Object> entry = Json.obj(item);
            String friendCode = Codes.normalize(Json.str(entry.get("code"), ""));
            if (Codes.valid(friendCode)) {
                store.incoming.add(new Friend(friendCode, Json.str(entry.get("nick"), "Друг"), 0L));
            }
        }
        store.save();
        return store;
    }

    public void save() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("nick", nick == null ? "Player" : nick);
        List<Object> list = new ArrayList<>();
        for (Friend friend : friends) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", friend.code);
            entry.put("nick", friend.nick);
            entry.put("last_seen", friend.lastSeen);
            list.add(entry);
        }
        data.put("friends", list);
        List<Object> pending = new ArrayList<>();
        for (Friend friend : incoming) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("code", friend.code);
            entry.put("nick", friend.nick);
            pending.add(entry);
        }
        data.put("incoming", pending);
        Json.writeFileQuiet(file(), data);
    }

    /** Найти друга по коду (в списке или среди запросов). */
    public Friend find(String friendCode) {
        String normalized = Codes.normalize(friendCode);
        for (Friend friend : friends) {
            if (friend.code.equals(normalized)) {
                return friend;
            }
        }
        return null;
    }

    public Friend findIncoming(String friendCode) {
        String normalized = Codes.normalize(friendCode);
        for (Friend friend : incoming) {
            if (friend.code.equals(normalized)) {
                return friend;
            }
        }
        return null;
    }

    /** Добавить друга (идемпотентно) и убрать из ожидающих. */
    public Friend addFriend(String friendCode, String friendNick) {
        String normalized = Codes.normalize(friendCode);
        Friend existing = find(normalized);
        if (existing != null) {
            incoming.removeIf(f -> f.code.equals(normalized));
            return existing;
        }
        Friend friend = new Friend(normalized, friendNick, 0L);
        friends.add(friend);
        incoming.removeIf(f -> f.code.equals(normalized));
        save();
        return friend;
    }

    public void removeFriend(String friendCode) {
        String normalized = Codes.normalize(friendCode);
        friends.removeIf(f -> f.code.equals(normalized));
        save();
    }
}
