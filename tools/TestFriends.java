import com.frimer.launcher.core.friends.Friends;
import java.util.concurrent.atomic.AtomicBoolean;

/** Проверка друзей без интерфейса: два процесса на одном брокере. */
public final class TestFriends {

    public static void main(String[] args) throws Exception {
        String peer = value(args, "--peer", "");
        boolean autoAccept = has(args, "--auto-accept");
        String text = value(args, "--text", "");
        String reply = value(args, "--reply", "");
        int seconds = Integer.parseInt(value(args, "--seconds", "35"));

        AtomicBoolean replied = new AtomicBoolean();
        Friends[] holder = new Friends[1];

        Friends.Listener listener = new Friends.Listener() {
            @Override
            public void onStatus(String status) {
                System.out.println("STATUS: " + status);
            }

            @Override
            public void onContactsChanged() {
                Friends friends = holder[0];
                StringBuilder list = new StringBuilder("FRIENDS:");
                for (var friend : friends.store().friends) {
                    list.append(' ').append(friend.nick).append('=').append(friend.code)
                            .append(friend.online() ? "(online)" : "(offline)");
                }
                System.out.println(list);
                if (autoAccept && !reply.isEmpty() && !replied.get()
                        && !friends.store().friends.isEmpty()) {
                    replied.set(true);
                    Thread thread = new Thread(() -> {
                        sleep(2500);
                        try {
                            holder[0].sendText(holder[0].store().friends.get(0).code, reply);
                            System.out.println("SENT_REPLY: " + reply);
                        } catch (RuntimeException e) {
                            System.out.println("REPLY_FAILED: " + e.getMessage());
                        }
                    }, "reply");
                    thread.setDaemon(true);
                    thread.start();
                }
            }

            @Override
            public void onChat(Friends.Message message, boolean unread) {
                System.out.println("CHAT from " + message.nick() + ": " + message.text());
            }

            @Override
            public void onIncomingRequest(String code, String nick) {
                System.out.println("REQUEST from " + nick + " (" + code + ")");
                if (autoAccept) {
                    holder[0].acceptFriend(code);
                    System.out.println("ACCEPTED " + code);
                }
            }

            @Override
            public void onBuild(String friendCode, java.util.Map<String, Object> build) {
                System.out.println("BUILD from " + friendCode + ": " + build);
            }

            @Override
            public void onInvite(String friendCode, String address, String serverName) {
                System.out.println("INVITE from " + friendCode + ": " + serverName + " " + address);
            }
        };

        Friends friends = new Friends(listener);
        holder[0] = friends;
        friends.start();
        System.out.println("MYCODE=" + friends.store().code);
        sleep(7000);

        if (!peer.isEmpty() && friends.store().find(peer) == null) {
            friends.requestFriend(peer, "Тест");
            System.out.println("REQUEST_SENT to " + peer);
        }
        sleep(6000);

        if (!text.isEmpty() && friends.store().find(peer) != null) {
            friends.sendText(peer, text);
            System.out.println("SENT: " + text);
        } else if (!text.isEmpty()) {
            System.out.println("NOT_FRIENDS_YET");
        }

        sleep(seconds * 1000L);
        friends.stop();
        System.out.println("DONE");
        System.exit(0);
    }

    private static boolean has(String[] args, String key) {
        for (String arg : args) {
            if (arg.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String value(String[] args, String key, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}