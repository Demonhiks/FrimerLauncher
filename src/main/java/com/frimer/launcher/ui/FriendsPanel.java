package com.frimer.launcher.ui;

import com.frimer.launcher.core.friends.Codes;
import com.frimer.launcher.core.friends.FriendStore;
import com.frimer.launcher.core.friends.Friends;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Вкладка «Друзья»: свой код, список друзей, чат, обмен сборками и приглашения. */
public final class FriendsPanel extends JPanel implements Friends.Listener {

    private static final long serialVersionUID = 1L;

    /** Что предлагаем другу: данные текущей сборки и сервера. */
    public interface Context {
        String instanceName();

        String mcVersion();

        String loader();

        int mods();

        String serverName();

        String serverAddress();
    }

    private final Theme theme;
    private final Context context;
    private final Friends friends;

    private final JLabel statusLabel;
    private final JLabel codeLabel;
    private final JPanel listPanel;
    private final JTextArea chatArea;
    private final JTextField input;
    private final JLabel chatTitle;
    private final JPanel chatActions;
    private final JTextField addField;

    private final Map<String, List<Friends.Message>> history = new LinkedHashMap<>();
    private String selected = "";

    public FriendsPanel(Theme theme, Context context) {
        this.theme = theme;
        this.context = context;
        this.friends = new Friends(this);

        setLayout(new BorderLayout(14, 12));
        setBackground(theme.bg);
        setBorder(new EmptyBorder(24, 28, 20, 28));

        // ------------------------------------------------------------- шапка
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new BorderLayout(10, 0));
        titleRow.setOpaque(false);
        JLabel title = Ui.label("Друзья", Theme.titleFont(Font.BOLD, 24), theme.text);
        statusLabel = Ui.label("офлайн", Theme.uiFont(Font.PLAIN, 12), theme.muted);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(statusLabel, BorderLayout.EAST);
        header.add(titleRow);
        header.add(Box.createVerticalStrut(8));

        JPanel codeRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        codeRow.setOpaque(false);
        codeLabel = Ui.label("Мой код: …", Theme.uiFont(Font.BOLD, 13), theme.accent);
        codeRow.add(codeLabel);
        JButton copyCode = Ui.miniButton("Копировать", theme);
        copyCode.addActionListener(e -> copyToClipboard(Codes.normalize(friends.store().code)));
        codeRow.add(copyCode);
        codeRow.add(Ui.muted("— скажи его другу, чтобы он добавил тебя", theme));
        header.add(codeRow);
        header.add(Box.createVerticalStrut(10));

        JPanel addRow = new JPanel(new BorderLayout(8, 0));
        addRow.setOpaque(false);
        addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        addField = new Ui.Field(theme, "Код друга ABCDE-FGHJK");
        addField.setToolTipText("Код друга, например ABCDE-FGHJK");
        JButton addButton = Ui.primaryButton("Добавить друга", theme);
        addButton.addActionListener(e -> addFriend());
        addRow.add(addField, BorderLayout.CENTER);
        addRow.add(addButton, BorderLayout.EAST);
        header.add(addRow);

        add(header, BorderLayout.NORTH);

        // ------------------------------------------------------------- список
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setPreferredSize(new Dimension(240, 0));
        left.setBorder(new EmptyBorder(0, 0, 0, 6));
        JScrollPane listScroll = Ui.borderlessScroll(left, theme);
        listPanel = left;
        listScroll.setPreferredSize(new Dimension(240, 0));

        // ------------------------------------------------------------- чат
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);

        JPanel chatHead = new JPanel(new BorderLayout());
        chatHead.setOpaque(false);
        chatTitle = Ui.label("Выбери друга слева", Theme.titleFont(Font.BOLD, 15), theme.text);
        chatHead.add(chatTitle, BorderLayout.WEST);
        right.add(chatHead, BorderLayout.NORTH);

        chatArea = Ui.logArea(theme);
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        right.add(Ui.scroll(chatArea, theme), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);

        chatActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        chatActions.setOpaque(false);
        JButton shareBuild = Ui.miniButton("Поделиться сборкой", theme);
        shareBuild.addActionListener(e -> shareBuild());
        JButton invite = Ui.miniButton("Позвать на сервер", theme);
        invite.addActionListener(e -> inviteToServer());
        chatActions.add(shareBuild);
        chatActions.add(invite);

        JPanel composeRow = new JPanel(new BorderLayout(8, 0));
        composeRow.setOpaque(false);
        input = new Ui.Field(theme, "Сообщение");
        input.setToolTipText("Сообщение другу");
        input.addActionListener(e -> sendMessage());
        JButton send = Ui.primaryButton("Отправить", theme);
        send.addActionListener(e -> sendMessage());
        composeRow.add(input, BorderLayout.CENTER);
        composeRow.add(send, BorderLayout.EAST);

        bottom.add(chatActions, BorderLayout.NORTH);
        bottom.add(composeRow, BorderLayout.SOUTH);
        right.add(bottom, BorderLayout.SOUTH);

        add(listScroll, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        friends.start();
        refreshAll();
    }

    // ================================================================ действия

    private void addFriend() {
        String raw = addField.getText();
        if (!Codes.valid(raw)) {
            showInfo("Код друга выглядит как ABCDE-FGHJK — 10 символов и дефис.");
            return;
        }
        try {
            friends.requestFriend(raw, friends.store().nick);
            addField.setText("");
            showInfo("Запрос отправлен на " + Codes.normalize(raw) + ". Ждём, пока друг нажмёт «Принять».");
        } catch (RuntimeException e) {
            showInfo(e.getMessage());
        }
    }

    private void sendMessage() {
        if (selected.isEmpty()) {
            return;
        }
        String text = input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            friends.sendText(selected, text);
            input.setText("");
            appendLine("Я: " + text, theme.text);
        } catch (RuntimeException e) {
            showInfo(e.getMessage());
        }
    }

    private void shareBuild() {
        if (selected.isEmpty()) {
            return;
        }
        String name = context.instanceName();
        if (name == null || name.isBlank()) {
            showInfo("Сначала выбери сборку на вкладке «Инстансы».");
            return;
        }
        try {
            friends.sendBuild(selected, name, context.mcVersion(), context.loader(), context.mods());
            appendLine("Отправил сборку «" + name + "» другу.", theme.muted);
        } catch (RuntimeException e) {
            showInfo(e.getMessage());
        }
    }

    private void inviteToServer() {
        if (selected.isEmpty()) {
            return;
        }
        String address = context.serverAddress();
        if (address == null || address.isBlank()) {
            showInfo("Сначала запусти сервер на вкладке «Серверы» — нужен адрес для друзей.");
            return;
        }
        try {
            friends.sendInvite(selected, address, context.serverName());
            appendLine("Позвал друга на сервер: " + address, theme.muted);
        } catch (RuntimeException e) {
            showInfo(e.getMessage());
        }
    }

    // ================================================================ интерфейс

    private void refreshAll() {
        codeLabel.setText("Мой код: " + friends.store().code);
        statusLabel.setText(friends.status());
        statusLabel.setForeground(friends.online() ? theme.ok : theme.muted);
        refreshList();
        refreshChat();
    }

    private void refreshList() {
        listPanel.removeAll();
        List<FriendStore.Friend> incoming = new ArrayList<>(friends.store().incoming);
        for (FriendStore.Friend request : incoming) {
            JPanel row = new JPanel(new BorderLayout(6, 4));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(4, 0, 4, 0));
            row.add(Ui.label(request.nick + "  " + request.code, Theme.uiFont(Font.PLAIN, 12),
                    theme.text), BorderLayout.NORTH);
            JButton accept = Ui.miniButton("Принять", theme);
            accept.addActionListener(e -> friends.acceptFriend(request.code));
            JButton decline = Ui.miniButton("Отклонить", theme);
            decline.addActionListener(e -> friends.declineFriend(request.code));
            row.add(Ui.buttonRow(accept, decline), BorderLayout.SOUTH);
            listPanel.add(row);
        }
        if (!incoming.isEmpty()) {
            listPanel.add(Box.createVerticalStrut(6));
        }
        List<FriendStore.Friend> friendsList = new ArrayList<>(friends.store().friends);
        if (friendsList.isEmpty() && incoming.isEmpty()) {
            listPanel.add(Ui.muted("Пока никого. Добавь друга по коду.", theme));
        }
        for (FriendStore.Friend friend : friendsList) {
            JButton row = new JButton();
            row.setLayout(new BorderLayout(6, 0));
            row.setOpaque(true);
            row.setBorder(new EmptyBorder(8, 10, 8, 10));
            row.setBackground(friend.code.equals(selected) ? theme.cardSel : theme.card);
            row.setHorizontalAlignment(SwingConstants.LEFT);
            JLabel dot = Ui.label(friend.online() ? "●" : "○", Theme.uiFont(Font.BOLD, 12),
                    friend.online() ? theme.ok : theme.muted);
            JLabel name = Ui.label(friend.nick, Theme.uiFont(Font.BOLD, 13), theme.text);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel sub = Ui.label(friend.online() ? "онлайн" : "офлайн",
                    Theme.uiFont(Font.PLAIN, 11), theme.muted);
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(name);
            text.add(sub);
            row.add(dot, BorderLayout.WEST);
            row.add(text, BorderLayout.CENTER);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            row.addActionListener(e -> selectFriend(friend.code));
            listPanel.add(row);
            listPanel.add(Box.createVerticalStrut(4));
        }
        listPanel.add(Box.createVerticalGlue());
        listPanel.revalidate();
        listPanel.repaint();
    }

    private void selectFriend(String code) {
        selected = code;
        FriendStore.Friend friend = friends.store().find(code);
        chatTitle.setText(friend == null ? "Друг" : friend.nick + "  " + friend.code);
        refreshList();
        refreshChat();
        input.requestFocusInWindow();
    }

    private void refreshChat() {
        chatArea.setText("");
        if (selected.isEmpty()) {
            chatArea.append("Выбери друга слева или добавь нового по коду.\n");
            return;
        }
        List<Friends.Message> messages = history.get(selected);
        if (messages == null || messages.isEmpty()) {
            chatArea.append("Сообщений пока нет. Напиши первым.\n");
            return;
        }
        for (Friends.Message message : messages) {
            String who = message.mine() ? "Я" : message.nick();
            chatArea.append(who + ": " + message.text() + "\n");
        }
    }

    private void appendLine(String text, java.awt.Color color) {
        chatArea.append(text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void copyToClipboard(String text) {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
        showInfo("Код скопирован: " + text);
    }

    private void showInfo(String text) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(theme.bg);
        JLabel label = Ui.label(text, Theme.uiFont(Font.PLAIN, 13), theme.text);
        label.setBorder(new EmptyBorder(4, 6, 4, 6));
        wrap.add(label, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, wrap, "Друзья", JOptionPane.INFORMATION_MESSAGE);
    }

    // ================================================================ события

    @Override
    public void onStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(text);
            statusLabel.setForeground(friends.online() ? theme.ok : theme.muted);
        });
    }

    @Override
    public void onContactsChanged() {
        SwingUtilities.invokeLater(this::refreshAll);
    }

    @Override
    public void onChat(Friends.Message message, boolean unread) {
        SwingUtilities.invokeLater(() -> {
            history.computeIfAbsent(message.friendCode(), key -> new ArrayList<>()).add(message);
            if (message.friendCode().equals(selected)) {
                refreshChat();
            } else {
                refreshList();
            }
        });
    }

    @Override
    public void onIncomingRequest(String code, String nick) {
        SwingUtilities.invokeLater(this::refreshList);
    }

    @Override
    public void onBuild(String friendCode, Map<String, Object> build) {
        SwingUtilities.invokeLater(() -> {
            String line = "Сборка: «" + build.getOrDefault("name", "?") + "», "
                    + build.getOrDefault("mc", "?") + ", " + build.getOrDefault("loader", "?")
                    + ", модов: " + build.getOrDefault("mods", 0);
            history.computeIfAbsent(friendCode, key -> new ArrayList<>())
                    .add(new Friends.Message(friendCode, "Сборка", false,
                            System.currentTimeMillis(), line));
            if (friendCode.equals(selected)) {
                refreshChat();
            }
        });
    }

    @Override
    public void onInvite(String friendCode, String address, String serverName) {
        SwingUtilities.invokeLater(() -> {
            String line = "Зовёт на сервер «" + serverName + "»: " + address;
            history.computeIfAbsent(friendCode, key -> new ArrayList<>())
                    .add(new Friends.Message(friendCode, "Приглашение", false,
                            System.currentTimeMillis(), line));
            if (friendCode.equals(selected)) {
                refreshChat();
            }
            JButton copy = Ui.miniButton("Скопировать адрес", theme);
            copy.addActionListener(e -> copyToClipboard(address));
            chatActions.removeAll();
            JButton shareBuildButton = Ui.miniButton("Поделиться сборкой", theme);
            shareBuildButton.addActionListener(e -> shareBuild());
            JButton inviteButton = Ui.miniButton("Позвать на сервер", theme);
            inviteButton.addActionListener(e -> inviteToServer());
            chatActions.add(shareBuildButton);
            chatActions.add(inviteButton);
            chatActions.add(copy);
            chatActions.revalidate();
            chatActions.repaint();
        });
    }

    /** Остановить сеть при закрытии окна. */
    public void shutdown() {
        friends.stop();
    }

    /** Друзья для доступа из MainFrame (например, чтобы обновить ник). */
    public Friends service() {
        return friends;
    }
}
