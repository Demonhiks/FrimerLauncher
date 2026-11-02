package com.frimer.launcher.ui;

import com.frimer.launcher.core.AppSettings;
import com.frimer.launcher.core.Dirs;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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
import javax.swing.border.EmptyBorder;

/** Настройки лаунчера: ник, тема, память по умолчанию. */
public final class SettingsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final JTextField nickField;
    private final JComboBox<String> themeCombo = new JComboBox<>(new String[]{"dark", "light"});
    private final JSlider ramSlider = new JSlider(1024, 16384, AppSettings.defaultRamMb());
    private final JLabel ramLabel = new JLabel();
    private boolean saved;

    public SettingsDialog(JFrame owner, Theme theme) {
        super(owner, "Настройки", true);
        this.theme = theme;
        nickField = new Ui.Field(theme, AppSettings.nickname());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(18, 20, 16, 20));

        root.add(fieldLabel("Ник в игре"));

        nickField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        nickField.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(nickField);
        root.add(Box.createVerticalStrut(12));

        root.add(fieldLabel("Тема оформления"));
        themeCombo.setSelectedItem(AppSettings.theme());
        Ui.styleCombo(themeCombo, theme);
        themeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        themeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(themeCombo);
        root.add(Box.createVerticalStrut(12));

        root.add(fieldLabel("Память по умолчанию для новых сборок"));
        ramSlider.setMinimum(1024);
        ramSlider.setMaximum(16384);
        ramSlider.setValue(Math.max(1024, Math.min(16384, AppSettings.defaultRamMb())));
        Ui.styleSlider(ramSlider, theme);
        ramSlider.addChangeListener(e -> updateRamLabel());
        root.add(ramSlider);
        ramLabel.setFont(Theme.uiFont(Font.PLAIN, 12));
        ramLabel.setForeground(theme.accent);
        ramLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(ramLabel);
        root.add(Box.createVerticalStrut(14));

        JLabel pathLabel = new JLabel("<html><body style='width:340px'>Папка игры: "
                + Dirs.GAME_DIR + "</body></html>");
        pathLabel.setFont(Theme.uiFont(Font.PLAIN, 11));
        pathLabel.setForeground(theme.muted);
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(pathLabel);
        root.add(Box.createVerticalStrut(8));

        JButton openFolder = Ui.button("Открыть папку игры", theme);
        openFolder.addActionListener(e -> Ui.openFolder(Dirs.GAME_DIR));
        openFolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(openFolder);

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
        setSize(new Dimension(440, 430));
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
        String nick = nickField.getText().strip();
        if (!nick.matches("^[A-Za-z0-9_]{1,16}$")) {
            JOptionPane.showMessageDialog(this, "Ник: только буквы, цифры и _ (до 16 символов).",
                    "Неверный ник", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AppSettings.setNickname(nick);
        AppSettings.setTheme((String) themeCombo.getSelectedItem());
        AppSettings.setDefaultRamMb(ramSlider.getValue());
        AppSettings.flush();
        saved = true;
        dispose();
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
