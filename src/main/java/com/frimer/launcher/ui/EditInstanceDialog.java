package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** Изменение названия и памяти сборки. */
public final class EditInstanceDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final Instance instance;
    private final JTextField nameField = new JTextField();
    private final JSlider ramSlider = new JSlider(1024, 16384, 4096);
    private final JLabel ramLabel = new JLabel();
    private final JLabel infoLabel = new JLabel();
    private boolean saved;

    public EditInstanceDialog(JFrame owner, Theme theme, Instance instance) {
        super(owner, "Изменить сборку", true);
        this.theme = theme;
        this.instance = instance;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        root.add(fieldLabel("Название"));
        nameField.setText(instance.name);
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

        root.add(fieldLabel("Оперативная память"));
        ramSlider.setMinimum(1024);
        ramSlider.setMaximum(16384);
        ramSlider.setValue(Math.max(1024, Math.min(16384, instance.ramMb)));
        Ui.styleSlider(ramSlider, theme);
        ramSlider.addChangeListener(e -> updateRamLabel());
        root.add(ramSlider);
        ramLabel.setFont(Theme.uiFont(Font.PLAIN, 12));
        ramLabel.setForeground(theme.accent);
        ramLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(ramLabel);
        root.add(Box.createVerticalStrut(14));

        infoLabel.setText("<html><body style='width:320px'>" + instance.loader + " "
                + instance.mcVersion + "<br>Папка: " + instance.folder + "<br>Модов: "
                + Utils.countMods(instance.folder) + "</body></html>");
        infoLabel.setFont(Theme.uiFont(Font.PLAIN, 11));
        infoLabel.setForeground(theme.muted);
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(infoLabel);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(16, 0, 0, 0));
        JButton cancel = Ui.button("Отмена", theme);
        cancel.addActionListener(e -> {
            saved = false;
            dispose();
        });
        JButton save = Ui.primaryButton("Сохранить", theme);
        save.addActionListener(e -> save());
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.add(cancel);
        right.add(Box.createHorizontalStrut(8));
        right.add(save);
        buttons.add(right, BorderLayout.EAST);
        root.add(buttons);

        Ui.alignLeft(root);
        setContentPane(root);
        updateRamLabel();
        setSize(new Dimension(420, 330));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public boolean showDialog() {
        setVisible(true);
        return saved;
    }

    private void updateRamLabel() {
        int ram = Math.max(1024, (ramSlider.getValue() / 512) * 512);
        ramSlider.setValue(ram);
        ramLabel.setText(Utils.fmtGb(ram));
    }

    private void save() {
        String name = nameField.getText().strip();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Название не может быть пустым.",
                    "Проверь название", JOptionPane.WARNING_MESSAGE);
            return;
        }
        instance.name = name;
        instance.ramMb = ramSlider.getValue();
        try {
            instance.save();
            saved = true;
            dispose();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Не удалось сохранить",
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
}
