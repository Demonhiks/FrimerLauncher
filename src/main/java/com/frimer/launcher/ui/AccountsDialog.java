package com.frimer.launcher.ui;

import com.frimer.launcher.core.Accounts;
import com.frimer.launcher.core.AppSettings;
import com.frimer.launcher.core.MicrosoftAuth;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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

/** Аккаунты: офлайн-ники и вход через Microsoft (device code flow). */
public final class AccountsDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final DefaultListModel<Accounts.Account> model = new DefaultListModel<>();
    private final JList<Accounts.Account> list = new JList<>(model);
    private final JLabel status = new JLabel(" ");
    private final Runnable onChanged;

    public AccountsDialog(JFrame owner, Theme theme, Runnable onChanged) {
        super(owner, "Аккаунты", true, theme);
        this.theme = theme;
        this.onChanged = onChanged;

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel("Кем играть");
        title.setFont(Theme.titleFont(Font.BOLD, 16));
        title.setForeground(theme.text);
        JLabel hint = new JLabel("Офлайн-ник работает всегда; Microsoft — для лицензии и скина");
        hint.setFont(Theme.uiFont(Font.PLAIN, 12));
        hint.setForeground(theme.muted);

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(title);
        head.add(Box.createVerticalStrut(3));
        head.add(hint);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);

        list.setBackground(theme.card);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(46);
        list.setCellRenderer(new AccountRenderer());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    useSelected();
                }
            }
        });
        JScrollPane scroll = Ui.scroll(list, theme);
        scroll.setPreferredSize(new Dimension(520, 250));

        JButton addOffline = Ui.button("Офлайн-ник", theme);
        addOffline.addActionListener(e -> addOffline());
        JButton microsoft = Ui.primaryButton("Microsoft", theme);
        microsoft.addActionListener(e -> signInMicrosoft());
        JButton use = Ui.button("Выбрать", theme);
        use.addActionListener(e -> useSelected());
        JButton remove = Ui.miniButton("Удалить", theme);
        remove.addActionListener(e -> removeSelected());
        JButton close = Ui.miniButton("Закрыть", theme);
        close.addActionListener(e -> dispose());

        status.setFont(Theme.uiFont(Font.PLAIN, 12));
        status.setForeground(theme.muted);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(Ui.buttonRow(addOffline, microsoft, use), BorderLayout.WEST);
        actions.add(Ui.buttonRow(remove, close), BorderLayout.EAST);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(status);
        bottom.add(Box.createVerticalStrut(8));
        bottom.add(actions);

        JPanel headWrap = new JPanel(new java.awt.BorderLayout());
        headWrap.setOpaque(false);
        headWrap.add(head, java.awt.BorderLayout.WEST);
        root.add(headWrap, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(640, 470));
        setLocationRelativeTo(owner);
        reload();
    }

    public void showDialog() {
        setVisible(true);
    }

    private void reload() {
        model.clear();
        for (Accounts.Account account : Accounts.list()) {
            model.addElement(account);
        }
        Accounts.Account active = Accounts.active();
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).name.equalsIgnoreCase(active.name)) {
                list.setSelectedIndex(i);
                break;
            }
        }
        status.setText(model.isEmpty()
                ? "Аккаунтов нет — добавьте офлайн-ник или войдите через Microsoft"
                : "Активных: " + model.size() + " · текущий: " + active.name);
    }

    private void addOffline() {
        String nick = JOptionPane.showInputDialog(this, "Ник для офлайн-игры:",
                "Новый офлайн-аккаунт", JOptionPane.PLAIN_MESSAGE);
        if (nick == null) {
            return;
        }
        String clean = nick.strip();
        if (!clean.matches("^[A-Za-z0-9_]{1,16}$")) {
            JOptionPane.showMessageDialog(this, "Только буквы, цифры и _ (до 16 символов).",
                    "Неверный ник", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Accounts.Account account = Accounts.addOffline(clean);
        Accounts.setActive(account);
        AppSettings.flush();
        reload();
        notifyChanged();
    }

    private void useSelected() {
        Accounts.Account account = list.getSelectedValue();
        if (account == null) {
            return;
        }
        Accounts.setActive(account);
        AppSettings.flush();
        status.setText("Активный аккаунт: " + account.name);
        notifyChanged();
    }

    private void removeSelected() {
        Accounts.Account account = list.getSelectedValue();
        if (account == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Удалить аккаунт «" + account.name + "» из лаунчера?",
                "Удаление аккаунта", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        Accounts.remove(account);
        AppSettings.flush();
        reload();
        notifyChanged();
    }

    private void signInMicrosoft() {
        String clientId = MicrosoftAuth.clientId();
        if (clientId.isBlank()) {
            String entered = JOptionPane.showInputDialog(this,
                    "Для входа Microsoft нужен client ID вашего приложения Azure.\n"
                            + MicrosoftAuth.registrationHint()
                            + "\n\nВставьте Application (client) ID:",
                    "Microsoft вход", JOptionPane.PLAIN_MESSAGE);
            if (entered == null || entered.isBlank()) {
                return;
            }
            clientId = entered.strip();
            MicrosoftAuth.setClientId(clientId);
            AppSettings.flush();
        }
        String finalClientId = clientId;
        status.setText("Запрашиваю код у Microsoft...");
        new Thread(() -> {
            try {
                MicrosoftAuth.Device device = MicrosoftAuth.startDeviceCode(finalClientId);
                SwingUtilities.invokeLater(() -> openLoginDialog(device, finalClientId));
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        e.getMessage(), "Не удалось начать вход", JOptionPane.ERROR_MESSAGE));
            }
        }, "frimer-ms-start").start();
    }

    private void openLoginDialog(MicrosoftAuth.Device device, String clientId) {
        LoginDialog dialog = new LoginDialog(device, clientId);
        dialog.setVisible(true);
        Accounts.Account account = dialog.result;
        if (account != null) {
            Accounts.add(account);
            Accounts.setActive(account);
            AppSettings.flush();
            reload();
            notifyChanged();
            status.setText("Вошли как " + account.name);
        }
    }

    private void notifyChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /** Окно с кодом и ожиданием подтверждения входа. */
    private final class LoginDialog extends Ui.FramelessDialog {
        private static final long serialVersionUID = 1L;

        private final JLabel state = new JLabel("Откройте сайт и введите код");
        private Accounts.Account result;

        LoginDialog(MicrosoftAuth.Device device, String clientId) {
            super(AccountsDialog.this, "Вход Microsoft", true, theme);
            JPanel root = new JPanel();
            root.setBackground(theme.bg);
            root.setBorder(new EmptyBorder(14, 18, 14, 18));
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

            JLabel code = new JLabel(device.userCode());
            code.setFont(Theme.titleFont(Font.BOLD, 30));
            code.setForeground(theme.accent);
            code.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel link = new JLabel(device.verifyUri());
            link.setFont(Theme.uiFont(Font.PLAIN, 13));
            link.setForeground(theme.text);
            link.setAlignmentX(Component.LEFT_ALIGNMENT);

            state.setFont(Theme.uiFont(Font.PLAIN, 12));
            state.setForeground(theme.muted);
            state.setAlignmentX(Component.LEFT_ALIGNMENT);

            JButton copy = Ui.miniButton("Скопировать код", theme);
            copy.addActionListener(e -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(device.userCode()), null));
            JButton open = Ui.primaryButton("Открыть сайт", theme);
            open.addActionListener(e -> com.frimer.launcher.core.Modrinth.openPage(device.verifyUri()));
            JButton cancel = Ui.miniButton("Отмена", theme);
            cancel.addActionListener(e -> dispose());

            root.add(state);
            root.add(Box.createVerticalStrut(10));
            root.add(code);
            root.add(Box.createVerticalStrut(6));
            root.add(link);
            root.add(Box.createVerticalStrut(14));
            root.add(Ui.buttonRow(copy, open, cancel));

            setContentPane(root);
            setSize(new Dimension(460, 260));
            setLocationRelativeTo(AccountsDialog.this);

            new Thread(() -> {
                try {
                    Accounts.Account account = MicrosoftAuth.waitForAccount(device, clientId,
                            text -> SwingUtilities.invokeLater(() -> state.setText(text)));
                    SwingUtilities.invokeLater(() -> {
                        result = account;
                        dispose();
                    });
                } catch (RuntimeException e) {
                    SwingUtilities.invokeLater(() -> {
                        state.setText("Ошибка: " + Utils.shortText(e.getMessage(), 90));
                        state.setForeground(theme.err);
                    });
                }
            }, "frimer-ms-wait").start();
        }
    }

    /** Строка списка аккаунтов. */
    private final class AccountRenderer extends JPanel implements ListCellRenderer<Accounts.Account> {
        private static final long serialVersionUID = 1L;
        private final JLabel name = new JLabel();
        private final JLabel kind = new JLabel();

        AccountRenderer() {
            setLayout(new BorderLayout());
            name.setFont(Theme.uiFont(Font.BOLD, 14));
            kind.setFont(Theme.uiFont(Font.PLAIN, 11));
            kind.setHorizontalAlignment(JLabel.RIGHT);
            add(name, BorderLayout.CENTER);
            add(kind, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Accounts.Account> list,
                                                      Accounts.Account value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            boolean active = Accounts.active().name.equalsIgnoreCase(value.name);
            name.setText((active ? "● " : "   ") + value.name);
            kind.setText(value.microsoft() ? "Microsoft" : "офлайн");
            setBackground(isSelected ? theme.cardSel : theme.card);
            name.setForeground(active ? theme.accent : (isSelected ? theme.accent : theme.text));
            kind.setForeground(theme.muted);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border),
                    new EmptyBorder(6, 10, 6, 10)));
            setOpaque(true);
            return this;
        }
    }

    /** Заголовок окна для тестов. */
    public static String title() {
        return "Аккаунты";
    }

    /** Не используется, оставлено для совместимости. */
    private static void unused(JDialog dialog) {
        dialog.toFront();
    }
}
