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
import java.util.Collections;
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

    /** This start's way onto the client thread; closed while the plugin is off. */
    private volatile ClientThreadGate gate = ClientThreadGate.closed();
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

    /**
     * The rules in force and where they came from, which decides their
     * freshness (see rulesAreFresh). Replaced only by switchRules.
     */
    private volatile ActiveRules active = ActiveRules.NONE;
    private final ChunkPanelViewModelFactory chunkPanelFactory =
        new ChunkPanelViewModelFactory();
    private final GuardedActionFactory guardedActionFactory = new GuardedActionFactory();
    private final StrictModeClickHandler strictClickHandler =
        new StrictModeClickHandler(new StrictModeGuard());
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

    /** The rules in force; the overlays and the HUD read them from here. */
    public FateLockedBundle getBundle()
    {
        return active.getBundle();
    }

    @Override
    protected void startUp()
    {
        // startUp runs on the Swing thread: everything that reads the game or
        // changes plugin state waits for the client thread, through the gate.
        ClientThreadGate started = new ClientThreadGate(clientThread, new PluginSession());
        gate = started;
        connectionSettings.clearLegacySettings();
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
            started::run,
            relayImporter,
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
        Runnable pauseStrictMode = () -> started.run(this::pauseStrictModeForSixtySeconds);
        travelBlockOverlay = new FateLockedTravelBlockOverlay(
            travelNoticeStore,
            config::strictMode,
            strictPause::isPaused,
            pauseStrictMode);

        overlayManager.add(worldMapOverlay);
        overlayManager.add(sceneOverlay);
        overlayManager.add(minimapOverlay);
        overlayManager.add(hudOverlay);
        overlayManager.add(contentOverlay);
        overlayManager.add(flashOverlay);
        travelBlockOverlay.setPauseGuardian(pauseStrictMode);
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
            pauseStrictMode,
            () -> started.run(() -> { strictPause.resume(); updateStrictModePanel(); }),
            () -> configManager.setConfiguration(
                FateLockedConfig.GROUP, "strictModeIntroSeen", true));
        panel.setRollInboxLink(FateLockedPanel.TRACKER_URL);
        panel.updateConnection(connectionController.snapshot());
        navButton = buildNavigationButton(panel);
        clientToolbar.addNavigation(navButton);

        started.run(() -> {
            startSessionTracking();
            updateStrictModePanel();
            updateStrictAuditPanel();
            updatePanelRollInbox();
            refreshInfoBoxes();
        });
        loadBackupFile(false);
        keyManager.registerKeyListener(reimportHotkey);
        startTrackerPoll();
    }

    @Override
    protected void shutDown()
    {
        // First, so nothing this start queued can bring rules back later.
        gate.close();
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
        worldMapPointManager.removeIf(LockedAreaPoint.class::isInstance);
        infoBoxManager.removeIf(b -> b instanceof FateLockedInfoBox);
        active = ActiveRules.NONE;
        lastChunk = null;
    }

    /**
     * RuneLite posts ConfigChanged on the thread that changed the setting:
     * the Swing thread for the sidebar and the config panel. The sidebar
     * control updates there; everything else waits for the client thread.
     */
    @Subscribe
    public void onConfigChanged(ConfigChanged ev)
    {
        if (!FateLockedConfig.GROUP.equals(ev.getGroup())) return;
        panel.refreshConfig(ev.getKey());
        String key = ev.getKey();
        gate.run(() -> applyConfigChange(key));
    }

    private void applyConfigChange(String key)
    {
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
        String locked = lockedSlayerTask(getBundle());
        slayerTaskWarn = locked;
        warnLockedSlayerTask(locked);
    }

    /** The current slayer task if these rules put its monster only in locked chunks, else null. */
    private String lockedSlayerTask(FateLockedBundle rules)
    {
        String task = slayerTask;
        if (!config.warnLockedSlayer() || task == null || task.isEmpty())
        {
            return null;
        }
        // Reachable or unknown: no warning.
        return rules.monsterReach(task) == FateLockedBundle.Reach.LOCKED ? task : null;
    }

    /** Say once per assignment that the task is in a locked area. */
    private void warnLockedSlayerTask(String locked)
    {
        if (locked == null || locked.equalsIgnoreCase(slayerWarnedFor))
        {
            return;
        }
        slayerWarnedFor = locked;
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append("Your slayer task (")
            .append(ChatColorType.HIGHLIGHT).append(locked)
            .append(ChatColorType.NORMAL).append(") is in a locked area.");
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
        notifyIfEnabled("Slayer task (" + locked + ") is in a locked area");
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded ev)
    {
        if (ev.getGroupId() == 408) minigameCompletionDetector.onPestControlWidget(System.currentTimeMillis());
        // Locked-bank warning is independent of the roll-nudge toggle.
        if ((ev.getGroupId() == BANK_GROUP_ID || ev.getGroupId() == DEPOSIT_BOX_GROUP_ID)
            && config.warnLockedBank() && getBundle().banksLocked())
        {
            warnLockedBankIfNeeded();
        }

        if (ev.getGroupId() == QUEST_COMPLETED_GROUP_ID)
        {
            // The scroll's text arrives after the widget loads.
            gate.runNextTick(() ->
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
            });
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
        FateLockedBundle rules = getBundle();
        String where;
        if (rules.isLegacyRules())
        {
            if (rules.isBankUnlocked(chunk)) return;
            String label = rules.labelAt(chunk);
            where = label == null ? "This bank" : label + " bank";
        }
        else
        {
            RuleDecision decision = ruleEngine(rules).target(chunk, "BANK", "");
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
        FateLockedBundle currentBundle = getBundle();
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
        List<OverTierItem> over = overTierGear(getBundle());
        overTierSummary = overTierSummary(over);
        warnOverTierGear(over);
    }

    /** Worn items these rules put above their slot's unlocked tier. */
    private List<OverTierItem> overTierGear(FateLockedBundle rules)
    {
        if (!config.warnOverTierGear()) return Collections.emptyList();
        Map<String, Integer> tiers = rules.getItemTiers();
        FateLockedBundle.RunState st = rules.getState();
        Map<String, Integer> equip = st == null ? null : st.getEquipment();
        // A bundle without tier data leaves the feature dormant.
        if (tiers.isEmpty() || equip == null) return Collections.emptyList();

        ItemContainer eq = client.getItemContainer(InventoryID.WORN);
        if (eq == null) return Collections.emptyList();

        List<OverTierItem> over = new ArrayList<>();
        for (Map.Entry<EquipmentInventorySlot, String> e : SLOT_NAMES.entrySet())
        {
            Item item = eq.getItem(e.getKey().getSlotIdx());
            if (item == null || item.getId() <= 0) continue;
            Integer tier = tiers.get(String.valueOf(item.getId()));
            if (tier == null) continue; // unknown item — don't flag
            int unlocked = equip.getOrDefault(e.getValue(), 0);
            if (tier <= unlocked) continue;
            over.add(new OverTierItem(item.getId(), e.getValue(), tier, unlocked));
        }
        return over;
    }

    /** The HUD's list of over-tier slots, or null when there are none. */
    private static String overTierSummary(List<OverTierItem> over)
    {
        if (over.isEmpty()) return null;
        List<String> slots = new ArrayList<>();
        for (OverTierItem item : over) slots.add(item.slot);
        return String.join(", ", slots);
    }

    /** Say once per session that each over-tier item is above its tier. */
    private void warnOverTierGear(List<OverTierItem> over)
    {
        for (OverTierItem item : over)
        {
            if (!warnedOverTier.add(item.itemId)) continue;
            String name = itemManager.getItemComposition(item.itemId).getName();
            ChatMessageBuilder msg = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
                .append(ChatColorType.NORMAL).append(name)
                .append(" is T" + item.tier + " but your " + item.slot + " is only unlocked to T" + item.unlocked + ".");
            chatMessageManager.queue(QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(msg.build())
                .build());
            notifyIfEnabled(name + " is above your unlocked " + item.slot + " tier");
        }
    }

    /** One worn item above its slot's unlocked tier. */
    private static final class OverTierItem
    {
        final int itemId;
        final String slot;
        final int tier;
        final int unlocked;

        OverTierItem(int itemId, String slot, int tier, int unlocked)
        {
            this.itemId = itemId;
            this.slot = slot;
            this.tier = tier;
            this.unlocked = unlocked;
        }
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
        FateLockedBundle.RunState st = getBundle().getState();
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
        FateLockedBundle b = getBundle();
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
        FateLockedBundle current = getBundle();
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
        FateLockedBundle current = getBundle();
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
        ActiveRules current = active;
        RulesSource source = current.getSource();
        if (source == RulesSource.NONE) return false;
        if (source == RulesSource.RELAY && trackerPaired())
        {
            Instant confirmed = trackerLastSync();
            return confirmed != null
                && Duration.between(confirmed, now).compareTo(FRESH_RULES_WINDOW) < 0;
        }
        Instant exported = current.getBundle().exportedAt();
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
        FateLockedBundle b = getBundle();
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

    /** The sidebar's "Load newest backup file". */
    private void loadNewestBackupFile()
    {
        loadBackupFile(true);
    }

    /**
     * Find and read the newest backup file once, off the game thread, then
     * switch to it on the client thread. At startup ({@code explicit} is
     * false) a missing file says nothing, since no file is not "no rules".
     * Nothing watches the folder, so a file that appears later changes
     * nothing.
     */
    private void loadBackupFile(boolean explicit)
    {
        ClientThreadGate onClient = gate;
        executor.execute(onClient.guard(() -> {
            Path file = null;
            FateLockedBundle parsed;
            try
            {
                file = effectiveBundlePath();
                if (file == null)
                {
                    if (explicit)
                    {
                        panel.flashStatus(
                            "no backup file in .runelite/fate-locked — rules unchanged", false);
                    }
                    onClient.run(this::refreshPanel);
                    return;
                }
                parsed = FateLockedBundle.loadFromFile(gson, file);
            }
            catch (IOException | RuntimeException ex)
            {
                log.warn("Failed to load backup file {}: {}", file, ex.getMessage());
                panel.flashStatus(
                    "couldn't read the backup file — rules unchanged", false);
                onClient.run(this::refreshPanel);
                return;
            }
            Path loaded = file;
            onClient.run(() -> useBackupFile(parsed, loaded, explicit));
        }));
    }

    /** Switch to rules read from a backup file; the tracker's copy wins on its next check. */
    private void useBackupFile(FateLockedBundle parsed, Path file, boolean explicit)
    {
        if (!switchRules(parsed, RulesSource.FILE))
        {
            panel.flashStatus("couldn't read the backup file — rules unchanged", false);
            return;
        }
        trackerRulesReplaced();
        log.info("Fate Locked bundle loaded from {}: {} regions, {} unlocked",
            file, parsed.getRegionChunks().size(), parsed.getUnlockedRegions().size());
        if (explicit)
        {
            panel.flashStatus(
                "loaded backup file: " + parsed.getRegionChunks().size() + " regions", true);
        }
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
     * clipboard here, parse it on RuneLite's executor, and switch to it on
     * the client thread.
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
        importClipboardText(text);
    }

    /** The clipboard's text, trimmed; empty when it holds no text. */
    String clipboardText() throws Exception
    {
        Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        return data == null ? "" : data.toString().trim();
    }

    /**
     * A full bundle takes a noticeable time to parse, so that happens off
     * the game thread. The switch reads game state such as worn equipment,
     * which RuneLite allows only on the client thread, so it waits for the
     * gate, which runs each import once.
     */
    private void importClipboardText(String json)
    {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.matches("[0-9a-f]{32}"))
        {
            panel.flashStatus(
                "pairing code detected — use Connect tracker", false);
            return;
        }
        ClientThreadGate onClient = gate;
        executor.execute(onClient.guard(() -> {
            FateLockedBundle parsed;
            try
            {
                parsed = FateLockedBundle.loadFromJson(gson, json);
            }
            catch (RuntimeException ex)
            {
                // Every attempt shows its result: a success or a tracker sync may
                // have replaced the last failure message. Only the log is limited.
                if (invalidImportLimiter.shouldReport(
                    trimmed, System.currentTimeMillis()))
                {
                    log.warn("Clipboard bundle could not be parsed: {}", ex.getMessage());
                }
                panel.flashStatus("import failed — using previous rules", false);
                return;
            }
            onClient.run(() -> useClipboardRules(parsed));
        }));
    }

    /** On the client thread: switch to rules read from the clipboard. */
    private void useClipboardRules(FateLockedBundle parsed)
    {
        if (!switchRules(parsed, RulesSource.IMPORT))
        {
            panel.flashStatus("import failed — using previous rules", false);
            return;
        }
        panel.flashStatus(
            "imported " + parsed.getRegionChunks().size() + " regions", true);
        trackerRulesReplaced();
        log.info(
            "Fate Locked bundle imported from the clipboard: {} regions",
            parsed.getRegionChunks().size());
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
     * The relay's rules arrive in two steps. Connection version, sync time
     * and acknowledgement stay with TrackerConnectionController, which
     * changes them only after the second step returns true.
     */
    private final TrackerConnectionController.RelayBundleImporter<FateLockedBundle> relayImporter =
        new TrackerConnectionController.RelayBundleImporter<FateLockedBundle>()
        {
            @Override
            public FateLockedBundle prepare(String payload)
            {
                return parseRelayPayload(payload);
            }

            @Override
            public boolean commit(FateLockedBundle rules)
            {
                return acceptRelayRules(rules);
            }
        };

    /**
     * On the thread that read the relay's reply, never the game thread: parse
     * the payload, accepting only strict v4 rules. Null rejects it.
     */
    private FateLockedBundle parseRelayPayload(String payload)
    {
        try
        {
            FateLockedBundle parsed = FateLockedBundle.loadFromJson(gson, payload);
            return parsed.getVersion() == 4 && !parsed.isLegacyRules() ? parsed : null;
        }
        catch (RuntimeException ex)
        {
            log.debug("Relay bundle rejected: {}", ex.getMessage());
            return null;
        }
    }

    /** On the client thread: switch to rules the relay sent. */
    private boolean acceptRelayRules(FateLockedBundle parsed)
    {
        if (!switchRules(parsed, RulesSource.RELAY))
        {
            return false;
        }
        panel.flashStatus(
            "synced " + parsed.getRegionChunks().size()
                + " regions", true);
        log.info(
            "Fate Locked bundle imported from relay: {} regions",
            parsed.getRegionChunks().size());
        return true;
    }

    /**
     * Switch to new rules. Everything they change is worked out from the
     * candidate first, then the rules and their source are swapped at once,
     * then each change is shown on its own. A failure while working them out
     * leaves everything as it was; a failure while showing one change is
     * logged, and neither undoes the switch nor stops the others.
     */
    private boolean switchRules(FateLockedBundle candidate, RulesSource source)
    {
        RulesEffects effects;
        try
        {
            effects = effectsOf(candidate);
        }
        catch (RuntimeException ex)
        {
            log.warn("New rules could not be applied: {}", ex.getMessage());
            return false;
        }
        active = new ActiveRules(candidate, source);
        show(candidate, effects);
        return true;
    }

    /** Recompute the player's current chunk and show everything the active rules mean. */
    private void refreshPanel()
    {
        FateLockedBundle current = getBundle();
        show(current, effectsOf(current));
    }

    /**
     * What a rule set means for the sidebar, the HUD, the map and the
     * warnings. Reads the game, so it runs on the client thread, but changes
     * nothing.
     */
    private RulesEffects effectsOf(FateLockedBundle rules)
    {
        CanonicalChunk current = null;
        Player local = client.getLocalPlayer();
        if (local != null && local.getWorldLocation() != null)
        {
            current = CanonicalChunk.of(local.getWorldLocation());
        }
        return new RulesEffects(
            viewModelFor(rules, current),
            overTierGear(rules),
            lockedSlayerTask(rules),
            lockedAreaPins(rules));
    }

    /** Show what the rules mean: the HUD fields, then each other change on its own. */
    private void show(FateLockedBundle rules, RulesEffects effects)
    {
        overTierSummary = overTierSummary(effects.overTierGear);
        slayerTaskWarn = effects.lockedSlayerTask;
        showIsolated("sidebar", () -> {
            panel.updateTrackerAccount(trackerAccount(rules));
            panel.update(rules, effects.view);
        });
        showIsolated("world map pins", () -> placeLockedAreaPins(effects.pins));
        showIsolated("gear warning", () -> warnOverTierGear(effects.overTierGear));
        showIsolated("Slayer warning", () -> warnLockedSlayerTask(effects.lockedSlayerTask));
    }

    private void showIsolated(String what, Runnable change)
    {
        try
        {
            change.run();
        }
        catch (RuntimeException ex)
        {
            log.warn("Could not update the {}: {}", what, ex.getMessage());
        }
    }

    /** The account the tracker profile is bound to, from the rules or the older run state. */
    private static String trackerAccount(FateLockedBundle rules)
    {
        if (rules.getRules() != null) return rules.getRules().getAccount();
        return rules.getState() == null ? null : rules.getState().getLinkedAccount();
    }

    /** Everything a rule set means, worked out before anything changes. */
    private static final class RulesEffects
    {
        final ChunkPanelViewModel view;
        final List<OverTierItem> overTierGear;
        final String lockedSlayerTask;
        final List<WorldMapPoint> pins;

        RulesEffects(
            ChunkPanelViewModel view,
            List<OverTierItem> overTierGear,
            String lockedSlayerTask,
            List<WorldMapPoint> pins)
        {
            this.view = view;
            this.overTierGear = overTierGear;
            this.lockedSlayerTask = lockedSlayerTask;
            this.pins = pins;
        }
    }

    /** Place a click-to-jump marker on each authored area you haven't unlocked yet. */
    private void refreshWorldMapMarkers()
    {
        placeLockedAreaPins(lockedAreaPins(getBundle()));
    }

    private void placeLockedAreaPins(List<WorldMapPoint> pins)
    {
        worldMapPointManager.removeIf(LockedAreaPoint.class::isInstance);
        for (WorldMapPoint pin : pins)
        {
            worldMapPointManager.add(pin);
        }
    }

    /** A pin for each authored area these rules leave locked, if pins are on. */
    private List<WorldMapPoint> lockedAreaPins(FateLockedBundle rules)
    {
        if (!config.worldMapMarkers()) return Collections.emptyList();

        List<WorldMapPoint> pins = new ArrayList<>();
        for (Map.Entry<String, Set<CanonicalChunk>> e : rules.getSubAreaChunks().entrySet())
        {
            String area = e.getKey();
            if (rules.isUnlocked(area)) continue; // only pin what's still locked

            Set<CanonicalChunk> chunks = e.getValue();
            if (chunks.isEmpty()) continue;
            long sx = 0, sy = 0;
            for (CanonicalChunk c : chunks) { sx += c.getCx(); sy += c.getCy(); }
            int cx = (int) (sx / chunks.size());
            int cy = (int) (sy / chunks.size());
            WorldPoint wp = new WorldPoint((cx << 6) + 32, (cy << 6) + 32, 0);

            WorldMapPoint point = new LockedAreaPoint(wp, lockedPinImage());
            point.setName(area);
            point.setTooltip(area + " — LOCKED");
            point.setTarget(wp);
            point.setJumpOnClick(true);
            point.setSnapToEdge(false);
            point.setImagePoint(new Point(lockedPinImage().getWidth() / 2, lockedPinImage().getHeight() / 2));
            pins.add(point);
        }
        return pins;
    }

    /** A world-map pin this plugin placed, so it can remove exactly its own. */
    static final class LockedAreaPoint extends WorldMapPoint
    {
        LockedAreaPoint(WorldPoint point, BufferedImage image)
        {
            super(point, image);
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
            () -> { FateLockedBundle.RunState s = getBundle().getState(); return s == null ? "—" : String.valueOf(s.getKeys()); },
            () -> {
                FateLockedBundle.RunState s = getBundle().getState();
                return s == null ? "Fate Locked keys"
                    : "Keys: " + s.getKeys() + " · Omni " + s.getSpecialKeys() + " · Chaos " + s.getChaosKeys();
            }));

        infoBoxManager.addInfoBox(new FateLockedInfoBox(discIcon(new Color(168, 85, 247)), this,
            new Color(196, 145, 255),
            () -> { FateLockedBundle.RunState s = getBundle().getState(); return s == null ? "—" : String.valueOf(s.getFatePoints()); },
            () -> "Fate points"));

        infoBoxManager.addInfoBox(new FateLockedInfoBox(discIcon(new Color(52, 211, 153)), this,
            new Color(52, 211, 153),
            () -> {
                FateLockedBundle b = getBundle();
                if (b.getTotalChunks() <= 0) return "—";
                return Math.round(100.0 * b.getUnlockedChunks() / b.getTotalChunks()) + "%";
            },
            () -> {
                FateLockedBundle b = getBundle();
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
        ClientThreadGate onClient = gate;
        onClient.run(() -> {
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
            SwingUtilities.invokeLater(onClient.guard(
                () -> openTrackerPairing(onClient, url, code)));
        });
    }

    private void openTrackerPairing(ClientThreadGate onClient, String url, String code)
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
            onClient.run(() -> {
                if (!samePairing(code, connectionSettings.pairingCode()))
                {
                    return;
                }
                connectionController.reportBrowserLaunchFailure();
                panel.flashStatus(
                    "couldn't open the web tracker", false);
            });
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
            gate.guard(this::pollTrackerConnection), 2, 4, TimeUnit.SECONDS);
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
