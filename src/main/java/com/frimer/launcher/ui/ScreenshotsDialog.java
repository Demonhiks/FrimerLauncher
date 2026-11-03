package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Галерея скриншотов сборки: превью, открытие, копирование в буфер и удаление. */
public final class ScreenshotsDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private static final int THUMB_WIDTH = 224;
    private static final int THUMB_HEIGHT = 126;
    private static final int MAX_SHOWN = 300;
    private static final Set<String> EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".bmp", ".webp");

    private final Theme theme;
    private final Instance instance;
    private final Path folder;
    private final WrapPanel grid = new WrapPanel(8);
    private final Set<Path> selected = new LinkedHashSet<>();
    private final JLabel info = new JLabel(" ");

    public ScreenshotsDialog(JFrame owner, Theme theme, Instance instance) {
        super(owner, "Скрины — " + instance.name, true, theme);
        this.theme = theme;
        this.instance = instance;
        this.folder = instance.folder.resolve("screenshots");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 16, 12, 16));

        JLabel title = new JLabel("Скриншоты сборки «" + instance.name + "»");
        title.setFont(Theme.titleFont(Font.BOLD, 16));
        title.setForeground(theme.text);

        grid.setBackground(theme.bg);
        JScrollPane scroll = Ui.borderlessScroll(grid, theme);
        scroll.setPreferredSize(new Dimension(760, 420));

        JButton refresh = Ui.button("Обновить", theme);
        refresh.addActionListener(e -> reload());
        JButton openFolder = Ui.button("Открыть папку", theme);
        openFolder.addActionListener(e -> Ui.openFolder(folder));
        JButton copy = Ui.button("Копировать", theme);
        copy.addActionListener(e -> copySelected());
        JButton open = Ui.button("Открыть", theme);
        open.addActionListener(e -> openSelected());
        JButton delete = Ui.button("Удалить", theme);
        delete.addActionListener(e -> deleteSelected());
        JButton close = Ui.miniButton("Закрыть", theme);
        close.addActionListener(e -> dispose());

        info.setFont(Theme.uiFont(Font.PLAIN, 12));
        info.setForeground(theme.muted);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(Ui.buttonRow(refresh, openFolder, open, copy), BorderLayout.WEST);
        actions.add(Ui.buttonRow(delete, close), BorderLayout.EAST);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(info);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(actions);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(title);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(820, 580));
        setLocationRelativeTo(owner);
        reload();
    }

    public void showDialog() {
        setVisible(true);
    }

    private void reload() {
        grid.removeAll();
        selected.clear();
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (var stream = Files.list(folder)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> EXTENSIONS.contains(extensionOf(p)))
                        .forEach(files::add);
            } catch (IOException ignored) {
                // папка недоступна
            }
        }
        files.sort((a, b) -> Long.compare(lastModified(b), lastModified(a)));

        int total = files.size();
        List<Path> shown = files.size() > MAX_SHOWN ? files.subList(0, MAX_SHOWN) : files;
        if (total == 0) {
            info.setText("Скринов пока нет — сделай скриншот в игре (F2).");
        } else if (total > MAX_SHOWN) {
            info.setText("Показаны " + MAX_SHOWN + " новейших из " + total);
        } else {
            info.setText("Скринов: " + total);
        }
        grid.revalidate();
        grid.repaint();
        if (shown.isEmpty()) {
            return;
        }

        // превью грузим в фоне, чтобы окно не подвисало на 300 картинках
        new Thread(() -> {
            for (Path file : shown) {
                ImageIcon icon = thumbnail(file);
                SwingUtilities.invokeLater(() -> {
                    grid.add(tile(file, icon));
                    grid.revalidate();
                    grid.repaint();
                });
            }
        }, "frimer-screenshot-thumbs").start();
    }

    private JPanel tile(Path file, ImageIcon icon) {
        JPanel tile = new JPanel(new BorderLayout(0, 4));
        tile.setBackground(theme.card);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border), new EmptyBorder(6, 6, 6, 6)));
        JLabel image = new JLabel(icon);
        image.setPreferredSize(new Dimension(THUMB_WIDTH, THUMB_HEIGHT));
        image.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel name = new JLabel(Utils.shortText(file.getFileName().toString(), 28));
        name.setFont(Theme.uiFont(Font.PLAIN, 11));
        name.setForeground(theme.muted);
        tile.add(image, BorderLayout.CENTER);
        tile.add(name, BorderLayout.SOUTH);
        tile.setPreferredSize(new Dimension(THUMB_WIDTH + 14, THUMB_HEIGHT + 34));
        tile.setToolTipText(file.getFileName().toString());

        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    select(file, false);
                    showMenu(tile, e, file);
                    return;
                }
                if (e.getClickCount() == 2) {
                    openFile(file);
                    return;
                }
                select(file, e.isControlDown());
            }
        });
        if (selected.contains(file)) {
            tile.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(theme.accent, 2), new EmptyBorder(5, 5, 5, 5)));
        }
        return tile;
    }

    private void select(Path file, boolean add) {
        if (!add) {
            selected.clear();
        }
        if (!selected.remove(file)) {
            selected.add(file);
        }
        applySelectionBorders();
        info.setText("Выбрано: " + selected.size());
    }

    private void applySelectionBorders() {
        int index = 0;
        for (java.awt.Component component : grid.getComponents()) {
            if (!(component instanceof JPanel tile)) {
                continue;
            }
            Path file = tileFile(tile);
            boolean isSelected = file != null && selected.contains(file);
            tile.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isSelected ? theme.accent : theme.border, isSelected ? 2 : 1),
                    new EmptyBorder(isSelected ? 5 : 6, isSelected ? 5 : 6,
                            isSelected ? 5 : 6, isSelected ? 5 : 6)));
            index++;
        }
        if (index == 0) {
            return;
        }
        grid.revalidate();
        grid.repaint();
    }

    private Path tileFile(JPanel tile) {
        String tip = tile.getToolTipText();
        return tip == null ? null : folder.resolve(tip);
    }

    private void showMenu(JPanel anchor, MouseEvent e, Path file) {
        List<Ui.MenuItemSpec> items = new ArrayList<>();
        items.add(Ui.MenuItemSpec.of("Открыть", () -> openFile(file)));
        items.add(Ui.MenuItemSpec.of("Копировать картинку", this::copySelected));
        items.add(Ui.MenuItemSpec.of("Показать в папке", () -> Ui.openFolder(folder)));
        items.add(Ui.MenuItemSpec.separator());
        items.add(Ui.MenuItemSpec.of("Удалить", this::deleteSelected));
        Ui.showMenu(anchor, e.getX(), e.getY(), theme, items);
    }

    private void openSelected() {
        Path first = selected.isEmpty() ? null : selected.iterator().next();
        if (first != null) {
            openFile(first);
        }
    }

    private void openFile(Path file) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file.toFile());
                return;
            }
        } catch (Exception ignored) {
            // падаем в системную команду
        }
        try {
            new ProcessBuilder("explorer", file.toString()).start();
        } catch (IOException ignored) {
            // не открылось
        }
    }

    private void copySelected() {
        Path first = selected.isEmpty() ? null : selected.iterator().next();
        if (first == null) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(first.toFile());
            if (image == null) {
                return;
            }
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.Transferable() {
                        @Override
                        public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                            return new java.awt.datatransfer.DataFlavor[]{
                                    java.awt.datatransfer.DataFlavor.imageFlavor};
                        }

                        @Override
                        public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                            return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
                        }

                        @Override
                        public Object getTransferData(java.awt.datatransfer.DataFlavor flavor)
                                throws java.awt.datatransfer.UnsupportedFlavorException {
                            if (!isDataFlavorSupported(flavor)) {
                                throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                            }
                            return image;
                        }
                    }, null);
            info.setText("Скопировано: " + first.getFileName());
        } catch (IOException e) {
            info.setText("Не удалось скопировать");
        }
    }

    private void deleteSelected() {
        if (selected.isEmpty()) {
            return;
        }
        String what = selected.size() == 1
                ? "«" + selected.iterator().next().getFileName() + "»"
                : "скринов: " + selected.size();
        int answer = JOptionPane.showConfirmDialog(this, "Удалить " + what + "?",
                "Удаление", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        for (Path file : new ArrayList<>(selected)) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // файл занят
            }
        }
        reload();
    }

    private ImageIcon thumbnail(Path file) {
        try {
            BufferedImage source = ImageIO.read(file.toFile());
            if (source == null) {
                return null;
            }
            double scale = Math.min((double) THUMB_WIDTH / source.getWidth(),
                    (double) THUMB_HEIGHT / source.getHeight());
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage out = new BufferedImage(THUMB_WIDTH, THUMB_HEIGHT,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(new Color(12, 14, 18));
            g.fillRect(0, 0, THUMB_WIDTH, THUMB_HEIGHT);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, (THUMB_WIDTH - width) / 2, (THUMB_HEIGHT - height) / 2,
                    width, height, null);
            g.dispose();
            return new ImageIcon(out);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    /** Список выбранных файлов (для тестов). */
    public List<Path> selectedFiles() {
        return new ArrayList<>(selected);
    }

    /** Папка скриншотов (для тестов). */
    public Path folder() {
        return folder;
    }

    /** Открыть папку скриншотов напрямую (нужно для тестового рендера). */
    public static Path screenshotsFolder(Instance instance) {
        return instance.folder.resolve("screenshots");
    }

    /** Проверить, что файл — картинка поддерживаемого формата. */
    public static boolean isSupported(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(name.substring(dot));
    }
}
