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
import com.frimer.launcher.core.presence.DiscordRpc;
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

    private final java.util.Map<java.nio.file.Path, GameLauncher> running =
            new java.util.LinkedHashMap<>();
    private ServerRunner serverRunner;
    private ServerConsoleDialog serverConsole;
    private String playitAddress = "";

    // сайдбар
    private JButton nickButton;
    private JButton tabInstButton;
    private JButton tabSrvButton;
    private JButton tabFriendsButton;
    private final java.awt.CardLayout contentCards = new java.awt.CardLayout();
    private final JPanel contentHolder = new JPanel(contentCards);
    private FriendsPanel friendsPanel;
    private JLabel sectionLabel;
    private JButton newInstButton;
    private JButton delInstButton;
    private JButton settingsButton;
    private JButton importButton;
    private JButton modpackButton;
    private JButton newSrvButton;
    private JButton delSrvButton;

    // контент
    private Ui.Card heroCard;
    private JLabel heroIcon;
    private JLabel heroTitle;
    private JLabel heroSub;
    private JLabel heroStat;
    private JPanel heroChips;
    private JPanel contentPanel;
    private JPanel titleBlock;
    private JPanel actionRowPanel;
    private Ui.IconButton changelogButton;
    private static final java.util.regex.Pattern TPS_LINE = java.util.regex.Pattern.compile(
            "TPS from last 1m, 5m, 15m:\\s*([0-9.,*]+)");
    private final java.util.Map<Path, String> serverTps = new java.util.HashMap<>();
    private final java.util.Map<Path, Integer> serverOnline = new java.util.HashMap<>();
    private final java.util.Map<Path, Integer> restartAttempts = new java.util.HashMap<>();
    private boolean playitClaimOpened;
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
        super("Frimer Launcher");
        setIconImage(Ui.appIcon());
        theme = com.frimer.launcher.core.Themes.load(AppSettings.theme());
        applyUiDefaults(theme);
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
        contentHolder.add(buildContent(), "main");
        root.add(contentHolder, BorderLayout.CENTER);
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

    /**
     * Цвета для стандартных всплывающих частей Swing: без этого выпадающие списки
     * и меню рисуются белыми, а текст в них — светлым из темы (нечитаемо).
     */
    private static void applyUiDefaults(Theme theme) {
        javax.swing.UIManager.put("PopupMenu.background", theme.card);
        javax.swing.UIManager.put("PopupMenu.foreground", theme.text);
        javax.swing.UIManager.put("MenuItem.background", theme.card);
        javax.swing.UIManager.put("MenuItem.foreground", theme.text);
        javax.swing.UIManager.put("MenuItem.selectionBackground", theme.cardSel);
        javax.swing.UIManager.put("MenuItem.selectionForeground", theme.accent);
        javax.swing.UIManager.put("ComboBox.background", theme.input);
        javax.swing.UIManager.put("ComboBox.foreground", theme.text);
        javax.swing.UIManager.put("ComboBox.selectionBackground", theme.cardSel);
        javax.swing.UIManager.put("ComboBox.selectionForeground", theme.accent);
        javax.swing.UIManager.put("List.background", theme.card);
        javax.swing.UIManager.put("List.foreground", theme.text);
        javax.swing.UIManager.put("List.selectionBackground", theme.cardSel);
        javax.swing.UIManager.put("List.selectionForeground", theme.accent);
    }

    /** Реагировать на изменение размера окна. */
    private void installResponsiveHook() {        addComponentListener(new java.awt.event.ComponentAdapter() {
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

    /** Чипы под названием сборки: память, моды, последний запуск, загрузчик. */
    private void updateHeroChips(Instance inst) {
        if (heroChips == null) {
            return;
        }
        heroChips.removeAll();
        heroChips.add(Ui.chip(Utils.fmtGb(inst.ramMb) + " RAM", theme));
        int mods = Utils.countMods(inst.folder);
        heroChips.add(Ui.chip(mods + " модов", theme));
        if (!inst.lastPlayed().isEmpty()) {
            heroChips.add(Ui.chip("был(а) " + inst.lastPlayed(), theme));
        }
        heroChips.add(Ui.accentChip(inst.loader + " " + inst.mcVersion, theme));
        heroChips.revalidate();
        heroChips.repaint();
    }

    /**
     * Карточка выбранной сборки показывается только когда хватает места.
     * Если карточка видна, дублирующие название и подпись скрываются,
     * а главная кнопка «Играть» остаётся одна — внутри карточки.
     */
    private void updateHeroVisibility() {
        if (heroCard == null) {
            return;
        }
        boolean show = !serverTab && current != null && getWidth() >= 900;
        heroCard.setVisible(show);
        if (contentPanel != null && titleBlock != null) {
            if (show) {
                contentPanel.remove(titleBlock);
            } else if (titleBlock.getParent() == null) {
                int index = 0;
                for (int i = 0; i < contentPanel.getComponentCount(); i++) {
                    if (contentPanel.getComponent(i) == heroCard) {
                        index = i + 2;
                        break;
                    }
                }
                contentPanel.add(titleBlock, Math.min(index, contentPanel.getComponentCount()));
            }
        }
        if (actionRowPanel != null && playButton != null) {
            if (show) {
                actionRowPanel.remove(playButton);
            } else if (playButton.getParent() == null) {
                actionRowPanel.add(playButton, 0);
            }
        }
        if (contentPanel != null) {
            contentPanel.revalidate();
            contentPanel.repaint();
        }
    }

    // =================================================================== сайдбар

    private JPanel buildSidebar() {
        Ui.GradientPanel sidebar = new Ui.GradientPanel(theme.sidebar, theme.bg);
        sidebar.setEdgeLine(theme.border);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(318, 0));
        sidebar.setBorder(new EmptyBorder(20, 18, 16, 18));

        JPanel brandRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        brandRow.setOpaque(false);
        brandRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandRow.add(new JLabel(Ui.logoMark(theme, 34)));
        JLabel brand = new JLabel("Frimer");
        brand.setFont(Theme.titleFont(Font.BOLD, 24));
        brand.setForeground(theme.text);
        brandRow.add(brand);
        sidebar.add(brandRow);

        JLabel tagline = Ui.label("Minecraft launcher", Theme.uiFont(Font.PLAIN, 11), theme.muted);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagline.setBorder(new EmptyBorder(0, 4, 0, 0));
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(18));

        JLabel accountLabel = Ui.sectionLabel("Аккаунт", theme);
        accountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(accountLabel);
        sidebar.add(Box.createVerticalStrut(6));

        nickButton = Ui.button("", theme);
        nickButton.setHorizontalAlignment(SwingConstants.LEFT);
        nickButton.setFont(Theme.uiFont(Font.BOLD, 14));
        nickButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        nickButton.addActionListener(e -> openAccounts());
        refreshNickButton();

        JButton skinsButton = Ui.miniButton("Скины", theme);
        skinsButton.addActionListener(e -> openSkins());
        JButton folderButton = Ui.miniButton("Папка", theme);
        folderButton.addActionListener(e -> openCurrentFolder());

        JPanel nickRow = new JPanel(new java.awt.BorderLayout());
        nickRow.setOpaque(false);
        nickRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nickRow.add(nickButton, java.awt.BorderLayout.CENTER);
        sidebar.add(nickRow);
        sidebar.add(Box.createVerticalStrut(6));

        JPanel nickTools = Ui.buttonRow(skinsButton, folderButton);
        nickTools.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(nickTools);
        sidebar.add(Box.createVerticalStrut(18));

        tabInstButton = tabButton("Сборки", true);
        tabSrvButton = tabButton("Серверы", false);
        tabFriendsButton = tabButton("Друзья", false);
        tabInstButton.addActionListener(e -> switchTab(false));
        tabSrvButton.addActionListener(e -> switchTab(true));
        tabFriendsButton.addActionListener(e -> showFriends());

        Ui.Card tabCard = new Ui.Card(theme.input, theme.border);
        tabCard.setRadius(11);
        tabCard.setLayout(new java.awt.GridLayout(1, 3, 4, 0));
        tabCard.setBorder(new EmptyBorder(4, 4, 4, 4));
        tabCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        tabCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabCard.add(tabInstButton);
        tabCard.add(tabSrvButton);
        tabCard.add(tabFriendsButton);
        sidebar.add(tabCard);
        sidebar.add(Box.createVerticalStrut(18));

        sectionLabel = Ui.sectionLabel("Инстансы", theme);
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(sectionLabel);
        sidebar.add(Box.createVerticalStrut(8));

        newInstButton = Ui.primaryButton("Создать", theme);
        newInstButton.addActionListener(e -> createInstance());
        delInstButton = Ui.miniButton("Удалить", theme);
        delInstButton.addActionListener(e -> deleteCurrentInstance());
        settingsButton = Ui.miniButton("Настройки", theme);
        settingsButton.addActionListener(e -> openSettings());

        JPanel instRow = Ui.buttonRow(newInstButton, delInstButton);
        instRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(instRow);
        sidebar.add(Box.createVerticalStrut(6));

        settingsButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(settingsButton);
        sidebar.add(Box.createVerticalStrut(6));

        importButton = Ui.miniButton("Импорт .mrpack", theme);
        importButton.addActionListener(e -> chooseModpack());
        importButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(importButton);
        sidebar.add(Box.createVerticalStrut(6));

        modpackButton = Ui.miniButton("Сборки с Modrinth", theme);
        modpackButton.addActionListener(e -> openModpacks());
        modpackButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(modpackButton);

        newSrvButton = Ui.primaryButton("Создать сервер", theme);
        newSrvButton.addActionListener(e -> createServer());
        delSrvButton = Ui.miniButton("Удалить сервер", theme);
        delSrvButton.addActionListener(e -> deleteCurrentServer());

        newSrvButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(newSrvButton);
        sidebar.add(Box.createVerticalStrut(6));

        delSrvButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(delSrvButton);

        sidebar.add(Box.createVerticalGlue());

        JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.add(Ui.chip("Frimer " + AppSettings.APP_VERSION, theme));
        footer.add(Box.createHorizontalStrut(6));
        changelogButton = new Ui.IconButton(theme, Ui.sparkleIcon(theme, 16));
        changelogButton.setToolTipText("Что нового");
        changelogButton.addActionListener(e -> openChangelog());
        changelogButton.setBadge(hasUnseenChanges());
        footer.add(changelogButton);
        sidebar.add(footer);

        // BoxLayout по вертикали растягивает детей до maximumSize - фиксируем высоты.
        for (Component c : sidebar.getComponents()) {
            if (c instanceof JButton button) {
                button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                button.setMinimumSize(new Dimension(0, 28));
            } else if (c instanceof JPanel row) {
                int preferred = Math.max(30, row.getPreferredSize().height);
                // при нехватке ширины FlowLayout переносит кнопки на вторую строку
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred + 34));
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
        button.setFont(Theme.uiFont(Font.PLAIN, 11));
        button.setActive(active);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
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
        content.setBorder(new EmptyBorder(24, 28, 20, 28));

        contentPanel = content;
        heroCard = buildHero();
        heroCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(heroCard);
        content.add(Box.createVerticalStrut(16));

        titleLabel = Ui.label("Инстанс не выбран", Theme.titleFont(Font.BOLD, 24), theme.text);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        subLabel = Ui.label("Создай инстанс кнопкой «Создать»", Theme.uiFont(Font.PLAIN, 13), theme.muted);
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.add(titleLabel);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(subLabel);
        titleBlock.add(Box.createVerticalStrut(14));
        content.add(titleBlock);
        updateHeroVisibility();

        progressBar = new Ui.MiniBar(theme);
        progressBar.setPreferredSize(new Dimension(0, 8));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        progressBar.setMinimumSize(new Dimension(40, 8));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(progressBar);
        content.add(Box.createVerticalStrut(10));

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
        content.add(Box.createVerticalStrut(12));

        tilesPanel = new WrapPanel(12);
        tilesPanel.setBackground(theme.bg);
        tilesScroll = Ui.borderlessScroll(tilesPanel, theme);
        tilesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(tilesScroll);

        content.add(Box.createVerticalStrut(12));

        playButton = Ui.primaryButton("Играть", theme);
        playButton.setFont(Theme.titleFont(Font.BOLD, 16));
        playButton.setPreferredSize(new Dimension(200, 46));
        playButton.setMaximumSize(new Dimension(200, 46));
        playButton.setEnabled(false);
        playButton.addActionListener(e -> play());

        killButton = Ui.button("Закрыть Minecraft", theme);
        killButton.setVisible(false);
        killButton.addActionListener(e -> killGame());

        startSrvButton = Ui.primaryButton("Запустить сервер", theme);
        startSrvButton.setFont(Theme.titleFont(Font.BOLD, 15));
        startSrvButton.setPreferredSize(new Dimension(200, 46));
        startSrvButton.setMaximumSize(new Dimension(200, 46));
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
            b.setMinimumSize(new Dimension(130, 46));
        }
        for (JButton b : new JButton[]{consoleButton, copyAddrButton, killButton}) {
            b.setMinimumSize(new Dimension(100, 40));
        }
        logToggle.setMinimumSize(new Dimension(84, 30));

        JPanel actionRow = new JPanel();
        actionRow.setOpaque(false);
        actionRow.setLayout(new BoxLayout(actionRow, BoxLayout.X_AXIS));
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
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
        actionRowPanel = actionRow;
        content.add(actionRow);
        content.add(Box.createVerticalStrut(10));

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
        hero.setRadius(14);
        hero.setGradient(theme.cardTop(), theme.cardBottom());
        hero.setShadow(Ui.shadow(theme));
        hero.setLayout(new BorderLayout(16, 0));
        hero.setBorder(new EmptyBorder(12, 16, 12, 16));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
        hero.setPreferredSize(new Dimension(10, 104));

        heroIcon = new JLabel();
        heroIcon.setHorizontalAlignment(SwingConstants.CENTER);
        Ui.Card iconBox = new Ui.Card(theme.input, theme.border);
        iconBox.setRadius(14);
        iconBox.setLayout(new java.awt.BorderLayout());
        iconBox.setBorder(new EmptyBorder(6, 6, 6, 6));
        iconBox.setPreferredSize(new Dimension(72, 72));
        iconBox.setMaximumSize(new Dimension(72, 72));
        iconBox.add(heroIcon, java.awt.BorderLayout.CENTER);
        JPanel iconWrap = new JPanel(new java.awt.BorderLayout());
        iconWrap.setOpaque(false);
        iconWrap.add(iconBox, java.awt.BorderLayout.CENTER);
        hero.add(iconWrap, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        heroTitle = Ui.label("", Theme.titleFont(Font.BOLD, 21), theme.text);
        heroSub = Ui.label("", Theme.uiFont(Font.PLAIN, 13), theme.muted);
        heroStat = Ui.label("", Theme.uiFont(Font.PLAIN, 12), theme.muted);
        heroStat.setVisible(false);
        heroChips = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        heroChips.setOpaque(false);
        heroChips.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        heroSub.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(Box.createVerticalGlue());
        text.add(heroTitle);
        text.add(Box.createVerticalStrut(4));
        text.add(heroSub);
        text.add(Box.createVerticalStrut(8));
        text.add(heroChips);
        text.add(Box.createVerticalGlue());
        hero.add(text, BorderLayout.CENTER);

        JButton heroPlay = Ui.primaryButton("Играть", theme);
        heroPlay.setFont(Theme.titleFont(Font.BOLD, 15));
        heroPlay.setPreferredSize(new Dimension(150, 46));
        heroPlay.addActionListener(e -> play());
        JPanel wrap = new JPanel(new java.awt.BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(8, 0, 8, 0));
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
        tile.setRunning(isInstanceRunning(inst));
        return tile;
    }

    private JComponent serverTile(ServerInstance srv) {
        Ui.Card card = new Ui.Card(theme.card, theme.border);
        card.setRadius(12);
        card.setGradient(theme.cardTop(), theme.cardBottom());
        card.setLayout(new BorderLayout(14, 0));
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
            card.setGradient(theme.cardSel, theme.card);
            card.setStroke(theme.accent);
            card.setShadow(Ui.shadow(theme));
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
                    card.setGradient(theme.hover, theme.card);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (currentServer == null || !currentServer.folder.equals(srv.folder)) {
                    card.setGradient(theme.cardTop(), theme.cardBottom());
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

        heroIcon.setIcon(InstanceTile.iconFor(inst, theme, 56));
        heroTitle.setText(inst.name);
        heroSub.setText(inst.loader + " " + inst.mcVersion);
        updateHeroChips(inst);
        updateHeroVisibility();
        playButton.setEnabled(!isInstanceRunning(inst));
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
        contentCards.show(contentHolder, "main");
        updateTabStyle(tabFriendsButton, false);
        serverTab = toServers;
        updateTabStyle(tabInstButton, !toServers);
        updateTabStyle(tabSrvButton, toServers);
        sectionLabel.setText(toServers ? "СЕРВЕРЫ" : "ИНСТАНСЫ");

        newInstButton.setVisible(!toServers);
        delInstButton.setVisible(!toServers);
        settingsButton.setVisible(!toServers);
        importButton.setVisible(!toServers);
        modpackButton.setVisible(!toServers);
        newSrvButton.setVisible(toServers);
        delSrvButton.setVisible(toServers);

        playButton.setVisible(!toServers);
        killButton.setVisible(!toServers && isInstanceRunning(current));
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

    /** Открыть вкладку «Друзья» (сеть поднимается при первом открытии). */
    private void showFriends() {
        if (friendsPanel == null) {
            friendsPanel = new FriendsPanel(theme, new FriendsPanel.Context() {
                @Override
                public String instanceName() {
                    return current == null ? "" : current.name;
                }

                @Override
                public String mcVersion() {
                    return current == null ? "" : current.mcVersion;
                }

                @Override
                public String loader() {
                    return current == null ? "" : current.loader;
                }

                @Override
                public int mods() {
                    return current == null ? 0
                            : com.frimer.launcher.core.ModFiles
                                    .list(current.folder.resolve("mods")).size();
                }

                @Override
                public String serverName() {
                    return currentServer == null ? "" : currentServer.name;
                }

                @Override
                public String serverAddress() {
                    if (currentServer == null || currentServer.playitAddress == null) {
                        return "";
                    }
                    return currentServer.playitAddress;
                }
            });
            contentHolder.add(friendsPanel, "friends");
        }
        updateTabStyle(tabInstButton, false);
        updateTabStyle(tabSrvButton, false);
        updateTabStyle(tabFriendsButton, true);
        sectionLabel.setText("ДРУЗЬЯ");
        for (JButton button : new JButton[]{newInstButton, delInstButton, settingsButton,
                importButton, modpackButton, newSrvButton, delSrvButton, playButton, killButton,
                startSrvButton, stopSrvButton, consoleButton, copyAddrButton}) {
            if (button != null) {
                button.setVisible(false);
            }
        }
        contentCards.show(contentHolder, "friends");
        revalidate();
        repaint();
    }

    // =================================================================== ник

    private void refreshNickButton() {
        com.frimer.launcher.core.Accounts.Account active =
                com.frimer.launcher.core.Accounts.active();
        nickButton.setText(active.name + (active.microsoft() ? "  · MS" : ""));
        nickButton.setIcon(Ui.chevron(theme, false));
        nickButton.setHorizontalTextPosition(SwingConstants.LEFT);
        nickButton.setIconTextGap(10);
    }

    private void showNickMenu() {
        java.util.List<Ui.MenuItemSpec> items = new java.util.ArrayList<>();
        for (String nick : AppSettings.nicknameHistory()) {
            items.add(Ui.MenuItemSpec.of(nick, () -> {
                AppSettings.setNickname(nick);
                AppSettings.flush();
                refreshNickButton();
            }));
        }
        if (!items.isEmpty()) {
            items.add(Ui.MenuItemSpec.separator());
        }
        items.add(Ui.MenuItemSpec.of("+ Добавить аккаунт", this::promptNewNick));
        Ui.showMenu(nickButton, 0, nickButton.getHeight(), theme, items);
    }

    /** Спросить и запомнить новый ник. */
    private void promptNewNick() {
        String nick = JOptionPane.showInputDialog(this, "Ник:", "Новый аккаунт",
                JOptionPane.PLAIN_MESSAGE);
        if (nick == null) {
            return;
        }
        String clean = nick.strip();
        if (!clean.matches("^[A-Za-z0-9_]{1,16}$")) {
            JOptionPane.showMessageDialog(this, "Только буквы, цифры и _ (до 16 символов).",
                    "Неверный ник", JOptionPane.WARNING_MESSAGE);
            return;
        }
        AppSettings.setNickname(clean);
        AppSettings.flush();
        refreshNickButton();
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
        java.util.List<Ui.MenuItemSpec> items = new java.util.ArrayList<>();
        items.add(Ui.MenuItemSpec.of("Играть", this::play));
        items.add(Ui.MenuItemSpec.separator());
        items.add(Ui.MenuItemSpec.of("Моды (" + Utils.countMods(inst.folder) + ")",
                () -> openModsDialog(inst)));
        items.add(Ui.MenuItemSpec.of("Найти моды…", () -> openModrinth(inst)));
        items.add(Ui.MenuItemSpec.of("Миры", () -> openWorlds(inst)));
        items.add(Ui.MenuItemSpec.of("Скрины", () -> openScreenshots(inst)));
        items.add(Ui.MenuItemSpec.of("Проверить файлы", () -> openIntegrity(inst)));
        items.add(Ui.MenuItemSpec.of("Изменить", () -> editInstance(inst)));
        items.add(Ui.MenuItemSpec.of("Дублировать", () -> duplicateInstance(inst)));
        items.add(Ui.MenuItemSpec.of("Открыть папку", () -> Ui.openFolder(inst.folder)));
        items.add(Ui.MenuItemSpec.separator());
        items.add(Ui.MenuItemSpec.of("Удалить", () -> {
            selectInstance(inst);
            deleteCurrentInstance();
        }));
        Ui.showMenu(anchor, point.x, point.y, theme, items);
    }

    /** Браузер модов Modrinth для этой сборки. */
    private void openModrinth(Instance inst) {
        Path target = inst.folder.resolve("mods");
        try {
            java.nio.file.Files.createDirectories(target);
        } catch (java.io.IOException ignored) {
            // папку создаст установка
        }
        ModrinthDialog dialog = new ModrinthDialog(this, theme, inst,
                com.frimer.launcher.core.Modrinth.MOD, target);
        dialog.showDialog();
        refreshInstances();
        selectInstance(inst);
    }

    /** Миры сборки: размеры, датапаки, удаление. */
    private void openWorlds(Instance inst) {
        WorldsDialog dialog = new WorldsDialog(this, theme, inst);
        dialog.showDialog();
    }

    /** Проверка целостности файлов сборки с починкой. */
    private void openIntegrity(Instance inst) {
        IntegrityDialog dialog = new IntegrityDialog(this, theme, inst);
        dialog.showDialog();
    }

    /** Галерея скриншотов сборки. */
    private void openScreenshots(Instance inst) {
        ScreenshotsDialog dialog = new ScreenshotsDialog(this, theme, inst);
        dialog.showDialog();
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

    /** Запустить выбранную сборку (можно несколько одновременно). */
    private void play() {
        Instance inst = current;
        if (inst == null) {
            return;
        }
        GameLauncher existing = running.get(inst.folder);
        if (existing != null && existing.isRunning()) {
            setStatus("Сборка «" + inst.name + "» уже запущена");
            return;
        }
        GameLauncher launcher = new GameLauncher(inst, com.frimer.launcher.core.Accounts.active(),
                new GameLauncher.Listener() {
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
                appendLog("[" + inst.name + "] " + line);
            }

            @Override
            public void onGameStarted(long timestamp) {
                DiscordRpc.playing(inst.name + " · " + inst.loader + " " + inst.mcVersion, "Играет");
                SwingUtilities.invokeLater(() -> {
                    setStatus("Игра запущена: " + inst.name);
                    setFraction(0, 0);
                });
            }

            @Override
            public void onFinished(int code) {
                SwingUtilities.invokeLater(() -> {
                    running.remove(inst.folder);
                    refreshRunningUi();
                    DiscordRpc.idle();
                    setStatus("Игра закрыта: " + inst.name + " (код " + code + ")");
                    if (current != null && current.folder.equals(inst.folder)) {
                        selectInstance(inst);
                    }
                });
            }

            @Override
            public void onFailed(String error) {
                SwingUtilities.invokeLater(() -> {
                    running.remove(inst.folder);
                    refreshRunningUi();
                    setFraction(0, 0);
                    DiscordRpc.idle();
                    setStatus("Ошибка запуска: " + inst.name);
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
        running.put(inst.folder, launcher);
        refreshRunningUi();
        setStatus("Готовлю запуск «" + inst.name + "»...");
        appendLog("=== Запуск " + inst.name + " (" + inst.loader + " " + inst.mcVersion + ") ===");
        launcher.start();
    }

    /** Запущена ли эта сборка. */
    private boolean isInstanceRunning(Instance inst) {
        if (inst == null) {
            return false;
        }
        GameLauncher launcher = running.get(inst.folder);
        return launcher != null && launcher.isRunning();
    }

    /** Обновить кнопки, плитки и счётчик после запуска/остановки сборок. */
    private void refreshRunningUi() {
        boolean runningHere = isInstanceRunning(current);
        if (playButton != null) {
            playButton.setEnabled(!runningHere);
        }
        if (killButton != null) {
            killButton.setVisible(!serverTab && runningHere);
        }
        if (progressLabel != null) {
            progressLabel.setText(running.isEmpty() ? "" : "запущено: " + running.size());
        }
        rebuildTiles();
    }

    private void killGame() {
        Instance inst = current;
        if (inst == null) {
            return;
        }
        GameLauncher launcher = running.get(inst.folder);
        if (launcher != null && launcher.killGame()) {
            setStatus("Minecraft закрывается (" + inst.name + ")...");
        }
    }

    private String currentAccountName() {
        return com.frimer.launcher.core.Accounts.active().name;
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
                            trackServerMetrics(srv, line);
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
                            boolean killed = serverRunner != null && serverRunner.killRequested();
                            SwingUtilities.invokeLater(() -> {
                                stopSrvButton.setEnabled(false);
                                startSrvButton.setEnabled(true);
                                setStatus("Сервер остановлен (код " + code + ")");
                                if (serverConsole != null) {
                                    serverConsole.setRunning(false);
                                }
                                if (srv.autoRestart && code != 0 && !killed) {
                                    int attempts = restartAttempts.merge(srv.folder, 1, Integer::sum);
                                    if (attempts <= 3) {
                                        int delay = Math.max(5, srv.restartDelay);
                                        appendLog("[auto] падение (код " + code + "), перезапуск через "
                                                + delay + " с, попытка " + attempts);
                                        javax.swing.Timer timer = new javax.swing.Timer(delay * 1000,
                                                ev -> {
                                                    ((javax.swing.Timer) ev.getSource()).stop();
                                                    launchServerProcess();
                                                });
                                        timer.setRepeats(false);
                                        timer.start();
                                    } else {
                                        appendLog("[auto] перезапуски остановлены: частые падения");
                                    }
                                } else if (code == 0) {
                                    restartAttempts.remove(srv.folder);
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

    /** Достать TPS и число игроков из строк лога сервера. */
    private void trackServerMetrics(ServerInstance srv, String line) {
        if (line == null) {
            return;
        }
        java.util.regex.Matcher matcher = TPS_LINE.matcher(line);
        if (matcher.find()) {
            serverTps.put(srv.folder, matcher.group(1));
        }
        if (line.contains("joined the game")) {
            serverOnline.merge(srv.folder, 1, Integer::sum);
        } else if (line.contains("left the game")) {
            serverOnline.merge(srv.folder, -1, Integer::sum);
        }
        String tps = serverTps.get(srv.folder);
        Integer online = serverOnline.get(srv.folder);
        if (tps == null && online == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (serverConsole != null) {
                serverConsole.setMetrics(tps, online == null ? -1 : Math.max(0, online));
            }
        });
    }

    private void detectPlayitAddress(String line) {
        java.util.regex.Matcher claim = java.util.regex.Pattern
                .compile("https://(?:www\\.)?playit\\.gg/[^\\s\"']+")
                .matcher(line == null ? "" : line);
        if (claim.find() && !playitClaimOpened) {
            playitClaimOpened = true;
            String url = claim.group();
            SwingUtilities.invokeLater(() -> {
                appendLog("[playit] подтвердите туннель: " + url);
                com.frimer.launcher.core.Modrinth.openPage(url);
                JOptionPane.showMessageDialog(this,
                        "Открыл браузер — подтвердите туннель и вернитесь в лаунчер.\n\n" + url,
                        "playit.gg", JOptionPane.INFORMATION_MESSAGE);
            });
        }
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
        String address;
        if (!playitAddress.isEmpty()) {
            address = playitAddress;
        } else {
            address = "localhost:" + (currentServer == null ? 25565 : currentServer.port);
        }
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

    /** Окно аккаунтов: офлайн-ники и вход Microsoft. */
    private void openAccounts() {
        AccountsDialog dialog = new AccountsDialog(this, theme, () -> {
            refreshNickButton();
            setStatus("Аккаунт: " + com.frimer.launcher.core.Accounts.active().name);
        });
        dialog.showDialog();
        refreshNickButton();
    }

    private void openSkins() {
        SkinDialog dialog = new SkinDialog(this, theme, new ArrayList<>(instances));
        dialog.showDialog();
    }

    /** Поиск готовых сборок на Modrinth. */
    private void openModpacks() {
        ModpackBrowserDialog dialog = new ModpackBrowserDialog(this, theme, instance -> {
            refreshInstances();
            selectInstance(instance);
            setStatus("Сборка «" + instance.name + "» установлена");
        });
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
            playButton.setEnabled(!isInstanceRunning(current));
        }
    }

    /** Есть ли непрочитанные изменения (версия новее просмотренной). */
    private boolean hasUnseenChanges() {
        return !AppSettings.APP_VERSION.equals(AppSettings.lastSeenVersion());
    }

    /** Открыть журнал изменений и отметить текущую версию просмотренной. */
    private void openChangelog() {
        ChangelogDialog dialog = new ChangelogDialog(this, theme);
        dialog.showDialog();
        AppSettings.setLastSeenVersion(AppSettings.APP_VERSION);
        AppSettings.flush();
        if (changelogButton != null) {
            changelogButton.setBadge(false);
        }
    }

    /** Показать «что нового», если версия ещё не просмотрена, и проверить обновления. */
    public void afterShow() {
        DiscordRpc.configure(AppSettings.discordAppId());
        DiscordRpc.idle();
        String migrationWarning = Dirs.migrationWarning();
        if (migrationWarning != null) {
            appendLog("[данные] " + migrationWarning);
            setStatus(Utils.shortText(migrationWarning, 110));
        }
        if (hasUnseenChanges()) {
            openChangelog();
        }
        startUpdateCheck();
    }

    /** Фоновая проверка обновлений: окно показываем, только если версия новее. */
    private void startUpdateCheck() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            com.frimer.launcher.core.Updater.Info info = com.frimer.launcher.core.Updater.latest();
            if (info != null) {
                SwingUtilities.invokeLater(() -> {
                    UpdateDialog dialog = new UpdateDialog(this, theme, info);
                    dialog.showDialog();
                });
            }
        }, "frimer-updater");
        thread.setDaemon(true);
        thread.start();
    }
    private void onClose() {
        if (!running.isEmpty()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Minecraft ещё запущен. Закрыть его и выйти?",
                    "Игра работает", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.CANCEL_OPTION) {
                return;
            }
            if (answer == JOptionPane.YES_OPTION) {
                for (GameLauncher launcher : new java.util.ArrayList<>(running.values())) {
                    launcher.killGame();
                }
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
        if (friendsPanel != null) {
            friendsPanel.shutdown();
        }
        dispose();
        System.exit(0);
    }

    // =================================================================== раскладка

}
