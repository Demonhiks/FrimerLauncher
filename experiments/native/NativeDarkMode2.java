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
 * Вторая попытка: тёмный заголовок применяется ДО показа окна
 * (нативное окно уже создано через addNotify). Плюс принудительный
 * пересчёт рамки через SetWindowPos(..., SWP_FRAMECHANGED).
 *
 * Сборка: javac --release 22 -d build/native experiments/native/NativeDarkMode2.java
 * Запуск: java -cp build/native NativeDarkMode2
 */
public final class NativeDarkMode2 {

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_OLD = 19;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_FRAMECHANGED = 0x0020;

    public static void main(String[] args) throws Throwable {
        javax.swing.JFrame[] holder = new javax.swing.JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame("NativeDark2");
            frame.setSize(540, 330);
            frame.setLocation(160, 160);
            frame.getContentPane().setBackground(new Color(0x15, 0x18, 0x1e));
            frame.addNotify();
            holder[0] = frame;
        });
        Thread.sleep(400);

        long hwnd = findWindow("NativeDark2");
        System.out.println("HWND = 0x" + Long.toHexString(hwnd) + (hwnd == 0 ? " (не найдено)" : ""));
        if (hwnd == 0) {
            System.exit(2);
        }

        int hr20 = setDark(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, true);
        System.out.println("атрибут 20 -> HRESULT 0x" + Integer.toHexString(hr20));
        repaintFrame(hwnd);

        SwingUtilities.invokeAndWait(() -> holder[0].setVisible(true));
        Thread.sleep(1200);

        Rectangle bar = new Rectangle(160, 160, 540, 32);
        BufferedImage after = new Robot().createScreenCapture(bar);
        System.out.println("после показа, яркость заголовка = " + brightness(after));

        int hr19 = setDark(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_OLD, true);
        System.out.println("атрибут 19 -> HRESULT 0x" + Integer.toHexString(hr19));
        repaintFrame(hwnd);
        Thread.sleep(900);

        BufferedImage after2 = new Robot().createScreenCapture(bar);
        System.out.println("после атрибута 19, яркость = " + brightness(after2));

        File out = new File("build/native");
        out.mkdirs();
        ImageIO.write(after, "png", new File(out, "titlebar2-20.png"));
        ImageIO.write(after2, "png", new File(out, "titlebar2-19.png"));
        System.out.println("снимки: build/native/titlebar2-20.png, titlebar2-19.png");

        boolean dark = brightness(after) < 150 || brightness(after2) < 150;
        System.out.println(dark ? "РЕЗУЛЬТАТ: заголовок потемнел" : "РЕЗУЛЬТАТ: заголовок остался светлым");
        Thread.sleep(300);
        System.exit(dark ? 0 : 1);
    }

    private static long findWindow(String title) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            MethodHandle findWindowW = linker.downcallHandle(
                    SymbolLookup.libraryLookup("user32.dll", arena).find("FindWindowW").orElseThrow(),
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

    private static int setDark(long hwnd, int attribute, boolean enabled) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            MethodHandle setAttribute = linker.downcallHandle(
                    SymbolLookup.libraryLookup("dwmapi.dll", arena)
                            .find("DwmSetWindowAttribute").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            MemorySegment value = arena.allocate(ValueLayout.JAVA_INT);
            value.set(ValueLayout.JAVA_INT, 0, enabled ? 1 : 0);
            return (int) setAttribute.invoke(MemorySegment.ofAddress(hwnd), attribute, value, 4);
        }
    }

    private static void repaintFrame(long hwnd) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            MethodHandle setWindowPos = linker.downcallHandle(
                    SymbolLookup.libraryLookup("user32.dll", arena).find("SetWindowPos").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            setWindowPos.invoke(MemorySegment.ofAddress(hwnd), MemorySegment.NULL,
                    0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED);
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
