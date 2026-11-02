package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Skins;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Менеджер скинов: локальная библиотека, загрузка с Mojang, экспорт в сборки. */
public final class SkinDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final List<Instance> instances;
    private final DefaultListModel<Map<String, Object>> model = new DefaultListModel<>();
    private final JList<Map<String, Object>> list = new JList<>(model);
    private final JLabel preview = new JLabel();
    private final JLabel info = new JLabel();

    public SkinDialog(JFrame owner, Theme theme, List<Instance> instances) {
        super(owner, "Скины", true);
        this.theme = theme;
        this.instances = instances;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(12, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        list.setBackground(theme.card);
        list.setCellRenderer(new SkinRendererRow());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(52);
        list.addListSelectionListener(e -> updatePreview());
        JScrollPane listScroll = Ui.borderlessScroll(list, theme);
        listScroll.setPreferredSize(new Dimension(280, 380));

        preview.setPreferredSize(new Dimension(160, 320));
        preview.setHorizontalAlignment(SwingConstants.CENTER);
        preview.setVerticalAlignment(SwingConstants.TOP);
        info.setFont(Theme.uiFont(Font.PLAIN, 12));
        info.setForeground(theme.muted);
        info.setVerticalAlignment(SwingConstants.TOP);
        info.setPreferredSize(new Dimension(260, 320));

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBorder(new EmptyBorder(0, 6, 0, 0));
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.setAlignmentX(Component.LEFT_ALIGNMENT);
        rightPanel.add(preview);
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(info);

        JButton fromFile = Ui.miniButton("Из файла…", theme);
        fromFile.addActionListener(e -> addFromFile());
        JButton byNick = Ui.miniButton("По нику…", theme);
        byNick.addActionListener(e -> addByNick());
        JButton remove = Ui.miniButton("Удалить", theme);
        remove.addActionListener(e -> deleteSelected());
        JButton apply = Ui.primaryButton("Применить в сборки", theme);
        apply.addActionListener(e -> applyToInstances());
        JButton close = Ui.miniButton("Закрыть", theme);
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new BorderLayout(8, 0));
        buttons.setOpaque(false);
        buttons.add(Ui.buttonRow(fromFile, byNick, remove, apply), BorderLayout.CENTER);
        buttons.add(close, BorderLayout.EAST);

        root.add(listScroll, BorderLayout.WEST);
        root.add(rightPanel, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(760, 520));
        setLocationRelativeTo(owner);
        reload();
    }

    public void showDialog() {
        setVisible(true);
    }

    private void reload() {
        model.clear();
        for (Map<String, Object> entry : Skins.loadIndex()) {
            model.addElement(entry);
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        updatePreview();
    }

    private void updatePreview() {
        Map<String, Object> entry = list.getSelectedValue();
        if (entry == null) {
            preview.setIcon(null);
            info.setText("<html><body style='width:240px'>Скинов пока нет.<br><br>"
                    + "Добавь PNG (64×64 или 64×32) из файла или скачай по нику лицензионного игрока."
                    + "</body></html>");
            return;
        }
        byte[] data = Skins.readSkin(entry);
        if (data == null) {
            preview.setIcon(null);
            info.setText("Файл скина не найден.");
            return;
        }
        byte[] png = Skins.renderPreviewPng(data, 260);
        Icon icon = Ui.imageIcon(png, 260);
        preview.setIcon(icon);
        info.setText("<html><body style='width:240px'><b>"
                + escape(com.frimer.launcher.core.Json.str(entry.get("name"), "Скин"))
                + "</b><br>источник: "
                + escape(com.frimer.launcher.core.Json.str(entry.get("source"), "файл"))
                + "</body></html>");
    }

    private void addFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выбери PNG со скином");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG (*.png)", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        try {
            byte[] data = Files.readAllBytes(file);
            Skins.addLocal(data, stripPng(file.getFileName().toString()), "файл");
            reload();
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Не удалось добавить скин",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addByNick() {
        String nick = JOptionPane.showInputDialog(this,
                "Ник лицензионного игрока:", "Скин по нику", JOptionPane.PLAIN_MESSAGE);
        if (nick == null || nick.isBlank()) {
            return;
        }
        String clean = nick.strip();
        try {
            byte[] data = Skins.fetchByUsername(clean);
            Skins.addLocal(data, clean, "Mojang: " + clean);
            reload();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Не удалось получить скин",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        Map<String, Object> entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        Skins.deleteLocal(com.frimer.launcher.core.Json.str(entry.get("id"), ""));
        reload();
    }

    private void applyToInstances() {
        Map<String, Object> entry = list.getSelectedValue();
        if (entry == null) {
            JOptionPane.showMessageDialog(this, "Выбери скин в списке.", "Скин не выбран",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        byte[] data = Skins.readSkin(entry);
        if (data == null) {
            return;
        }
        String nick = com.frimer.launcher.core.AppSettings.nickname();
        List<Path> written = Skins.exportToCustomSkinLoader(nick, data, instances);
        JOptionPane.showMessageDialog(this,
                written.isEmpty()
                        ? "Сборок пока нет — скин сохранён только в библиотеке."
                        : "Скин разложен в " + written.size() + " ссобор(ы) (CustomSkinLoader/LocalSkin).",
                "Готово", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String stripPng(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                ? name.substring(0, name.length() - 4) : name;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Строка списка с миниатюрой. */
    private final class SkinRendererRow extends JPanel implements ListCellRenderer<Map<String, Object>> {
        private static final long serialVersionUID = 1L;
        private final JLabel icon = new JLabel();
        private final JLabel name = new JLabel();

        SkinRendererRow() {
            setLayout(new BorderLayout(10, 0));
            icon.setPreferredSize(new Dimension(32, 42));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            name.setFont(Theme.uiFont(Font.PLAIN, 13));
            add(icon, BorderLayout.WEST);
            add(name, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Map<String, Object>> list,
                                                      Map<String, Object> value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            byte[] data = Skins.readSkin(value);
            Icon preview = null;
            if (data != null) {
                byte[] png = Skins.renderPreviewPng(data, 32);
                preview = Ui.imageIcon(png, 32);
            }
            icon.setIcon(preview);
            name.setText(com.frimer.launcher.core.Json.str(value.get("name"), "Скин"));
            setBackground(isSelected ? theme.cardSel : theme.card);
            name.setForeground(isSelected ? theme.accent : theme.text);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border),
                    new EmptyBorder(4, 8, 4, 8)));
            setOpaque(true);
            return this;
        }
    }
}
