package com.fatelocked;

import com.fatelocked.panel.ChunkPanelViewModel;
import com.fatelocked.rules.PermissionStatus;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Narrow category-first side panel with compact rule rows. */
class FateLockedPanel extends PluginPanel
{
    static final String TRACKER_URL = "https://nubles.github.io/OSRS-Fate-Locked/";
    private static final Color GREEN = new Color(16, 185, 129);
    private static final Color AMBER = new Color(245, 158, 11);
    private static final Color RED = new Color(239, 68, 68);
    private static final Color GRAY = new Color(156, 163, 175);
    private static final Color SURFACE = new Color(35, 39, 46);
    private static final Color SEPARATOR = new Color(58, 63, 72);

    private final JLabel profileVal = value();
    private final JLabel accountVal = value();
    private final JLabel runIdVal = value();
    private final JLabel keysVal = value();
    private final JLabel fateVal = value();
    private final JLabel buffVal = value();
    private final JLabel goalVal = value();
    private final JLabel localEventsVal = value();
    private final JLabel reviewVal = value();
    private final JLabel warningsVal = value();
    private final JLabel historyStatusVal = value();
    private final JLabel connectionVal = value();
    private final JLabel trackerAccountVal = value();
    private final JLabel lastSyncVal = value();
    private final JLabel importVal = value();
    private final JPanel chunkBody = column();
    private final JPanel bundleBody = column();
    private final JTextArea pasteArea = new JTextArea(6, 10);
    private final JLabel strictModeVal = value();
    private final JButton strictModeButton = new JButton();
    private final JButton connectTrackerButton = new JButton("Connect tracker");
    private final JPanel strictIntro = card();
    private final JPanel recentPreventedBody = column();
    private final Map<String, CollapsiblePanelSection> sections =
        new LinkedHashMap<>();
    private final Map<String, Set<String>> sectionSettingKeys =
        new LinkedHashMap<>();
    private final Map<String, JComponent> settingControls =
        new LinkedHashMap<>();
    private final FateLockedConfigBinder configBinder;
    private final CollapsiblePanelSection bundleSection;
    private Runnable onStrictPause = () -> {};
    private Runnable onStrictResume = () -> {};
    private Runnable onStrictIntroDismiss = () -> {};
    private boolean strictPaused;

    private String rollInboxUrl = TRACKER_URL + "?open=roll-inbox";
    private Consumer<String> onImport = json -> {};
    private Runnable onReload = () -> {};
    private Runnable onConnect = () -> {};

    FateLockedPanel()
    {
        this(new FateLockedConfig() { }, null);
    }

    @Inject
    FateLockedPanel(FateLockedConfig config, ConfigManager configManager)
    {
        configBinder = new FateLockedConfigBinder(
            configManager, message -> flashStatus(message, false));

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel col = column();
        col.add(title("FATE LOCKED IRONMAN"));
        col.add(Box.createVerticalStrut(6));

        JButton trackerBtn = new JButton("Open web tracker");
        fullWidth(trackerBtn);
        trackerBtn.addActionListener(e -> LinkBrowser.browse(TRACKER_URL));
        col.add(trackerBtn);
        col.add(Box.createVerticalStrut(6));

        fullWidth(connectTrackerButton);
        connectTrackerButton.addActionListener(event -> onConnect.run());
        col.add(connectTrackerButton);
        col.add(Box.createVerticalStrut(5));

        connectionVal.setText("Not connected");
        connectionVal.setForeground(GRAY);
        col.add(stats(
            new String[]{"Connection", "Tracker account", "Last sync"},
            new JLabel[]{connectionVal, trackerAccountVal, lastSyncVal}));
        col.add(Box.createVerticalStrut(4));

        importVal.setVisible(false);
        importVal.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(importVal);
        col.add(Box.createVerticalStrut(4));

        JLabel disclosure = new JLabel(
            "<html>RuneLite retrieves rules from the Fate Locked relay. "
                + "Your IP address is visible to the relay, but RuneLite "
                + "does not upload gameplay data.</html>");
        disclosure.setForeground(GRAY);
        disclosure.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(disclosure);
        col.add(Box.createVerticalStrut(10));

        chunkBody.add(emptyChunk());
        addSection(col, "Current chunk", true, chunkBody);
        addSection(col, "Guardian", true, buildGuardianBody(config));
        addSection(col, "Roll inbox", false, buildRollInboxBody());
        addSection(col, "Run", false, buildRunBody());
        buildBundleBody(config);
        bundleSection = addSection(col, "Bundle", false, bundleBody);
        addSection(col, "Warnings", false, buildWarningsBody(config));
        addSection(col, "Rendering", false, buildRenderingBody(config));

        add(col, BorderLayout.NORTH);
    }

    private CollapsiblePanelSection addSection(
        JPanel parent, String name, boolean expanded, JPanel content)
    {
        CollapsiblePanelSection section =
            new CollapsiblePanelSection(name, expanded);
        section.body().add(content);
        sections.put(name, section);
        sectionSettingKeys.computeIfAbsent(
            name, ignored -> new LinkedHashSet<>());
        parent.add(section);
        parent.add(Box.createVerticalStrut(9));
        return section;
    }

    private JPanel buildGuardianBody(FateLockedConfig config)
    {
        JPanel body = column();
        buildStrictIntro();
        addSetting(body, ownSetting("Guardian", "strictMode",
            configBinder.booleanSetting(
                "strictMode", "Strict Mode", config::strictMode)));
        body.add(strictIntro);
        body.add(stats(new String[]{"Guardian status"},
            new JLabel[]{strictModeVal}));
        body.add(Box.createVerticalStrut(5));
        fullWidth(strictModeButton);
        strictModeButton.addActionListener(event -> {
            if (strictPaused)
            {
                onStrictResume.run();
            }
            else
            {
                onStrictPause.run();
            }
        });
        body.add(strictModeButton);
        body.add(Box.createVerticalStrut(7));
        body.add(collapsibleHeader(
            "RECENT PREVENTED ACTIONS", recentPreventedBody, false));
        body.add(recentPreventedBody);
        return body;
    }

    private JPanel buildRollInboxBody()
    {
        JPanel body = column();
        body.add(stats(
            new String[]{"Local events", "Needs review", "Warnings"},
            new JLabel[]{localEventsVal, reviewVal, warningsVal}));
        body.add(Box.createVerticalStrut(5));
        JLabel disclosure = new JLabel(
            "<html>Local only — RuneLite does not upload gameplay data.</html>");
        disclosure.setForeground(GRAY);
        disclosure.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(disclosure);
        historyStatusVal.setText("");
        historyStatusVal.setForeground(RED);
        historyStatusVal.setVisible(false);
        historyStatusVal.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(historyStatusVal);
        body.add(Box.createVerticalStrut(6));
        JButton inboxBtn = new JButton("Open web Roll Inbox");
        fullWidth(inboxBtn);
        inboxBtn.setToolTipText(
            "Open the separate web Roll Inbox; local history is not transferred");
        inboxBtn.addActionListener(event -> LinkBrowser.browse(rollInboxUrl));
        body.add(inboxBtn);
        return body;
    }

    private JPanel buildRunBody()
    {
        JPanel body = column();
        body.add(stats(
            new String[]{"Profile", "Account", "Run ID", "Keys", "Fate", "Buff", "Goal"},
            new JLabel[]{profileVal, accountVal, runIdVal, keysVal, fateVal, buffVal, goalVal}));
        return body;
    }

    private void buildBundleBody(FateLockedConfig config)
    {
        addSetting(bundleBody, ownSetting("Bundle", "autoReload",
            configBinder.booleanSetting(
                "autoReload", "Auto-reload on change", config::autoReload)));
        addLabeledSetting(bundleBody, "Re-import hotkey",
            ownSetting("Bundle", "reimportHotkey",
                configBinder.keybindSetting(
                    "reimportHotkey", "Re-import hotkey", config::reimportHotkey)));
        buildImportControls();
    }

    private JPanel buildWarningsBody(FateLockedConfig config)
    {
        JPanel body = column();
        addBoolean("Warnings", body, "chatOnEnter", "Chat on chunk entry", config::chatOnEnter);
        addBoolean("Warnings", body, "warnOnLocked", "Warn entering locked chunk", config::warnOnLocked);
        addBoolean("Warnings", body, "warnLockedBank", "Warn opening a locked bank", config::warnLockedBank);
        addBoolean("Warnings", body, "flashOnLocked", "Screen flash on locked entry", config::flashOnLocked);
        addBoolean("Warnings", body, "warnAccountMismatch", "Warn on wrong account", config::warnAccountMismatch);
        addBoolean("Warnings", body, "tagLockedMenus", "Tag locked right-click targets", config::tagLockedMenus);
        addBoolean("Warnings", body, "tagLockedTeleports", "Tag teleports to locked chunks", config::tagLockedTeleports);
        addBoolean("Warnings", body, "showHud", "Show in-game HUD", config::showHud);
        addBoolean("Warnings", body, "showNearest", "HUD: nearest bank & shop", config::showNearest);
        addBoolean("Warnings", body, "showChunkContentBox", "Show \"in this chunk\" box", config::showChunkContentBox);
        addBoolean("Warnings", body, "useNotifier", "Send RuneLite notifications", config::useNotifier);
        addBoolean("Warnings", body, "warnLockedSlayer", "Warn on locked slayer task", config::warnLockedSlayer);
        addBoolean("Warnings", body, "warnOverTierGear", "Warn on over-tier gear", config::warnOverTierGear);
        addBoolean("Warnings", body, "showInfoBoxes", "Show key/fate/progress infoboxes", config::showInfoBoxes);
        addBoolean("Warnings", body, "rollNudges", "Roll reminders", config::rollNudges);
        return body;
    }

    private JPanel buildRenderingBody(FateLockedConfig config)
    {
        JPanel body = column();
        addBoolean("Rendering", body, "drawWorldMap", "Draw on world map", config::drawWorldMap);
        addBoolean("Rendering", body, "drawScene", "Draw around player", config::drawScene);
        addBoolean("Rendering", body, "drawMinimap", "Draw on minimap", config::drawMinimap);
        addBoolean("Rendering", body, "highlightLockedBorders", "Highlight locked borders", config::highlightLockedBorders);
        addBoolean("Rendering", body, "shadeNearbyLocked", "Shade nearby locked chunks", config::shadeNearbyLocked);
        addBoolean("Rendering", body, "worldMapMarkers", "Pin locked areas on world map", config::worldMapMarkers);
        addBoolean("Rendering", body, "worldMapTooltip", "World map hover tooltip", config::worldMapTooltip);
        addBoolean("Rendering", body, "worldMapTooltipContent", "Tooltip: what's in the chunk", config::worldMapTooltipContent);
        addSetting(body, ownSetting("Rendering", "unlockedColor",
            configBinder.colorSetting(
                "unlockedColor", "Unlocked color", config::unlockedColor)));
        addSetting(body, ownSetting("Rendering", "frontierColor",
            configBinder.colorSetting(
                "frontierColor", "Frontier color (Chunked)", config::frontierColor)));
        addSetting(body, ownSetting("Rendering", "lockedColor",
            configBinder.colorSetting(
                "lockedColor", "Locked color", config::lockedColor)));
        addSetting(body, ownSetting("Rendering", "unauthoredColor",
            configBinder.colorSetting(
                "unauthoredColor", "Unauthored color", config::unauthoredColor)));
        return body;
    }

    private void addBoolean(String sectionName, JPanel body,
        String key, String label,
        java.util.function.BooleanSupplier current)
    {
        addSetting(body, ownSetting(sectionName, key,
            configBinder.booleanSetting(key, label, current)));
    }

    private JComponent ownSetting(
        String sectionName, String key, JComponent control)
    {
        if (settingControls.containsKey(key))
        {
            throw new IllegalStateException("Duplicate setting control: " + key);
        }
        settingControls.put(key, control);
        sectionSettingKeys.computeIfAbsent(
            sectionName, ignored -> new LinkedHashSet<>()).add(key);
        return control;
    }

    private static void addSetting(JPanel body, JComponent control)
    {
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(
            Integer.MAX_VALUE, control.getPreferredSize().height));
        body.add(control);
        body.add(Box.createVerticalStrut(3));
    }

    private static void addLabeledSetting(
        JPanel body, String label, JComponent control)
    {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel copy = new JLabel(label);
        copy.setForeground(Color.LIGHT_GRAY);
        row.add(copy, BorderLayout.CENTER);
        row.add(control, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(
            Integer.MAX_VALUE, row.getPreferredSize().height));
        body.add(row);
        body.add(Box.createVerticalStrut(3));
    }
    private void buildStrictIntro()
    {
        JLabel copy = new JLabel("<html>Strict Mode prevents only actions proven locked by fresh rules. Known locked travel clicks can be stopped; uncertain movement is never blocked. Pause it for 60 seconds here or turn it off above.</html>");
        copy.setForeground(Color.LIGHT_GRAY);
        strictIntro.add(copy);
        JButton dismiss = new JButton("Got it");
        fullWidth(dismiss);
        dismiss.addActionListener(event -> {
            strictIntro.setVisible(false);
            onStrictIntroDismiss.run();
        });
        strictIntro.add(Box.createVerticalStrut(5));
        strictIntro.add(dismiss);
        strictIntro.setVisible(false);
    }

    void setGuardianCallbacks(Runnable pause, Runnable resume, Runnable dismissIntro)
    {
        onStrictPause = pause;
        onStrictResume = resume;
        onStrictIntroDismiss = dismissIntro;
    }

    void showStrictModeIntro()
    {
        SwingUtilities.invokeLater(() -> strictIntro.setVisible(true));
    }

    void updateRecentPrevented(List<String> lines)
    {
        SwingUtilities.invokeLater(() -> {
            recentPreventedBody.removeAll();
            if (lines == null || lines.isEmpty())
            {
                JLabel empty = new JLabel("None yet");
                empty.setForeground(GRAY);
                recentPreventedBody.add(empty);
            }
            else
            {
                for (String line : lines)
                {
                    JLabel item = new JLabel(line);
                    item.setForeground(Color.LIGHT_GRAY);
                    item.setToolTipText(line);
                    recentPreventedBody.add(item);
                    recentPreventedBody.add(Box.createVerticalStrut(3));
                }
            }
            recentPreventedBody.revalidate();
        });
    }
    void updateStrictMode(boolean enabled, boolean paused, long seconds)
    {
        SwingUtilities.invokeLater(() -> {
            strictPaused = paused;
            strictModeVal.setText(enabled ? paused ? "Paused" : "On" : "Off");
            strictModeVal.setForeground(enabled ? paused ? AMBER : GREEN : GRAY);
            strictModeButton.setVisible(enabled);
            strictModeButton.setText(paused
                ? "Resume Strict Mode · " + seconds + "s"
                : "Pause Strict Mode for 60 seconds");
        });
    }
    private void buildImportControls()
    {
        bundleBody.add(Box.createVerticalStrut(4));
        JButton clipboardBtn = new JButton("Import from clipboard");
        fullWidth(clipboardBtn);
        clipboardBtn.setToolTipText("Click RuneLite in the tracker, then click here");
        clipboardBtn.addActionListener(e -> importFromClipboard());
        bundleBody.add(clipboardBtn);
        bundleBody.add(Box.createVerticalStrut(6));
        bundleBody.add(section("…OR PASTE JSON"));

        pasteArea.setLineWrap(true);
        pasteArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pasteArea.setForeground(Color.LIGHT_GRAY);
        pasteArea.setCaretColor(Color.LIGHT_GRAY);
        pasteArea.setBorder(new EmptyBorder(4, 4, 4, 4));
        JScrollPane scroll = new JScrollPane(pasteArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        bundleBody.add(scroll);
        bundleBody.add(Box.createVerticalStrut(6));

        JButton importBtn = new JButton("Import pasted JSON");
        fullWidth(importBtn);
        importBtn.addActionListener(e -> {
            String text = pasteArea.getText().trim();
            if (!text.isEmpty()) onImport.accept(text);
        });
        bundleBody.add(importBtn);
        bundleBody.add(Box.createVerticalStrut(4));

        JButton reloadBtn = new JButton("Reload from file");
        fullWidth(reloadBtn);
        reloadBtn.addActionListener(e -> onReload.run());
        bundleBody.add(reloadBtn);
    }

    void setCallbacks(
        Consumer<String> onImport, Runnable onReload, Runnable onConnect)
    {
        this.onImport = onImport;
        this.onReload = onReload;
        this.onConnect = onConnect;
    }

    void setCallbacks(Consumer<String> onImport, Runnable onReload)
    {
        setCallbacks(onImport, onReload, onConnect);
    }
    void setRollInboxLink(String trackerUrl)
    {
        rollInboxUrl = rollInboxUrl(trackerUrl);
    }

    static String rollInboxUrl(String trackerUrl)
    {
        String base = trackerUrl == null || trackerUrl.trim().isEmpty()
            ? TRACKER_URL : trackerUrl.trim();
        return base + "?open=roll-inbox";
    }

    void updateConnection(TrackerConnectionSnapshot snapshot)
    {
        runOnEdt(() -> applyConnection(snapshot));
    }

    void updateTrackerAccount(String account)
    {
        runOnEdt(() -> trackerAccountVal.setText(orDash(account)));
    }

    void updateRollInboxStatus(
        int localEvents, int needsReview, int warnings,
        boolean saveFailed)
    {
        runOnEdt(() -> {
            localEventsVal.setText(String.valueOf(Math.max(0, localEvents)));
            reviewVal.setText(String.valueOf(Math.max(0, needsReview)));
            warningsVal.setText(warnings <= 0 ? "None" : warnings + " active");
            warningsVal.setForeground(warnings <= 0 ? GREEN : RED);
            historyStatusVal.setText(
                saveFailed ? "Local history save failed" : "");
            historyStatusVal.setVisible(saveFailed);
        });
    }

    void refreshConfig(String key)
    {
        runOnEdt(() -> configBinder.refresh(key));
    }
    private void applyConnection(TrackerConnectionSnapshot snapshot)
    {
        TrackerConnectionSnapshot copy = snapshot == null
            ? TrackerConnectionSnapshot.disconnected() : snapshot;
        TrackerConnectionState state = copy.getState();
        String message = copy.getMessage();
        String text = message == null || message.trim().isEmpty()
            ? state.name() : message;
        Color color = GRAY;
        if (state == TrackerConnectionState.CONNECTED)
        {
            color = GREEN;
            if (copy.getLastSync() != null)
            {
                text += " \u00b7 " + formatUtc(copy.getLastSync());
            }
        }
        else if (state == TrackerConnectionState.PREPARING
            || state == TrackerConnectionState.WAITING
            || state == TrackerConnectionState.IMPORTING)
        {
            color = AMBER;
        }
        else if (state == TrackerConnectionState.EXPIRED
            || state == TrackerConnectionState.IMPORT_FAILED)
        {
            color = RED;
        }
        connectionVal.setText(text);
        connectionVal.setForeground(color);
        lastSyncVal.setText(copy.getLastSync() == null
            ? "\u2014" : formatUtc(copy.getLastSync()));
        lastSyncVal.setForeground(
            copy.getLastSync() == null ? GRAY : GREEN);
    }

    private static String formatUtc(Instant instant)
    {
        return DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC).format(instant);
    }

    private static void runOnEdt(Runnable update)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            update.run();
        }
        else
        {
            SwingUtilities.invokeLater(update);
        }
    }

    String localEventsTextForTest() { return localEventsVal.getText(); }
    String reviewTextForTest() { return reviewVal.getText(); }
    String warningTextForTest() { return warningsVal.getText(); }
    String historyStatusTextForTest() { return historyStatusVal.getText(); }
    boolean historyStatusVisibleForTest()
    { return historyStatusVal.isVisible(); }
    String lastSyncTextForTest() { return lastSyncVal.getText(); }
    String connectionTextForTest() { return connectionVal.getText(); }
    String trackerAccountTextForTest() { return trackerAccountVal.getText(); }
    JButton connectButtonForTest() { return connectTrackerButton; }
    JButton guardianPauseButtonForTest() { return strictModeButton; }
    List<String> sectionTitlesForTest()
    {
        return Collections.unmodifiableList(
            new java.util.ArrayList<>(sections.keySet()));
    }
    CollapsiblePanelSection sectionForTest(String title)
    {
        return sections.get(title);
    }
    Set<String> settingKeysForTest()
    {
        return configBinder.keys();
    }
    Set<String> sectionSettingKeysForTest(String title)
    {
        Set<String> keys = sectionSettingKeys.get(title);
        return keys == null
            ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(keys));
    }
    JComponent settingControlForTest(String key)
    {
        return settingControls.get(key);
    }
    String rollInboxUrlForTest()
    {
        return rollInboxUrl;
    }
    boolean hasTextForTest(String text)
    {
        return hasText(this, text);
    }
    private void importFromClipboard()
    {
        try
        {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
                .getData(DataFlavor.stringFlavor);
            String text = data == null ? "" : data.toString().trim();
            if (text.isEmpty())
            {
                flashStatus("clipboard empty", false);
                return;
            }
            pasteArea.setText(text);
            onImport.accept(text);
        }
        catch (Exception ex)
        {
            flashStatus("couldn't read clipboard", false);
        }
    }

    void update(FateLockedBundle bundle, ChunkPanelViewModel view)
    {
        FateLockedBundle.RunState state = bundle.getState();
        List<String> goals = state == null || state.getPinnedGoals() == null
            ? Collections.emptyList() : state.getPinnedGoals();
        SwingUtilities.invokeLater(() -> {
            profileVal.setText(orDash(bundle.getProfileName()));
            runIdVal.setText(orDash(bundle.getRunId()));
            String manifestAccount = bundle.getRules() == null
                ? null : bundle.getRules().getAccount();
            accountVal.setText(orDash(manifestAccount));
            if (state != null)
            {
                if (manifestAccount == null) accountVal.setText(orDash(state.getLinkedAccount()));
                keysVal.setText(state.getKeys() + " · O " + state.getSpecialKeys()
                    + " · C " + state.getChaosKeys());
                keysVal.setForeground(AMBER);
                fateVal.setText(String.valueOf(state.getFatePoints()));
                buffVal.setText(orDash(state.getActiveBuff()));
                goalVal.setText(goals.isEmpty() ? "—" : goals.get(0));
            }
            else
            {
                keysVal.setText("—");
                fateVal.setText("—");
                buffVal.setText("—");
                goalVal.setText("—");
            }
            renderChunk(view);
        });
    }

    void renderChunkForTest(ChunkPanelViewModel view)
    {
        renderChunk(view);
    }

    private void renderChunk(ChunkPanelViewModel view)
    {
        chunkBody.removeAll();
        if (view == null)
        {
            chunkBody.add(emptyChunk());
            chunkBody.revalidate();
            return;
        }

        JPanel header = card();
        JLabel area = new JLabel(view.getName());
        area.setForeground(Color.WHITE);
        area.setFont(area.getFont().deriveFont(Font.BOLD, 13f));
        header.add(area);
        JLabel meta = new JLabel(
            (view.getRegion() == null ? "Unknown region" : view.getRegion())
                + "  ·  " + view.getCoordinates());
        meta.setForeground(GRAY);
        meta.setFont(meta.getFont().deriveFont(10f));
        header.add(meta);
        JLabel entry = new JLabel(
            statusText(view.getEntryStatus()) + "  ·  " + view.getFreshnessLabel());
        entry.setForeground(statusColor(view.getEntryStatus()));
        entry.setFont(entry.getFont().deriveFont(Font.BOLD, 10f));
        header.add(entry);
        chunkBody.add(header);
        chunkBody.add(Box.createVerticalStrut(6));

        JPanel counts = new JPanel(new GridLayout(1, 3, 4, 0));
        counts.setOpaque(false);
        counts.setAlignmentX(Component.LEFT_ALIGNMENT);
        counts.add(chip("Can do " + view.getAllowedCount(), GREEN));
        counts.add(chip("Not ready " + view.getNotReadyCount(), AMBER));
        counts.add(chip("Locked " + view.getLockedCount(), RED));
        counts.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        chunkBody.add(counts);

        for (ChunkPanelViewModel.CategoryView category : view.getCategories())
        {
            chunkBody.add(Box.createVerticalStrut(9));
            chunkBody.add(section(category.getTitle()));
            for (ChunkPanelViewModel.RowView row : category.getRows())
            {
                chunkBody.add(permissionRow(row));
            }
        }
        chunkBody.revalidate();
        chunkBody.repaint();
    }

    private static JPanel permissionRow(ChunkPanelViewModel.RowView row)
    {
        JPanel panel = new JPanel(new BorderLayout(5, 1));
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, SEPARATOR),
            new EmptyBorder(5, 6, 5, 6)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        String shown = row.getName().length() > 28
            ? row.getName().substring(0, 27) + "…" : row.getName();
        JLabel name = new JLabel(row.getStatusGlyph() + " " + shown);
        name.setForeground(statusColor(row.getStatus()));
        name.setToolTipText(row.getDetail() == null
            ? row.getName() : row.getName() + " — " + row.getDetail());
        panel.add(name, BorderLayout.CENTER);

        if (row.getStatusText() != null)
        {
            JLabel status = new JLabel(row.getStatusText());
            status.setForeground(statusColor(row.getStatus()));
            status.setFont(status.getFont().deriveFont(10f));
            panel.add(status, BorderLayout.EAST);
        }
        if (row.getDetail() != null)
        {
            JLabel detail = new JLabel(row.getDetail());
            detail.setForeground(GRAY);
            detail.setFont(detail.getFont().deriveFont(9f));
            panel.add(detail, BorderLayout.SOUTH);
        }
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
            row.getDetail() == null ? 28 : 40));
        return panel;
    }

    private static JPanel emptyChunk()
    {
        JPanel panel = card();
        JLabel label = new JLabel("Enter the game to see this chunk");
        label.setForeground(GRAY);
        panel.add(label);
        return panel;
    }

    private static JPanel card()
    {
        JPanel panel = column();
        panel.setBackground(SURFACE);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        return panel;
    }

    private static JLabel chip(String text, Color color)
    {
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setOpaque(true);
        label.setBackground(SURFACE);
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 9f));
        label.setBorder(new EmptyBorder(4, 2, 4, 2));
        return label;
    }

    void flashStatus(String message, boolean ok)
    {
        runOnEdt(() -> {
            importVal.setText(message);
            importVal.setForeground(ok ? GREEN : RED);
            importVal.setVisible(true);
            if (ok && bundleSection.isExpanded())
            {
                bundleSection.setExpanded(false);
            }
        });
    }

    private static boolean hasText(Component component, String text)
    {
        if (component instanceof JLabel)
        {
            String shown = ((JLabel) component).getText();
            if (shown != null && shown.contains(text))
            {
                return true;
            }
        }
        if (component instanceof java.awt.Container)
        {
            for (Component child
                : ((java.awt.Container) component).getComponents())
            {
                if (hasText(child, text))
                {
                    return true;
                }
            }
        }
        return false;
    }
    private static Color statusColor(PermissionStatus status)
    {
        if (status == PermissionStatus.ALLOWED) return GREEN;
        if (status == PermissionStatus.NOT_READY) return AMBER;
        if (status == PermissionStatus.LOCKED) return RED;
        return GRAY;
    }

    private static String statusText(PermissionStatus status)
    {
        if (status == PermissionStatus.ALLOWED) return "Available";
        if (status == PermissionStatus.NOT_READY) return "Not ready";
        if (status == PermissionStatus.LOCKED) return "Locked";
        return "Unknown";
    }

    private static JLabel title(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(AMBER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel section(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(GRAY);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JPanel column()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static String headerText(String label, boolean open)
    {
        return (open ? "▾ " : "▸ ") + label;
    }

    private static JButton collapsibleHeader(String label, JPanel body, boolean open)
    {
        JButton header = new JButton(headerText(label, open));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 10f));
        header.setForeground(GRAY);
        header.setContentAreaFilled(false);
        header.setBorder(new EmptyBorder(0, 0, 4, 0));
        header.setFocusPainted(false);
        header.setHorizontalAlignment(JButton.LEFT);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.setVisible(open);
        header.addActionListener(event -> {
            boolean nowOpen = !body.isVisible();
            body.setVisible(nowOpen);
            header.setText(headerText(label, nowOpen));
            body.revalidate();
        });
        return header;
    }

    private static void fullWidth(JButton button)
    {
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(
            Integer.MAX_VALUE, button.getPreferredSize().height));
    }

    private static JLabel value()
    {
        JLabel label = new JLabel("—");
        label.setForeground(Color.WHITE);
        return label;
    }

    private static JPanel stats(String[] labels, JLabel[] values)
    {
        JPanel grid = new JPanel(new GridLayout(0, 2, 6, 3));
        grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < labels.length; i++)
        {
            JLabel key = new JLabel(labels[i]);
            key.setForeground(Color.LIGHT_GRAY);
            grid.add(key);
            grid.add(values[i]);
        }
        grid.setMaximumSize(new Dimension(
            Integer.MAX_VALUE, grid.getPreferredSize().height));
        return grid;
    }

    private static String orDash(String value)
    {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }
}
