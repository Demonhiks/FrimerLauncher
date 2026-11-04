package com.frimer.launcher.ui;

import com.frimer.launcher.core.Downloader;
import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.ModpackImporter;
import com.frimer.launcher.core.Modrinth;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
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

/** Поиск готовых сборок (модпаков) на Modrinth и установка в один клик. */
public final class ModpackBrowserDialog extends Ui.FramelessDialog {

    private static final long serialVersionUID = 1L;

    private final Theme theme;
    private final Consumer<Instance> onInstalled;

    private final JTextField searchField;
    private final DefaultListModel<Modrinth.Hit> model = new DefaultListModel<>();
    private final JList<Modrinth.Hit> list = new JList<>(model);
    private final JLabel status = new JLabel(" ");
    private final Ui.MiniBar bar;
    private final JButton install;
    private final Map<String, byte[]> iconBytes = new ConcurrentHashMap<>();
    private final Map<String, Icon> iconCache = new ConcurrentHashMap<>();

    private volatile boolean busy;

    public ModpackBrowserDialog(JFrame owner, Theme theme, Consumer<Instance> onInstalled) {
        super(owner, "Сборки с Modrinth", true, theme);
        this.theme = theme;
        this.onInstalled = onInstalled;
        searchField = new Ui.Field(theme, "");

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(theme.bg);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel title = new JLabel("Готовые сборки");
        title.setFont(Theme.titleFont(Font.BOLD, 16));
        title.setForeground(theme.text);
        JLabel hint = new JLabel("Сборка сама принесёт моды, конфиги и версию загрузчика");
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

        searchField.addActionListener(e -> doSearch());
        JButton find = Ui.primaryButton("Найти", theme);
        find.addActionListener(e -> doSearch());

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(find, BorderLayout.EAST);

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
        list.setFixedCellHeight(64);
        list.setCellRenderer(new HitRenderer());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    doInstall();
                }
            }
        });
        JScrollPane scroll = Ui.scroll(list, theme);
        scroll.setPreferredSize(new Dimension(700, 360));

        bar = new Ui.MiniBar(theme);
        bar.setPreferredSize(new Dimension(0, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        status.setFont(Theme.uiFont(Font.PLAIN, 12));
        status.setForeground(theme.muted);

        install = Ui.primaryButton("Установить сборку", theme);
        install.addActionListener(e -> doInstall());
        JButton page = Ui.button("Страница сборки", theme);
        page.addActionListener(e -> {
            Modrinth.Hit hit = list.getSelectedValue();
            if (hit != null) {
                Modrinth.openPage(hit.pageUrl());
            }
        });
        JButton close = Ui.miniButton("Закрыть", theme);
        close.addActionListener(e -> dispose());

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        actions.add(Ui.buttonRow(install, page), BorderLayout.WEST);
        actions.add(close, BorderLayout.EAST);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(status);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(bar);
        bottom.add(Box.createVerticalStrut(8));
        bottom.add(actions);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(760, 560));
        setLocationRelativeTo(owner);
        doSearch();
    }

    public void showDialog() {
        setVisible(true);
    }

    private void doSearch() {
        if (busy) {
            return;
        }
        busy = true;
        install.setEnabled(false);
        status.setText("Ищу сборки...");
        String query = searchField.getText();
        new Thread(() -> {
            try {
                Modrinth.SearchResult result = Modrinth.search(query, null, null,
                        Modrinth.MODPACK, 0, 40);
                SwingUtilities.invokeLater(() -> {
                    model.clear();
                    for (Modrinth.Hit hit : result.hits()) {
                        model.addElement(hit);
                    }
                    if (!model.isEmpty()) {
                        list.setSelectedIndex(0);
                    }
                    status.setText(result.hits().isEmpty() ? "Ничего не найдено"
                            : "Найдено: " + result.total() + " (показаны первые "
                            + result.hits().size() + ")");
                });
                loadIcons(result.hits());
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> status.setText("Ошибка поиска: "
                        + Utils.shortText(e.getMessage(), 110)));
            } finally {
                busy = false;
                SwingUtilities.invokeLater(() -> install.setEnabled(true));
            }
        }, "frimer-modpack-search").start();
    }

    private void loadIcons(List<Modrinth.Hit> hits) {
        new Thread(() -> {
            for (Modrinth.Hit hit : hits) {
                if (hit.iconUrl().isEmpty() || iconBytes.containsKey(hit.id())) {
                    continue;
                }
                byte[] bytes = com.frimer.launcher.core.IconImages.fetch(hit.iconUrl());
                if (bytes != null) {
                    iconBytes.put(hit.id(), bytes);
                    SwingUtilities.invokeLater(list::repaint);
                }
            }
        }, "frimer-modpack-icons").start();
    }

    private void doInstall() {
        Modrinth.Hit hit = list.getSelectedValue();
        if (hit == null) {
            JOptionPane.showMessageDialog(this, "Выбери сборку в списке.", "Ничего не выбрано",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        install.setEnabled(false);
        status.setText("Готовлю «" + hit.title() + "»...");
        new Thread(() -> {
            Path temp = null;
            try {
                Modrinth.FileRef file = Modrinth.modpackFile(hit.id());
                temp = Files.createTempFile("frimer-pack-", ".mrpack");
                status.setText("Скачиваю " + file.filename() + "...");
                Downloader.download(file.url(), temp, null, null, null,
                        (done, total) -> SwingUtilities.invokeLater(() -> {
                            int percent = total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
                            bar.setValue(percent);
                        }));
                status.setText("Распаковываю сборку...");
                ModpackImporter.Result result = ModpackImporter.importMrpack(temp,
                        line -> SwingUtilities.invokeLater(() -> status.setText(
                                Utils.shortText(line, 110))),
                        (done, total) -> SwingUtilities.invokeLater(() -> {
                            int percent = total <= 0 ? 0 : (int) Math.min(100, done * 100 / total);
                            bar.setValue(percent);
                        }));
                SwingUtilities.invokeLater(() -> {
                    bar.setValue(100);
                    status.setText("Установлено: " + result.instance().name
                            + (result.warnings().isEmpty() ? "" : " (предупреждений: "
                            + result.warnings().size() + ")"));
                    if (onInstalled != null) {
                        onInstalled.accept(result.instance());
                    }
                    install.setEnabled(true);
                });
            } catch (RuntimeException | IOException e) {
                SwingUtilities.invokeLater(() -> {
                    status.setText("Не установилось");
                    install.setEnabled(true);
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка установки",
                            JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                if (temp != null) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                        // временный файл уберёт ОС
                    }
                }
            }
        }, "frimer-modpack-install").start();
    }

    /** Строка списка: иконка, название, автор и описание. */
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
                byte[] bytes = iconBytes.get(value.id());
                if (bytes != null) {
                    cached = Ui.imageIcon(bytes, 40);
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
            description.setText(Utils.shortText(Utils.plainText(value.description()), 100));
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
}
