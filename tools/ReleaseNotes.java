import com.frimer.launcher.core.Changelog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Заметки к релизу из Changelog.java — пишет changes.md для GitHub Actions.
 * Запуск: java -cp "target/classes;build/notes" ReleaseNotes 2.3.3
 */
public final class ReleaseNotes {

    private ReleaseNotes() {
    }

    public static void main(String[] args) throws Exception {
        String version = args.length > 0 ? args[0].trim() : "";
        StringBuilder out = new StringBuilder();
        out.append("## Frimer Launcher ").append(version).append("\n\n");
        out.append("Minecraft-лаунчер: Vanilla / Fabric / Quilt / Forge / NeoForge, сервера, скины,\n");
        out.append("моды и браузер Modrinth.\n\n");

        Changelog.Entry entry = null;
        for (Changelog.Entry candidate : Changelog.entries()) {
            if (candidate.version().equals(version)) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) {
            out.append("### Что нового\n- (нет записи в журнале изменений)\n");
        } else {
            out.append("### Что нового в ").append(version)
                    .append(" (").append(entry.date()).append(")\n");
            for (String change : entry.changes()) {
                out.append("- ").append(change).append('\n');
            }
        }

        out.append('\n');
        out.append("### Как запустить\n");
        out.append("Java ставить не нужно — она уже внутри сборки.\n\n");
        out.append("**Windows** — распакуйте `FrimerLauncher-win64.zip` и запустите `Frimer Launcher.exe`.\n\n");
        out.append("**Linux** — распакуйте `FrimerLauncher-linux-x64.tar.gz` и запустите\n");
        out.append("`Frimer Launcher/bin/Frimer Launcher`.\n\n");
        out.append("**macOS** — распакуйте `FrimerLauncher-macos-arm64.zip` (Apple Silicon) или\n");
        out.append("`FrimerLauncher-macos-x64.zip` (Intel) и запустите `Frimer Launcher.app`.\n");
        out.append("Первый запуск — правый клик по значку → «Открыть»: сборка без подписи.\n\n");
        out.append("Обычный jar тоже лежит в релизе: `java -jar FrimerLauncher.jar` (нужна Java 17+).\n\n");
        out.append("Данные лежат в `~/.frimerlauncher` (`C:\\Users\\<имя>\\.frimerlauncher`) — сборки,\n");
        out.append("сервера и настройки из Python-версии подхватываются сами.\n\n");
        out.append("> Windows SmartScreen и macOS Gatekeeper предупреждают о неподписанной сборке —\n");
        out.append("> это обычное поведение: «Подробнее» → «Выполнить в любом случае».\n");

        Files.writeString(Path.of("changes.md"), out, StandardCharsets.UTF_8);
        System.out.print(out);
    }
}
