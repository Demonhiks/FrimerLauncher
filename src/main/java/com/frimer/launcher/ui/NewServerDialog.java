package com.frimer.launcher.ui;

import com.frimer.launcher.core.Loaders;
import com.frimer.launcher.core.Mojang;
import com.frimer.launcher.core.ServerInstance;
import com.frimer.launcher.core.ServerStore;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Диалог создания сервера: ядро, версия, порт, память. */
public final class NewServerDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final JTextField nameField = new JTextField("Мой сервер");
    private final JComboBox<String> coreCombo = new JComboBox<>(
            new String[]{"Paper", "Vanilla", "Fabric", "Forge", "NeoForge"});
    private final JComboBox<String> mcCombo = new JComboBox<>();
    private final JSpinner portSpinner = new JSpinner(new SpinnerNumberModel(25565, 1, 65535, 1));
    private final JSlider ramSlider = new JSlider(1024, 16384, 4096);
    private final JLabel ramLabel = new JLabel();
    private final JLabel hintLabel = new JLabel(" ");
    private final JLabel coreHint = new JLabel(" ");

    private ServerInstance result;

    public NewServerDialog(JFrame owner, Theme theme) {
        super(owner, "Новый сервер", true);
        this.theme = theme;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        root.add(fieldLabel("Название сервера"));
        nameField.setBackground(theme.input);
        nameField.setForeground(theme.text);
        nameField.setCaretColor(theme.accent);
        nameField.setFont(Theme.uiFont(Font.PLAIN, 14));
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border), new EmptyBorder(6, 8, 6, 8)));
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(nameField);
        root.add(Box.createVerticalStrut(12));

        root.add(fieldLabel("Ядро"));
        styleCombo(coreCombo);
        root.add(coreCombo);
        coreHint.setFont(Theme.uiFont(Font.PLAIN, 11));
        coreHint.setForeground(theme.muted);
        coreHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(coreHint);
        root.add(Box.createVerticalStrut(10));

        root.add(fieldLabel("Версия Minecraft"));
        styleCombo(mcCombo);
        mcCombo.addItem("загрузка версий...");
        root.add(mcCombo);
        root.add(Box.createVerticalStrut(12));

        root.add(fieldLabel("Порт (по умолчанию 25565)"));
        portSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        portSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(portSpinner);
        root.add(Box.createVerticalStrut(12));

        root.add(fieldLabel("Оперативная память"));
        ramSlider.setMinimum(1024);
        ramSlider.setMaximum(16384);
        ramSlider.setOpaque(false);
        ramSlider.setForeground(theme.accent);
        ramSlider.setBackground(theme.bg);
        ramSlider.addChangeListener(e -> updateRamLabel());
        root.add(ramSlider);
        ramLabel.setFont(Theme.uiFont(Font.PLAIN, 12));
        ramLabel.setForeground(theme.accent);
        ramLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(ramLabel);
        root.add(Box.createVerticalStrut(8));

        hintLabel.setFont(Theme.uiFont(Font.PLAIN, 11));
        hintLabel.setForeground(theme.muted);
        hintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(hintLabel);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(14, 0, 0, 0));
        JButton cancel = Ui.button("Отмена", theme);
        cancel.addActionListener(e -> {
            result = null;
            dispose();
        });
        JButton create = Ui.primaryButton("Создать и установить", theme);
        create.addActionListener(e -> create());
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(cancel);
        right.add(Box.createHorizontalStrut(8));
        right.add(create);
        buttons.add(right, BorderLayout.EAST);
        root.add(buttons);

        Ui.alignLeft(root);
        setContentPane(root);
        updateRamLabel();
        coreCombo.addActionListener(e -> {
            updateCoreHint();
            loadVersions();
        });
        setSize(new Dimension(440, 520));
        setResizable(false);
        setLocationRelativeTo(owner);
        updateCoreHint();
        loadVersions();
    }

    public ServerInstance showDialog() {
        setVisible(true);
        return result;
    }

    private void updateCoreHint() {
        String core = ((String) coreCombo.getSelectedItem()).toLowerCase(java.util.Locale.ROOT);
        coreHint.setText("<html><body style='width:380px'>" + ServerStore.coreHint(core) + "</body></html>");
    }

    private void loadVersions() {
        String core = ((String) coreCombo.getSelectedItem()).toLowerCase(java.util.Locale.ROOT);
        mcCombo.removeAllItems();
        mcCombo.addItem("загрузка версий...");
        Thread thread = new Thread(() -> {
            try {
                List<String> releases = Mojang.releaseVersions();
                Set<String> supported = Loaders.serverSupportedMc(core);
                List<String> filtered = releases.stream()
                        .filter(v -> supported == null || supported.contains(v))
                        .toList();
                SwingUtilities.invokeLater(() -> {
                    if (!core.equals(((String) coreCombo.getSelectedItem())
                            .toLowerCase(java.util.Locale.ROOT))) {
                        return;
                    }
                    mcCombo.removeAllItems();
                    if (filtered.isEmpty()) {
                        mcCombo.addItem("нет подходящих версий");
                        return;
                    }
                    for (String v : filtered) {
                        mcCombo.addItem(v);
                    }
                    mcCombo.setSelectedIndex(0);
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    mcCombo.removeAllItems();
                    mcCombo.addItem("нет связи с Mojang");
                    hintLabel.setText("Не удалось получить версии: " + e.getMessage());
                });
            }
        }, "frimer-server-versions");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateRamLabel() {
        int ram = Math.max(1024, (ramSlider.getValue() / 512) * 512);
        ramSlider.setValue(ram);
        ramLabel.setText(Utils.fmtGb(ram));
    }

    private void create() {
        String name = nameField.getText().strip();
        if (name.isEmpty()) {
            name = "Сервер";
        }
        Object mcValue = mcCombo.getSelectedItem();
        if (mcValue == null || mcValue.toString().startsWith("загрузка")
                || mcValue.toString().startsWith("нет связи")
                || mcValue.toString().startsWith("нет подходящих")) {
            JOptionPane.showMessageDialog(this, "Выбери версию Minecraft (список ещё грузится).",
                    "Проверь версию", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String core = ((String) coreCombo.getSelectedItem()).toLowerCase(java.util.Locale.ROOT);
        int port = (Integer) portSpinner.getValue();
        try {
            result = ServerStore.create(name, mcValue.toString(), core, ramSlider.getValue(), port);
            dispose();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Не удалось создать сервер",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.uiFont(Font.PLAIN, 12));
        label.setForeground(theme.muted);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        return label;
    }

    private void styleCombo(JComboBox<String> combo) {
        combo.setBackground(theme.input);
        combo.setForeground(theme.text);
        combo.setFont(Theme.uiFont(Font.PLAIN, 14));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
