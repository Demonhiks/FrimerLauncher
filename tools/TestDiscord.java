import com.frimer.launcher.core.presence.DiscordRpc;

/** Проверка Discord RPC против поддельного пайпа. */
public final class TestDiscord {
    public static void main(String[] a) throws Exception {
        DiscordRpc.configure("1234567890123456789");
        DiscordRpc.playing("Сборка «Тест»", "Fabric 1.20.1");
        Thread.sleep(5000);
        System.out.println("connected=" + DiscordRpc.connected());
        DiscordRpc.idle();
        Thread.sleep(2500);
        System.out.println("done");
    }
}