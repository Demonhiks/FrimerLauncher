package com.frimer.launcher.ui;

import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.Modrinth;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Браузер модов/шейдеров/ресурспаков/датапаков с Modrinth. */
public final class ModrinthDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final String projectType;
    private final String mcVersion;
    private final String loader;
    private final Path dest;
    private final Instance instance;

    private final JTextField searchField;
    private final DefaultListModel<Modrinth.Hit> model = new DefaultListModel<>();
    private final JList<Modrinth.Hit> list = new JList<>(model);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton installButton;
    private final JButton versionsButton;
    private final Map<String, byte[]> iconBytes = new ConcurrentHashMap<>();
    private final Map<String, Icon> iconCache = new ConcurrentHashMap<>();

    private volatile boolean busy;

    public ModrinthDialog(JFrame owner, Theme theme, Instance instance, String projectType, Path dest) {
        super(owner, "Modrinth — " + Modrinth.labelFor(projectType), true, theme);
        this.theme = theme;
        searchField = new Ui.Field(theme, "");
        this.instance = instance;
        this.projectType = projectType;
        this.dest = dest;
        this.mcVersion = instance == null ? "" : instance.mcVersion;
        this.loader = instance == null || "Vanilla".equals(instance.loader) ? "" : instance.loader;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel(Modrinth.labelFor(projectType) + " с Modrinth");
        title.setFont(Theme.titleFont(Font.BOLD, 16));
        title.setForeground(theme.text);

        JLabel context = new JLabel(mcVersion + (loader.isEmpty() ? "" : " · " + loader)
                + " · папка: " + dest.getFileName());
        context.setFont(Theme.uiFont(Font.PLAIN, 12));
        context.setForeground(theme.muted);

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(title);
        head.add(Box.createVerticalStrut(2));
        head.add(context);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        context.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);


        searchField.addActionListener(e -> doSearch());
        JButton searchButton = Ui.primaryButton("Найти", theme);
        searchButton.addActionListener(e -> doSearch());

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel headWrap = new JPanel(new java.awt.BorderLayout());
        headWrap.setOpaque(false);
        headWrap.add(head, java.awt.BorderLayout.WEST);
        top.add(headWrap);
        top.add(Box.createVerticalStrut(10));
        top.add(searchRow);

        list.setBackground(theme.card);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(70);
        list.setCellRenderer(new HitRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    install(null);
                }
            }
        });
        JScrollPane scroll = Ui.scroll(list, theme);
        scroll.setPreferredSize(new Dimension(720, 380));

        installButton = Ui.primaryButton("Установить", theme);
        installButton.addActionListener(e -> install(null));
        versionsButton = Ui.button("Выбрать версию…", theme);
        versionsButton.addActionListener(e -> chooseVersion());
        JButton pageButton = Ui.button("Открыть страницу", theme);
        pageButton.addActionListener(e -> {
            Modrinth.Hit hit = list.getSelectedValue();
            if (hit != null) {
                Modrinth.openPage(hit.pageUrl());
            }
        });
        JButton closeButton = Ui.miniButton("Закрыть", theme);
        closeButton.addActionListener(e -> dispose());

        statusLabel.setFont(Theme.uiFont(Font.PLAIN, 12));
        statusLabel.setForeground(theme.muted);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        JPanel buttons = Ui.buttonRow(installButton, versionsButton, pageButton);
        actions.add(buttons, BorderLayout.WEST);
        actions.add(closeButton, BorderLayout.EAST);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(statusLabel);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(actions);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(780, 580));
        setLocationRelativeTo(owner);
        doSearch();
    }

    public void showDialog() {
        setVisible(true);
    }

    /** Перечитать папку назначения — вызывается после установки. */
    public Path destination() {
        return dest;
    }

    private void doSearch() {
        if (busy) {
            return;
        }
        busy = true;
        installButton.setEnabled(false);
        versionsButton.setEnabled(false);
        statusLabel.setText("Ищу на Modrinth...");
        String query = searchField.getText();
        new Thread(() -> {
            try {
                Modrinth.SearchResult result = Modrinth.search(query, mcVersion, loader,
                        projectType, 0, 40);
                SwingUtilities.invokeLater(() -> {
                    model.clear();
                    for (Modrinth.Hit hit : result.hits()) {
                        model.addElement(hit);
                    }
                    statusLabel.setText(result.hits().isEmpty()
                            ? "Ничего не найдено"
                            : "Найдено: " + result.total() + " (показаны первые "
                            + result.hits().size() + ")");
                    if (!model.isEmpty()) {
                        list.setSelectedIndex(0);
                    }
                });
                loadIcons(result.hits());
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Ошибка поиска: "
                        + Utils.shortText(e.getMessage(), 120)));
            } finally {
                busy = false;
                SwingUtilities.invokeLater(() -> {
                    installButton.setEnabled(true);
                    versionsButton.setEnabled(true);
                });
            }
        }, "frimer-modrinth-search").start();
    }

    private void loadIcons(List<Modrinth.Hit> hits) {
        new Thread(() -> {
            for (Modrinth.Hit hit : hits) {
                if (hit.iconUrl().isEmpty() || iconBytes.containsKey(hit.id())) {
                    continue;
                }
                byte[] data = com.frimer.launcher.core.Downloader.fetchBytes(hit.iconUrl());
                if (data != null) {
                    iconBytes.put(hit.id(), data);
                    SwingUtilities.invokeLater(list::repaint);
                }
            }
        }, "frimer-modrinth-icons").start();
    }

    private void install(String versionId) {
        Modrinth.Hit hit = list.getSelectedValue();
        if (hit == null) {
            JOptionPane.showMessageDialog(this, "Выбери мод в списке.", "Ничего не выбрано",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        installButton.setEnabled(false);
        versionsButton.setEnabled(false);
        statusLabel.setText("Устанавливаю " + hit.title() + "...");
        new Thread(() -> {
            try {
                try {
                    java.nio.file.Files.createDirectories(dest);
                } catch (java.io.IOException ignored) {
                    // папку создаст загрузчик
                }
                Modrinth.Installed installed = Modrinth.install(hit, mcVersion, loader, projectType,
                        dest, versionId, null);
                SwingUtilities.invokeLater(() -> {
                    StringBuilder text = new StringBuilder("Установлено: ")
                            .append(String.join(", ", installed.files()));
                    if (!installed.warnings().isEmpty()) {
                        text.append(" · предупреждений: ").append(installed.warnings().size());
                    }
                    statusLabel.setText(Utils.shortText(text.toString(), 150));
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Не установилось");
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка установки",
                            JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    installButton.setEnabled(true);
                    versionsButton.setEnabled(true);
                });
            }
        }, "frimer-modrinth-install").start();
    }

    private void chooseVersion() {
        Modrinth.Hit hit = list.getSelectedValue();
        if (hit == null) {
            JOptionPane.showMessageDialog(this, "Выбери мод в списке.", "Ничего не выбрано",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        statusLabel.setText("Загружаю версии " + hit.title() + "...");
        new Thread(() -> {
            try {
                List<Modrinth.Version> versions = Modrinth.versions(hit.id(), mcVersion, loader,
                        projectType);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(versions.isEmpty() ? "Версий для этой сборки нет"
                            : "Версий: " + versions.size());
                    if (!versions.isEmpty()) {
                        VersionDialog dialog = new VersionDialog(this, theme, hit, versions);
                        Modrinth.Version picked = dialog.showDialog();
                        if (picked != null) {
                            install(picked.id());
                        }
                    }
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Ошибка: "
                        + Utils.shortText(e.getMessage(), 120)));
            }
        }, "frimer-modrinth-versions").start();
    }

    /** Выбор конкретной версии проекта. */
    private static final class VersionDialog extends Ui.FramelessDialog {
        private static final long serialVersionUID = 1L;

        private Modrinth.Version picked;

        VersionDialog(JDialog owner, Theme theme, Modrinth.Hit hit, List<Modrinth.Version> versions) {
            super(owner, "Версии — " + hit.title(), true, theme);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JPanel root = new JPanel(new BorderLayout(0, 10));
            root.setBackground(theme.bg);
            root.setBorder(new EmptyBorder(14, 16, 12, 16));

            DefaultListModel<Modrinth.Version> model = new DefaultListModel<>();
            for (Modrinth.Version v : versions) {
                model.addElement(v);
            }
            JList<Modrinth.Version> list = new JList<>(model);
            list.setBackground(theme.card);
            list.setForeground(theme.text);
            list.setFixedCellHeight(34);
            list.setFont(Theme.uiFont(Font.PLAIN, 13));
            list.setCellRenderer((l, value, index, selected, focused) -> {
                JLabel label = new JLabel(value.label() + "   " + value.type() + " · " + value.date());
                label.setOpaque(true);
                label.setFont(Theme.uiFont(Font.PLAIN, 13));
                label.setBorder(new EmptyBorder(4, 8, 4, 8));
                label.setBackground(selected ? theme.cardSel : theme.card);
                label.setForeground(selected ? theme.accent : theme.text);
                return label;
            });
            list.setSelectedIndex(0);
            JScrollPane scroll = Ui.scroll(list, theme);

            JButton ok = Ui.primaryButton("Установить", theme);
            ok.addActionListener(e -> {
                picked = list.getSelectedValue();
                dispose();
            });
            JButton cancel = Ui.miniButton("Отмена", theme);
            cancel.addActionListener(e -> dispose());

            JPanel buttons = Ui.buttonRow(ok, cancel);
            root.add(scroll, BorderLayout.CENTER);
            root.add(buttons, BorderLayout.SOUTH);
            setContentPane(root);
            setSize(new Dimension(460, 380));
            setLocationRelativeTo(owner);
        }

        Modrinth.Version showDialog() {
            setVisible(true);
            return picked;
        }
    }

    /** Строка результата: иконка, название, автор и загрузки, описание. */
    private final class HitRenderer extends JPanel implements ListCellRenderer<Modrinth.Hit> {
        private static final long serialVersionUID = 1L;
        private final JLabel icon = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel meta = new JLabel();
        private final JLabel description = new JLabel();

        HitRenderer() {
            setLayout(new BorderLayout(10, 0));
            icon.setPreferredSize(new Dimension(40, 40));
            icon.setHorizontalAlignment(JLabel.CENTER);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            title.setFont(Theme.titleFont(Font.BOLD, 14));
            meta.setFont(Theme.uiFont(Font.PLAIN, 11));
            description.setFont(Theme.uiFont(Font.PLAIN, 12));
            text.add(title);
            text.add(Box.createVerticalStrut(1));
            text.add(meta);
            text.add(Box.createVerticalStrut(2));
            text.add(description);
            add(icon, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Modrinth.Hit> list,
                                                      Modrinth.Hit value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Icon cached = iconCache.get(value.id());
            if (cached == null) {
                byte[] data = iconBytes.get(value.id());
                if (data != null) {
                    cached = Ui.imageIcon(data, 40);
                    if (cached != null) {
                        iconCache.put(value.id(), cached);
                    }
                }
            }
            icon.setIcon(cached != null ? cached
                    : Ui.badge(value.title().isEmpty() ? "?"
                    : value.title().substring(0, 1).toUpperCase(), theme.card, theme.muted, 40));
            title.setText(value.title());
            meta.setText((value.author().isEmpty() ? "" : "автор: " + value.author())
                    + " · " + Utils.fmtNum(value.downloads()) + " загрузок");
            description.setText(Utils.shortText(value.description(), 110));
            setBackground(isSelected ? theme.cardSel : theme.card);
            title.setForeground(isSelected ? theme.accent : theme.text);
            meta.setForeground(theme.muted);
            description.setForeground(isSelected ? theme.text : theme.muted);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, theme.border),
                    new EmptyBorder(4, 8, 4, 8)));
            setOpaque(true);
            return this;
        }
    }

    /** Список установленных файлов для отчёта. */
    public static List<String> installedNames(Modrinth.Installed installed) {
        return new ArrayList<>(installed.files());
    }
}
