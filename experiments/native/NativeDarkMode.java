import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Демонстрация «Java + C без JNI»: вызов Win32 через java.lang.foreign (JDK 22+).
 * Включает тёмный заголовок окна через DwmSetWindowAttribute и снимает полосу
 * заголовка до/после, чтобы результат было видно.
 *
 * Сборка:  javac --release 22 -d build/native experiments/native/NativeDarkMode.java
 * Запуск:  java -cp build/native NativeDarkMode
 */
public final class NativeDarkMode {

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

    public static void main(String[] args) throws Throwable {
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame("NativeDarkMode");
            frame.setSize(540, 330);
            frame.setLocation(140, 140);
            frame.getContentPane().setBackground(new Color(0x15, 0x18, 0x1e));
            frame.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
        Thread.sleep(1000);

        Rectangle titleBar = new Rectangle(140, 140, 540, 32);
        BufferedImage before = new Robot().createScreenCapture(titleBar);
        System.out.println("до:    средняя яркость заголовка = " + brightness(before));

        long hwnd = findWindow("NativeDarkMode");
        if (hwnd == 0) {
            System.out.println("не нашёл окно через FindWindowW");
            System.exit(2);
        }
        int hr = setDarkMode(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, true);
        System.out.println("HWND=0x" + Long.toHexString(hwnd) + "  DwmSetWindowAttribute -> HRESULT 0x"
                + Integer.toHexString(hr) + (hr == 0 ? " (успех)" : " (не поддерживается)"));
        Thread.sleep(700);

        BufferedImage after = new Robot().createScreenCapture(titleBar);
        System.out.println("после: средняя яркость заголовка = " + brightness(after));

        File out = new File("build/native");
        out.mkdirs();
        ImageIO.write(before, "png", new File(out, "titlebar-before.png"));
        ImageIO.write(after, "png", new File(out, "titlebar-after.png"));
        System.out.println("снимки: build/native/titlebar-before.png, titlebar-after.png");

        Thread.sleep(400);
        System.exit(hr == 0 ? 0 : 1);
    }

    private static long findWindow(String title) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32.dll", arena);
            MethodHandle findWindowW = linker.downcallHandle(
                    user32.find("FindWindowW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));
            char[] chars = (title + "\0").toCharArray();
            MemorySegment text = arena.allocate(ValueLayout.JAVA_CHAR, chars.length);
            for (int i = 0; i < chars.length; i++) {
                text.setAtIndex(ValueLayout.JAVA_CHAR, i, chars[i]);
            }
            MemorySegment handle = (MemorySegment) findWindowW.invoke(MemorySegment.NULL, text);
            return handle.address();
        }
    }

    private static int setDarkMode(long hwnd, int attribute, boolean enabled) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup dwmapi = SymbolLookup.libraryLookup("dwmapi.dll", arena);
            MethodHandle setAttribute = linker.downcallHandle(
                    dwmapi.find("DwmSetWindowAttribute").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            MemorySegment value = arena.allocate(ValueLayout.JAVA_INT);
            value.set(ValueLayout.JAVA_INT, 0, enabled ? 1 : 0);
            return (int) setAttribute.invoke(MemorySegment.ofAddress(hwnd), attribute, value, 4);
        }
    }

    private static int brightness(BufferedImage image) {
        long sum = 0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                sum += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                count += 3;
            }
        }
        return count == 0 ? 0 : (int) (sum / count);
    }
}
