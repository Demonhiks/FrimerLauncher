package com.frimer.launcher.ui;

import com.frimer.launcher.core.AppSettings;
import com.frimer.launcher.core.Dirs;
import com.frimer.launcher.core.GameLauncher;
import com.frimer.launcher.core.Instance;
import com.frimer.launcher.core.InstanceStore;
import com.frimer.launcher.core.ModpackImporter;
import com.frimer.launcher.core.ServerInstance;
import com.frimer.launcher.core.ServerInstallers;
import com.frimer.launcher.core.ServerRunner;
import com.frimer.launcher.core.ServerStore;
import com.frimer.launcher.core.Utils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.border.EmptyBorder;

/** Главное окно Frimer: сайдбар, плитки сборок, карточка выбранной сборки и консоль. */
public final class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private Theme theme;
    private JPanel sidebarPanel;

    private List<Instance> instances = new ArrayList<>();
    private List<ServerInstance> servers = new ArrayList<>();
    private Instance current;
    private ServerInstance currentServer;
    private boolean serverTab;

    private GameLauncher gameLauncher;
    private ServerRunner serverRunner;
    private ServerConsoleDialog serverConsole;
    private volatile boolean gameRunning;
    private String playitAddress = "";

    // сайдбар
    private JButton nickButton;
    private JButton tabInstButton;
    private JButton tabSrvButton;
    private JLabel sectionLabel;
    private JButton newInstButton;
    private JButton delInstButton;
    private JButton settingsButton;
    private JButton importButton;
    private JButton newSrvButton;
    private JButton delSrvButton;

    // контент
    private Ui.Card heroCard;
    private JLabel heroIcon;
    private JLabel heroTitle;
    private JLabel heroSub;
    private JLabel heroStat;
    private JLabel titleLabel;
    private JLabel subLabel;
    private JLabel statusLabel;
    private JLabel progressLabel;
    private Ui.MiniBar progressBar;    private JPanel tilesPanel;
    private JScrollPane tilesScroll;
    private JButton playButton;
    private JButton killButton;
    private JButton startSrvButton;
    private JButton stopSrvButton;
    private JButton consoleButton;
    private JButton copyAddrButton;
    private JTextArea logArea;
    private JScrollPane logScroll;

    public MainFrame() {
        super("Frimer");
        theme = Theme.of(AppSettings.theme());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        java.awt.Rectangle bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int width = Math.max(820, Math.min(1080, bounds.width - 40));
        int height = Math.max(560, Math.min(740, bounds.height - 40));
        setMinimumSize(new Dimension(Math.min(820, width), Math.min(560, height)));
        setSize(width, height);
        setLocation(bounds.x + Math.max(0, (bounds.width - width) / 2),
                bounds.y + Math.max(0, (bounds.height - height) / 2));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(theme.bg);
        sidebarPanel = buildSidebar();
        root.add(sidebarPanel, BorderLayout.WEST);
        root.add(buildContent(), BorderLayout.CENTER);
        setContentPane(root);

        enableDropImport(root);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });

        refreshInstances();
        refreshServers();
        switchTab(false);
        updatePresenceLabels();
        installResponsiveHook();
    }

    /** Реагировать на изменение размера окна. */
    private void installResponsiveHook() {
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                applyResponsiveLayout();
            }
        });
        applyResponsiveLayout();
    }

    /** Подстройка под ширину окна: узкий сайдбар и скрытие карточки, если тесно. */
    private void applyResponsiveLayout() {
        boolean narrow = getWidth() < 900;
        if (sidebarPanel != null) {
            sidebarPanel.setPreferredSize(new Dimension(narrow ? 244 : 300, 0));
        }
        updateHeroVisibility();
        revalidate();
        repaint();
    }

    /** Карточка выбранной сборки показывается только когда хватает места. */
    private void updateHeroVisibility() {
        if (heroCard == null) {
            return;
        }
        heroCard.setVisible(!serverTab && current != null && getWidth() >= 900);
    }

    // =================================================================== сайдбар

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(theme.sidebar);
        sidebar.setPreferredSize(new Dimension(300, 0));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, theme.border),
                new EmptyBorder(22, 20, 18, 20)));

        JLabel title = new JLabel("Frimer");
        title.setFont(Theme.titleFont(Font.BOLD, 26));
        title.setForeground(theme.accent);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(14));

        nickButton = Ui.button("", theme);
        nickButton.setHorizontalAlignment(SwingConstants.LEFT);
        nickButton.setFont(Theme.uiFont(Font.BOLD, 14));
        nickButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        nickButton.addActionListener(e -> showNickMenu());
        refreshNickButton();

        JButton skinsButton = Ui.miniButton("Скины", theme);
        skinsButton.addActionListener(e -> openSkins());
        JButton folderButton = Ui.miniButton("Папка", theme);
        folderButton.addActionListener(e -> openCurrentFolder());

        JPanel nickRow = Ui.buttonRow(nickButton, skinsButton, folderButton);
        nickRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(nickRow);
        sidebar.add(Box.createVerticalStrut(14));

        tabInstButton = tabButton("Инстансы", true);
        tabSrvButton = tabButton("Серверы", false);
        tabInstButton.addActionListener(e -> switchTab(false));
        tabSrvButton.addActionListener(e -> switchTab(true));

        JPanel tabRow = new JPanel();
        tabRow.setOpaque(false);
        tabRow.setLayout(new BoxLayout(tabRow, BoxLayout.X_AXIS));
        tabRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabRow.add(tabInstButton);
        tabRow.add(Box.createHorizontalStrut(6));
        tabRow.add(tabSrvButton);
        tabRow.add(Box.createHorizontalGlue());
        sidebar.add(tabRow);
        sidebar.add(Box.createVerticalStrut(12));

        sectionLabel = Ui.label("Инстансы", Theme.uiFont(Font.PLAIN, 12), theme.muted);
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(sectionLabel);
        sidebar.add(Box.createVerticalStrut(6));

        newInstButton = Ui.miniButton("Создать", theme);
        newInstButton.addActionListener(e -> createInstance());
        delInstButton = Ui.miniButton("Удалить", theme);
        delInstButton.addActionListener(e -> deleteCurrentInstance());
        settingsButton = Ui.miniButton("Настройки", theme);
        settingsButton.addActionListener(e -> openSettings());

        JPanel instRow = Ui.buttonRow(newInstButton, delInstButton, settingsButton);
        instRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(instRow);
        sidebar.add(Box.createVerticalStrut(6));

        importButton = Ui.miniButton("Импорт .mrpack", theme);
        importButton.addActionListener(e -> chooseModpack());
        importButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(importButton);

        newSrvButton = Ui.miniButton("Создать сервер", theme);
        newSrvButton.addActionListener(e -> createServer());
        delSrvButton = Ui.miniButton("Удалить сервер", theme);
        delSrvButton.addActionListener(e -> deleteCurrentServer());

        JPanel srvRow = Ui.buttonRow(newSrvButton, delSrvButton);
        srvRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(srvRow);

        sidebar.add(Box.createVerticalGlue());
        JLabel version = Ui.label("Frimer " + AppSettings.APP_VERSION, Theme.uiFont(Font.PLAIN, 11),
                new Color(theme.muted.getRed(), theme.muted.getGreen(), theme.muted.getBlue(), 160));
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(version);

        // BoxLayout по вертикали растягивает детей до maximumSize - фиксируем высоты,
        // иначе кнопки расползаются на пол-экрана.
        for (Component c : sidebar.getComponents()) {
            if (c instanceof JButton button) {
                button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                button.setMinimumSize(new Dimension(0, 26));
            } else if (c instanceof JPanel row) {
                int preferred = Math.max(30, row.getPreferredSize().height);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred));
                row.setMinimumSize(new Dimension(0, preferred));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
            } else if (c instanceof JLabel label) {
                int preferred = Math.max(16, label.getPreferredSize().height);
                label.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred));
                label.setMinimumSize(new Dimension(0, preferred));
            }
        }
        return sidebar;
    }

    private JButton tabButton(String text, boolean active) {
        Ui.SegmentButton button = new Ui.SegmentButton(text, theme);
        button.setActive(active);
        button.setMaximumSize(new Dimension(120, 32));
        return button;
    }

    private void updateTabStyle(JButton button, boolean active) {
        if (button instanceof Ui.SegmentButton segment) {
            segment.setActive(active);
        }
    }

    // =================================================================== контент

    private JComponent buildContent() {
        JPanel content = new JPanel();
        content.setBackground(theme.bg);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(22, 26, 18, 26));

        heroCard = buildHero();
        heroCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        updateHeroVisibility();
        content.add(heroCard);
        content.add(Box.createVerticalStrut(12));

        titleLabel = Ui.label("Инстанс не выбран", Theme.titleFont(Font.BOLD, 22), theme.text);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(titleLabel);

        subLabel = Ui.label("Создай инстанс кнопкой «Создать»", Theme.uiFont(Font.PLAIN, 13), theme.muted);
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(subLabel);
        content.add(Box.createVerticalStrut(10));

        progressBar = new Ui.MiniBar(theme);
        progressBar.setPreferredSize(new Dimension(0, 6));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        progressBar.setMinimumSize(new Dimension(40, 6));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(progressBar);
        content.add(Box.createVerticalStrut(8));

        JPanel statusRow = new JPanel();
        statusRow.setOpaque(false);
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        statusLabel = Ui.label("Готов", Theme.uiFont(Font.PLAIN, 12), theme.muted);
        progressLabel = Ui.label("", Theme.uiFont(Font.PLAIN, 12), theme.muted);
        statusRow.add(statusLabel);
        statusRow.add(Box.createHorizontalGlue());
        statusRow.add(progressLabel);
        content.add(statusRow);
        content.add(Box.createVerticalStrut(10));

        tilesPanel = new WrapPanel(new WrapLayout(10));
        tilesPanel.setBackground(theme.bg);
        tilesScroll = Ui.borderlessScroll(tilesPanel, theme);
        tilesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(tilesScroll);

        content.add(Box.createVerticalStrut(10));

        playButton = Ui.primaryButton("Играть", theme);
        playButton.setFont(Theme.titleFont(Font.BOLD, 16));
        playButton.setPreferredSize(new Dimension(190, 42));
        playButton.setMaximumSize(new Dimension(190, 42));
        playButton.setEnabled(false);
        playButton.addActionListener(e -> play());

        killButton = Ui.button("Закрыть Minecraft", theme);
        killButton.setVisible(false);
        killButton.addActionListener(e -> killGame());

        startSrvButton = Ui.primaryButton("Запустить сервер", theme);
        startSrvButton.setFont(Theme.titleFont(Font.BOLD, 15));
        startSrvButton.setPreferredSize(new Dimension(190, 42));
        startSrvButton.setMaximumSize(new Dimension(190, 42));
        startSrvButton.setVisible(false);
        startSrvButton.setEnabled(false);
        startSrvButton.addActionListener(e -> startServer());

        stopSrvButton = Ui.button("Остановить", theme);
        stopSrvButton.setVisible(false);
        stopSrvButton.setEnabled(false);
        stopSrvButton.addActionListener(e -> stopServer());

        consoleButton = Ui.button("Консоль", theme);
        consoleButton.setVisible(false);
        consoleButton.setEnabled(false);
        consoleButton.addActionListener(e -> openServerConsole());

        copyAddrButton = Ui.button("Адрес для друзей", theme);
        copyAddrButton.setVisible(false);
        copyAddrButton.setEnabled(false);
        copyAddrButton.addActionListener(e -> copyServerAddress());

        JButton logToggle = Ui.miniButton("Консоль", theme);
        logToggle.setIcon(Ui.chevron(theme, false));
        logToggle.setHorizontalTextPosition(SwingConstants.LEFT);
        logToggle.setIconTextGap(8);
        logToggle.addActionListener(e -> toggleLog(logToggle));

        for (JButton b : new JButton[]{playButton, startSrvButton, stopSrvButton}) {
            b.setMinimumSize(new Dimension(120, 42));
        }
        for (JButton b : new JButton[]{consoleButton, copyAddrButton, killButton}) {
            b.setMinimumSize(new Dimension(96, 36));
        }
        logToggle.setMinimumSize(new Dimension(84, 30));

        JPanel actionRow = new JPanel();
        actionRow.setOpaque(false);
        actionRow.setLayout(new BoxLayout(actionRow, BoxLayout.X_AXIS));
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        actionRow.add(playButton);
        actionRow.add(Box.createHorizontalStrut(8));
        actionRow.add(startSrvButton);
        actionRow.add(Box.createHorizontalStrut(8));
        actionRow.add(stopSrvButton);
        actionRow.add(Box.createHorizontalStrut(8));
        actionRow.add(consoleButton);
        actionRow.add(Box.createHorizontalStrut(8));
        actionRow.add(copyAddrButton);
        actionRow.add(Box.createHorizontalStrut(8));
        actionRow.add(killButton);
        actionRow.add(Box.createHorizontalGlue());
        actionRow.add(logToggle);
        content.add(actionRow);
        content.add(Box.createVerticalStrut(8));

        logArea = Ui.logArea(theme);
        logScroll = Ui.scroll(logArea, theme);
        logScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        logScroll.setVisible(false);
        logScroll.setPreferredSize(new Dimension(10, 170));
        logScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        content.add(logScroll);

        return content;
    }

    private Ui.Card buildHero() {
        Ui.Card hero = new Ui.Card(theme.card, theme.border);
        hero.setRadius(12);
        hero.setLayout(new BorderLayout(16, 0));
        hero.setBorder(new EmptyBorder(12, 16, 12, 16));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        hero.setPreferredSize(new Dimension(10, 96));

        heroIcon = new JLabel();
        heroIcon.setPreferredSize(new Dimension(64, 64));
        heroIcon.setHorizontalAlignment(SwingConstants.CENTER);
        hero.add(heroIcon, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        heroTitle = Ui.label("", Theme.titleFont(Font.BOLD, 20), theme.text);
        heroSub = Ui.label("", Theme.uiFont(Font.PLAIN, 13), theme.muted);
        heroStat = Ui.label("", Theme.uiFont(Font.PLAIN, 12), theme.muted);
        text.add(Box.createVerticalGlue());
        text.add(heroTitle);
        text.add(Box.createVerticalStrut(2));
        text.add(heroSub);
        text.add(Box.createVerticalStrut(2));
        text.add(heroStat);
        text.add(Box.createVerticalGlue());
        hero.add(text, BorderLayout.CENTER);

        JButton heroPlay = Ui.primaryButton("Играть", theme);
        heroPlay.setFont(Theme.titleFont(Font.BOLD, 15));
        heroPlay.setPreferredSize(new Dimension(140, 44));
        heroPlay.addActionListener(e -> play());
        JPanel wrap = new JPanel(new java.awt.BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(10, 0, 10, 0));
        wrap.add(heroPlay, java.awt.BorderLayout.CENTER);
        hero.add(wrap, BorderLayout.EAST);
        return hero;
    }

    private void toggleLog(JButton toggle) {
        boolean visible = !logScroll.isVisible();
        logScroll.setVisible(visible);
        toggle.setText(visible ? "Консоль" : "Консоль");
        toggle.setIcon(Ui.chevron(theme, visible));
        revalidate();
        repaint();
    }

    // =================================================================== данные

    private void refreshInstances() {
        instances = InstanceStore.loadAll();
        String currentName = current == null ? null : current.name;
        current = null;
        rebuildTiles();
        if (instances.isEmpty()) {
            titleLabel.setText("Инстанс не выбран");
            subLabel.setText("Создай инстанс кнопкой «Создать»");
            playButton.setEnabled(false);
            updateHeroVisibility();
            return;
        }
        Instance pick = instances.get(0);
        if (currentName != null) {
            for (Instance inst : instances) {
                if (inst.name.equals(currentName)) {
                    pick = inst;
                    break;
                }
            }
        }
        selectInstance(pick);
    }

    private void refreshServers() {
        servers = ServerInstance.loadAll();
        String currentName = currentServer == null ? null : currentServer.name;
        currentServer = null;
        rebuildTiles();
        if (servers.isEmpty()) {
            if (serverTab) {
                titleLabel.setText("Сервер не выбран");
                subLabel.setText("Создай сервер кнопкой «Создать сервер»");
                startSrvButton.setEnabled(false);
            }
            return;
        }
        ServerInstance pick = servers.get(0);
        if (currentName != null) {
            for (ServerInstance srv : servers) {
                if (srv.name.equals(currentName)) {
                    pick = srv;
                    break;
                }
            }
        }
        if (serverTab) {
            selectServer(pick);
        }
    }

    private void rebuildTiles() {
        tilesPanel.removeAll();
        if (serverTab) {
            for (ServerInstance srv : servers) {
                tilesPanel.add(serverTile(srv));
            }
            if (servers.isEmpty()) {
                tilesPanel.add(Ui.centered("Пока нет серверов", theme));
            }
        } else {
            for (Instance inst : instances) {
                tilesPanel.add(instanceTile(inst));
            }
            if (instances.isEmpty()) {
                tilesPanel.add(Ui.centered("Пока нет сборок — нажми «Создать»", theme));
            }
        }
        tilesPanel.revalidate();
        tilesPanel.repaint();
    }

    private JComponent instanceTile(Instance inst) {
        final InstanceTile[] holder = new InstanceTile[1];
        InstanceTile tile = new InstanceTile(inst, theme, new InstanceTile.Handler() {
            @Override
            public void onSelect(Instance instance) {
                selectInstance(instance);
            }

            @Override
            public void onActivate(Instance instance) {
                selectInstance(instance);
                play();
            }

            @Override
            public void onMenu(Instance instance, Point point) {
                selectInstance(instance);
                showInstanceMenu(instance, holder[0], point);
            }
        });
        holder[0] = tile;
        if (current != null && current.folder.equals(inst.folder)) {
            tile.setSelected(true);
        }
        return tile;
    }

    private JComponent serverTile(ServerInstance srv) {
        Ui.Card card = new Ui.Card(theme.card, theme.border);
        card.setRadius(8);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        card.setPreferredSize(InstanceTile.SIZE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, InstanceTile.SIZE.height));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel icon = new JLabel(Ui.badge(ServerStore.coreLabel(srv.core).substring(0, 1),
                theme.loaderBg(ServerStore.coreLabel(srv.core)),
                theme.loaderFg(ServerStore.coreLabel(srv.core)), 44));
        icon.setPreferredSize(new Dimension(44, 44));
        card.add(icon, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = Ui.label(srv.name, Theme.titleFont(Font.BOLD, 14), theme.text);
        JLabel sub = Ui.label(ServerStore.coreLabel(srv.core) + " " + srv.mcVersion
                + " · порт " + srv.port, Theme.uiFont(Font.PLAIN, 11), theme.muted);
        text.add(Box.createVerticalGlue());
        text.add(name);
        text.add(Box.createVerticalStrut(3));
        text.add(sub);
        text.add(Box.createVerticalGlue());
        card.add(text, BorderLayout.CENTER);

        if (currentServer != null && currentServer.folder.equals(srv.folder)) {
            card.setFill(theme.cardSel);
            card.setStroke(theme.accent);
        }

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectServer(srv);
                if (e.getClickCount() == 2) {
                    startServer();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (currentServer == null || !currentServer.folder.equals(srv.folder)) {
                    card.setFill(theme.hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (currentServer == null || !currentServer.folder.equals(srv.folder)) {
                    card.setFill(theme.card);
                }
            }
        });
        return card;
    }

    private void selectInstance(Instance inst) {
        if (inst == null || serverTab) {
            return;
        }
        current = inst;
        for (Component c : tilesPanel.getComponents()) {
            if (c instanceof InstanceTile tile) {
                tile.setSelected(tile.instance().folder.equals(inst.folder));
            }
        }
        titleLabel.setText(inst.name);
        subLabel.setText(inst.loader + " " + inst.mcVersion + " — "
                + Utils.fmtGb(inst.ramMb) + " RAM — " + Utils.countMods(inst.folder)
                + " модов — играл: " + Utils.fmtDuration(inst.playedSeconds()));

        heroIcon.setIcon(InstanceTile.iconFor(inst, theme));
        heroTitle.setText(inst.name);
        heroSub.setText(inst.loader + " " + inst.mcVersion);
        StringBuilder stat = new StringBuilder(Utils.fmtGb(inst.ramMb));
        stat.append(" · ").append(Utils.countMods(inst.folder)).append(" модов");
        if (!inst.lastPlayed().isEmpty()) {
            stat.append(" · последний запуск: ").append(inst.lastPlayed());
        }
        heroStat.setText(stat.toString());
        updateHeroVisibility();
        playButton.setEnabled(!gameRunning);
        setStatus("Готов");
        setFraction(0, 0);
    }

    private void selectServer(ServerInstance srv) {
        currentServer = srv;
        rebuildTiles();
        if (srv == null) {
            titleLabel.setText("Сервер не выбран");
            subLabel.setText("Создай сервер кнопкой «Создать сервер»");
            startSrvButton.setEnabled(false);
            updateHeroVisibility();
            return;
        }
        titleLabel.setText(srv.name);
        subLabel.setText(ServerStore.coreLabel(srv.core) + " " + srv.mcVersion
                + " — " + Utils.fmtGb(srv.ramMb) + " RAM — порт " + srv.port);
        subLabel.setToolTipText(ServerStore.coreHint(srv.core));
        heroIcon.setIcon(Ui.badge(ServerStore.coreLabel(srv.core).substring(0, 1),
                theme.loaderBg(ServerStore.coreLabel(srv.core)),
                theme.loaderFg(ServerStore.coreLabel(srv.core)), 64));
        heroTitle.setText(srv.name);
        heroSub.setText(ServerStore.coreLabel(srv.core) + " " + srv.mcVersion);
        heroStat.setText(Utils.fmtGb(srv.ramMb) + " RAM · порт " + srv.port
                + (playitAddress.isEmpty() ? "" : " · " + playitAddress));
        updateHeroVisibility();
        boolean installed = srv.launchArgs() != null;
        startSrvButton.setEnabled(!isServerRunning());
        startSrvButton.setText(installed ? "Запустить сервер" : "Установить и запустить");
        stopSrvButton.setEnabled(isServerRunning());
        consoleButton.setEnabled(true);
        copyAddrButton.setEnabled(true);
        setStatus(isServerRunning() ? "Сервер запущен" : "Готов");
    }

    // =================================================================== вкладки

    private void switchTab(boolean toServers) {
        serverTab = toServers;
        updateTabStyle(tabInstButton, !toServers);
        updateTabStyle(tabSrvButton, toServers);
        sectionLabel.setText(toServers ? "Серверы" : "Инстансы");

        newInstButton.setVisible(!toServers);
        delInstButton.setVisible(!toServers);
        settingsButton.setVisible(!toServers);
        importButton.setVisible(!toServers);
        newSrvButton.setVisible(toServers);
        delSrvButton.setVisible(toServers);

        playButton.setVisible(!toServers);
        killButton.setVisible(!toServers && gameRunning);
        startSrvButton.setVisible(toServers);
        stopSrvButton.setVisible(toServers);
        consoleButton.setVisible(toServers);
        copyAddrButton.setVisible(toServers);

        if (toServers) {
            updateHeroVisibility();
            if (servers.isEmpty()) {
                titleLabel.setText("Сервер не выбран");
                subLabel.setText("Создай сервер кнопкой «Создать сервер»");
                startSrvButton.setEnabled(false);
            } else {
                selectServer(currentServer == null ? servers.get(0) : currentServer);
            }
        } else {
            if (instances.isEmpty()) {
                titleLabel.setText("Инстанс не выбран");
                subLabel.setText("Создай инстанс кнопкой «Создать»");
                playButton.setEnabled(false);
                updateHeroVisibility();
            } else {
                selectInstance(current != null ? current : instances.get(0));
            }
        }
        rebuildTiles();
        revalidate();
        repaint();
    }

    // =================================================================== ник

    private void refreshNickButton() {
        nickButton.setText(AppSettings.nickname());
        nickButton.setIcon(Ui.chevron(theme, false));
        nickButton.setHorizontalTextPosition(SwingConstants.LEFT);
        nickButton.setIconTextGap(10);
    }

    private void showNickMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(theme.card);
        menu.setBorder(BorderFactory.createLineBorder(theme.border));
        for (String nick : AppSettings.nicknameHistory()) {
            JMenuItem item = menuItem(nick);
            item.addActionListener(e -> {
                AppSettings.setNickname(nick);
                AppSettings.flush();
                refreshNickButton();
            });
            menu.add(item);
        }
        if (!AppSettings.nicknameHistory().isEmpty()) {
            menu.addSeparator();
        }
        JMenuItem add = menuItem("+ Добавить аккаунт");
        add.addActionListener(e -> {
            String nick = JOptionPane.showInputDialog(this, "Ник:", "Новый аккаунт",
                    JOptionPane.PLAIN_MESSAGE);
            if (nick == null) {
                return;
            }
            nick = nick.strip();
            if (!nick.matches("^[A-Za-z0-9_]{1,16}$")) {
                JOptionPane.showMessageDialog(this, "Только буквы, цифры и _ (до 16 символов).",
                        "Неверный ник", JOptionPane.WARNING_MESSAGE);
                return;
            }
            AppSettings.setNickname(nick);
            AppSettings.flush();
            refreshNickButton();
        });
        menu.add(add);
        menu.show(nickButton, 0, nickButton.getHeight());
    }

    private JMenuItem menuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(theme.card);
        item.setForeground(theme.text);
        item.setFont(Theme.uiFont(Font.PLAIN, 13));
        return item;
    }

    // =================================================================== инстансы

    private void createInstance() {
        NewInstanceDialog dialog = new NewInstanceDialog(this, theme);
        Instance created = dialog.showDialog();
        if (created != null) {
            refreshInstances();
            selectInstance(created);
            setStatus("Сборка «" + created.name + "» создана");
        }
    }

    private void deleteCurrentInstance() {
        if (current == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Удалить сборку «" + current.name + "» вместе с папкой и модами?",
                "Удаление сборки", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        Path folder = current.folder;
        runBackground("Удаляю сборку...", () -> {
            com.frimer.launcher.core.JavaProvisioner.deleteDir(folder);
            SwingUtilities.invokeLater(() -> {
                current = null;
                refreshInstances();
                setStatus("Сборка удалена");
            });
        });
    }

    private void editInstance(Instance inst) {
        EditInstanceDialog dialog = new EditInstanceDialog(this, theme, inst);
        if (dialog.showDialog()) {
            refreshInstances();
            selectInstance(inst);
            setStatus("Сборка обновлена");
        }
    }

    private void duplicateInstance(Instance inst) {
        runBackground("Копирую сборку...", () -> {
            Instance copy = inst.duplicate();
            SwingUtilities.invokeLater(() -> {
                refreshInstances();
                if (copy != null) {
                    selectInstance(copy);
                    setStatus("Создана копия «" + copy.name + "»");
                } else {
                    setStatus("Не удалось скопировать сборку");
                }
            });
        });
    }

    private void showInstanceMenu(Instance inst, JComponent anchor, Point point) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(theme.card);
        menu.setBorder(BorderFactory.createLineBorder(theme.border));

        JMenuItem playItem = menuItem("Играть");
        playItem.addActionListener(e -> play());
        menu.add(playItem);
        menu.add(new JSeparator());

        JMenuItem modsItem = menuItem("Моды (" + Utils.countMods(inst.folder) + ")");
        modsItem.addActionListener(e -> openModsDialog(inst));
        menu.add(modsItem);

        JMenuItem editItem = menuItem("Изменить");
        editItem.addActionListener(e -> editInstance(inst));
        menu.add(editItem);

        JMenuItem dupItem = menuItem("Дублировать");
        dupItem.addActionListener(e -> duplicateInstance(inst));
        menu.add(dupItem);

        JMenuItem folderItem = menuItem("Открыть папку");
        folderItem.addActionListener(e -> Ui.openFolder(inst.folder));
        menu.add(folderItem);
        menu.add(new JSeparator());

        JMenuItem delItem = menuItem("Удалить");
        delItem.addActionListener(e -> {
            selectInstance(inst);
            deleteCurrentInstance();
        });
        menu.add(delItem);

        menu.show(anchor, point.x, point.y);
    }

    private void openModsDialog(Instance inst) {
        ModsDialog dialog = new ModsDialog(this, theme, inst);
        dialog.showDialog();
        selectInstance(inst);
    }

    private void openCurrentFolder() {
        Path target = serverTab
                ? (currentServer == null ? null : currentServer.folder)
                : (current == null ? null : current.folder);
        if (target == null) {
            setStatus("Сначала выбери сборку или сервер");
            return;
        }
        Ui.openFolder(target);
    }

    // =================================================================== игра

    private void play() {
        if (current == null || gameRunning) {
            return;
        }
        Instance inst = current;
        gameRunning = true;
        playButton.setEnabled(false);
        playButton.setText("Запускаю...");
        killButton.setVisible(true);
        setStatus("Готовлю запуск...");
        appendLog("=== Запуск " + inst.name + " (" + inst.loader + " " + inst.mcVersion + ") ===");

        gameLauncher = new GameLauncher(inst, AppSettings.nickname(), new GameLauncher.Listener() {
            @Override
            public void onStatus(String status) {
                SwingUtilities.invokeLater(() -> setStatus(status));
            }

            @Override
            public void onProgress(long done, long total) {
                SwingUtilities.invokeLater(() -> setFraction(done, total));
            }

            @Override
            public void onLog(String line) {
                appendLog(line);
            }

            @Override
            public void onGameStarted(long timestamp) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Игра запущена");
                    setFraction(0, 0);
                });
            }

            @Override
            public void onFinished(int code) {
                SwingUtilities.invokeLater(() -> {
                    gameRunning = false;
                    playButton.setEnabled(true);
                    playButton.setText("Играть");
                    killButton.setVisible(false);
                    setStatus("Игра закрыта (код " + code + ")");
                    selectInstance(inst);
                });
            }

            @Override
            public void onFailed(String error) {
                SwingUtilities.invokeLater(() -> {
                    gameRunning = false;
                    playButton.setEnabled(true);
                    playButton.setText("Играть");
                    killButton.setVisible(false);
                    setFraction(0, 0);
                    setStatus("Ошибка запуска");
                    appendLog("ОШИБКА: " + error);
                    logScroll.setVisible(true);
                    JOptionPane.showMessageDialog(MainFrame.this, error, "Не удалось запустить игру",
                            JOptionPane.ERROR_MESSAGE);
                });
            }

            @Override
            public void onCrash(String summary, String body) {
                SwingUtilities.invokeLater(() -> {
                    appendLog(summary);
                    CrashingDialog dialog = new CrashingDialog(MainFrame.this, theme, summary, body);
                    dialog.showDialog();
                });
            }
        });
        gameLauncher.start();
    }

    private void killGame() {
        if (gameLauncher != null && gameLauncher.killGame()) {
            setStatus("Minecraft закрывается...");
        }
    }

    // =================================================================== сервера

    private void createServer() {
        NewServerDialog dialog = new NewServerDialog(this, theme);
        ServerInstance created = dialog.showDialog();
        if (created == null) {
            return;
        }
        serverTab = true;
        switchTab(true);
        runBackground("Устанавливаю ядро " + ServerStore.coreLabel(created.core) + "...", () -> {
            try {
                ServerInstallers.install(created, line -> appendLog(line), (done, total) -> {
                });
                SwingUtilities.invokeLater(() -> {
                    refreshServers();
                    selectServer(created);
                    setStatus("Сервер «" + created.name + "» готов к запуску");
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Ошибка установки сервера");
                    appendLog("ОШИБКА: " + e.getMessage());
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Не удалось установить сервер",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void startServer() {
        final ServerInstance srv = currentServer;
        if (srv == null) {
            return;
        }
        if (isServerRunning()) {
            openServerConsole();
            return;
        }
        startSrvButton.setEnabled(false);
        appendLog("=== Сервер " + srv.name + " (" + ServerStore.coreLabel(srv.core) + " "
                + srv.mcVersion + ") ===");

        if (srv.launchArgs() == null) {
            runBackground("Устанавливаю ядро...", () -> {
                try {
                    ServerInstallers.install(srv, line -> appendLog(line), (done, total) -> {
                    });
                    SwingUtilities.invokeLater(this::launchServerProcess);
                } catch (RuntimeException e) {
                    SwingUtilities.invokeLater(() -> {
                        startSrvButton.setEnabled(true);
                        setStatus("Ошибка установки сервера");
                        appendLog("ОШИБКА: " + e.getMessage());
                        JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка установки",
                                JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
        } else {
            launchServerProcess();
        }
    }

    private void launchServerProcess() {
        final ServerInstance srv = currentServer;
        if (srv == null) {
            return;
        }
        runBackground("Готовлю Java для сервера...", () -> {
            try {
                Path java = com.frimer.launcher.core.JavaProvisioner.ensureJava(srv.mcVersion,
                        line -> appendLog(line), (done, total) -> {
                        });
                SwingUtilities.invokeLater(() -> {
                    serverRunner = new ServerRunner(srv, java, true, new ServerRunner.Listener() {
                        @Override
                        public void onStatus(String status) {
                            SwingUtilities.invokeLater(() -> setStatus(status));
                        }

                        @Override
                        public void onLog(String line) {
                            appendLog(line);
                            detectPlayitAddress(line);
                            SwingUtilities.invokeLater(() -> {
                                if (serverConsole != null) {
                                    serverConsole.appendLog(line);
                                }
                            });
                        }

                        @Override
                        public void onStarted() {
                            SwingUtilities.invokeLater(() -> {
                                stopSrvButton.setEnabled(true);
                                startSrvButton.setEnabled(false);
                                startSrvButton.setText("Запустить сервер");
                                setStatus("Сервер запущен");
                                copyAddrButton.setEnabled(true);
                            });
                        }

                        @Override
                        public void onStopped(int code) {
                            SwingUtilities.invokeLater(() -> {
                                stopSrvButton.setEnabled(false);
                                startSrvButton.setEnabled(true);
                                setStatus("Сервер остановлен (код " + code + ")");
                                if (serverConsole != null) {
                                    serverConsole.setRunning(false);
                                }
                            });
                        }

                        @Override
                        public void onFailed(String error) {
                            SwingUtilities.invokeLater(() -> {
                                startSrvButton.setEnabled(true);
                                setStatus("Ошибка сервера");
                                appendLog("ОШИБКА: " + error);
                                JOptionPane.showMessageDialog(MainFrame.this, error, "Сервер",
                                        JOptionPane.ERROR_MESSAGE);
                            });
                        }
                    });
                    serverRunner.start();
                    if (serverConsole != null) {
                        serverConsole.setRunning(true);
                    }
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    startSrvButton.setEnabled(true);
                    setStatus("Ошибка Java для сервера");
                    appendLog("ОШИБКА: " + e.getMessage());
                });
            }
        });
    }

    private void detectPlayitAddress(String line) {
        var m = java.util.regex.Pattern.compile("([a-z0-9-]+\\.(?:playit\\.gg|at\\.ply\\.gg|joinmc\\.link))")
                .matcher(line == null ? "" : line.toLowerCase());
        if (m.find()) {
            playitAddress = m.group(1);
            SwingUtilities.invokeLater(() -> {
                copyAddrButton.setText("Копировать: " + playitAddress);
                if (currentServer != null) {
                    currentServer.playitAddress = playitAddress;
                    currentServer.save();
                }
            });
        }
    }

    private void stopServer() {
        if (serverRunner == null || !serverRunner.isRunning()) {
            return;
        }
        setStatus("Останавливаю сервер...");
        appendLog("[server] отправляю stop");
        serverRunner.requestStop();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (serverRunner != null && serverRunner.isRunning()) {
                appendLog("[server] не остановился за 15 секунд - завершаю принудительно");
                serverRunner.kill();
            }
        }, "frimer-server-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private boolean isServerRunning() {
        return serverRunner != null && serverRunner.isRunning();
    }

    private void openServerConsole() {
        if (currentServer == null) {
            return;
        }
        if (serverConsole == null || !serverConsole.isDisplayable()) {
            serverConsole = new ServerConsoleDialog(this, theme, currentServer, () -> {
                if (serverRunner != null) {
                    serverRunner.requestStop();
                }
            }, () -> {
                if (serverRunner != null) {
                    serverRunner.kill();
                }
            });
        }
        serverConsole.setRunning(isServerRunning());
        serverConsole.setVisible(true);
    }

    private void copyServerAddress() {
        String address = playitAddress.isEmpty()
                ? "localhost:" + (currentServer == null ? 25565 : currentServer.port)
                : playitAddress;
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(address), null);
        setStatus("Адрес скопирован: " + address);
    }

    private void deleteCurrentServer() {
        if (currentServer == null) {
            return;
        }
        if (isServerRunning()) {
            JOptionPane.showMessageDialog(this, "Сначала останови сервер.", "Сервер работает",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Удалить сервер «" + currentServer.name + "» со всеми мирами?",
                "Удаление сервера", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        Path folder = currentServer.folder;
        runBackground("Удаляю сервер...", () -> {
            com.frimer.launcher.core.JavaProvisioner.deleteDir(folder);
            SwingUtilities.invokeLater(() -> {
                currentServer = null;
                refreshServers();
                selectServer(servers.isEmpty() ? null : servers.get(0));
                setStatus("Сервер удалён");
            });
        });
    }

    // =================================================================== прочее

    private void openSettings() {
        SettingsDialog dialog = new SettingsDialog(this, theme);
        if (dialog.showDialog()) {
            String newTheme = AppSettings.theme();
            if (!newTheme.equals(theme.name)) {
                JOptionPane.showMessageDialog(this, "Тема применится после перезапуска лаунчера.",
                        "Настройки", JOptionPane.INFORMATION_MESSAGE);
            }
            refreshNickButton();
            refreshInstances();
            refreshServers();
        }
    }

    private void openSkins() {
        SkinDialog dialog = new SkinDialog(this, theme, new ArrayList<>(instances));
        dialog.showDialog();
    }

    private void chooseModpack() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Выбери сборку Modrinth (.mrpack)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Modrinth modpack (*.mrpack)", "mrpack"));
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        importModpack(chooser.getSelectedFile().toPath());
    }

    private void importModpack(Path file) {
        logScroll.setVisible(true);
        runBackground("Импортирую сборку " + file.getFileName() + "...", () -> {
            try {
                ModpackImporter.Result result = ModpackImporter.importMrpack(file,
                        line -> appendLog(line),
                        (done, total) -> SwingUtilities.invokeLater(() -> setFraction(done, total)));
                SwingUtilities.invokeLater(() -> {
                    setFraction(0, 0);
                    refreshInstances();
                    selectInstance(result.instance());
                    setStatus("Сборка «" + result.instance().name + "» импортирована"
                            + (result.warnings().isEmpty() ? "" : " (предупреждений: "
                            + result.warnings().size() + ")"));
                    for (String warning : result.warnings()) {
                        appendLog("! " + warning);
                    }
                });
            } catch (RuntimeException e) {
                SwingUtilities.invokeLater(() -> {
                    setFraction(0, 0);
                    setStatus("Импорт не удался");
                    appendLog("ОШИБКА: " + e.getMessage());
                    JOptionPane.showMessageDialog(this, e.getMessage(), "Импорт сборки",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void enableDropImport(JComponent component) {
        component.setTransferHandler(new TransferHandler() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        if (file.getName().toLowerCase().endsWith(".mrpack")) {
                            importModpack(file.toPath());
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                    // не файл - игнорируем
                }
                return false;
            }
        });
    }

    private void runBackground(String statusText, Runnable task) {
        setStatus(statusText);
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(true);
            progressLabel.setText("");
        });
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                });
            }
        }, "frimer-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void setStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void setFraction(long done, long total) {
        SwingUtilities.invokeLater(() -> {
            if (total <= 0) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                progressLabel.setText("");
                return;
            }
            int percent = (int) Math.min(100, Math.max(0, done * 100 / total));
            progressBar.setIndeterminate(false);
            progressBar.setValue(percent);
            progressLabel.setText(percent + "%");
        });
    }

    private void appendLog(String line) {
        if (line == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            String current = logArea.getText();
            if (current.length() > 200_000) {
                logArea.setText("");
            }
            logArea.append(line + "\n");
            if (!logScroll.isVisible()) {
                logScroll.setVisible(true);
                revalidate();
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void updatePresenceLabels() {
        if (current != null) {
            playButton.setEnabled(!gameRunning);
        }
    }

    /** Показать «что нового» после обновления версии. */
    public void afterShow() {
        if (!AppSettings.APP_VERSION.equals(AppSettings.lastSeenVersion())) {
            String notes = String.join("\n", List.of(
                    "- Лаунчер переписан на Java (Swing) без внешних зависимостей",
                    "- Поддержка Vanilla / Fabric / Quilt / Forge / NeoForge",
                    "- Папка данных та же: ~/.mylauncher (настройки и сборки совместимы)",
                    "- Сервера: Paper / Vanilla / Fabric / Forge / NeoForge + playit.gg",
                    "- Импорт сборок Modrinth (.mrpack) перетаскиванием на окно"));
            JOptionPane.showMessageDialog(this, notes,
                    "Frimer " + AppSettings.APP_VERSION + " — что нового",
                    JOptionPane.INFORMATION_MESSAGE);
            AppSettings.setLastSeenVersion(AppSettings.APP_VERSION);
            AppSettings.flush();
        }
    }

    private void onClose() {
        if (gameRunning) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Minecraft ещё запущен. Закрыть его и выйти?",
                    "Игра работает", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.CANCEL_OPTION) {
                return;
            }
            if (answer == JOptionPane.YES_OPTION) {
                killGame();
            }
        }
        if (isServerRunning()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Сервер ещё запущен. Остановить его и выйти?\nЕсли «Нет» — сервер продолжит работать.",
                    "Сервер работает", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.CANCEL_OPTION) {
                return;
            }
            if (answer == JOptionPane.YES_OPTION && serverRunner != null) {
                serverRunner.requestStop();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (serverRunner.isRunning()) {
                    serverRunner.kill();
                }
            }
        }
        AppSettings.flush();
        dispose();
        System.exit(0);
    }

    // =================================================================== раскладка

    /** Панель плиток: ширина равна видимой области, поэтому перенос строк работает верно. */
    private static final class WrapPanel extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;

        WrapPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation,
                                              int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation,
                                               int direction) {
            return Math.max(60, visibleRect.height - 40);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Простая «обтекаемая» раскладка плиток по ширине контейнера. */
    private static final class WrapLayout implements LayoutManager {
        private final int gap;

        WrapLayout(int gap) {
            this.gap = gap;
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                int limit = availableWidth(target);
                if (limit <= 0) {
                    limit = 640;
                }
                Insets insets = target.getInsets();
                int usable = Math.max(120, limit - insets.left - insets.right - gap);
                int usedWidth = 0;
                int rowWidth = 0;
                int rowHeight = 0;
                int totalHeight = 0;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Dimension d = c.getPreferredSize();
                    if (rowWidth > 0 && rowWidth + gap + d.width > usable) {
                        totalHeight += rowHeight + gap;
                        usedWidth = Math.max(usedWidth, rowWidth);
                        rowWidth = d.width;
                        rowHeight = d.height;
                    } else {
                        rowWidth = rowWidth == 0 ? d.width : rowWidth + gap + d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                totalHeight += rowHeight;
                usedWidth = Math.max(usedWidth, rowWidth);
                return new Dimension(usedWidth + insets.left + insets.right + gap,
                        totalHeight + insets.top + insets.bottom + gap);
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return new Dimension(120, 60);
        }

        /** Ширина, доступная плиткам: у вьюпорта точнее, чем у самой панели. */
        private int availableWidth(Container target) {
            Container parent = target.getParent();
            if (parent instanceof javax.swing.JViewport viewport) {
                int extent = viewport.getExtentSize().width;
                if (extent > 0) {
                    return extent;
                }
            }
            return target.getWidth();
        }

        @Override
        public void layoutContainer(Container target) {
            synchronized (target.getTreeLock()) {
                Insets insets = target.getInsets();
                int limit = availableWidth(target) - insets.right;
                int x = insets.left;
                int y = insets.top;
                int rowHeight = 0;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Dimension d = c.getPreferredSize();
                    if (x > insets.left && x + d.width > limit) {
                        x = insets.left;
                        y += rowHeight + gap;
                        rowHeight = 0;
                    }
                    c.setBounds(x, y, Math.min(d.width, Math.max(120, limit - x)), d.height);
                    x += d.width + gap;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }
        }
    }
}
