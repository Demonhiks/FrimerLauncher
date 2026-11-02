import com.frimer.launcher.core.JavaProvisioner;
import com.frimer.launcher.core.Modrinth;
import com.frimer.launcher.core.Utils;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Проверка Modrinth без GUI: поиск, установка и проверка хешей в временную папку.
 * Запуск: javac -cp build/classes -d build/test tools/TestModrinth.java
 *          java -cp "build/classes;build/test" TestModrinth
 */
public final class TestModrinth {

    public static void main(String[] args) throws Exception {
        String mc = args.length > 0 ? args[0] : "1.21.1";
        String loader = args.length > 1 ? args[1] : "NeoForge";
        String query = args.length > 2 ? args[2] : "sodium";

        Modrinth.SearchResult result = Modrinth.search(query, mc, loader, Modrinth.MOD, 0, 5);
        System.out.println("поиск: hits=" + result.hits().size() + ", всего=" + result.total());
        for (Modrinth.Hit hit : result.hits()) {
            System.out.println("  - " + hit.title() + " (" + hit.author() + ", "
                    + Utils.fmtNum(hit.downloads()) + ")");
        }
        if (result.hits().isEmpty()) {
            System.out.println("ничего не найдено — проверять установку нечего");
            return;
        }

        Modrinth.Hit target = result.hits().get(0);
        Path dest = Files.createTempDirectory("frimer-mods-");
        try {
            Modrinth.Installed installed = Modrinth.install(target, mc, loader, Modrinth.MOD,
                    dest, null, null);
            System.out.println("установлено файлов: " + installed.files().size()
                    + ", предупреждений: " + installed.warnings().size());
            for (String file : installed.files()) {
                Path path = dest.resolve(file);
                System.out.println("  " + file + " · " + Utils.fmtSize(Files.size(path))
                        + " · sha1=" + Utils.sha1(path).substring(0, 12) + "…");
            }
            for (String warning : installed.warnings()) {
                System.out.println("  ! " + warning);
            }
        } finally {
            JavaProvisioner.deleteDir(dest);
        }
        System.out.println("ОК");
    }
}
