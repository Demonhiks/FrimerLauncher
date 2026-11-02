package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Modrinth;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Миры сборки: размер, открытие папки, удаление и датапаки с Modrinth. */
public final class WorldsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final Instance instance;
    private final Path worldsFolder;
    private final DefaultListModel<Path> model = new DefaultListModel<>();
    private final JList<Path> list = new JList<>(model);
    private final JLabel info = new JLabel(" ");

    public WorldsDialog(JFrame owner, Theme theme, Instance instance) {
        super(owner, "Миры — " + instance.name, true);
        this.theme = theme;
        this.instance = instance;
        this.worldsFolder = instance.folder.resolve("saves");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 16, 12, 16));

        JLabel title = new JLabel("Миры сборки «" + instance.name + "»");
        title.setFont(Theme.titleFont(Font.BOLD, 16));
        title.setForeground(theme.text);

        list.setBackground(theme.card);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(38);
        list.setCellRenderer(new WorldRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedFolder();
                }
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        list.setSelectedIndex(index);
                    }
                    showMenu(e);
                }
            }
        });
        JScrollPane scroll = Ui.scroll(list, theme);
        scroll.setPreferredSize(new Dimension(620, 360));

        JButton refresh = Ui.button("Обновить", theme);
        refresh.addActionListener(e -> reload());
        JButton openFolder = Ui.button("Открыть папку", theme);
        openFolder.addActionListener(e -> Ui.openFolder(worldsFolder));
        JButton datapack = Ui.button("Скачать датапак", theme);
        datapack.addActionListener(e -> downloadDatapack());
        JButton delete = Ui.button("Удалить мир", theme);
        delete.addActionListener(e -> deleteSelected());
        JButton close = Ui.miniButton("Закрыть", theme);
        close.addActionListener(e -> dispose());

        info.setFont(Theme.uiFont(Font.PLAIN, 12));
        info.setForeground(theme.muted);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(Ui.buttonRow(refresh, openFolder, datapack), BorderLayout.WEST);
        JPanel right = Ui.buttonRow(delete, close);
        actions.add(right, BorderLayout.EAST);

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
        setSize(new Dimension(680, 520));
        setLocationRelativeTo(owner);
        reload();
    }

    public void showDialog() {
        setVisible(true);
    }

    private void reload() {
        model.clear();
        List<Path> worlds = new ArrayList<>();
        if (Files.isDirectory(worldsFolder)) {
            try (var stream = Files.list(worldsFolder)) {
                stream.filter(Files::isDirectory).forEach(worlds::add);
            } catch (java.io.IOException ignored) {
                // папка недоступна
            }
        }
        worlds.sort(java.util.Comparator.comparing(p -> p.getFileName().toString(),
                String.CASE_INSENSITIVE_ORDER));
        for (Path world : worlds) {
            model.addElement(world);
        }
        if (worlds.isEmpty()) {
            info.setText("Миров нет — создай новый в игре.");
        } else {
            info.setText("Миров: " + worlds.size());
            list.setSelectedIndex(0);
        }
    }

    private void showMenu(MouseEvent e) {
        Path world = list.getSelectedValue();
        if (world == null) {
            return;
        }
        List<Ui.MenuItemSpec> items = new ArrayList<>();
        items.add(Ui.MenuItemSpec.of("Скачать датапак…", () -> downloadDatapackTo(world)));
        items.add(Ui.MenuItemSpec.of("Открыть папку мира", () -> Ui.openFolder(world)));
        items.add(Ui.MenuItemSpec.separator());
        items.add(Ui.MenuItemSpec.of("Удалить мир", this::deleteSelected));
        Ui.showMenu(list, e.getX(), e.getY(), theme, items);
    }

    private void openSelectedFolder() {
        Path world = list.getSelectedValue();
        if (world != null) {
            Ui.openFolder(world);
        }
    }

    private void downloadDatapack() {
        Path world = list.getSelectedValue();
        if (world == null) {
            JOptionPane.showMessageDialog(this, "Сначала выбери мир в списке.", "Нет мира",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        downloadDatapackTo(world);
    }

    private void downloadDatapackTo(Path world) {
        Path target = world.resolve("datapacks");
        try {
            Files.createDirectories(target);
        } catch (java.io.IOException ignored) {
            // создадим при установке
        }
        ModrinthDialog dialog = new ModrinthDialog((JFrame) getOwner(), theme, instance,
                Modrinth.DATAPACK, target);
        dialog.showDialog();
    }

    private void deleteSelected() {
        Path world = list.getSelectedValue();
        if (world == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Удалить мир «" + world.getFileName() + "»? Это необратимо.",
                "Удаление мира", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        com.frimer.launcher.core.JavaProvisioner.deleteDir(world);
        reload();
    }

    /** Строка списка: название мира и его размер. */
    private final class WorldRenderer extends JPanel implements ListCellRenderer<Path> {
        private static final long serialVersionUID = 1L;
        private final JLabel name = new JLabel();
        private final JLabel size = new JLabel();

        WorldRenderer() {
            setLayout(new BorderLayout());
            name.setFont(Theme.uiFont(Font.PLAIN, 14));
            size.setFont(Theme.uiFont(Font.PLAIN, 11));
            size.setHorizontalAlignment(JLabel.RIGHT);
            add(name, BorderLayout.CENTER);
            add(size, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Path> list, Path value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            name.setText(value.getFileName().toString());
            size.setText(Utils.fmtSize(Utils.dirSize(value)));
            setBackground(isSelected ? theme.cardSel : theme.card);
            name.setForeground(isSelected ? theme.accent : theme.text);
            size.setForeground(theme.muted);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border),
                    new EmptyBorder(6, 10, 6, 10)));
            setOpaque(true);
            return this;
        }
    }

    /** Пересчитать список (например, после установки датапака). */
    public void refreshWorlds() {
        SwingUtilities.invokeLater(this::reload);
    }
}
