package com.frimer.launcher.ui;

import com.frimer.launcher.core.AppSettings;
import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.InstanceStore;
import com.frimer.launcher.core.Loaders;
import com.frimer.launcher.core.Mojang;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Диалог создания новой сборки. */
public final class NewInstanceDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final JTextField nameField;
    private final JComboBox<String> loaderCombo =
            new JComboBox<>(new String[]{"Vanilla", "Fabric", "Quilt", "Forge", "NeoForge"});
    private final JComboBox<String> mcCombo = new JComboBox<>();
    private final JComboBox<String> loaderVerCombo = new JComboBox<>();
    private final JSlider ramSlider = new JSlider(1024, 16384, AppSettings.defaultRamMb());
    private final JLabel ramLabel = new JLabel();
    private final JLabel hintLabel = new JLabel(" ");
    private final JButton createButton = Ui.primaryButton("Создать", Theme.DARK);

    private Instance result;

    public NewInstanceDialog(JFrame owner, Theme theme) {
        super(owner, "Новая сборка", true, theme);
        this.theme = theme;
        nameField = new Ui.Field(theme, "Сборка");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        root.add(fieldLabel("Название сборки"));

        root.add(nameField);
        root.add(Box.createVerticalStrut(12));

        root.add(fieldLabel("Загрузчик"));
        styleCombo(loaderCombo);
        root.add(loaderCombo);
        root.add(Box.createVerticalStrut(12));

        root.add(fieldLabel("Версия Minecraft"));
        styleCombo(mcCombo);
        mcCombo.addItem("загрузка версий...");
        root.add(mcCombo);
        root.add(Box.createVerticalStrut(12));

        root.add(fieldLabel("Версия загрузчика"));
        styleCombo(loaderVerCombo);

        loaderVerCombo.addItem("последняя");
        root.add(loaderVerCombo);
        root.add(Box.createVerticalStrut(14));

        root.add(fieldLabel("Оперативная память"));
        Ui.styleSlider(ramSlider, theme);
        ramSlider.setMajorTickSpacing(4096);
        ramSlider.setMinorTickSpacing(512);
        ramSlider.setPaintTicks(false);
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
        createButton.setBackground(theme.accent);
        createButton.setForeground(theme.bg);
        createButton.addActionListener(e -> create());

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(cancel);
        right.add(Box.createHorizontalStrut(8));
        right.add(createButton);
        buttons.add(right, BorderLayout.EAST);
        root.add(buttons);

        Ui.alignLeft(root);
        setContentPane(root);
        updateRamLabel();
        loaderCombo.addActionListener(e -> onLoaderChanged());
        mcCombo.addActionListener(e -> loadLoaderVersions());

        setSize(new Dimension(430, 500));
        setResizable(false);
        setLocationRelativeTo(owner);
        loadVersions();
    }

    public Instance showDialog() {
        setVisible(true);
        return result;
    }

    private void loadVersions() {
        Thread thread = new Thread(() -> {
            try {
                List<String> releases = Mojang.releaseVersions();
                SwingUtilities.invokeLater(() -> {
                    mcCombo.removeAllItems();
                    for (String v : releases) {
                        mcCombo.addItem(v);
                    }
                    if (mcCombo.getItemCount() > 0) {
                        mcCombo.setSelectedIndex(0);
                    }
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    mcCombo.removeAllItems();
                    mcCombo.addItem("нет связи с Mojang");
                    hintLabel.setText("Не удалось получить список версий: " + e.getMessage());
                });
            }
        }, "frimer-versions");
        thread.setDaemon(true);
        thread.start();
    }

    private void onLoaderChanged() {
        String loader = (String) loaderCombo.getSelectedItem();
        boolean vanilla = "Vanilla".equals(loader);
        loaderVerCombo.setEnabled(true);
        if (vanilla) {
            loaderVerCombo.removeAllItems();
            loaderVerCombo.addItem("последняя");
        } else {
            loadLoaderVersions();
        }
    }

    private void loadLoaderVersions() {
        String loader = (String) loaderCombo.getSelectedItem();
        Object mcValue = mcCombo.getSelectedItem();
        if ("Vanilla".equals(loader) || mcValue == null) {
            return;
        }
        String mc = mcValue.toString();
        if (mc.startsWith("загрузка") || mc.startsWith("нет связи")) {
            return;
        }
        loaderVerCombo.removeAllItems();
        loaderVerCombo.addItem("загрузка...");
        Thread thread = new Thread(() -> {
            try {
                List<String> versions = Loaders.loaderVersions(loader, mc);
                SwingUtilities.invokeLater(() -> {
                    loaderVerCombo.removeAllItems();
                    loaderVerCombo.addItem("последняя");
                    for (String v : versions) {
                        if (!v.isBlank()) {
                            loaderVerCombo.addItem(v);
                        }
                    }
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    loaderVerCombo.removeAllItems();
                    loaderVerCombo.addItem("последняя");
                    hintLabel.setText("Версии " + loader + " недоступны: " + e.getMessage());
                });
            }
        }, "frimer-loader-versions");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateRamLabel() {
        int ram = ramSlider.getValue();
        int snapped = Math.max(1024, (ram / 512) * 512);
        ramSlider.setValue(snapped);
        ramLabel.setText(Utils.fmtGb(snapped) + "  (минимум 1 GB, максимум 16 GB)");
    }

    private void create() {
        String name = nameField.getText().strip();
        if (name.isEmpty()) {
            name = "Сборка";
        }
        Object mcValue = mcCombo.getSelectedItem();
        if (mcValue == null || mcValue.toString().startsWith("загрузка")
                || mcValue.toString().startsWith("нет связи")) {
            JOptionPane.showMessageDialog(this, "Список версий ещё не загрузился.",
                    "Подожди", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String loader = (String) loaderCombo.getSelectedItem();
        String loaderVersion = null;
        Object lv = loaderVerCombo.getSelectedItem();
        if (lv != null && !"Vanilla".equals(loader) && !"последняя".equals(lv.toString())
                && !lv.toString().startsWith("загрузка")) {
            loaderVersion = lv.toString();
        }
        try {
            result = InstanceStore.create(name, loader, mcValue.toString(),
                    ramSlider.getValue(), loaderVersion);
            dispose();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Не удалось создать сборку",
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

    private void styleField(JTextField field) {
        field.setBackground(theme.input);
        field.setForeground(theme.text);
        field.setCaretColor(theme.accent);
        field.setFont(Theme.uiFont(Font.PLAIN, 14));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border),
                new EmptyBorder(6, 8, 6, 8)));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void styleCombo(JComboBox<String> combo) {
        Ui.styleCombo(combo, theme);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
