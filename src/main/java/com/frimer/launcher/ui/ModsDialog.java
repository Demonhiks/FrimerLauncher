package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.ModFiles;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
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
import javax.swing.border.EmptyBorder;

/** Список модов сборки: включить/отключить, удалить, открыть папку. */
public final class ModsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final Instance instance;
    private final DefaultListModel<ModFiles.ModEntry> model = new DefaultListModel<>();
    private final JList<ModFiles.ModEntry> list = new JList<>(model);
    private final JLabel counter = new JLabel();

    public ModsDialog(JFrame owner, Theme theme, Instance instance) {
        super(owner, "Моды — " + instance.name, true);
        this.theme = theme;
        this.instance = instance;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        list.setBackground(theme.card);
        list.setForeground(theme.text);
        list.setCellRenderer(new ModRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(44);
        JScrollPane scroll = Ui.scroll(list, theme);
        scroll.setPreferredSize(new Dimension(640, 380));

        counter.setFont(Theme.uiFont(Font.PLAIN, 12));
        counter.setForeground(theme.muted);

        JButton toggle = Ui.button("Включить / Отключить", theme);
        toggle.addActionListener(e -> toggleSelected());
        JButton remove = Ui.button("Удалить", theme);
        remove.addActionListener(e -> deleteSelected());
        JButton refresh = Ui.miniButton("Обновить", theme);
        refresh.addActionListener(e -> reload());
        JButton folder = Ui.miniButton("Открыть папку mods", theme);
        folder.addActionListener(e -> Ui.openFolder(instance.folder.resolve("mods")));
        JButton close = Ui.miniButton("Закрыть", theme);
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new BorderLayout(8, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.buttonRow(toggle, remove, refresh, folder), BorderLayout.CENTER);
        buttons.add(close, BorderLayout.EAST);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.X_AXIS));
        top.add(counter);
        top.add(Box.createHorizontalGlue());

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(700, 520));
        setLocationRelativeTo(owner);
        reload();
    }

    public void showDialog() {
        setVisible(true);
    }

    private void reload() {
        model.clear();
        List<ModFiles.ModEntry> mods = ModFiles.list(instance.folder.resolve("mods"));
        for (ModFiles.ModEntry mod : mods) {
            model.addElement(mod);
        }
        int enabled = (int) mods.stream().filter(ModFiles.ModEntry::enabled).count();
        counter.setText("Всего: " + mods.size() + " · включено: " + enabled
                + " · папка: " + instance.folder.resolve("mods").getFileName());
        if (mods.isEmpty()) {
            counter.setText("Модов нет — положи .jar в папку mods сборки");
        }
    }

    private void toggleSelected() {
        ModFiles.ModEntry selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        try {
            ModFiles.toggle(selected);
            reload();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        ModFiles.ModEntry selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Удалить " + selected.fileName() + "?", "Удаление мода",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ModFiles.delete(selected);
            reload();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Строка списка: иконка мода, название и файл. */
    private final class ModRenderer extends JPanel implements ListCellRenderer<ModFiles.ModEntry> {
        private static final long serialVersionUID = 1L;
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel sub = new JLabel();

        ModRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(4, 8, 4, 8));
            icon.setPreferredSize(new Dimension(32, 32));
            icon.setHorizontalAlignment(JLabel.CENTER);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            title.setFont(Theme.titleFont(Font.BOLD, 13));
            sub.setFont(Theme.uiFont(Font.PLAIN, 11));
            text.add(title);
            text.add(sub);
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ModFiles.ModEntry> list,
                                                      ModFiles.ModEntry value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Icon modIcon = Ui.imageIcon(value.icon(), 32);
            icon.setIcon(modIcon != null ? modIcon
                    : Ui.badge(value.title().isEmpty() ? "?" : value.title().substring(0, 1).toUpperCase(),
                    theme.card, theme.muted, 32));
            title.setText(value.title());
            String tail = value.fileName() + " · " + Utils.fmtSize(value.size());
            sub.setText(value.enabled() ? tail : "выключен · " + tail);
            setBackground(isSelected ? theme.cardSel : theme.card);
            title.setForeground(value.enabled() ? (isSelected ? theme.accent : theme.text)
                    : theme.disabledText);
            sub.setForeground(theme.muted);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border),
                    new EmptyBorder(4, 8, 4, 8)));
            setOpaque(true);
            return this;
        }
    }

    private static Color alpha(Color base, int a) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }
}
