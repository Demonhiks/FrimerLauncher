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
import javax.swing.SwingUtilities;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** Консоль сервера: живой вывод, остановка и открытие папки. */
public final class ServerConsoleDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final ServerInstance server;
    private final javax.swing.JTextArea output;
    private final JLabel header;
    private final JLabel metrics = new JLabel(" ");
    private final JTextField commandField = new JTextField();
    private final JButton stopButton;
    private final JButton killButton;

    public ServerConsoleDialog(JFrame owner, Theme theme, ServerInstance server,
                               Runnable onStop, Runnable onKill) {
        super(owner, "Консоль — " + server.name, false, theme);
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
        JButton pluginsButton = Ui.button("Плагины Modrinth", theme);
        pluginsButton.addActionListener(e -> {
            java.nio.file.Path dest = server.folder.resolve("plugins");
            try {
                java.nio.file.Files.createDirectories(dest);
            } catch (java.io.IOException ignored) {
                // папку создаст установка
            }
            new ModrinthDialog((JFrame) getOwner(), theme, server.mcVersion, "",
                    com.frimer.launcher.core.Modrinth.PLUGIN, dest).showDialog();
        });
        JButton shareButton = Ui.button("Открыть порт (UPnP)", theme);
        shareButton.addActionListener(e -> {
            appendLog("Пробую открыть порт через UPnP...");
            new Thread(() -> {
                try {
                    com.frimer.launcher.core.Upnp.Mapping mapping =
                            com.frimer.launcher.core.Upnp.open(server.port,
                                    "Frimer: " + server.name, 3600);
                    SwingUtilities.invokeLater(() -> {
                        server.publicAddress = mapping.address();
                        server.save();
                        appendLog("Порт открыт: " + mapping.address()
                                + " (роутер: " + mapping.router() + ")");
                        metrics.setText("адрес: " + mapping.address());
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> appendLog("UPnP не сработал: "
                            + ex.getMessage() + ". Используйте playit.gg — «Адрес для друзей»."));
                }
            }, "frimer-upnp-console").start();
        });
        JButton clearButton = Ui.miniButton("Очистить", theme);
        clearButton.addActionListener(e -> output.setText(""));

        commandField.setBackground(theme.input);
        commandField.setForeground(theme.text);
        commandField.setCaretColor(theme.accent);
        commandField.setFont(Theme.uiFont(Font.PLAIN, 13));
        commandField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.border), new EmptyBorder(6, 8, 6, 8)));
        commandField.setToolTipText("Команда серверу через RCON, например: list или say привет");
        commandField.addActionListener(e -> sendCommand());
        JButton sendButton = Ui.button("Отправить", theme);
        sendButton.addActionListener(e -> sendCommand());
        JButton autoRestart = Ui.miniButton("Авто-рестарт: "
                + (server.autoRestart ? "вкл" : "выкл"), theme);
        autoRestart.addActionListener(e -> {
            server.autoRestart = !server.autoRestart;
            server.save();
            autoRestart.setText("Авто-рестарт: " + (server.autoRestart ? "вкл" : "выкл"));
        });
        JPanel rconRow = new JPanel(new BorderLayout(8, 0));
        rconRow.setOpaque(false);
        rconRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        rconRow.add(commandField, BorderLayout.CENTER);
        rconRow.add(Ui.buttonRow(sendButton, autoRestart), BorderLayout.EAST);


        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        metrics.setFont(Theme.uiFont(Font.PLAIN, 12));
        metrics.setForeground(theme.muted);
        metrics.setHorizontalAlignment(JLabel.CENTER);
        top.add(header, BorderLayout.WEST);
        top.add(metrics, BorderLayout.CENTER);
        top.add(clearButton, BorderLayout.EAST);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(stopButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(killButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(folderButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(pluginsButton);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(shareButton);
        buttons.add(Box.createHorizontalGlue());

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(buttons);
        bottom.add(rconRow);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(760, 520));
        setLocationRelativeTo(owner);
        setRunning(false);
    }

    /** Показать метрики сервера в шапке (TPS и число игроков). */
    public void setMetrics(String tps, int online) {
        if (tps == null && online < 0) {
            metrics.setText(" ");
            return;
        }
        StringBuilder text = new StringBuilder();
        if (tps != null) {
            text.append("TPS ").append(tps);
        }
        if (online >= 0) {
            if (text.length() > 0) {
                text.append("  ·  ");
            }
            text.append("игроков: ").append(online);
        }
        metrics.setText(text.toString());
    }

    /** Отправить команду через RCON и показать ответ. */
    private void sendCommand() {
        String text = commandField.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        commandField.setText("");
        appendLog("> " + text);
        new Thread(() -> {
            try (com.frimer.launcher.core.Rcon rcon = new com.frimer.launcher.core.Rcon(
                    "127.0.0.1", server.rconPort, server.rconPassword)) {
                String answer = rcon.command(text);
                SwingUtilities.invokeLater(() -> appendLog(
                        answer.isBlank() ? "(команда выполнена)" : answer));
            } catch (java.io.IOException | RuntimeException e) {
                SwingUtilities.invokeLater(() -> appendLog("RCON: " + e.getMessage()
                        + " (проверьте enable-rcon и пароль в server.properties)"));
            }
        }, "frimer-rcon").start();
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
