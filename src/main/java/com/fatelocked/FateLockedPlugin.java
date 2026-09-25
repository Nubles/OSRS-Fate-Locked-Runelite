package com.fatelocked;

import com.google.gson.Gson;
import com.fatelocked.events.FateEventHistory;
import com.fatelocked.events.FateEventFactory;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
import com.fatelocked.panel.ChunkPanelViewModel;
import com.fatelocked.panel.ChunkPanelViewModelFactory;
import com.fatelocked.guardian.GuardedAction;
import com.fatelocked.guardian.GuardedActionFactory;
import com.fatelocked.guardian.GuardContext;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModePause;
import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeAuditLog;
import com.fatelocked.guardian.StrictModeAuditPresenter;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.guardian.travel.RuneLiteTravelAvailability;
import com.fatelocked.guardian.travel.TravelActionResolver;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.fatelocked.detectors.BossRaidDetector;
import com.fatelocked.detectors.CollectionLogDetector;
import com.fatelocked.detectors.ClueCasketDetector;
import com.fatelocked.detectors.CombatAchievementDetector;
import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.detectors.QuestDetector;
import com.fatelocked.detectors.SkillLevelDetector;
import com.fatelocked.detectors.SlayerTaskDetector;
import com.fatelocked.detectors.DiaryTierReviewDetector;
import com.fatelocked.detectors.PetDropDetector;
import com.fatelocked.detectors.MinigameCompletionDetector;
import com.fatelocked.detectors.BossKillDetectorV2;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.api.Point;

import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
    name = "Fate Locked Ironman",
    description = "Shows app-authored Fate Locked rules, local observations, overlays, warnings, and optional Strict Mode",
    tags = { "chunk", "ironman", "locked", "map", "fate" }
)
public class FateLockedPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private FateLockedConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private FateLockedWorldMapOverlay worldMapOverlay;
    @Inject private FateLockedSceneOverlay sceneOverlay;
    @Inject private FateLockedMinimapOverlay minimapOverlay;
    @Inject private FateLockedHudOverlay hudOverlay;
    @Inject private FateLockedContentOverlay contentOverlay;
    @Inject private FateLockedFlashOverlay flashOverlay;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private FateLockedPanel panel;
    @Inject private Gson gson;
    @Inject private ScheduledExecutorService executor;
    @Inject private ItemManager itemManager;
    @Inject private Notifier notifier;
    @Inject private WorldMapPointManager worldMapPointManager;
    @Inject private InfoBoxManager infoBoxManager;
    @Inject private KeyManager keyManager;
    @Inject private MouseManager mouseManager;
    @Inject private OkHttpClient okHttpClient;
    @Inject private ConfigManager configManager;
    @Inject private TrackerConnectionSettings connectionSettings;

    /** This start's session: queued work runs only while the session that queued it lasts. */
    private volatile PluginSession session = PluginSession.ended();
    private ScheduledFuture<?> trackerPollFuture;
    private TrackerConnectionController connectionController;
    private final RepeatedValueLimiter invalidImportLimiter =
        new RepeatedValueLimiter(TimeUnit.SECONDS.toMillis(30));
    private FateEventHistory eventHistory;
    private boolean historySaveFailed;
    private StrictModeAuditLog strictAuditLog;
    private final FateEventFactory eventFactory = new FateEventFactory();
    private final SkillLevelDetector skillLevelDetector = new SkillLevelDetector();
    private final QuestDetector questDetector = new QuestDetector();
    private final CombatAchievementDetector combatAchievementDetector = new CombatAchievementDetector();
    private final CollectionLogDetector collectionLogDetector = new CollectionLogDetector();
    private final ClueCasketDetector clueCasketDetector = new ClueCasketDetector();
private final BossRaidDetector bossRaidDetector = new BossRaidDetector();
    private final BossKillDetectorV2 bossKillDetectorV2 = new BossKillDetectorV2();
    private final DiaryTierReviewDetector diaryTierReviewDetector = new DiaryTierReviewDetector();
    private final PetDropDetector petDropDetector = new PetDropDetector();
    private final MinigameCompletionDetector minigameCompletionDetector = new MinigameCompletionDetector();
    private SlayerTaskDetector slayerTaskDetector;
    /** Configurable hotkey: re-import the bundle from the clipboard. */
    private final HotkeyListener reimportHotkey = new HotkeyListener(() -> config.reimportHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            reimportFromClipboard();
        }
    };

    @Getter private volatile FateLockedBundle bundle = FateLockedBundle.empty();
    private final ChunkPanelViewModelFactory chunkPanelFactory =
        new ChunkPanelViewModelFactory();
    private final GuardedActionFactory guardedActionFactory = new GuardedActionFactory();
    private final StrictModeClickHandler strictClickHandler =
        new StrictModeClickHandler(new StrictModeGuard());
    /** Where the active rules came from; freshness depends on it (see rulesAreFresh). */
    private volatile RulesSource rulesSource = RulesSource.NONE;
    /** Strict Mode acts only on rules confirmed or exported within this window. */
    static final Duration FRESH_RULES_WINDOW = Duration.ofMinutes(15);
    /** How far in the future an export time may be before it is not trusted. */
    static final Duration EXPORT_CLOCK_SKEW = Duration.ofMinutes(5);
    private final StrictModePause strictPause = new StrictModePause(Clock.systemUTC());
    private TravelActionResolver travelActionResolver;
    private TravelRuleEvaluator travelRuleEvaluator;
    private TravelAvailability travelAvailability;
    private TravelAlternativeFinder travelAlternativeFinder;
    private TravelBlockNoticeStore travelNoticeStore;
    private TravelGuardianCoordinator travelGuardianCoordinator;
    private TravelGuardianPluginShell travelGuardianShell;
    private FateLockedTravelBlockOverlay travelBlockOverlay;
    private TravelGuardianOverlayLifecycle travelOverlayLifecycle;

    /** How long the locked-entry screen flash lasts. */
    public static final long LOCKED_FLASH_MS = 1600;
    @Getter private volatile long lockedFlashUntil;

    private CanonicalChunk lastChunk;
    private FateLockedBundle.LockState lastLockState;
    /** Warnings count the sidebar shows, so each change is sent to it once. */
    private int shownWarningCount = -1;
    private NavigationButton navButton;

    /** Achievement-diary completion varbits (1 = that tier done), watched for 0→1. */
    private static final int[] DIARY_VARBITS = {
        VarbitID.ARDOUGNE_DIARY_EASY_COMPLETE, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE, VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE, VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE,
        VarbitID.DESERT_DIARY_EASY_COMPLETE, VarbitID.DESERT_DIARY_MEDIUM_COMPLETE, VarbitID.DESERT_DIARY_HARD_COMPLETE, VarbitID.DESERT_DIARY_ELITE_COMPLETE,
        VarbitID.FALADOR_DIARY_EASY_COMPLETE, VarbitID.FALADOR_DIARY_MEDIUM_COMPLETE, VarbitID.FALADOR_DIARY_HARD_COMPLETE, VarbitID.FALADOR_DIARY_ELITE_COMPLETE,
        VarbitID.FREMENNIK_DIARY_EASY_COMPLETE, VarbitID.FREMENNIK_DIARY_MEDIUM_COMPLETE, VarbitID.FREMENNIK_DIARY_HARD_COMPLETE, VarbitID.FREMENNIK_DIARY_ELITE_COMPLETE,
        VarbitID.KANDARIN_DIARY_EASY_COMPLETE, VarbitID.KANDARIN_DIARY_MEDIUM_COMPLETE, VarbitID.KANDARIN_DIARY_HARD_COMPLETE, VarbitID.KANDARIN_DIARY_ELITE_COMPLETE,
        VarbitID.ATJUN_EASY_DONE, VarbitID.ATJUN_MED_DONE, VarbitID.ATJUN_HARD_DONE, VarbitID.KARAMJA_DIARY_ELITE_COMPLETE,
        VarbitID.KOUREND_DIARY_EASY_COMPLETE, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE, VarbitID.KOUREND_DIARY_HARD_COMPLETE, VarbitID.KOUREND_DIARY_ELITE_COMPLETE,
        VarbitID.LUMBRIDGE_DIARY_EASY_COMPLETE, VarbitID.LUMBRIDGE_DIARY_MEDIUM_COMPLETE, VarbitID.LUMBRIDGE_DIARY_HARD_COMPLETE, VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE,
        VarbitID.MORYTANIA_DIARY_EASY_COMPLETE, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE,
        VarbitID.VARROCK_DIARY_EASY_COMPLETE, VarbitID.VARROCK_DIARY_MEDIUM_COMPLETE, VarbitID.VARROCK_DIARY_HARD_COMPLETE, VarbitID.VARROCK_DIARY_ELITE_COMPLETE,
        VarbitID.WESTERN_DIARY_EASY_COMPLETE, VarbitID.WESTERN_DIARY_MEDIUM_COMPLETE, VarbitID.WESTERN_DIARY_HARD_COMPLETE, VarbitID.WESTERN_DIARY_ELITE_COMPLETE,
        VarbitID.WILDERNESS_DIARY_EASY_COMPLETE, VarbitID.WILDERNESS_DIARY_MEDIUM_COMPLETE, VarbitID.WILDERNESS_DIARY_HARD_COMPLETE, VarbitID.WILDERNESS_DIARY_ELITE_COMPLETE,
    };
    /** Last seen value per diary varbit; first observation per login is a baseline (no nudge). */
    private final Map<Integer, Integer> diaryState = new HashMap<>();
    /** Diary region names, in DIARY_VARBITS order (4 tiers per region). */
    private static final String[] DIARY_REGIONS = {
        "Ardougne", "Desert", "Falador", "Fremennik", "Kandarin", "Karamja",
        "Kourend & Kebos", "Lumbridge & Draynor", "Morytania", "Varrock",
        "Western Provinces", "Wilderness",
    };
    private static final String[] DIARY_TIERS = { "Easy", "Medium", "Hard", "Elite" };
    /** Varbit id → "Ardougne Elite"-style name; key set doubles as the per-event filter. */
    private static final Map<Integer, String> DIARY_VARBIT_NAMES = new HashMap<>();
    static
    {
        for (int i = 0; i < DIARY_VARBITS.length; i++)
        {
            DIARY_VARBIT_NAMES.put(DIARY_VARBITS[i], DIARY_REGIONS[i / 4] + " " + DIARY_TIERS[i % 4]);
        }
    }
    /** Whether this login's diary baseline has been captured (see onVarbitChanged). */
    private boolean diaryBaselined = false;
    /** Widget group shown when a quest is completed (the reward scroll). */
    private static final int QUEST_COMPLETED_GROUP_ID = 153;
    /** Interface group ids for the bank (12) and deposit box (192) — stable
     *  numeric ids, used raw like QUEST_COMPLETED to avoid API-constant churn. */
    private static final int BANK_GROUP_ID = 12;
    private static final int DEPOSIT_BOX_GROUP_ID = 192;
    /** Crystal key — a gold-key item icon for the Keys infobox. */
    private static final int KEYS_ICON_ITEM = 989;
    /** Plugin-specific data dir under .runelite/ — all file I/O is confined here. */
    private static final File DATA_DIR = new File(RuneLite.RUNELITE_DIR, "fate-locked");

    /** RuneLite equipment slot → the web app's slot name (for tier lookup). */
    private static final Map<EquipmentInventorySlot, String> SLOT_NAMES = new LinkedHashMap<>();
    static
    {
        SLOT_NAMES.put(EquipmentInventorySlot.HEAD, "Head");
        SLOT_NAMES.put(EquipmentInventorySlot.CAPE, "Cape");
        SLOT_NAMES.put(EquipmentInventorySlot.AMULET, "Neck");
        SLOT_NAMES.put(EquipmentInventorySlot.AMMO, "Ammo");
        SLOT_NAMES.put(EquipmentInventorySlot.WEAPON, "Weapon");
        SLOT_NAMES.put(EquipmentInventorySlot.BODY, "Body");
        SLOT_NAMES.put(EquipmentInventorySlot.SHIELD, "Shield");
        SLOT_NAMES.put(EquipmentInventorySlot.LEGS, "Legs");
        SLOT_NAMES.put(EquipmentInventorySlot.GLOVES, "Gloves");
        SLOT_NAMES.put(EquipmentInventorySlot.BOOTS, "Boots");
        SLOT_NAMES.put(EquipmentInventorySlot.RING, "Ring");
    }

    /** Active world-map markers for locked areas (so we can remove them on refresh). */
    private final List<WorldMapPoint> mapMarkers = new ArrayList<>();
    private BufferedImage lockedPinImage;

    /** Worn-gear slots currently above your unlocked tier, for the HUD (null = none). */
    @Getter private volatile String overTierSummary;
    /** Item ids already warned about this session, to avoid chat spam. */
    private final Set<Integer> warnedOverTier = new HashSet<>();

    /** Assigned slayer monster from chat (matches both "to kill X;" and Konar's "in <area>"). */
    private static final Pattern SLAYER_TASK =
        Pattern.compile("to kill\\s+(?:the\\s+)?(.+?)(?:\\s+in\\s+|[;:.])", Pattern.CASE_INSENSITIVE);
    /** The client's own broadcast on a new Collection Log entry: "New item added to your collection log: X". */
    private static final Pattern COLLECTION_LOG_ITEM =
        Pattern.compile("new item added to your collection log:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    /** CA completion broadcast: the task name follows "combat task:". */
    private static final Pattern COMBAT_TASK =
        Pattern.compile("combat task:\\s*(.+?)\\.?$", Pattern.CASE_INSENSITIVE);
    /**
     * Reward-scroll text. Most quests read "You have completed The Corsair
     * Curse!" (no trailing "quest"); a few older ones read "...completed the
     * Dragon Slayer quest". Try the suffixed form first so it doesn't leave
     * a dangling "quest" in the captured name, then the bare form.
     */
    private static final Pattern QUEST_COMPLETE_SUFFIXED =
        Pattern.compile("completed (?:the )?(.+?) quest[!.]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUEST_COMPLETE_BARE =
        Pattern.compile("(?:you have |have )completed (.+?)[!.]", Pattern.CASE_INSENSITIVE);
    /** Current slayer task monster name (raw), or null. */
    private String slayerTask;
    /** The locked slayer task to show on the HUD, or null. */
    @Getter private volatile String slayerTaskWarn;
    /** Task we've already chat-warned about, to warn at most once per assignment. */
    private String slayerWarnedFor;
    /** Last account name we warned about, so we nag at most once per character. */
    private String lastAccountWarned;
    /**
     * Whether the next LOGGED_IN starts a new session: set by the login
     * screen, logging in, hopping and a lost connection, but not by the
     * loading screens RuneLite also reports as LOGGED_IN.
     */
    private boolean awaitingLogin = true;
    /** Account hash of the current login; -1 until one is seen. */
    private long loggedInAccountHash = -1;


    @Provides
    FateLockedConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FateLockedConfig.class);
    }

    @Override
    protected void startUp()
    {
        PluginSession started = new PluginSession();
        session = started;
        connectionSettings.clearLegacySettings();
        startSessionTracking();
        File dataDirectory = dataDirectory();
        if (!dataDirectory.exists()) dataDirectory.mkdirs();
        // Local state is optional: a store that can't be opened leaves its
        // feature off for this session, and never stops the plugin starting.
        try
        {
            slayerTaskDetector = new SlayerTaskDetector(gson,
                dataDirectory.toPath().resolve("slayer-assignment.json"));
        }
        catch (IOException | RuntimeException ex)
        {
            log.warn("Could not open Slayer assignment state", ex);
            slayerTaskDetector = null;
        }
        try
        {
            Path dataPath = dataDirectory.toPath();
            eventHistory = new FateEventHistory(
                gson,
                dataPath.resolve("event-history.json"),
                dataPath.resolve("event-" + "outbox.json"));
            historySaveFailed = false;
        }
        catch (IOException | RuntimeException ex)
        {
            log.warn("Could not open local Fate event history", ex);
            eventHistory = null;
            historySaveFailed = true;
        }
        try
        {
            strictAuditLog = new StrictModeAuditLog(gson,
                dataDirectory.toPath().resolve("strict-mode-events.json"));
        }
        catch (IOException | RuntimeException ex)
        {
            log.warn("Could not open Strict Mode audit log", ex);
            strictAuditLog = null;
        }
        connectionController = new TrackerConnectionController(
            okHttpClient,
            gson,
            connectionSettings,
            Clock.systemUTC(),
            runnable -> clientThread.invoke(started.guard(runnable)),
            this::acceptRelayPayload,
            panel::updateConnection);

        travelActionResolver = new TravelActionResolver();
        travelRuleEvaluator = new TravelRuleEvaluator();
        travelAvailability = new RuneLiteTravelAvailability(client);
        travelAlternativeFinder = new TravelAlternativeFinder();
        travelNoticeStore = new TravelBlockNoticeStore(Clock.systemUTC());
        travelGuardianCoordinator = new TravelGuardianCoordinator(
            travelActionResolver,
            travelRuleEvaluator,
            travelAlternativeFinder,
            travelNoticeStore,
            strictClickHandler);
        travelGuardianShell = new TravelGuardianPluginShell(
            travelGuardianCoordinator,
            travelAvailability,
            this::writeTravelChat,
            this::writeTravelAudit,
            (stage, error) -> log.debug(
                "Travel Guardian {} failed: {}", stage, error.getMessage()),
            Clock.systemUTC());
        travelBlockOverlay = new FateLockedTravelBlockOverlay(
            travelNoticeStore,
            config::strictMode,
            strictPause::isPaused,
            this::pauseStrictModeForSixtySeconds);

        overlayManager.add(worldMapOverlay);
        overlayManager.add(sceneOverlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(hudOverlay);
        overlayManager.add(contentOverlay);
        overlayManager.add(flashOverlay);
        travelBlockOverlay.setPauseGuardian(this::pauseStrictModeForSixtySeconds);
        travelOverlayLifecycle = new TravelGuardianOverlayLifecycle(
            () -> overlayManager.add(travelBlockOverlay),
            () -> mouseManager.registerMouseListener(travelBlockOverlay),
            () -> mouseManager.unregisterMouseListener(travelBlockOverlay),
            () -> overlayManager.remove(travelBlockOverlay));
        travelOverlayLifecycle.start();

        wirePanelActions(
            panel,
            this::reimportFromClipboard,
            this::loadNewestBackupFile,
            this::beginTrackerPairing);
        panel.setGuardianCallbacks(
            this::pauseStrictModeForSixtySeconds,
            () -> { strictPause.resume(); updateStrictModePanel(); },
            () -> configManager.setConfiguration(
                FateLockedConfig.GROUP, "strictModeIntroSeen", true));
        updateStrictModePanel();
        updateStrictAuditPanel();
        panel.setRollInboxLink(FateLockedPanel.TRACKER_URL);
        updatePanelRollInbox();
        panel.updateConnection(connectionController.snapshot());
        navButton = buildNavigationButton(panel);
        clientToolbar.addNavigation(navButton);

        loadBackupFileAtStartup();
        refreshInfoBoxes();
        keyManager.registerKeyListener(reimportHotkey);
        startTrackerPoll();
    }

    @Override
    protected void shutDown()
    {
        // First, so nothing this start queued can bring rules back later.
        session.end();
        stopTrackerPoll();
        if (travelOverlayLifecycle != null)
        {
            try
            {
                travelOverlayLifecycle.stop();
            }
            catch (RuntimeException ex)
            {
                log.debug("Could not fully clean up Travel Guardian overlay: {}",
                    ex.getMessage());
            }
        }
        overlayManager.remove(worldMapOverlay);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(hudOverlay);
        overlayManager.remove(contentOverlay);
        overlayManager.remove(flashOverlay);
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        keyManager.unregisterKeyListener(reimportHotkey);
        for (WorldMapPoint p : mapMarkers) worldMapPointManager.remove(p);
        mapMarkers.clear();
        infoBoxManager.removeIf(b -> b instanceof FateLockedInfoBox);
        bundle = FateLockedBundle.empty();
        rulesSource = RulesSource.NONE;
        lastChunk = null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged ev)
    {
        if (!FateLockedConfig.GROUP.equals(ev.getGroup())) return;
        panel.refreshConfig(ev.getKey());
        String key = ev.getKey();
        if ("warnOverTierGear".equals(key))
        {
            recomputeOverTierGear();
        }
        else if ("warnLockedSlayer".equals(key))
        {
            recomputeSlayer();
        }
        else if ("worldMapMarkers".equals(key))
        {
            refreshWorldMapMarkers();
        }
        else if ("showInfoBoxes".equals(key))
        {
            refreshInfoBoxes();
        }
        else if ("strictMode".equals(key))
        {
            strictPause.resume();
            updateStrictModePanel();
            Boolean seen = configManager.getConfiguration(
                FateLockedConfig.GROUP, "strictModeIntroSeen", Boolean.class);
            if (config.strictMode() && !Boolean.TRUE.equals(seen))
            {
                panel.showStrictModeIntro();
            }
        }
        else if (FateLockedConfig.NETWORK_ACCESS_KEY.equals(key))
        {
            if (connectionController != null)
            {
                connectionController.networkAccessChanged();
            }
        }
        else if (TrackerConnectionSettings.PAIRING_CODE_KEY.equals(key))
        {
            panel.setRollInboxLink(FateLockedPanel.TRACKER_URL);
            updatePanelRollInbox();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged ev)
    {
        GameState state = ev.getGameState();
        if (state == GameState.LOGIN_SCREEN)
        {
            // Logged out: the next login warns and announces afresh.
            awaitingLogin = true;
            forgetLoginWarnings();
            return;
        }
        if (state == GameState.LOGGING_IN || state == GameState.HOPPING
            || state == GameState.CONNECTION_LOST)
        {
            awaitingLogin = true;
            return;
        }
        if (state != GameState.LOGGED_IN) return;

        // RuneLite also reports LOGGED_IN after every loading screen. Only a
        // login, a hop, a reconnect or a different account starts a new
        // session (RuneLite's XP tracker makes the same distinction).
        // Resetting after each loading screen repeated the account warning
        // and its sound, re-announced the chunk and dropped level-ups.
        long accountHash = client.getAccountHash();
        boolean newAccount = accountHash != loggedInAccountHash;
        boolean newSession = awaitingLogin || newAccount;
        awaitingLogin = false;
        loggedInAccountHash = accountHash;
        if (newAccount) forgetLoginWarnings();
        if (newSession) resetBaselines();
    }

    /**
     * Start session tracking from a clean slate. A login already under way
     * (the plugin turned on in game) is adopted, so its next loading screen
     * is not mistaken for a new login.
     */
    private void startSessionTracking()
    {
        boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
        awaitingLogin = !loggedIn;
        loggedInAccountHash = loggedIn ? client.getAccountHash() : -1;
        forgetLoginWarnings();
        resetBaselines();
    }

    /** Let the account and gear warnings, and the chunk announcement, show once more. */
    private void forgetLoginWarnings()
    {
        lastChunk = null;
        lastAccountWarned = null;
        warnedOverTier.clear();
    }

    /**
     * The client sends every skill and varbit again after a login, hop or
     * reconnect; clearing here lets those set the baseline without nudges.
     */
    private void resetBaselines()
    {
        skillLevelDetector.clear();
        diaryState.clear();
        diaryBaselined = false;
    }

    // ── Roll reminders ────────────────────────────────────────────────────────
    // Read-only nudges: a chat line when something that may grant a roll in the
    // tracker happens (level-up, quest, diary, combat achievement). Purely
    // informational — the plugin never acts on the player's behalf.

    @Subscribe
    public void onStatChanged(StatChanged ev)
    {
        Skill skill = ev.getSkill();
        int level = ev.getLevel();
        java.util.Optional<DetectedEvent> detected =
            skillLevelDetector.detect(skillName(skill), level);
        if (detected.isPresent())
        {
            record(detected.get());
            if (config.rollNudges())
            {
                nudge("Leveled " + skillName(skill) + " to " + level
                    + " — may be worth a roll.");
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage ev)
    {
        if (ev.getType() != ChatMessageType.GAMEMESSAGE && ev.getType() != ChatMessageType.SPAM) return;
        String raw = ev.getMessage() == null ? "" : ev.getMessage();
String m = raw.toLowerCase();

        minigameCompletionDetector.onMessage(Text.removeTags(raw), System.currentTimeMillis())
            .ifPresent(this::record);
        Integer followerId = client.getFollower() == null
            ? null : client.getFollower().getId();
        petDropDetector.detect(Text.removeTags(raw), followerId, System.currentTimeMillis())
            .ifPresent(this::record);
        if (slayerTaskDetector != null
            && (m.contains("completed your task") || m.contains("return to a slayer master")))
        {
            try { slayerTaskDetector.completion(Text.removeTags(raw)).ifPresent(this::record); }
            catch (IOException ex) { log.debug("Could not update Slayer state", ex); }
        }

        // Combat achievements stay on chat (their varbits are progress counts with
        // totals that shift as Jagex adds tasks). Diaries are detected via varbit
        // (onVarbitChanged), quests via the reward widget — both more reliable.
        if (m.contains("combat task:"))
        {
            String plain = Text.removeTags(raw);
            DetectedEvent detected = combatAchievementDetector.detect(plain);
            record(detected);
            if (config.rollNudges())
            {
                String task = detected.getCanonicalLabel();
                nudge(task != null
                    ? "Combat achievement: " + task + " — may be worth a roll."
                    : "Combat achievement complete — may be worth a roll.");
            }
        }

        if (m.contains("new item added to your collection log"))
        {
            Matcher mat = COLLECTION_LOG_ITEM.matcher(raw);
            String item = mat.find() ? mat.group(1).trim() : null;
            // RuneLite has the observed label but not the app's canonical item-id
            // index, so delivery stays conservative until the app confirms it.
            record(collectionLogDetector.detect(item, false));
            if (config.rollNudges())
            {
                nudge(item != null
                    ? "Collection log: " + item + " — may be worth a roll."
                    : "Collection log entry added — may be worth a roll.");
            }
        }
        // Slayer assignment / task-check messages mention the monster.
        if (m.contains("to kill"))
        {
            Matcher mat = SLAYER_TASK.matcher(raw);
            if (mat.find())
            {
                slayerTask = mat.group(1).trim();
                if (slayerTaskDetector != null)
                {
                    try { slayerTaskDetector.assignment(slayerTask, null, 0, false); }
                    catch (IOException ex) { log.debug("Could not save Slayer assignment", ex); }
                }
                if (config.warnLockedSlayer())
                {
                    recomputeSlayer();
                }
            }
        }
    }

    /** Re-check whether the current slayer task's monster is in an unlocked chunk. */
    private void recomputeSlayer()
    {
        if (!config.warnLockedSlayer() || slayerTask == null || slayerTask.isEmpty())
        {
            slayerTaskWarn = null;
            return;
        }
        FateLockedBundle.Reach reach = bundle.monsterReach(slayerTask);
        if (reach == FateLockedBundle.Reach.LOCKED)
        {
            slayerTaskWarn = slayerTask;
            if (!slayerTask.equalsIgnoreCase(slayerWarnedFor))
            {
                slayerWarnedFor = slayerTask;
                ChatMessageBuilder msg = new ChatMessageBuilder()
                    .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
                    .append(ChatColorType.NORMAL).append("Your slayer task (")
                    .append(ChatColorType.HIGHLIGHT).append(slayerTask)
                    .append(ChatColorType.NORMAL).append(") is in a locked area.");
                chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(msg.build())
                    .build());
                notifyIfEnabled("Slayer task (" + slayerTask + ") is in a locked area");
            }
        }
        else
        {
            slayerTaskWarn = null; // reachable or unknown — no warning
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded ev)
    {
        if (ev.getGroupId() == 408) minigameCompletionDetector.onPestControlWidget(System.currentTimeMillis());
        // Locked-bank warning is independent of the roll-nudge toggle.
        if ((ev.getGroupId() == BANK_GROUP_ID || ev.getGroupId() == DEPOSIT_BOX_GROUP_ID)
            && config.warnLockedBank() && bundle.banksLocked())
        {
            warnLockedBankIfNeeded();
        }

        if (ev.getGroupId() == QUEST_COMPLETED_GROUP_ID)
        {
            clientThread.invokeLater(session.guard(() ->
            {
                DetectedEvent detected = questDetector.detect(extractQuestName());
                record(detected);
                if (config.rollNudges())
                {
                    nudge(detected.getCanonicalLabel() == null
                        ? "Quest complete — may be worth a roll."
                        : "Quest complete: " + detected.getCanonicalLabel()
                            + " — may be worth a roll.");
                }
            }));
        }
    }

    /** Build the shared compact model for the current chunk. */
    ChunkPanelViewModel viewModelFor(FateLockedBundle source, CanonicalChunk chunk)
    {
        if (chunk == null) return null;
        return chunkPanelFactory.create(
            source,
            chunk,
            currentAccountMatches(source),
            trackerPaired() ? trackerLastSync() : null);
    }
    private FateRuleEngine ruleEngine(FateLockedBundle source)
    {
        return new FateRuleEngine(source, currentAccountMatches(source), false);
    }

    private boolean currentAccountMatches(FateLockedBundle source)
    {
        String bound = source.getRules() == null
            ? source.getState() == null ? null : source.getState().getLinkedAccount()
            : source.getRules().getAccount();
        if (bound == null || bound.trim().isEmpty()) return true;
        Player local = client.getLocalPlayer();
        String current = local == null ? null : local.getName();
        return current != null && normName(bound).equals(normName(current));
    }

    private boolean strictTravelAccountMatches(FateLockedBundle source)
    {
        if (source == null || source.getRules() == null)
        {
            return false;
        }
        String bound = source.getRules().getAccount();
        if (bound == null || bound.trim().isEmpty())
        {
            return false;
        }
        Player local = client.getLocalPlayer();
        String current = local == null ? null : local.getName();
        return current != null && normName(bound).equals(normName(current));
    }

    /** Advisory when a bank is explicitly locked by the shared rules. */
    private void warnLockedBankIfNeeded()
    {
        Player local = client.getLocalPlayer();
        WorldPoint wp = local == null ? null : local.getWorldLocation();
        if (wp == null) return;
        CanonicalChunk chunk = CanonicalChunk.of(wp);
        String where;
        if (bundle.isLegacyRules())
        {
            if (bundle.isBankUnlocked(chunk)) return;
            String label = bundle.labelAt(chunk);
            where = label == null ? "This bank" : label + " bank";
        }
        else
        {
            RuleDecision decision = ruleEngine(bundle).target(chunk, "BANK", "");
            if (decision.getStatus() != PermissionStatus.LOCKED) return;
            where = decision.getLabel();
        }
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append(where)
            .append(ChatColorType.NORMAL).append(" is LOCKED — roll it under Banks in the tracker before you rely on it.");
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
        notifyIfEnabled(where + " is locked");
    }

    /** Reads the quest name off the reward scroll widget, or null if it can't be found. */
    private String extractQuestName()
    {
        try
        {
            for (int child = 0; child < 10; child++)
            {
                net.runelite.api.widgets.Widget w = client.getWidget(QUEST_COMPLETED_GROUP_ID, child);
                if (w == null || w.getText() == null) continue;
                String text = Text.removeTags(w.getText());
                Matcher mat = QUEST_COMPLETE_SUFFIXED.matcher(text);
                if (mat.find()) return mat.group(1).trim();
                mat = QUEST_COMPLETE_BARE.matcher(text);
                if (mat.find()) return mat.group(1).trim();
            }
        }
        catch (Exception ignored) { /* layout mismatch — fall back to generic label */ }
        return null;
    }

    /**
     * Precise boss/raid kill detection — fires on the actual loot drop rather
     * than inferring a kill from chunk content, so it's reliable even for
     * bosses the chunk dataset doesn't cover. EVENT-type loot (CoX/ToB/ToA
     * reward chests) always nudges; for NPC loot the boss detectors decide
     * which kills count.
     */
    @Subscribe
    public void onLootReceived(LootReceived ev)
    {
        String type = ev.getType() == null ? "" : ev.getType().name();
        if (ev.getItems() != null)
        {
            for (net.runelite.client.game.ItemStack stack : ev.getItems())
            {
                String itemName = itemManager.getItemComposition(stack.getId()).getName();
                java.util.Optional<DetectedEvent> clue = clueCasketDetector.detect(itemName);
                if (clue.isPresent()) record(clue.get());
            }
        }
java.util.Optional<DetectedEvent> detected =
            bossKillDetectorV2.detect(type, ev.getName(), client.getGameCycle());
        if (!detected.isPresent())
        {
            detected = bossRaidDetector.detect(type, ev.getName(), ev.getCombatLevel());
        }
        if (detected.isPresent())
        {
            record(detected.get());
            if (config.rollNudges())
            {
                nudge(detected.get().getType() == com.fatelocked.events.FateEventType.RAID_COMPLETION
                    ? "Raid loot (" + ev.getName() + ") — may be worth a roll."
                    : "Boss kill (" + ev.getName() + ") — may be worth a roll.");
            }
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged ev)
    {
        // Reliable diary-tier detection: each varbit flips 0→1 when that tier is
        // finished. VarbitChanged fires for EVERY varbit in the game — a very hot
        // event — so the steady-state path is a set-lookup filter, not a scan.
        // The one-time baseline still reads all 48: a tier varbit that's 0 at
        // login never fires an event, so filtering alone would leave it with no
        // baseline and its later completion would be missed. (The first event
        // after LOGGED_IN arrives after the initial varp sync, so the values
        // read here are the real ones, not pre-sync zeros.)
        if (!diaryBaselined)
        {
            for (int id : DIARY_VARBITS) diaryState.put(id, client.getVarbitValue(id));
            diaryBaselined = true;
            return;
        }
        int id = ev.getVarbitId();
        String name = DIARY_VARBIT_NAMES.get(id);
        if (name == null) return;
        int v = ev.getValue();
        Integer prev = diaryState.put(id, v);
        if (prev != null && prev == 0 && v == 1)
        {
            diaryTierReviewDetector.onVarbit(name, prev, v).ifPresent(this::record);
            if (config.rollNudges())
            {
                nudge("Diary complete: " + name + " — may be worth a roll; log it in the tracker.");
            }
        }
    }

    private void record(DetectedEvent detected)
    {
        if (detected == null || eventHistory == null) return;
        FateLockedBundle currentBundle = bundle;
        if (currentBundle == null || currentBundle.getRunId() == null
            || currentBundle.getRunId().trim().isEmpty()) return;
        Player local = client.getLocalPlayer();
        String account = local == null ? null : local.getName();
        if (account == null || account.trim().isEmpty()) return;
        FateEvent event = eventFactory.create(
            detected.getType(), detected.getCanonicalLabel(), detected.getConfidence(),
            detected.getEvidence(), currentBundle, account,
            detected.getDetectorId(), detected.getDetectorVersion());
        try
        {
            if (eventHistory.record(event))
            {
                historySaveFailed = false;
            }
            updatePanelRollInbox();
        }
        catch (IOException ex)
        {
            historySaveFailed = true;
            log.warn("Could not persist local Fate event history", ex);
            updatePanelRollInbox();
        }
    }

    /** Friendly skill name, e.g. "Woodcutting" from the WOODCUTTING enum. */
    private static String skillName(Skill skill)
    {
        String n = skill.getName();
        return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1).toLowerCase();
    }

    /** Fire a RuneLite notification if the user enabled it (respects their global settings). */
    private void notifyIfEnabled(String text)
    {
        if (config.useNotifier()) notifier.notify(text);
    }

    /** Queue a one-line informational chat nudge (client-side only). */
    private void nudge(String text)
    {
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append(text);
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
    }

    // ── Over-tier gear warning ────────────────────────────────────────────────
    // Warn (chat once + HUD line) when a worn item's tier exceeds the unlocked
    // tier for its slot. The plugin can't block equipping (server-authoritative),
    // only flag it — same as the locked-chunk warnings.

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged ev)
    {
        if (ev.getContainerId() == InventoryID.WORN)
        {
            recomputeOverTierGear();
        }
    }

    private void recomputeOverTierGear()
    {
        if (!config.warnOverTierGear())
        {
            overTierSummary = null;
            return;
        }
        FateLockedBundle b = bundle;
        Map<String, Integer> tiers = b.getItemTiers();
        FateLockedBundle.RunState st = b.getState();
        Map<String, Integer> equip = st == null ? null : st.getEquipment();
        if (tiers.isEmpty() || equip == null)
        {
            overTierSummary = null; // bundle predates the tier data — feature dormant
            return;
        }

        ItemContainer eq = client.getItemContainer(InventoryID.WORN);
        if (eq == null)
        {
            overTierSummary = null;
            return;
        }

        List<String> over = new ArrayList<>();
        for (Map.Entry<EquipmentInventorySlot, String> e : SLOT_NAMES.entrySet())
        {
            Item item = eq.getItem(e.getKey().getSlotIdx());
            if (item == null || item.getId() <= 0) continue;
            Integer tier = tiers.get(String.valueOf(item.getId()));
            if (tier == null) continue; // unknown item — don't flag
            int unlocked = equip.getOrDefault(e.getValue(), 0);
            if (tier <= unlocked) continue;

            over.add(e.getValue());
            if (warnedOverTier.add(item.getId()))
            {
                String name = itemManager.getItemComposition(item.getId()).getName();
                ChatMessageBuilder msg = new ChatMessageBuilder()
                    .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
                    .append(ChatColorType.NORMAL).append(name)
                    .append(" is T" + tier + " but your " + e.getValue() + " is only unlocked to T" + unlocked + ".");
                chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.GAMEMESSAGE)
                    .runeLiteFormattedMessage(msg.build())
                    .build());
                notifyIfEnabled(name + " is above your unlocked " + e.getValue() + " tier");
            }
        }
        overTierSummary = over.isEmpty() ? null : String.join(", ", over);
    }

    /** Normalise an OSRS name for comparison via RuneLite's Text.sanitize (handles
     *  non-breaking spaces, tags and stray whitespace), then case-fold. */
    static String normName(String s)
    {
        return s == null ? "" : Text.sanitize(s).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Warn (once per login) if the bound account doesn't match the logged-in
     * character — the run's progress is tied to one OSRS account.
     */
    private void checkBoundAccount()
    {
        if (!config.warnAccountMismatch()) return;
        FateLockedBundle.RunState st = bundle.getState();
        String bound = st == null ? null : st.getLinkedAccount();
        if (bound == null || bound.trim().isEmpty()) return;

        Player local = client.getLocalPlayer();
        String current = local == null ? null : local.getName();
        if (current == null || current.isEmpty()) return;

        if (normName(bound).equals(normName(current))) return;
        if (normName(bound).equals(lastAccountWarned)) return;
        lastAccountWarned = normName(bound);

        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append("This run is bound to ")
            .append(ChatColorType.HIGHLIGHT).append(bound)
            .append(ChatColorType.NORMAL).append(" — you're logged in as ")
            .append(ChatColorType.HIGHLIGHT).append(current)
            .append(ChatColorType.NORMAL).append(".");
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
        client.playSoundEffect(2277);
        notifyIfEnabled("You're logged in as " + current + ", not the bound account " + bound);
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        Player local = client.getLocalPlayer();
        if (local == null) return;

        updateStrictModePanel();

        // Once per login, flag if the character doesn't match the bound account.
        checkBoundAccount();

        WorldPoint wp = local.getWorldLocation();
        if (wp == null) return;

        CanonicalChunk current = CanonicalChunk.of(wp);
        FateLockedBundle b = bundle;
        FateLockedBundle.LockState lock = b.lockStateAt(current);
        String label = b.labelAt(current);
        boolean unlocked = lock == FateLockedBundle.LockState.UNLOCKED;


        boolean changed = !current.equals(lastChunk);
        if (changed)
        {
            panel.update(b, viewModelFor(b, current));
            // Chunks the tracker hasn't mapped (every chunk before rules are
            // loaded; dungeons and instances) are never announced.
            if (config.chatOnEnter() && lock != FateLockedBundle.LockState.UNAUTHORED)
            {
                announceEntry(current, label, unlocked);
            }
            // Flash, sound and notification once on the way INTO locked
            // territory, not at every chunk inside it, whatever the chat
            // setting.
            if (lock == FateLockedBundle.LockState.LOCKED
                && lastLockState != FateLockedBundle.LockState.LOCKED)
            {
                lockedFlashUntil = System.currentTimeMillis() + LOCKED_FLASH_MS;
                if (config.warnOnLocked())
                {
                    client.playSoundEffect(2277); // death squelch — good "you done messed up" cue
                    notifyIfEnabled("Entered LOCKED chunk: " + label);
                }
            }
            lastChunk = current;
            lastLockState = lock;
        }
        refreshWarningCount();
    }

    /**
     * Strict Mode: stop a click only when it is exactly matched travel and
     * fresh rules bound to this character prove the destination locked. One
     * trust gate covers the only click the plugin ever consumes; walking,
     * NPCs, objects, banks and equipment are never blocked.
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        FateLockedBundle current = bundle;
        boolean accountMatch = strictTravelAccountMatches(current);
        FateRuleEngine rules = new FateRuleEngine(current, accountMatch, false);
        GuardContext context = new GuardContext(
            config.strictMode(), strictPause.isPaused(), accountMatch,
            rulesAreFresh(), rules);
        CanonicalChunk origin = null;
        Player local = client.getLocalPlayer();
        if (local != null && local.getWorldLocation() != null)
        {
            origin = CanonicalChunk.of(local.getWorldLocation());
        }
        travelGuardianShell.handle(event, client, origin, context, rules);
    }

    private void writeTravelChat(String text)
    {
        String prefix = "[Fate Guardian] ";
        String content = text.startsWith(prefix)
            ? text.substring(prefix.length()) : text;
        ChatMessageBuilder message = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append(prefix)
            .append(ChatColorType.NORMAL).append(content);
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(message.build())
            .build());
    }

    private void writeTravelAudit(StrictModeAuditEntry entry) throws IOException
    {
        if (strictAuditLog == null) return;
        strictAuditLog.append(entry);
        updateStrictAuditPanel();
    }

    private void updateStrictAuditPanel()
    {
        panel.updateRecentPrevented(
            StrictModeAuditPresenter.recentPrevented(
                strictAuditLog == null ? null : strictAuditLog.recent(5)));
    }
    void pauseStrictModeForSixtySeconds()
    {
        strictPause.pauseFor(Duration.ofSeconds(60));
        updateStrictModePanel();
    }

    private void updateStrictModePanel()
    {
        panel.updateStrictMode(
            config.strictMode(), strictPause.isPaused(), strictPause.remainingSeconds(),
            strictModeReadiness().getReason());
    }

    /** Whether Strict Mode can act right now, from the same facts as its trust gate. */
    StrictModeReadiness strictModeReadiness()
    {
        FateLockedBundle current = bundle;
        Player local = client.getLocalPlayer();
        return StrictModeReadiness.evaluate(
            config.strictMode(),
            strictPause.isPaused(),
            current.getRules() != null && !current.isLegacyRules(),
            current.getRules() == null ? null : current.getRules().getAccount(),
            local == null ? null : local.getName(),
            strictTravelAccountMatches(current),
            rulesAreFresh());
    }
    /**
     * Whether the active rules are recent enough for Strict Mode to act on.
     * Tracker rules stay fresh while the relay keeps confirming them. File and
     * clipboard rules, and tracker rules no longer being checked, count from
     * when the tracker exported them, so an old file can never block anything.
     */
    boolean rulesAreFresh()
    {
        Instant now = Instant.now();
        RulesSource source = rulesSource;
        if (source == RulesSource.NONE) return false;
        if (source == RulesSource.RELAY && trackerPaired())
        {
            Instant confirmed = trackerLastSync();
            return confirmed != null
                && Duration.between(confirmed, now).compareTo(FRESH_RULES_WINDOW) < 0;
        }
        Instant exported = bundle.exportedAt();
        if (exported == null || exported.isAfter(now.plus(EXPORT_CLOCK_SKEW))) return false;
        return Duration.between(exported, now).compareTo(FRESH_RULES_WINDOW) < 0;
    }
    /**
     * Tag right-click menu entries whose target stands in a locked chunk with a
     * red (LOCKED) marker: the "are you sure?" before you ever click.
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!config.tagLockedMenus() && !config.tagLockedTeleports()) return;
        FateLockedBundle b = bundle;
        if (b.getRegionChunks().isEmpty()) return;

MenuEntry entry = event.getMenuEntry();
        GuardedAction action = guardedActionFactory.from(entry, client);
        boolean locked = false;
        if (config.tagLockedMenus()
            && action.getChunk() != null
            && action.getKind() != GuardedAction.Kind.TELEPORT)
        {
            locked = b.isLegacyRules()
                ? b.lockStateAt(action.getChunk()) == FateLockedBundle.LockState.LOCKED
                : ruleEngine(b).entry(action.getChunk()).getStatus() == PermissionStatus.LOCKED;
        }
        if (!locked && config.tagLockedTeleports()
            && action.getKind() == GuardedAction.Kind.TELEPORT
            && action.getChunk() != null)
        {
            locked = b.isLegacyRules()
                ? b.lockStateAt(action.getChunk()) == FateLockedBundle.LockState.LOCKED
                : ruleEngine(b).entry(action.getChunk()).getStatus() == PermissionStatus.LOCKED;
        }
        if (!locked) return;
        String t = entry.getTarget();
        String base = t == null ? "" : t;
        if (!base.contains("(LOCKED)"))
        {
            entry.setTarget(base + " <col=ef4444>(LOCKED)</col>");
        }
    }

    /** Chat line for entering a mapped chunk; {@code region} is never null. */
    private void announceEntry(CanonicalChunk chunk, String region, boolean unlocked)
    {
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append("Chunk ")
            .append("(" + chunk.getCx() + ", " + chunk.getCy() + ")");

        if (unlocked)
        {
            msg.append(ChatColorType.NORMAL).append(" · ")
               .append(ChatColorType.HIGHLIGHT).append(region)
               .append(ChatColorType.NORMAL).append(" ✓ unlocked");
        }
        else
        {
            msg.append(ChatColorType.NORMAL).append(" · ")
               .append(ChatColorType.HIGHLIGHT).append(region)
               .append(ChatColorType.NORMAL).append(" ⚠ LOCKED");
        }

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
    }

    /** At startup, use the newest backup file if there is one. */
    private void loadBackupFileAtStartup()
    {
        Path file = effectiveBundlePath();
        if (file == null)
        {
            // No file is not "no rules": keep the active tracker or clipboard
            // rules instead of replacing them with nothing.
            refreshPanel();
            return;
        }
        try
        {
            useBackupFile(FateLockedBundle.loadFromFile(gson, file), file);
        }
        catch (IOException | RuntimeException ex)
        {
            log.warn("Failed to load bundle at {}: {}", file, ex.getMessage());
            panel.flashStatus("import failed — using previous rules", false);
        }
        refreshPanel();
    }

    /**
     * The sidebar's "Load newest backup file": find and read the file once,
     * off the game thread, then switch to it on the client thread. Nothing
     * watches the folder, so a file that appears later changes nothing.
     */
    private void loadNewestBackupFile()
    {
        PluginSession started = session;
        executor.execute(started.guard(() -> {
            Path file = null;
            FateLockedBundle parsed;
            try
            {
                file = effectiveBundlePath();
                if (file == null)
                {
                    panel.flashStatus(
                        "no backup file in .runelite/fate-locked \u2014 rules unchanged", false);
                    return;
                }
                parsed = FateLockedBundle.loadFromFile(gson, file);
            }
            catch (IOException | RuntimeException ex)
            {
                log.warn("Failed to load backup file {}: {}", file, ex.getMessage());
                panel.flashStatus(
                    "couldn't read the backup file \u2014 rules unchanged", false);
                return;
            }
            Path loaded = file;
            clientThread.invoke(started.guard(() -> {
                useBackupFile(parsed, loaded);
                refreshPanel();
                panel.flashStatus(
                    "loaded backup file: " + parsed.getRegionChunks().size() + " regions",
                    true);
            }));
        }));
    }

    /** Switch to rules read from a backup file; the tracker's copy wins on its next check. */
    private void useBackupFile(FateLockedBundle parsed, Path file)
    {
        bundle = parsed;
        rulesSource = RulesSource.FILE;
        trackerRulesReplaced();
        log.info("Fate Locked bundle loaded from {}: {} regions, {} unlocked",
            file, parsed.getRegionChunks().size(), parsed.getUnlockedRegions().size());
    }

    /** A local import replaced the rules: the tracker's copy wins on its next check. */
    private void trackerRulesReplaced()
    {
        TrackerConnectionController controller = connectionController;
        if (controller != null)
        {
            controller.localRulesReplacedTrackerRules();
        }
    }

    /**
     * The backup file to read: the newest fate-locked-bundle-*.json in the
     * plugin's data dir under .runelite/. All file I/O is confined there (Hub
     * rule).
     */
    private Path effectiveBundlePath()
    {
        File[] files = dataDirectory().listFiles((d, name) ->
            name.startsWith("fate-locked-bundle") && name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) return null;
        File newest = null;
        for (File f : files)
        {
            if (newest == null || f.lastModified() > newest.lastModified()) newest = f;
        }
        return newest == null ? null : newest.toPath();
    }

    File dataDirectory()
    {
        return DATA_DIR;
    }

    /**
     * The hotkey and the sidebar's "Import from clipboard": read the
     * clipboard and import it as a bundle (on the client thread).
     */
    private void reimportFromClipboard()
    {
        String text;
        try
        {
            text = clipboardText();
        }
        catch (Exception ex)
        {
            panel.flashStatus("couldn't read clipboard", false);
            return;
        }
        if (text.isEmpty())
        {
            panel.flashStatus("clipboard empty", false);
            return;
        }
        importOnClientThread(text);
    }

    /** The clipboard's text, trimmed; empty when it holds no text. */
    String clipboardText() throws Exception
    {
        Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        return data == null ? "" : data.toString().trim();
    }

    /**
     * Imports read game state such as worn equipment, which RuneLite allows
     * only on the client thread. The guard is a Runnable, which keeps this
     * ClientThread.invoke(Runnable): the BooleanSupplier overload re-runs a
     * task that returns false on every client tick, so a failed import
     * would never stop.
     */
    private void importOnClientThread(String json)
    {
        clientThread.invoke(session.guard(() -> applyClipboardBundle(json)));
    }

    /** Load a bundle from JSON read from the clipboard. */
    private boolean applyClipboardBundle(String json)
    {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.matches("[0-9a-f]{32}"))
        {
            panel.flashStatus(
                "pairing code detected \u2014 use Connect tracker", false);
            return false;
        }
        FateLockedBundle previousBundle = bundle;
        RulesSource previousSource = rulesSource;
        try
        {
            FateLockedBundle parsed = FateLockedBundle.loadFromJson(gson, json);
            bundle = parsed;
            rulesSource = RulesSource.IMPORT;
            refreshPanel();
            panel.flashStatus(
                "imported " + parsed.getRegionChunks().size() + " regions", true);
            trackerRulesReplaced();
            log.info(
                "Fate Locked bundle imported from the clipboard: {} regions",
                parsed.getRegionChunks().size());
            return true;
        }
        catch (RuntimeException ex)
        {
            bundle = previousBundle;
            rulesSource = previousSource;
            // Every attempt shows its result: a success or a tracker sync may
            // have replaced the last failure message. Only the log is limited.
            if (invalidImportLimiter.shouldReport(
                trimmed, System.currentTimeMillis()))
            {
                log.warn("Clipboard bundle could not be parsed: {}", ex.getMessage());
            }
            panel.flashStatus("import failed — using previous rules", false);
            return false;
        }
    }

    enum RulesSource
    {
        /** Nothing imported this session. */
        NONE,
        /** A backup file from the data folder. */
        FILE,
        /** Read from the clipboard. */
        IMPORT,
        /** Delivered by the tracker relay. */
        RELAY
    }

    /**
     * Accept a relay bundle on the client thread only after strict v4 parsing
     * succeeds. Connection version, sync time, and acknowledgement remain owned
     * by TrackerConnectionController and change only after this returns true.
     */
    private boolean acceptRelayPayload(String payload)
    {
        final FateLockedBundle parsed;
        try
        {
            parsed = FateLockedBundle.loadFromJson(gson, payload);
            if (parsed.getVersion() != 4 || parsed.isLegacyRules())
            {
                return false;
            }
        }
        catch (RuntimeException ex)
        {
            log.debug("Relay bundle rejected: {}", ex.getMessage());
            return false;
        }

        FateLockedBundle previousBundle = bundle;
        RulesSource previousSource = rulesSource;
        try
        {
            bundle = parsed;
            rulesSource = RulesSource.RELAY;
            refreshPanel();
            panel.flashStatus(
                "synced " + parsed.getRegionChunks().size()
                    + " regions", true);
            log.info(
                "Fate Locked bundle imported from relay: {} regions",
                parsed.getRegionChunks().size());
            return true;
        }
        catch (RuntimeException ex)
        {
            bundle = previousBundle;
            rulesSource = previousSource;
            log.debug(
                "Relay bundle could not be applied: {}", ex.getMessage());
            return false;
        }
    }

    /** Recompute the player's current chunk and push everything to the panel. */
    private void refreshPanel()
    {
        CanonicalChunk current = null;
        Player local = client.getLocalPlayer();
        if (local != null && local.getWorldLocation() != null)
        {
            current = CanonicalChunk.of(local.getWorldLocation());
        }
        String trackerAccount = bundle.getRules() == null
            ? bundle.getState() == null
                ? null
                : bundle.getState().getLinkedAccount()
            : bundle.getRules().getAccount();
        panel.updateTrackerAccount(trackerAccount);
        panel.update(bundle, viewModelFor(bundle, current));
        // A fresh bundle may change unlocked tiers / areas — re-check worn gear,
        // the current slayer task, and the world-map markers.
        recomputeOverTierGear();
        recomputeSlayer();
        refreshWorldMapMarkers();
    }

    /** Place a click-to-jump marker on each authored area you haven't unlocked yet. */
    private void refreshWorldMapMarkers()
    {
        for (WorldMapPoint p : mapMarkers) worldMapPointManager.remove(p);
        mapMarkers.clear();
        if (!config.worldMapMarkers()) return;

        FateLockedBundle b = bundle;
        for (Map.Entry<String, Set<CanonicalChunk>> e : b.getSubAreaChunks().entrySet())
        {
            String area = e.getKey();
            if (b.isUnlocked(area)) continue; // only pin what's still locked

            Set<CanonicalChunk> chunks = e.getValue();
            if (chunks.isEmpty()) continue;
            long sx = 0, sy = 0;
            for (CanonicalChunk c : chunks) { sx += c.getCx(); sy += c.getCy(); }
            int cx = (int) (sx / chunks.size());
            int cy = (int) (sy / chunks.size());
            WorldPoint wp = new WorldPoint((cx << 6) + 32, (cy << 6) + 32, 0);

            WorldMapPoint point = new WorldMapPoint(wp, lockedPinImage());
            point.setName(area);
            point.setTooltip(area + " — LOCKED");
            point.setTarget(wp);
            point.setJumpOnClick(true);
            point.setSnapToEdge(false);
            point.setImagePoint(new Point(lockedPinImage().getWidth() / 2, lockedPinImage().getHeight() / 2));
            worldMapPointManager.add(point);
            mapMarkers.add(point);
        }
    }

    /** Small red lock-style pin, generated once. */
    private BufferedImage lockedPinImage()
    {
        if (lockedPinImage != null) return lockedPinImage;
        int s = 15;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(239, 68, 68, 235));
        g.fillOval(1, 1, s - 2, s - 2);
        g.setColor(new Color(20, 20, 20, 200));
        g.drawOval(1, 1, s - 2, s - 2);
        g.setColor(Color.WHITE);
        g.fillRect(s / 2 - 2, s / 2, 5, 4);          // lock body
        g.drawArc(s / 2 - 2, s / 2 - 3, 4, 5, 0, 180); // shackle
        g.dispose();
        lockedPinImage = img;
        return img;
    }

    // ── Infoboxes (keys / fate / unlock progress) ─────────────────────────────

    private void refreshInfoBoxes()
    {
        infoBoxManager.removeIf(b -> b instanceof FateLockedInfoBox);
        if (!config.showInfoBoxes()) return;

        infoBoxManager.addInfoBox(new FateLockedInfoBox(itemManager.getImage(KEYS_ICON_ITEM), this,
            new Color(245, 158, 11),
            () -> { FateLockedBundle.RunState s = bundle.getState(); return s == null ? "—" : String.valueOf(s.getKeys()); },
            () -> {
                FateLockedBundle.RunState s = bundle.getState();
                return s == null ? "Fate Locked keys"
                    : "Keys: " + s.getKeys() + " · Omni " + s.getSpecialKeys() + " · Chaos " + s.getChaosKeys();
            }));

        infoBoxManager.addInfoBox(new FateLockedInfoBox(discIcon(new Color(168, 85, 247)), this,
            new Color(196, 145, 255),
            () -> { FateLockedBundle.RunState s = bundle.getState(); return s == null ? "—" : String.valueOf(s.getFatePoints()); },
            () -> "Fate points"));

        infoBoxManager.addInfoBox(new FateLockedInfoBox(discIcon(new Color(52, 211, 153)), this,
            new Color(52, 211, 153),
            () -> {
                FateLockedBundle b = bundle;
                if (b.getTotalChunks() <= 0) return "—";
                return Math.round(100.0 * b.getUnlockedChunks() / b.getTotalChunks()) + "%";
            },
            () -> {
                FateLockedBundle b = bundle;
                return "Unlock progress: " + b.getUnlockedAreas() + "/" + b.getTotalAreas()
                    + " areas · " + b.getUnlockedChunks() + "/" + b.getTotalChunks() + " chunks";
            }));
    }

    /** A small filled-disc infobox icon in the given colour. */
    private static BufferedImage discIcon(Color c)
    {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c);
        g.fillOval(1, 1, 14, 14);
        g.setColor(new Color(0, 0, 0, 140));
        g.drawOval(1, 1, 14, 14);
        g.dispose();
        return img;
    }

    private void beginTrackerPairing()
    {
        boolean needsConsent = !connectionSettings.networkAccessAllowed();
        if (needsConsent && !panel.confirmNetworkConnection())
        {
            return;
        }
        PluginSession started = session;
        clientThread.invoke(started.guard(() -> {
            if (needsConsent)
            {
                try
                {
                    connectionSettings.allowNetworkAccess();
                }
                catch (RuntimeException error)
                {
                    panel.flashStatus("couldn't enable online sync", false);
                    return;
                }
            }
            if (!connectionSettings.networkAccessAllowed()) return;
            String url = connectionController.beginPairing();
            String code = connectionSettings.pairingCode();
            SwingUtilities.invokeLater(started.guard(
                () -> openTrackerPairing(started, url, code)));
        }));
    }

    private void openTrackerPairing(PluginSession started, String url, String code)
    {
        if (!connectionSettings.networkAccessAllowed()
            || !samePairing(code, connectionSettings.pairingCode()))
        {
            return;
        }
        try
        {
            launchTrackerBrowser(url);
        }
        catch (RuntimeException error)
        {
            clientThread.invoke(started.guard(() -> {
                if (!samePairing(code, connectionSettings.pairingCode()))
                {
                    return;
                }
                connectionController.reportBrowserLaunchFailure();
                panel.flashStatus(
                    "couldn't open the web tracker", false);
            }));
        }
    }

    void launchTrackerBrowser(String url)
    {
        LinkBrowser.browse(url);
    }

    private static boolean samePairing(String expected, String current)
    {
        return expected != null && expected.equals(current);
    }

    private static void wirePanelActions(
        FateLockedPanel target,
        Runnable onClipboardImport,
        Runnable onLoadBackupFile,
        Runnable onConnect)
    {
        target.setCallbacks(onClipboardImport, onLoadBackupFile, onConnect);
    }

    private static NavigationButton buildNavigationButton(FateLockedPanel target)
    {
        return NavigationButton.builder()
            .tooltip("Fate Locked Ironman")
            .icon(createIcon())
            .priority(7)
            .panel(target)
            .build();
    }

    private static BufferedImage createIcon()
    {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Key bow
        g.setColor(new Color(245, 158, 11));
        g.fillOval(2, 7, 11, 11);
        g.setColor(new Color(15, 17, 21));
        g.fillOval(5, 10, 5, 5);
        // Shaft + teeth
        g.setColor(new Color(245, 158, 11));
        g.fillRect(12, 11, 10, 3);
        g.fillRect(17, 14, 2, 4);
        g.fillRect(20, 14, 2, 4);
        g.dispose();
        return img;
    }

    private void startTrackerPoll()
    {
        // The lightweight scheduler tick keeps initial pairing responsive.
        // TrackerConnectionController gates actual relay requests to a
        // one-minute healthy cadence and backs off failures/rate limits.
        trackerPollFuture = executor.scheduleWithFixedDelay(
            session.guard(this::pollTrackerConnection), 2, 4, TimeUnit.SECONDS);
    }

    private void stopTrackerPoll()
    {
        if (trackerPollFuture != null)
        {
            trackerPollFuture.cancel(false);
            trackerPollFuture = null;
        }
        if (connectionController != null)
        {
            connectionController.stop();
        }
    }

    private boolean trackerPaired()
    {
        return connectionSettings != null
            && connectionSettings.networkAccessAllowed()
            && connectionSettings.isPaired();
    }

    private Instant trackerLastSync()
    {
        return connectionController == null
            ? null : connectionController.snapshot().getLastSync();
    }

    private void pollTrackerConnection()
    {
        TrackerConnectionController controller = connectionController;
        if (controller == null) return;
        controller.pollIfDue();
    }

    private void updatePanelRollInbox()
    {
        List<FateEvent> events = eventHistory == null
            ? java.util.Collections.<FateEvent>emptyList()
            : eventHistory.events();
        int needsReview = 0;
        for (FateEvent event : events)
        {
            if (event.getConfidence() == EventConfidence.UNCERTAIN) needsReview++;
        }
        shownWarningCount = activeWarningCount();
        panel.updateRollInboxStatus(
            events.size(), needsReview, shownWarningCount,
            historySaveFailed);
    }

    /** Keep the sidebar's Warnings count current as you move, change gear and get tasks. */
    private void refreshWarningCount()
    {
        if (activeWarningCount() != shownWarningCount) updatePanelRollInbox();
    }

    private int activeWarningCount()
    {
        int warnings = 0;
        if (lastLockState == FateLockedBundle.LockState.LOCKED) warnings++;
        if (slayerTaskWarn != null && !slayerTaskWarn.trim().isEmpty()) warnings++;
        if (overTierSummary != null && !overTierSummary.trim().isEmpty()) warnings++;
        return warnings;
    }
}
