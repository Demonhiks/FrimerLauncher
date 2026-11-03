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
        out.append("1. Скачайте `FrimerLauncher-win64.zip` ниже.\n");
        out.append("2. Распакуйте в любую папку.\n");
        out.append("3. Запустите `Frimer Launcher.exe` — Java ставить не нужно, она уже внутри.\n\n");
        out.append("Обычный jar тоже лежит в релизе: `java -jar FrimerLauncher.jar` (нужна Java 17+).\n\n");
        out.append("Данные лежат в `~/.frimerlauncher` (`C:\\Users\\<имя>\\.frimerlauncher`) — сборки,\n");
        out.append("сервера и настройки из Python-версии подхватываются сами.\n\n");
        out.append("> Windows SmartScreen может предупредить о неизвестном издателе — это обычное\n");
        out.append("> поведение для неподписанной сборки, «Подробнее» → «Выполнить в любом случае».\n");

        Files.writeString(Path.of("changes.md"), out, StandardCharsets.UTF_8);
        System.out.print(out);
    }
}
