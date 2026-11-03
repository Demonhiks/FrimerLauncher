import com.frimer.launcher.core.Upnp;

/** Проверка UPnP на этой машине: есть ли роутер и открывается ли порт. */
public final class TestUpnp {
    public static void main(String[] a) throws Exception {
        System.out.println("локальный IP : " + Upnp.localAddress());
        System.out.println("роутер       : " + (Upnp.routerName().isBlank() ? "(не найден)" : Upnp.routerName()));
        System.out.println("UPnP доступен: " + Upnp.available());
        try {
            Upnp.Mapping mapping = Upnp.open(25565, "Frimer test", 60);
            System.out.println("порт открыт  : " + mapping.address() + "  (роутер: " + mapping.router() + ")");
            Upnp.close(25565);
            System.out.println("правило удалено");
        } catch (Exception e) {
            System.out.println("не удалось    : " + e.getMessage());
        }
    }
}