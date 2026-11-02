import com.frimer.launcher.core.Dirs;
import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Json;
import com.frimer.launcher.core.Loaders;
import com.frimer.launcher.core.Mojang;
import com.frimer.launcher.core.Skins;
import com.frimer.launcher.core.Utils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Быстрая проверка ядра без GUI: JSON, UUID, версии Mojang, загрузчики, рендер скина.
 * Запуск: javac -cp build/classes -d build/test tools/SmokeTest.java
 *          java -cp build/classes;build/test SmokeTest
 */
public final class SmokeTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        check("offline uuid format", Utils.offlineUuid("Notch").matches(
                "^[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"));
        check("offline uuid stable", Utils.offlineUuid("Player").equals(Utils.offlineUuid("Player")));
        System.out.println("  uuid(Notch) = " + Utils.offlineUuid("Notch"));

        check("slugify ru", "moya-sborka".equals(Utils.slugify("Моя сборка")));
        check("slugify empty", "instance".equals(Utils.slugify("   ")));
        check("fmtSize", "1.0 KB".equals(Utils.fmtSize(1024)));
        check("fmtDuration", "1h 1m".equals(Utils.fmtDuration(3660)));
        check("numKey", Utils.numKey("1.20.4").equals(List.of(1L, 20L, 4L)));
        check("version sort", Utils.VERSION_DESC.compare("1.21.1", "1.9.4") < 0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "Сборка");
        data.put("ram", 4096);
        data.put("big", 1_000_000_000_000L);
        data.put("flag", true);
        data.put("list", List.of(1, 2, 3));
        String encoded = Json.write(data);
        Map<String, Object> back = Json.parseObject(encoded);
        check("json roundtrip string", "Сборка".equals(back.get("name")));
        check("json roundtrip number", Json.lng(back.get("ram"), 0) == 4096);
        check("json roundtrip long", Json.lng(back.get("big"), 0) == 1_000_000_000_000L);
        Object nested = Json.parse("{\"a\":{\"b\":[1,{\"c\":\"x\"}]}}");
        check("json nested", "x".equals(Json.str(Json.path(nested, "a", "b", "1", "c"), "")));

        check("safeJoin blocks escape", escapes(() -> Utils.safeJoin(Dirs.GAME_DIR, "../../evil")));

        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                skin.setRGB(x, y, 0xFF000000 | (x * 4 << 16) | (y * 4 << 8) | 0x40);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(skin, "png", out);
        check("skin preview renders", Skins.renderPreviewPng(out.toByteArray(), 128) != null);

        System.out.println("Сеть: проверяю Mojang...");
        try {
            List<String> releases = Mojang.releaseVersions();
            check("mojang releases", releases.size() > 100);
            System.out.println("  версий release: " + releases.size() + ", свежая: "
                    + (releases.isEmpty() ? "?" : releases.get(0)));
            Map<String, Object> info = Mojang.versionInfo(releases.get(0));
            check("version json has mainClass", !Json.str(info.get("mainClass"), "").isEmpty());
            check("version json has libraries", !Json.arr(info.get("libraries")).isEmpty());
        } catch (RuntimeException e) {
            System.out.println("  ! сеть недоступна: " + e.getMessage());
        }

        try {
            Map<String, Map<String, String>> forge = Loaders.forgeVersionsByMc();
            check("forge promos", !forge.isEmpty());
            System.out.println("  Forge MC-версий: " + forge.size());
        } catch (RuntimeException e) {
            System.out.println("  ! Forge API недоступен: " + e.getMessage());
        }

        Instance probe = new Instance(Dirs.INSTANCES_DIR.resolve("probe"), "probe", "Fabric",
                "1.20.1", 4096, "0.15.0");
        check("instance ram", probe.ramMb == 4096);

        System.out.println(failures == 0 ? "ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" : "ПРОВАЛОВ: " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static boolean escapes(Runnable action) {
        try {
            action.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "  ok   " : "  FAIL ") + name);
    }
}
