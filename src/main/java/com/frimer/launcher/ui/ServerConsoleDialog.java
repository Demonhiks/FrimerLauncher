package com.frimer.launcher.ui;

import com.frimer.launcher.core.ServerInstance;
import com.frimer.launcher.core.ServerStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** Консоль сервера: живой вывод, остановка и открытие папки. */
public final class ServerConsoleDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final ServerInstance server;
    private final javax.swing.JTextArea output;
    private final JLabel header;
    private final JButton stopButton;
    private final JButton killButton;

    public ServerConsoleDialog(JFrame owner, Theme theme, ServerInstance server,
                               Runnable onStop, Runnable onKill) {
        super(owner, "Консоль — " + server.name, false);
        this.theme = theme;
        this.server = server;
        setDefaultCloseOperation(HIDE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        header = new JLabel(ServerStore.coreLabel(server.core) + " " + server.mcVersion
                + " · порт " + server.port);
        header.setFont(Theme.uiFont(Font.BOLD, 13));
        header.setForeground(theme.text);

        output = Ui.logArea(theme);
        output.setRows(18);
        javax.swing.JScrollPane scroll = Ui.scroll(output, theme);
        scroll.setPreferredSize(new Dimension(700, 360));

        stopButton = Ui.primaryButton("Остановить (stop)", theme);
        stopButton.addActionListener(e -> {
            if (onStop != null) {
                onStop.run();
            }
        });
        killButton = Ui.button("Принудительно", theme);
        killButton.addActionListener(e -> {
            if (onKill != null) {
                onKill.run();
            }
        });
        JButton folderButton = Ui.button("Открыть папку", theme);
        folderButton.addActionListener(e -> Ui.openFolder(server.folder));
        JButton clearButton = Ui.miniButton("Очистить", theme);
        clearButton.addActionListener(e -> output.setText(""));

        JTextField commandField = new JTextField();
        commandField.setBackground(theme.input);
        commandField.setForeground(theme.text);
        commandField.setCaretColor(theme.accent);
        commandField.setFont(Theme.uiFont(Font.PLAIN, 13));
        commandField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border), new EmptyBorder(6, 8, 6, 8)));
        commandField.setToolTipText("Команды сервера (выполняется только stop; остальное - в игре)");

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.WEST);
        top.add(clearButton, BorderLayout.EAST);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(stopButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(killButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(folderButton);
        buttons.add(Box.createHorizontalGlue());

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(buttons);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(760, 520));
        setLocationRelativeTo(owner);
        setRunning(false);
    }

    public void appendLog(String line) {
        String current = output.getText();
        if (current.length() > 400_000) {
            output.setText("");
        }
        output.append(line + "\n");
        output.setCaretPosition(output.getDocument().getLength());
    }

    public void setRunning(boolean running) {
        stopButton.setEnabled(running);
        killButton.setEnabled(running);
        header.setText(ServerStore.coreLabel(server.core) + " " + server.mcVersion
                + " · порт " + server.port + (running ? " · работает" : " · остановлен"));
    }

    public void clear() {
        output.setText("");
    }

    public Path folder() {
        return server.folder;
    }
}
