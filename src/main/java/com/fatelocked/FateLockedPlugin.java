package com.fatelocked;

import com.google.gson.Gson;
import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.events.FateEventFactory;
import com.fatelocked.events.FateEvent;
import com.fatelocked.rules.ChunkPermissionRow;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.ItemTier;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.Trust;
import com.fatelocked.rules.UnlockNews;
import com.fatelocked.panel.LocalTimeText;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.GameFacts;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.HerePresenter;
import com.fatelocked.sidebar.PointTarget;
import com.fatelocked.sidebar.LockedThings;
import com.fatelocked.sidebar.PointerText;
import com.fatelocked.sidebar.RollInboxPresenter;
import com.fatelocked.sidebar.RowChecks;
import com.fatelocked.sidebar.StrictModeSectionPresenter;
import com.fatelocked.ui.Art;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Terms;
import com.fatelocked.guardian.GuardedAction;
import com.fatelocked.guardian.GuardedActionFactory;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModePause;
import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeAuditLog;
import com.fatelocked.guardian.StrictModeAuditPresenter;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.guardian.StrictModeStatusView;
import com.fatelocked.guardian.travel.RuneLiteTravelAvailability;
import com.fatelocked.guardian.travel.IntentClassifier;
import com.fatelocked.guardian.travel.TravelMatch;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.fatelocked.detection.DetectionTables;
import com.fatelocked.detection.Detectors;
import com.fatelocked.detection.DiaryTiers;
import com.fatelocked.detection.RollReminder;
import com.fatelocked.detection.Signal;
import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.detectors.PetDropDetector;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WorldType;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.task.Schedule;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Duration;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
    name = "Fate Locked Ironman",
    description = "Your Fate Locked run in game: locked areas, warnings, a Roll inbox and optional Strict Mode",
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
    @Inject private FateLockedFlashOverlay flashOverlay;
    @Inject private FateLockedUnlockOverlay unlockOverlay;
    @Inject private FateLockedOutlineOverlay outlineOverlay;
    @Inject private ChatMessageManager chatMessageManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private FateLockedPanel panel;
    @Inject private Gson gson;
    @Inject private ScheduledExecutorService executor;
    @Inject private ItemManager itemManager;
    @Inject private Notifier notifier;
    @Inject private WorldMapPointManager worldMapPointManager;
    @Inject private PluginManager pluginManager;
    @Inject private EventBus eventBus;
    @Inject private InfoBoxManager infoBoxManager;
    @Inject private SpriteManager spriteManager;
    @Inject private KeyManager keyManager;
    @Inject private MouseManager mouseManager;
    @Inject private OkHttpClient okHttpClient;
    @Inject private ConfigManager configManager;
    @Inject private TrackerConnectionSettings connectionSettings;

    /** This start's way onto the client thread; closed while the plugin is off. */
    private volatile ClientThreadGate gate = ClientThreadGate.closed();
    /** Every local file write runs here, in order, off the game thread. */
    private final SerialFileWriter fileWriter =
        new SerialFileWriter(task -> executor.execute(task));
    /** The last accepted rules, kept for the next start; opened by startUp. */
    private SavedRulesStore savedRules;
    private ScheduledFuture<?> trackerPollFuture;
    private TrackerConnectionController connectionController;
    private final RepeatedValueLimiter invalidImportLimiter =
        new RepeatedValueLimiter(TimeUnit.SECONDS.toMillis(30));
    /** Logs each kind of failed tracker tick, and a repeat at most every 15 minutes. */
    /** The skill tier line, once a minute for each thing clicked. */
    private final RepeatedValueLimiter tierChatLimiter =
        new RepeatedValueLimiter(TimeUnit.MINUTES.toMillis(1));
    /** What each chunk's rows say about its things, for the decisions they were read from. */
    private final Map<CanonicalChunk, LockedThings> lockedThings = new HashMap<>();
    private DecisionService lockedThingsFor;
    private final RepeatedValueLimiter trackerTickFailureLimiter =
        new RepeatedValueLimiter(TimeUnit.MINUTES.toMillis(15));
    /** The logged-in account's events; read on the Swing thread too, for Copy for tracker. */
    private volatile DetectedEventStore detectedEvents;
    /** What the Roll inbox's last copy did, until a new event or a dismissal; null for nothing. */
    private volatile String rollInboxNotice;
    private boolean historySaveFailed;
    private StrictModeAuditLog strictAuditLog;
    /** The account the history, audit log and memories belong to. */
    private long accountFilesHash;
    private final FateEventFactory eventFactory = new FateEventFactory();
    private final PetDropDetector petDropDetector = new PetDropDetector();
    /** The logged-in account's finished quests and diary tiers; null until its files open, or unread. */
    private FinishedMemory questMemory;
    private FinishedMemory tierMemory;
    /** What the memories hold, as last read or written, so only a change is written. */
    private Set<String> questsRemembered;
    private Set<String> tiersRemembered;
    /**
     * The logged-in character's detectors for this session (Stage 4); null until the session's
     * first reading, once its files are open and detection is on.
     */
    private Detectors detectors;
    /** Whether this session's first reading of levels, diaries, quests and Slayer is still to come. */
    private boolean sessionReadingDue = true;
    /** Whether the quests are to be read again: the quest scroll showed. */
    private boolean questReadingDue;
    /** The tick the quests were last read on. */
    private int questsReadTick;
    /** Whether a Slayer variable changed since the last reading. */
    private boolean slayerReadingDue;
    /** Whether the game's notification popup has started, as RuneLite's Screenshot plugin reads it. */
    private boolean notificationStarted;
    /** Optional hotkey, unset by default: pause Strict Mode for 60 seconds (B16). */
    private final HotkeyListener pauseStrictHotkey = new HotkeyListener(() -> config.pauseStrictModeHotkey())
    {
        @Override
        public void hotkeyPressed()
        {
            ClientThreadGate onClient = gate;
            if (onClient != null) onClient.run(FateLockedPlugin.this::pauseStrictModeFromHotkey);
        }
    };
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
    /**
     * The one reader of the active rules, for the logged-in character.
     * Rebuilt on the client thread when the rules or the character change;
     * overlays read it through this volatile field.
     */
    private volatile DecisionService decisions = DecisionService.create(RulesSnapshot.empty(), null, null);
    /** Counts scene loads, so overlays work a scene out once (U3). */
    private volatile int sceneGeneration;
    /** The loaded scene as the rules draw it, for the game view and the minimap. Client thread. */
    private final SceneEdgesCache sceneEdgesCache = new SceneEdgesCache();
    /** The colours the colour settings choose; overlays read it through this volatile field. */
    private volatile Palette palette = Palette.defaults();
    /** The parts of Here the player left open, as their profile keeps them: not a setting. */
    static final String HERE_OPEN = "hereOpen";
    /** The settings that change the palette. */
    private static final Set<String> PALETTE_KEYS = new HashSet<>(Arrays.asList(
        "colourPreset", "unlockedColor", "frontierColor", "lockedColor"));
    /** The bound account and character the decision service was built for (client thread). */
    private String decisionsBound = "";
    private String decisionsPlayer = "";
    /** What the HUD shows, worked out each tick; the overlay draws it as it is (E6). */
    private volatile HudModel hudModel = HudModel.NONE;
    // The nearest bank and shop, and the rules and chunk they were found for.
    private DecisionService nearestDecisions;
    private CanonicalChunk nearestChunk;
    private FateLockedBundle.Nearest nearestBank;
    private FateLockedBundle.Nearest nearestShop;
    /** The sidebar's models, posted only when they change (C7, A9). */
    private SidebarPublisher sidebarModels;
    private final HerePresenter herePresenter = new HerePresenter();
    /** Here, as last worked out, for the decision service and chunk it was worked out for. */
    /** Two tiles, in local units: close enough to the thing the arrow points at. */
    static final int POINTER_REACHED = 2 * Perspective.LOCAL_TILE_SIZE;
    /** The arrow the Here card put up, or null. Client thread. */
    private Pointer pointer;
    /** Something the arrow's row names has loaded since the last tick, so it's looked for. Client thread. */
    private boolean pointerLooks;
    /** Shortest Path, asked for the way to a spot seen before when it runs; made on first use. */
    private ShortestPathHandOff shortestPath;
    /** Where what the Here card can point at was seen, for the way back to one out of sight. */
    private final SpotMemory spots = new SpotMemory();
    /** Spots seen are saved at most this often, in ticks: half a minute. */
    static final int SPOTS_SAVE_TICKS = 50;
    private int spotsSavedTick;
    /** What each chunk's card can point at, for the rules in {@link #pointablesFor}. Client thread. */
    private final Map<CanonicalChunk, Pointables> pointables = new HashMap<>();
    private DecisionService pointablesFor;
    private HereModel hereModel;
    private DecisionService hereDecisions;
    private CanonicalChunk hereChunk;
    private GameFacts hereFacts = GameFacts.NONE;
    /** The game's facts as last read, and for which tick, chunk and rules. */
    private GameFacts lastFacts = GameFacts.NONE;
    private int factsTick = -1;
    private CanonicalChunk factsChunk;
    private DecisionService factsDecisions;
    /** What Strict Mode stopped lately, newest first, for its section. */
    private List<String> recentStopped = java.util.Collections.emptyList();
    private final GuardedActionFactory guardedActionFactory = new GuardedActionFactory();
    /** What a click is, by id in the tracker's travel table: Strict Mode and the tags read the same answer (F4). */
    private final IntentClassifier intentClassifier = new IntentClassifier();
    /** Where the player and menu targets are, for the client this plugin reads (B14). */
    private volatile ChunkLocator chunkLocator;
    private MenuFactsReader menuFactsReader;
    private final StrictModeClickHandler strictClickHandler =
        new StrictModeClickHandler(new StrictModeGuard());
    private final StrictModePause strictPause = new StrictModePause(System::nanoTime);
    /** Strict Mode's status as last worked out, for the HUD; null until then. */
    @Getter private volatile StrictModeStatusView strictModeStatus;
    private TravelRuleEvaluator travelRuleEvaluator;
    private TravelAvailability travelAvailability;
    private TravelAlternativeFinder travelAlternativeFinder;
    private TravelBlockNoticeStore travelNoticeStore;
    private TravelGuardianCoordinator travelGuardianCoordinator;
    private TravelGuardianPluginShell travelGuardianShell;
    private FateLockedTravelBlockOverlay travelBlockOverlay;
    private TravelGuardianOverlayLifecycle travelOverlayLifecycle;

    /** No locked-area fade is showing. */
    static final long NO_FADE = Long.MIN_VALUE;
    /** When the locked-area fade began, on the monotonic clock ({@link System#nanoTime}); NO_FADE for none. */
    @Getter private volatile long lockedFadeAt = NO_FADE;
    /** When the new unlock banner began, on the same clock; NO_FADE for none. */
    @Getter private volatile long unlockShownAt = NO_FADE;
    /** What the last sync opened, for the banner. */
    @Getter private volatile UnlockNews unlockNews = UnlockNews.NONE;
    /** Chunks a sync opened that the player hasn't stood in since, for the world map's glow. */
    @Getter private volatile List<CanonicalChunk> glowing = Collections.emptyList();
    /** What stepping into a chunk says: a line per area, and the locked alert once per area (U10). */
    private final LockedAreaAlerts areaAlerts = new LockedAreaAlerts();

    private CanonicalChunk lastChunk;
    /** The decision for the chunk the player is in, for the sidebar's warnings. */
    private PermissionStatus lastStatus;
    /** Warnings count the sidebar shows, so each change is sent to it once. */
    private int shownWarningCount = -1;
    /** Why the Roll inbox says nothing is noticed, as it shows it; null while something is. */
    private String shownQuiet;
    private NavigationButton navButton;

    /** The quests are read again this often, in ticks (a minute), for any finished without the quest scroll. */
    static final int QUEST_READING_TICKS = 100;
    /** The Slayer variables a task's reading is made of, as RuneLite's Slayer plugin watches them. */
    private static final Set<Integer> SLAYER_VARPS = new HashSet<>(Arrays.asList(VarPlayerID.SLAYER_COUNT,
        VarPlayerID.SLAYER_TARGET, VarPlayerID.SLAYER_COUNT_ORIGINAL, VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED));
    private static final Set<Integer> SLAYER_VARBITS = new HashSet<>(Arrays.asList(VarbitID.SLAYER_MASTER,
        VarbitID.SLAYER_TASKS_COMPLETED, VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, VarbitID.SLAYER_TARGET_BOSSID,
        VarbitID.SLAYER_MODIFIER_ID, VarbitID.SLAYER_MODIFIER_VALUE, VarbitID.SLAYER_MODIFIER_NEGATIVE));
    /** The game's Slayer task id for a boss task, from its helper script. */
    private static final int SLAYER_BOSS_TASK = 98;
    /** Widget group shown when a quest is completed (the reward scroll). */
    private static final int QUEST_COMPLETED_GROUP_ID = 153;
    /** Interface group ids for the bank (12) and deposit box (192) — stable
     *  numeric ids, used raw like QUEST_COMPLETED to avoid API-constant churn. */
    private static final int BANK_GROUP_ID = 12;
    private static final int DEPOSIT_BOX_GROUP_ID = 192;
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
    /** The palette the pin image was drawn in. */
    private Palette lockedPinPalette;

    /** Worn-gear slots currently above your unlocked tier, for the HUD (null = none). */
    @Getter private volatile String overTierSummary;
    /** Item ids already warned about this session, to avoid chat spam. */
    private final Set<Integer> warnedOverTier = new HashSet<>();

    /** The Slayer task the last task message stated, this character's; or null. */
    private SlayerAssignment slayerAssignment;
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
        // Before anything reads a setting, carry the old ones over (D2).
        migrateSettings();
        ClientThreadGate started = new ClientThreadGate(clientThread, new PluginSession());
        gate = started;
        sidebarModels = new SidebarPublisher(panel);
        connectionSettings.clearLegacySettings();
        File dataDirectory = dataDirectory();
        if (!dataDirectory.exists()) dataDirectory.mkdirs();
        // Each account's history, audit log and Slayer task open when it logs
        // in (openAccountFiles); a file that can't be opened leaves its
        // feature off, and never stops the plugin starting.
        savedRules = new SavedRulesStore(gson,
            dataDirectory.toPath().resolve(SavedRulesStore.FILE_NAME));
        connectionController = new TrackerConnectionController(
            okHttpClient,
            gson,
            connectionSettings,
            Clock.systemUTC(),
            started::run,
            relayImporter,
            snapshot -> started.run(this::refreshSidebar));

        travelRuleEvaluator = new TravelRuleEvaluator();
        travelAvailability = new RuneLiteTravelAvailability(client);
        travelAlternativeFinder = new TravelAlternativeFinder();
        travelNoticeStore = new TravelBlockNoticeStore(Clock.systemUTC());
        travelGuardianCoordinator = new TravelGuardianCoordinator(
            intentClassifier,
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
                "Strict Mode {} failed: {}", stage, error.getMessage()),
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
        overlayManager.add(flashOverlay);
        overlayManager.add(unlockOverlay);
        overlayManager.add(outlineOverlay);
        travelBlockOverlay.setPauseGuardian(pauseStrictMode);
        travelBlockOverlay.setPalette(this::palette);
        travelOverlayLifecycle = new TravelGuardianOverlayLifecycle(
            () -> overlayManager.add(travelBlockOverlay),
            () -> mouseManager.registerMouseListener(travelBlockOverlay),
            () -> mouseManager.unregisterMouseListener(travelBlockOverlay),
            () -> overlayManager.remove(travelBlockOverlay));
        travelOverlayLifecycle.start();

        panel.onAction(this::onSidebarAction);
        panel.onStrictModeToggle(this::setStrictMode);
        panel.onSyncToggle(this::setOnlineSync);
        panel.onIntroDismiss(() -> configManager.setConfiguration(
            FateLockedConfig.GROUP, "strictModeIntroSeen", true));
        panel.onHereFold(this::saveHereOpen);
        panel.onCopyForTracker(this::copyForTracker);
        panel.onDismissEvent(this::dismissEvent);
        panel.openHere(hereOpen());
        panel.onHerePoint((category, row) -> gate.run(() -> pointTo(category, row)));
        panel.onHereClearPoint(() -> gate.run(this::clearPointer));
        panel.setRollInboxLink(FateLockedPanel.TRACKER_URL);
        navButton = buildNavigationButton(panel);
        clientToolbar.addNavigation(navButton);

        started.run(() -> {
            refreshPalette();
            startSessionTracking();
            updateStrictModePanel();
            updateStrictAuditPanel();
            updatePanelRollInbox();
            refreshSidebar();
            refreshInfoBoxes();
        });
        loadSavedRules();
        loadSpots();
        keyManager.registerKeyListener(reimportHotkey);
        keyManager.registerKeyListener(pauseStrictHotkey);
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
                log.debug("Could not fully clean up Strict Mode's overlay: {}",
                    ex.getMessage());
            }
        }
        overlayManager.remove(worldMapOverlay);
        overlayManager.remove(sceneOverlay);
        overlayManager.remove(minimapOverlay);
        overlayManager.remove(hudOverlay);
        overlayManager.remove(flashOverlay);
        overlayManager.remove(unlockOverlay);
        overlayManager.remove(outlineOverlay);
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        keyManager.unregisterKeyListener(reimportHotkey);
        keyManager.unregisterKeyListener(pauseStrictHotkey);
        worldMapPointManager.removeIf(LockedAreaPoint.class::isInstance);
        worldMapPointManager.removeIf(WayPoint.class::isInstance);
        infoBoxManager.removeIf(b -> b instanceof FateLockedInfoBox);
        dropPointer();
        saveSpots();
        active = ActiveRules.NONE;
        // A pause belongs to this start: turning the plugin off and on ends it.
        strictPause.resume();
        decisions = DecisionService.create(RulesSnapshot.empty(), null, null);
        decisionsBound = "";
        decisionsPlayer = "";
        lastChunk = null;
        lastStatus = null;
        areaAlerts.forget();
        lockedFadeAt = NO_FADE;
        unlockShownAt = NO_FADE;
        unlockNews = UnlockNews.NONE;
        glowing = Collections.emptyList();
        hudModel = HudModel.NONE;
    }

    /**
     * RuneLite writes each profile's defaults on a switch; its old settings are carried over
     * too, and its colours are drawn.
     */
    @Subscribe
    public void onProfileChanged(ProfileChanged ev)
    {
        migrateSettings();
        gate.run(this::refreshPalette);
        if (panel != null)
        {
            panel.openHere(hereOpen());
        }
    }

    /**
     * The parts of Here the player left open, as their RuneLite profile keeps them; none
     * when the profile can't be read, which never stops the plugin starting.
     */
    Set<String> hereOpen()
    {
        String saved;
        try
        {
            saved = configManager.getConfiguration(FateLockedConfig.GROUP, HERE_OPEN);
        }
        catch (RuntimeException e)
        {
            log.warn("Could not read which parts of Here were left open: {}", e.getMessage());
            saved = null;
        }
        Set<String> open = new TreeSet<>();
        if (saved != null)
        {
            for (String key : saved.split(","))
            {
                if (!key.trim().isEmpty())
                {
                    open.add(key.trim());
                }
            }
        }
        return open;
    }

    private void saveHereOpen(Set<String> open)
    {
        if (open.isEmpty())
        {
            configManager.unsetConfiguration(FateLockedConfig.GROUP, HERE_OPEN);
        }
        else
        {
            configManager.setConfiguration(FateLockedConfig.GROUP, HERE_OPEN, String.join(",", open));
        }
    }

    /** Which load of the scene this is; it changes whenever a scene loads. */
    int sceneGeneration()
    {
        return sceneGeneration;
    }

    /** The loaded scene's edges and locked land under these rules, on this plane. Client thread. */
    SceneEdges sceneEdges(DecisionService rules, WorldView view, int plane)
    {
        return sceneEdgesCache.get(rules, view, plane, sceneGeneration, chunkLocator());
    }

    /** The colours everything is drawn in (U15): a preset, or the player's own. Client thread. */
    Palette palette()
    {
        return palette;
    }

    /** The palette these settings choose. The custom colours count only under Custom. */
    static Palette palette(FateLockedConfig config)
    {
        return Palette.of(Palette.Preset.valueOf(config.colourPreset().name()),
            config.unlockedColor(), config.frontierColor(), config.lockedColor());
    }

    /** Work the palette out again from the settings, hand it to the sidebar, and redraw the pins in it. */
    private void refreshPalette()
    {
        palette = palette(config);
        SidebarPublisher models = sidebarModels();
        if (models != null)
        {
            models.palette(palette);
        }
        if (config.worldMapMarkers())
        {
            refreshWorldMapMarkers();
        }
    }

    /** Carry each player's settings from before Stage 3 over to the merged ones (D2). */
    private void migrateSettings()
    {
        try
        {
            SettingsMigration.migrate(new SettingsMigration.ConfigStore()
            {
                @Override
                public String get(String key)
                {
                    return configManager.getConfiguration(FateLockedConfig.GROUP, key);
                }

                @Override
                public void set(String key, String value)
                {
                    configManager.setConfiguration(FateLockedConfig.GROUP, key, value);
                }
            });
        }
        catch (RuntimeException error)
        {
            // Settings that can't be carried over start at their defaults; the plugin still starts.
            log.warn("Could not carry old Fate Locked settings over: {}", error.getMessage());
        }
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
        String key = ev.getKey();
        gate.run(() -> applyConfigChange(key));
    }

    private void applyConfigChange(String key)
    {
        if (PALETTE_KEYS.contains(key))
        {
            refreshPalette();
        }
        else if ("ruleWarnings".equals(key))
        {
            // One switch warns about both, so each is worked out again.
            recomputeOverTierGear();
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
            refreshSidebar();
        }
        else if (TrackerConnectionSettings.PAIRING_CODE_KEY.equals(key))
        {
            panel.setRollInboxLink(FateLockedPanel.TRACKER_URL);
            updatePanelRollInbox();
            refreshSidebar();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged ev)
    {
        GameState state = ev.getGameState();
        if (state == GameState.LOADING)
        {
            // A new scene, even at the same base, which instances reuse.
            sceneGeneration++;
            return;
        }
        if (state == GameState.LOGIN_SCREEN)
        {
            // Logged out: the next login warns and announces afresh.
            awaitingLogin = true;
            hudModel = HudModel.NONE;
            endWay(pointer);
            pointer = null;
            pointerLooks = false;
            saveSpots();
            forgetLoginWarnings();
            trackerLoggedIn(false);
            refreshDecisions();
            refreshSidebar();
            return;
        }
        if (state == GameState.LOGGING_IN || state == GameState.HOPPING
            || state == GameState.CONNECTION_LOST)
        {
            awaitingLogin = true;
            return;
        }
        if (state != GameState.LOGGED_IN) return;
        trackerLoggedIn(true);
        if (client.getAccountHash() != loggedInAccountHash)
        {
            openAccountFiles();
        }

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
        if (newAccount)
        {
            forgetLoginWarnings();
            forgetSlayerTask();
        }
        if (newSession) resetBaselines();
        refreshDecisions();
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
        trackerLoggedIn(loggedIn);
        if (loggedIn)
        {
            openAccountFiles();
        }
    }

    /**
     * Open the logged-in account's files off the client thread, and use them
     * once open. Until then, that account's detections are dropped rather
     * than written into another account's files.
     */
    private void openAccountFiles()
    {
        long accountHash = client.getAccountHash();
        if (accountHash == -1) return;
        String name = loggedInName();
        boolean boundCharacter = AccountBinding.sameAccount(
            AccountBinding.boundAccount(getBundle()), name);
        Path dataPath = dataDirectory().toPath();
        ClientThreadGate onClient = gate;
        fileWriter.submit(() -> {
            AccountFiles files = AccountFiles.open(gson, dataPath, accountHash, boundCharacter);
            onClient.run(() -> useAccountFiles(files));
        });
    }

    private void useAccountFiles(AccountFiles files)
    {
        // Another account logged in while these opened: its own are coming.
        if (client.getAccountHash() != files.accountHash) return;
        accountFilesHash = files.accountHash;
        detectedEvents = files.detected;
        historySaveFailed = files.detected == null;
        strictAuditLog = files.auditLog;
        questMemory = files.quests;
        tierMemory = files.diaryTiers;
        questsRemembered = questMemory == null ? null : questMemory.finished();
        tiersRemembered = tierMemory == null ? null : tierMemory.finished();
        updatePanelRollInbox();
        updateStrictAuditPanel();
    }

    /** Whether the open files are the logged-in account's own. */
    private boolean accountFilesInUse()
    {
        return accountFilesHash == client.getAccountHash();
    }

    /**
     * Only the login screen counts as logged out for the tracker: a hop, a
     * lost connection or a loading screen keeps the minute's checks going.
     */
    private void trackerLoggedIn(boolean loggedIn)
    {
        TrackerConnectionController controller = connectionController;
        if (controller != null)
        {
            controller.loggedIn(loggedIn);
        }
    }

    /** Let the account and gear warnings, and the area's line and alert, show once more. */
    private void forgetLoginWarnings()
    {
        lastChunk = null;
        areaAlerts.forget();
        lastAccountWarned = null;
        warnedOverTier.clear();
    }

    /**
     * The client sends every skill and varbit again after a login, hop or
     * reconnect: the session's detectors start again from the next reading,
     * so those set the baseline without events.
     */
    private void resetBaselines()
    {
        detectors = null;
        sessionReadingDue = true;
    }

    // ── Detection (Stage 4) ───────────────────────────────────────────────────
    // RuneLite's events become the detectors' plain signals; what they make of
    // them is recorded for the Roll inbox. Read-only: the plugin never acts on
    // the player's behalf.

    @Subscribe
    public void onStatChanged(StatChanged ev)
    {
        detect(new Signal.Level(skillName(ev.getSkill()), ev.getLevel()));
    }

    @Subscribe
    public void onChatMessage(ChatMessage ev)
    {
        if (ev.getType() != ChatMessageType.GAMEMESSAGE && ev.getType() != ChatMessageType.SPAM) return;
        String raw = ev.getMessage() == null ? "" : ev.getMessage();
        String m = raw.toLowerCase();

        petDropDetector.detect(Text.removeTags(raw), System.currentTimeMillis())
            .ifPresent(this::record);
        detect(new Signal.Chat(ev.getType().name(), raw));

        // Slayer assignment / task-check messages mention the monster: the rules may lock the task.
        if (m.contains("to kill"))
        {
            SlayerAssignment assignment = SlayerAssignment.fromChat(
                Text.removeTags(raw), client.getVarbitValue(VarbitID.SLAYER_MASTER));
            if (assignment != null)
            {
                slayerAssignment = assignment;
                recomputeSlayer();
            }
        }
    }

    /** Re-check whether the rules lock the current slayer task. */
    private void recomputeSlayer()
    {
        Decision locked = lockedSlayerTask(decisions);
        slayerTaskWarn = locked == null ? null : locked.getLabel();
        warnLockedSlayerTask(locked);
    }

    /** The current slayer task's decision when the rules lock it, else null (B11, R16). */
    Decision lockedSlayerTask(DecisionService ruleDecisions)
    {
        SlayerAssignment assignment = slayerAssignment;
        if (!config.ruleWarnings() || assignment == null) return null;
        // Allowed, not ready or unknown: no warning.
        Decision decision = ruleDecisions.slayerTask(assignment.getMaster(), assignment.getTask(), assignment.getLocation());
        return decision.isLocked() ? decision : null;
    }

    /** Another character's task isn't this one's. */
    private void forgetSlayerTask()
    {
        slayerAssignment = null;
        slayerTaskWarn = null;
        slayerWarnedFor = null;
    }

    /** Say once per assignment that the task is locked, and why, in the tracker's words. */
    private void warnLockedSlayerTask(Decision decision)
    {
        String locked = decision == null ? null : decision.getLabel();
        if (locked == null || locked.equalsIgnoreCase(slayerWarnedFor))
        {
            return;
        }
        slayerWarnedFor = locked;
        String why = decision.getReason() == null ? "." : ": " + decision.getReason() + ".";
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append("Your Slayer task (")
            .append(ChatColorType.HIGHLIGHT).append(locked)
            .append(ChatColorType.NORMAL).append(") is locked" + why);
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
        notifyIfEnabled("Slayer task (" + locked + ") is locked");
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded ev)
    {
        // Locked-bank warning is independent of the roll-nudge toggle.
        if ((ev.getGroupId() == BANK_GROUP_ID || ev.getGroupId() == DEPOSIT_BOX_GROUP_ID)
            && config.ruleWarnings())
        {
            warnLockedBankIfNeeded();
        }

        if (ev.getGroupId() == QUEST_COMPLETED_GROUP_ID)
        {
            // A quest is done: the game's quest states are read on the next tick.
            questReadingDue = true;
        }
    }

    /** What the HUD shows now. */
    HudModel hudModel()
    {
        return hudModel;
    }

    /**
     * Work the HUD out again for the player's chunk, null when it can't be found; a model like
     * the last one is kept, so the overlay builds its panel again only on a change.
     */
    private void refreshHud(CanonicalChunk current)
    {
        FateLockedConfig.HudMode mode = config.hudMode();
        DecisionService ruleDecisions = decisions;
        // The nearest bank and shop are found again only when the chunk or the rules change.
        boolean near = mode != FateLockedConfig.HudMode.OFF && current != null;
        if (near && (ruleDecisions != nearestDecisions || !current.equals(nearestChunk)))
        {
            nearestDecisions = ruleDecisions;
            nearestChunk = current;
            nearestBank = ruleDecisions.nearestBank(current);
            nearestShop = ruleDecisions.nearestShop(current);
        }
        HudModel next = HudPresenter.present(HudPresenter.Facts.builder()
            .mode(mode)
            .decisions(ruleDecisions)
            .chunk(current)
            .strict(strictModeStatus)
            .bank(near ? nearestBank : null)
            .shop(near ? nearestShop : null)
            .slayerWarning(slayerTaskWarn)
            .overTier(overTierSummary)
            .run(getBundle().getState())
            .here(mode == FateLockedConfig.HudMode.DETAILED ? hereModel(ruleDecisions) : null)
            .build());
        if (!next.equals(hudModel))
        {
            hudModel = next;
        }
    }

    private String loggedInName()
    {
        Player local = client.getLocalPlayer();
        return local == null ? null : local.getName();
    }

    /**
     * Advisory when the bank just opened still needs rolling: the rules lock
     * it (B3) and it isn't rolled. A rolled bank in a locked area gets no
     * "roll it" line; the area's own alerts cover it.
     */
    private void warnLockedBankIfNeeded()
    {
        CanonicalChunk chunk = chunkLocator().player();
        if (chunk == null) return;
        DecisionService ruleDecisions = decisions;
        Decision bank = ruleDecisions.bankAt(chunk);
        if (!bank.isLocked() || !ruleDecisions.bankRoll(chunk).isLocked()) return;
        String where = bank.getLabel() == null ? "This bank" : bank.getLabel();
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append(where)
            .append(ChatColorType.NORMAL).append(" is locked. Unlock it from the Banks table in the tracker before you use it.");
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
        notifyIfEnabled(where + " is locked");
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged ev)
    {
        // VarbitChanged fires for every varbit in the game, so these are set lookups.
        int varbit = ev.getVarbitId();
        if (DiaryTiers.TIER_IDS.containsKey(varbit) || varbit == VarbitID.OPTION_COLLECTION_NEW_ITEM)
        {
            detect(new Signal.Varbit(varbit, ev.getValue()));
        }
        if (SLAYER_VARBITS.contains(varbit) || SLAYER_VARPS.contains(ev.getVarpId()))
        {
            // Read once the tick's changes are in: the amount and the streak can come apart.
            slayerReadingDue = true;
        }
    }

    /** The game's notification popup, as RuneLite's Screenshot plugin reads it: its title and text once shown. */
    @Subscribe
    public void onScriptPreFired(ScriptPreFired ev)
    {
        if (ev.getScriptId() == ScriptID.NOTIFICATION_START)
        {
            notificationStarted = true;
        }
        else if (ev.getScriptId() == ScriptID.NOTIFICATION_DELAY && notificationStarted)
        {
            notificationStarted = false;
            detect(new Signal.Popup(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE),
                client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN)));
        }
    }

    /**
     * What the session's detectors make of this signal, recorded. Nothing before the session's
     * first reading or while detection is off, so a memory changes only while the gate is open.
     */
    private void detect(Signal signal)
    {
        Detectors session = detectors;
        if (session == null || signal == null || !accountFilesInUse() || !detection().records()) return;
        session.useTables(detectionTables());
        for (DetectedEvent event : session.on(signal))
        {
            record(event);
        }
        remember(session);
    }

    /**
     * Once a tick, with the account's own files open and detection on: the session's first
     * reading, then the quests again after the quest scroll or a minute, and Slayer after its
     * variables changed.
     */
    private void readForDetectors()
    {
        if (detectedEvents == null || !accountFilesInUse() || !detection().records()) return;
        if (detectors == null)
        {
            detectors = new Detectors(detectionTables(), questsRemembered, tiersRemembered);
        }
        int tick = client.getTickCount();
        if (sessionReadingDue)
        {
            sessionReadingDue = false;
            questReadingDue = true;
            slayerReadingDue = true;
            detect(levelsReading());
            detect(diaryReading());
        }
        if (questReadingDue || tick - questsReadTick >= QUEST_READING_TICKS)
        {
            questReadingDue = false;
            questsReadTick = tick;
            detect(questsReading());
        }
        if (slayerReadingDue)
        {
            slayerReadingDue = false;
            detect(slayerReading());
        }
    }

    /** Every skill's real level: the levels' baseline. */
    private Signal levelsReading()
    {
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (Skill skill : Skill.values())
        {
            if (!"Overall".equals(skill.getName()))
            {
                levels.put(skillName(skill), client.getRealSkillLevel(skill));
            }
        }
        return new Signal.Levels(levels);
    }

    /** Every diary tier's varbit, and the collection log's notification setting. */
    private Signal diaryReading()
    {
        Map<Integer, Integer> values = new LinkedHashMap<>();
        for (int varbit : DiaryTiers.TIER_IDS.keySet())
        {
            values.put(varbit, client.getVarbitValue(varbit));
        }
        values.put(VarbitID.OPTION_COLLECTION_NEW_ITEM, client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM));
        return new Signal.Varbits(values);
    }

    /** Every quest the game says is finished, by RuneLite's name for it; null when the game can't say. */
    private Signal questsReading()
    {
        Set<String> finished = new HashSet<>();
        try
        {
            for (Quest quest : Quest.values())
            {
                if (questState(quest) == QuestState.FINISHED) finished.add(quest.getName());
            }
        }
        catch (RuntimeException error)
        {
            log.debug("Could not read the quests' states", error);
            return null;
        }
        return new Signal.Quests(finished);
    }

    /** A quest's state, from the game's own script. */
    QuestState questState(Quest quest)
    {
        return quest.getState(client);
    }

    /** The Slayer task as the game's variables give it, read as RuneLite's Slayer plugin reads them. */
    private Signal slayerReading()
    {
        int amount = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
        int master = client.getVarbitValue(VarbitID.SLAYER_MASTER);
        int streak = master == Detectors.KRYSTILIA ? client.getVarbitValue(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED)
            : master == Detectors.MORTIMER ? client.getVarpValue(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED)
            : client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED);
        int target = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
        int assigned = client.getVarpValue(VarPlayerID.SLAYER_COUNT_ORIGINAL);
        if (client.getVarbitValue(VarbitID.SLAYER_MODIFIER_ID) == 2)
        {
            int modifier = client.getVarbitValue(VarbitID.SLAYER_MODIFIER_VALUE);
            assigned += client.getVarbitValue(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1 ? -modifier : modifier;
        }
        return new Signal.Slayer(amount > 0 ? slayerTaskName(target) : null, amount, assigned, master, streak,
            target == SLAYER_BOSS_TASK);
    }

    /** The task's name from the game's Slayer task table, found as RuneLite's Slayer plugin finds it; null when not. */
    private String slayerTaskName(int taskId)
    {
        try
        {
            int row;
            if (taskId == SLAYER_BOSS_TASK)
            {
                List<Integer> bosses = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
                    DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0,
                    client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
                if (bosses == null || bosses.isEmpty()) return null;
                row = (Integer) client.getDBTableField(bosses.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
            }
            else
            {
                List<Integer> tasks = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
                if (tasks == null || tasks.isEmpty()) return null;
                row = tasks.get(0);
            }
            Object[] name = client.getDBTableField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
            return name != null && name.length > 0 && name[0] instanceof String ? (String) name[0] : null;
        }
        catch (RuntimeException error)
        {
            log.debug("Could not read the Slayer task's name", error);
            return null;
        }
    }

    /** The tracker's names for what RuneLite notices, from the rules in force; null without them. */
    private DetectionTables detectionTables()
    {
        FateLockedBundle bundle = getBundle();
        return bundle == null || bundle.getRules() == null ? null : bundle.getRules().getDetection();
    }

    /** Keep what the session found finished in the character's memories, when that changed. */
    private void remember(Detectors session)
    {
        Set<String> quests = session.finishedQuests();
        FinishedMemory questFile = questMemory;
        if (quests != null && questFile != null && !quests.equals(questsRemembered))
        {
            questsRemembered = quests;
            fileWriter.submit(() -> write(questFile, quests));
        }
        Set<String> tiers = session.finishedTiers();
        FinishedMemory tierFile = tierMemory;
        if (tiers != null && tierFile != null && !tiers.equals(tiersRemembered))
        {
            tiersRemembered = tiers;
            fileWriter.submit(() -> write(tierFile, tiers));
        }
    }

    private static void write(FinishedMemory memory, Set<String> finished)
    {
        try
        {
            memory.remember(finished);
        }
        catch (IOException ex)
        {
            log.debug("Could not save what the character has finished", ex);
        }
    }

    private void record(DetectedEvent detected)
    {
        if (detected == null || detectedEvents == null || !accountFilesInUse()
            || !detection().records()) return;
        FateLockedBundle currentBundle = getBundle();
        // The tracker won't roll it: not saved, so no reminder.
        if (SpentBosses.covers(currentBundle, detected.getType(), detected.getCanonicalLabel())) return;
        String account = loggedInName();
        FateEvent event = eventFactory.create(
            detected.getType(), detected.getCanonicalLabel(), detected.getConfidence(),
            detected.getEvidence(), currentBundle, account,
            detected.getDetectorId(), detected.getDetectorVersion(), detected.getCount());
        DetectedEventStore store = detectedEvents;
        ClientThreadGate onClient = gate;
        fileWriter.submit(() -> {
            // Null: the write failed; false: a duplicate, nothing written.
            Boolean recorded;
            try
            {
                recorded = store.record(event);
            }
            catch (IOException ex)
            {
                log.warn("Could not save a detected event", ex);
                recorded = null;
            }
            Boolean result = recorded;
            onClient.run(() -> {
                if (result == null)
                {
                    historySaveFailed = true;
                }
                else if (result)
                {
                    historySaveFailed = false;
                    // A new event: what the last copy did is behind it now.
                    rollInboxNotice = null;
                    remind(detected);
                }
                updatePanelRollInbox();
            });
        });
    }

    /** One chat line for a saved event, with the Roll reminders setting on (plan decision 15). */
    private void remind(DetectedEvent detected)
    {
        String text = RollReminder.text(detected);
        if (text != null && config.rollNudges())
        {
            nudge(text);
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

    /**
     * What detections may do here (DetectionGate): with the rules' bound character, logged in on
     * a world whose progress is the account's own, they are recorded and remind; while the rules
     * are bound to no one, they are only recorded. A main account or a Leagues world sharing this
     * RuneLite gets neither.
     */
    private DetectionGate.Detection detection()
    {
        return DetectionGate.decide(getBundle(), loggedInName(), client.getWorldType());
    }

    /** Queue a one-line informational chat nudge (client-side only). */
    private void nudge(String text)
    {
        if (!detection().reminds()) return;
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
        List<OverTierItem> over = overTierGear(decisions);
        overTierSummary = overTierSummary(over);
        warnOverTierGear(over);
    }

    /** Worn items these rules put above their slot's unlocked tier. */
    private List<OverTierItem> overTierGear(DecisionService ruleDecisions)
    {
        if (!config.ruleWarnings()) return Collections.emptyList();
        ItemContainer eq = client.getItemContainer(InventoryID.WORN);
        if (eq == null) return Collections.emptyList();

        List<OverTierItem> over = new ArrayList<>();
        for (Map.Entry<EquipmentInventorySlot, String> e : SLOT_NAMES.entrySet())
        {
            Item item = eq.getItem(e.getKey().getSlotIdx());
            if (item == null || item.getId() <= 0) continue;
            // Unrated items, and every item on another character, aren't flagged (B12).
            ItemTier tier = ruleDecisions.itemTier(item.getId(), e.getValue());
            if (tier == null || !tier.isOver()) continue;
            over.add(new OverTierItem(item.getId(), e.getValue(), tier.getTier(), tier.getUnlocked()));
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

    /**
     * Warn (once per login) if the bound account doesn't match the logged-in
     * character — the run's progress is tied to one OSRS account.
     */
    private void checkBoundAccount()
    {
        String bound = AccountBinding.boundAccount(getBundle());
        if (bound == null) return;

        String current = loggedInName();
        if (current == null || current.isEmpty()) return;

        if (AccountBinding.sameAccount(bound, current)) return;
        String warnedFor = AccountBinding.normalize(bound);
        if (warnedFor.equals(lastAccountWarned)) return;
        lastAccountWarned = warnedFor;

        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append("This run is linked to ")
            .append(ChatColorType.HIGHLIGHT).append(bound)
            .append(ChatColorType.NORMAL).append(", but you're logged in as ")
            .append(ChatColorType.HIGHLIGHT).append(current)
            .append(ChatColorType.NORMAL).append(".");
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
        notifyIfEnabled("You're logged in as " + current + ", but this run is linked to " + bound);
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        Player local = client.getLocalPlayer();
        if (local == null) return;

        refreshDecisions();
        readForDetectors();
        updateStrictModePanel();

        // Once per login, flag if the character doesn't match the bound account.
        checkBoundAccount();

        CanonicalChunk current = chunkLocator().player();
        if (current != null && !current.equals(lastChunk))
        {
            enter(current);
            visited(current);
        }
        refreshWarningCount();
        refreshHud(current);
        keepPointer(current);
        saveSpotsIfDue();
    }

    /** The player crossed into this chunk: its line, sound and fade, as the area and the alert setting say. */
    private void enter(CanonicalChunk current)
    {
        Decision entry = decisions.chunk(current);
        PermissionStatus status = entry.getStatus();
        String label = decisions.areaName(current);
        // Only the rules' own answers are announced (B6): never a chunk
        // they don't map (dungeons, instances, every chunk before rules
        // load), nor another character's rules. NOT_READY is owned, so
        // it never alerts. Lines and alerts are per area, not per chunk.
        LockedAreaAlerts.Alert alert = areaAlerts.enter(status, areaKey(current, entry, label),
            client.getTickCount(), config.lockedAreaAlert(), config.announceAreaChanges());
        if (alert.isLine())
        {
            announceEntry(current, label, entry);
            // A locked area's line is the alert's own: the notification goes with it, sound or
            // not, as the notifications setting says (the owner's call T8).
            if (status == PermissionStatus.LOCKED)
            {
                notifyIfEnabled("You've entered a locked area: "
                    + (label != null ? label : "chunk (" + current.getCx() + ", " + current.getCy() + ")"));
            }
        }
        if (alert.isFade())
        {
            lockedFadeAt = System.nanoTime();
        }
        if (alert.isSound())
        {
            client.playSoundEffect(2277); // death squelch — good "you done messed up" cue
        }
        lastChunk = current;
        lastStatus = status;
    }

    /**
     * Strict Mode: stop a click only when it is exactly matched travel and
     * fresh rules bound to this character prove the destination locked. The
     * readiness the sidebar shows is the one gate for the only click the
     * plugin ever consumes; walking, NPCs, objects, banks and equipment are
     * never blocked.
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        // The character may have changed since the last tick.
        refreshDecisions();
        travelGuardianShell.handle(event, client, strictModeReadiness(), decisions);
        if (!event.isConsumed())
        {
            sayWhyTierLocked(event.getMenuEntry());
        }
    }

    /**
     * Clicking something a skill tier doesn't open yet: one chat line that says which tier it
     * needs, once a minute for each thing. Only where the (Locked) tag would show it.
     */
    private void sayWhyTierLocked(MenuEntry entry)
    {
        if (entry == null || !MenuTagFilter.mayTag(entry.getType()) || !config.tagLockedOptions()) return;
        DecisionService ruleDecisions = decisions;
        if (ruleDecisions.trust() != Trust.TRUSTED) return;
        CanonicalChunk chunk = chunkLocator().menuTarget(entry);
        if (chunk == null || ruleDecisions.chunk(chunk).isLocked()) return;
        LockedThings.Thing thing = menuThing(entry, chunk, ruleDecisions);
        if (thing == null || thing.getLook() != LockedThings.Look.TIER) return;
        String label = thing.getTarget().getLabel();
        if (!tierChatLimiter.shouldReport(label, System.currentTimeMillis())) return;
        writeTravelChat(label + ": " + thing.getWhy());
    }

    /** A "[Fate Locked]" chat line: Strict Mode's, which names it and says how to pause (B15), and the skill tier line. */
    private void writeTravelChat(String text)
    {
        ChatMessageBuilder message = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append(text);
        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(message.build())
            .build());
    }

    /** Called inside the click handler, so the file write waits for the writer. */
    private void writeTravelAudit(StrictModeAuditEntry entry)
    {
        StrictModeAuditLog auditLog = strictAuditLog;
        if (auditLog == null || !accountFilesInUse()) return;
        ClientThreadGate onClient = gate;
        fileWriter.submit(() -> {
            try
            {
                auditLog.append(entry);
            }
            catch (IOException ex)
            {
                log.debug("Could not save the Strict Mode audit log: {}", ex.getMessage());
            }
            onClient.run(this::updateStrictAuditPanel);
        });
    }

    private void updateStrictAuditPanel()
    {
        recentStopped = StrictModeAuditPresenter.recentPrevented(
            strictAuditLog == null ? null : strictAuditLog.recent(5));
        refreshSidebar();
    }
    void pauseStrictModeForSixtySeconds()
    {
        strictPause.pauseFor(Duration.ofSeconds(60));
        updateStrictModePanel();
    }

    /** A pause only means something while Strict Mode is on. */
    private void pauseStrictModeFromHotkey()
    {
        if (config.strictMode()) pauseStrictModeForSixtySeconds();
    }

    /**
     * Work out Strict Mode's status once (each tick and on each change) for
     * the sidebar row and the HUD line, so they always agree (B16).
     */
    private void updateStrictModePanel()
    {
        StrictModeStatusView status = StrictModeStatusView.of(
            config.strictMode(), strictPause.isPaused(), strictPause.remainingSeconds(),
            strictModeReadiness().getReason());
        strictModeStatus = status;
        refreshSidebar();
    }

    /**
     * Whether Strict Mode can act right now: what the sidebar shows, and the
     * gate for clicks. The character check is the decision service's trust
     * (B4): Strict Mode needs tracker rules bound to the character playing.
     */
    StrictModeReadiness strictModeReadiness()
    {
        DecisionService ruleDecisions = decisions;
        RulesSnapshot rules = ruleDecisions.rules();
        return StrictModeReadiness.evaluate(
            config.strictMode(),
            strictPause.isPaused(),
            !rules.isEmpty() && !rules.isLegacy(),
            ruleDecisions.travelTable() != null,
            AccountBinding.boundAccount(getBundle()),
            loggedInName(),
            ruleDecisions.trust() == Trust.TRUSTED && ruleDecisions.isBound(),
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
        ActiveRules current = active;
        return FreshnessPolicy.isFresh(current.getSource(), trackerPaired(), trackerLastSync(),
            current.getBundle().exportedAt(), Instant.now());
    }
    /**
     * Tag right-click menu entries " (Locked)", in the palette's locked colour: the
     * "are you sure?" before you ever click. The decision service decides (B2), so a
     * tag never disagrees with the sidebar or Strict Mode, and another character, or
     * nobody logged in, sees none.
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        MenuEntry entry = event.getMenuEntry();
        // Most options could never be tagged: pass them over before reading anything (F1).
        if (!MenuTagFilter.mayTag(entry.getType()) || !config.tagLockedOptions()) return;
        DecisionService ruleDecisions = decisions;
        if (ruleDecisions.trust() != Trust.TRUSTED) return;
        if (!taggedLocked(entry, ruleDecisions)) return;
        String t = entry.getTarget();
        String base = t == null ? "" : t;
        if (!base.contains(MenuFacts.LOCKED_MARK))
        {
            entry.setTarget(base + " <col=" + palette.hex(Palette.Tone.BAD) + ">" + MenuFacts.LOCKED_MARK + "</col>");
        }
    }

    /**
     * Whether a menu option is tagged (F4, G14). Travel the tracker's table
     * matches by id is tagged only for an exact LOCKED decision, one place,
     * as Strict Mode reads it; networks and boats, which Strict Mode never
     * blocks, are tagged the same way. Anything else is tagged by the chunk
     * it stands in.
     */
    private boolean taggedLocked(MenuEntry entry, DecisionService ruleDecisions)
    {
        MenuFacts facts = menuFacts().read(entry);
        TravelMatch travel = intentClassifier.classify(facts, ruleDecisions.travelTable());
        if (travel != null)
        {
            return travel.getOption().destination() != null
                && ruleDecisions.travel(travel.getMethod(), travel.getOption()).isLocked();
        }
        GuardedAction action = guardedActionFactory.from(facts, entry, chunkLocator());
        CanonicalChunk chunk = action.getChunk();
        if (chunk == null) return false;
        if (ruleDecisions.chunk(chunk).isLocked()) return true;
        // In open land, the thing's own row: a locked bank or shop, or a skill tier too low.
        LockedThings.Thing thing = menuThing(entry, chunk, ruleDecisions);
        return thing != null && thing.getLook() != LockedThings.Look.OPEN;
    }

    /** The row for what a menu option is on, an NPC or an object, in its chunk; null for anything else. */
    private LockedThings.Thing menuThing(MenuEntry entry, CanonicalChunk chunk, DecisionService ruleDecisions)
    {
        String name;
        String[] options;
        NPC npc = entry.getNpc();
        if (npc != null)
        {
            NPCComposition shown = npc.getTransformedComposition();
            name = shown != null ? shown.getName() : npc.getName();
            options = shown != null ? shown.getActions() : null;
        }
        else if (isObjectOption(entry.getType()))
        {
            ObjectComposition shown = SceneSearch.shown(client, entry.getIdentifier());
            if (shown == null) return null;
            name = shown.getName();
            options = shown.getActions();
        }
        else
        {
            return null;
        }
        return name == null ? null : lockedThings(chunk, ruleDecisions).find(name, options);
    }

    private static boolean isObjectOption(MenuAction type)
    {
        return type == MenuAction.GAME_OBJECT_FIRST_OPTION
            || type == MenuAction.GAME_OBJECT_SECOND_OPTION
            || type == MenuAction.GAME_OBJECT_THIRD_OPTION
            || type == MenuAction.GAME_OBJECT_FOURTH_OPTION
            || type == MenuAction.GAME_OBJECT_FIFTH_OPTION;
    }

    /** What a chunk's rows say about the things in it, read once for the rules in force. */
    LockedThings lockedThings(CanonicalChunk chunk, DecisionService ruleDecisions)
    {
        if (lockedThingsFor != ruleDecisions)
        {
            lockedThings.clear();
            lockedThingsFor = ruleDecisions;
        }
        return lockedThings.computeIfAbsent(chunk,
            c -> LockedThings.of(ruleDecisions.details(c).orElse(null)));
    }

    /**
     * What groups chunks into one area for chat and alerts: the area's name, else the tracker's
     * reason, so the sea is one area under "Needs Sailing and Pandemonium", else the chunk.
     */
    private static String areaKey(CanonicalChunk chunk, Decision entry, String area)
    {
        if (area != null) return area;
        if (entry.getReason() != null) return "reason:" + entry.getReason();
        return "chunk:" + chunk.getCx() + "," + chunk.getCy();
    }

    /**
     * The line for entering an area: its name and its status in words, and why it is locked
     * or not ready, in the tracker's words (E8). A chunk only the tracker maps is named by its
     * coordinates. Words, not marks: the game's chat font has no ✓ or ⚠.
     */
    private void announceEntry(CanonicalChunk chunk, String area, Decision entry)
    {
        String status = Terms.place(entry.getStatus());
        boolean why = entry.getReason() != null
            && (entry.getStatus() == PermissionStatus.LOCKED || entry.getStatus() == PermissionStatus.NOT_READY);
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(area != null ? area : "Chunk (" + chunk.getCx() + ", " + chunk.getCy() + ")")
            .append(ChatColorType.NORMAL).append(": " + status + (why ? " — " + entry.getReason() : ""));

        chatMessageManager.queue(QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(msg.build())
            .build());
    }

    /**
     * At startup: the rules the last start accepted. They are read through
     * the file writer, so a save the previous start was still finishing
     * lands first. With none saved, as on the first start after updating,
     * the newest backup file instead.
     */
    private void loadSavedRules()
    {
        SavedRulesStore store = savedRules;
        ClientThreadGate onClient = gate;
        fileWriter.submit(onClient.guard(() -> {
            SavedRules saved = store == null ? null : store.load();
            ParsedRules parsed = null;
            if (saved != null)
            {
                try
                {
                    parsed = new ParsedRules(FateLockedBundle.loadFromJson(gson, saved.getPayload()), saved.getPayload());
                }
                catch (RuntimeException ex)
                {
                    log.warn("Saved rules could not be read: {}", ex.getMessage());
                }
            }
            if (parsed == null)
            {
                loadBackupFile(false);
                return;
            }
            ParsedRules rules = parsed;
            onClient.run(() -> useSavedRules(saved, rules));
        }));
    }

    /**
     * On the client thread: bring back the last start's rules, unless others
     * arrived since. Tracker rules saved from this pairing ask the relay
     * only whether they are still current; until it confirms them they are
     * never fresh enough for Strict Mode.
     */
    private void useSavedRules(SavedRules saved, ParsedRules rules)
    {
        if (!RulesPrecedence.mayReplace(active.getSource(), RulesPrecedence.Arrival.SAVED)
            || !switchRules(rules, saved.getSource(), RulesPrecedence.Arrival.SAVED, saved.getSavedAt()))
        {
            return;
        }
        panel.flashStatus(Notices.restored(saved.getSavedAt(), Instant.now(), ZoneId.systemDefault()), true);
        log.info("Fate Locked rules restored from the last start: {} regions",
            rules.bundle.getRegionChunks().size());
        TrackerConnectionController controller = connectionController;
        if (controller != null
            && saved.getSource() == RulesSource.RELAY
            && saved.getPairingTag() != null
            && saved.getPairingTag().equals(PairingSupport.tag(connectionSettings.pairingCode())))
        {
            controller.seedAcceptedVersion(saved.getRelayVersion());
        }
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
            ParsedRules parsed;
            try
            {
                file = effectiveBundlePath();
                if (file == null)
                {
                    if (explicit)
                    {
                        panel.flashStatus(Notices.NO_BACKUP_FILE, false);
                    }
                    onClient.run(this::refreshPanel);
                    return;
                }
                // The web app writes UTF-8, which the platform's default
                // charset would garble on Windows.
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                parsed = new ParsedRules(FateLockedBundle.loadFromJson(gson, text), text);
            }
            catch (IOException | RuntimeException ex)
            {
                log.warn("Failed to load backup file {}: {}", file, ex.getMessage());
                panel.flashStatus(Notices.BACKUP_UNREADABLE, false);
                onClient.run(this::refreshPanel);
                return;
            }
            Path loaded = file;
            onClient.run(() -> useBackupFile(parsed, loaded, explicit));
        }));
    }

    /** Switch to rules read from a backup file; the tracker's copy wins on its next check. */
    private void useBackupFile(ParsedRules parsed, Path file, boolean explicit)
    {
        RulesPrecedence.Arrival arrival = explicit
            ? RulesPrecedence.Arrival.IMPORT : RulesPrecedence.Arrival.STARTUP_FILE;
        if (!RulesPrecedence.mayReplace(active.getSource(), arrival))
        {
            return;
        }
        if (!switchRules(parsed, RulesSource.FILE, arrival, Instant.now()))
        {
            panel.flashStatus(Notices.BACKUP_UNREADABLE, false);
            return;
        }
        saveRules(RulesSource.FILE, parsed.text, null);
        trackerRulesReplaced();
        log.info("Fate Locked bundle loaded from {}: {} regions, {} unlocked",
            file, parsed.bundle.getRegionChunks().size(), parsed.bundle.getUnlockedRegions().size());
        if (explicit)
        {
            panel.flashStatus(Notices.loadedBackupFile(parsed.bundle.exportedAt(), Instant.now(),
                ZoneId.systemDefault()), true);
        }
    }

    /** Keep rules the plugin accepted for the next start, on the file writer. */
    private void saveRules(RulesSource source, String text, String relayVersion)
    {
        SavedRulesStore store = savedRules;
        if (store == null) return;
        // The code that delivered the rules: a re-pairing's new one, before it
        // is saved as the pairing.
        TrackerConnectionController controller = connectionController;
        String pairingTag = source != RulesSource.RELAY ? null : PairingSupport.tag(
            controller != null ? controller.activeCode() : connectionSettings.pairingCode());
        SavedRules rules = new SavedRules(text, source, Instant.now(), relayVersion, pairingTag);
        fileWriter.submit(() -> {
            try
            {
                store.save(rules);
            }
            catch (IOException | RuntimeException ex)
            {
                log.warn("Could not save the rules for the next start: {}", ex.getMessage());
            }
        });
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
            panel.flashStatus(Notices.CLIPBOARD_UNREADABLE, false);
            return;
        }
        if (text.isEmpty())
        {
            panel.flashStatus(Notices.CLIPBOARD_EMPTY, false);
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
            panel.flashStatus(Notices.PAIRING_CODE, false);
            return;
        }
        ClientThreadGate onClient = gate;
        executor.execute(onClient.guard(() -> {
            ParsedRules parsed;
            try
            {
                parsed = new ParsedRules(FateLockedBundle.loadFromJson(gson, json), json);
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
                panel.flashStatus(Notices.IMPORT_FAILED, false);
                return;
            }
            onClient.run(() -> useClipboardRules(parsed));
        }));
    }

    /** On the client thread: switch to rules read from the clipboard. */
    private void useClipboardRules(ParsedRules parsed)
    {
        if (!switchRules(parsed, RulesSource.IMPORT, RulesPrecedence.Arrival.IMPORT, Instant.now()))
        {
            panel.flashStatus(Notices.IMPORT_FAILED, false);
            return;
        }
        saveRules(RulesSource.IMPORT, parsed.text, null);
        panel.flashStatus(Notices.imported(parsed.bundle.exportedAt(), Instant.now(), ZoneId.systemDefault()),
            true);
        trackerRulesReplaced();
        log.info(
            "Fate Locked bundle imported from the clipboard: {} regions",
            parsed.bundle.getRegionChunks().size());
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
    private final TrackerConnectionController.RelayBundleImporter<ParsedRules> relayImporter =
        new TrackerConnectionController.RelayBundleImporter<ParsedRules>()
        {
            @Override
            public TrackerConnectionController.Prepared<ParsedRules> prepare(String payload)
            {
                try
                {
                    return TrackerConnectionController.Prepared.ok(
                        new ParsedRules(parseRelayPayload(payload), payload));
                }
                catch (FateLockedBundle.FutureFormatException ex)
                {
                    log.debug("Relay bundle needs a newer plugin: {}", ex.getMessage());
                    return TrackerConnectionController.Prepared.refused(
                        TrackerConnectionController.ImportVerdict.FUTURE_FORMAT);
                }
                catch (RuntimeException ex)
                {
                    log.debug("Relay bundle rejected: {}", ex.getMessage());
                    return TrackerConnectionController.Prepared.refused(
                        TrackerConnectionController.ImportVerdict.INVALID);
                }
            }

            @Override
            public boolean commit(ParsedRules rules, String version)
            {
                return acceptRelayRules(rules, version);
            }
        };

    /**
     * Parsed rules, their snapshot and the text they came as, which is what
     * gets saved. Made where the text was parsed, off the client thread, so
     * the snapshot is ready before the switch (R13).
     */
    private static final class ParsedRules
    {
        final FateLockedBundle bundle;
        final RulesSnapshot snapshot;
        final String text;

        ParsedRules(FateLockedBundle bundle, String text)
        {
            this.bundle = bundle;
            this.snapshot = RulesSnapshot.of(bundle);
            this.text = text;
        }
    }

    /**
     * On the thread that read the relay's reply, never the game thread: parse
     * the payload, accepting only strict v4 rules. Throws FutureFormatException
     * for a newer format, and another RuntimeException for anything else.
     */
    private FateLockedBundle parseRelayPayload(String payload)
    {
        FateLockedBundle parsed = FateLockedBundle.loadFromJson(gson, payload);
        if (parsed.getVersion() != 4 || parsed.isLegacyRules())
        {
            throw new IllegalArgumentException("Relay rules must be a version 4 bundle");
        }
        return parsed;
    }

    /** On the client thread: switch to rules the relay sent, and keep them for the next start. */
    private boolean acceptRelayRules(ParsedRules rules, String version)
    {
        FateLockedBundle parsed = rules.bundle;
        if (!switchRules(rules, RulesSource.RELAY, RulesPrecedence.Arrival.RELAY, Instant.now()))
        {
            return false;
        }
        saveRules(RulesSource.RELAY, rules.text, version);
        panel.flashStatus(Notices.SYNCED, true);
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
    private boolean switchRules(ParsedRules candidate, RulesSource source, RulesPrecedence.Arrival arrival,
        Instant arrivedAt)
    {
        RulesEffects effects;
        try
        {
            effects = effectsOf(candidate.bundle, decisionsFor(candidate.bundle, candidate.snapshot));
        }
        catch (RuntimeException ex)
        {
            log.warn("New rules could not be applied: {}", ex.getMessage());
            return false;
        }
        DecisionService before = decisions;
        active = new ActiveRules(candidate.bundle, candidate.snapshot, source, arrival, arrivedAt);
        refreshDecisions();
        show(candidate.bundle, effects);
        showIsolated("new unlocks", () -> announceUnlocks(UnlockNews.between(before, decisions)));
        return true;
    }

    /**
     * On the client thread: a decision service for the active rules and the
     * character logged in now. Cheap when nothing changed, so it runs every
     * tick as well as on each switch, login and logout.
     */
    private void refreshDecisions()
    {
        ActiveRules current = active;
        String bound = AccountBinding.normalize(AccountBinding.boundAccount(current.getBundle()));
        String player = AccountBinding.normalize(loggedInName());
        if (decisions.rules() == current.getSnapshot()
            && bound.equals(decisionsBound) && player.equals(decisionsPlayer))
        {
            return;
        }
        decisions = DecisionService.create(current.getSnapshot(), bound, player);
        decisionsBound = bound;
        decisionsPlayer = player;
        // The pins follow the decisions (B9): new rules, a login, another character.
        showIsolated("world map pins", this::refreshWorldMapMarkers);
    }

    /** What rules not yet switched in will decide for the character logged in now. */
    private DecisionService decisionsFor(FateLockedBundle bundle, RulesSnapshot snapshot)
    {
        return DecisionService.create(snapshot,
            AccountBinding.normalize(AccountBinding.boundAccount(bundle)),
            AccountBinding.normalize(loggedInName()));
    }

    /** The decision service in force, for every surface that shows the rules. */
    DecisionService decisions()
    {
        return decisions;
    }

    /** The menu tag's reader of menu entries, made on first use (F1); client thread only. */
    private MenuFactsReader menuFacts()
    {
        if (menuFactsReader == null) menuFactsReader = new MenuFactsReader(client);
        return menuFactsReader;
    }

    /** The one reader of where the player and menu targets are (B14). */
    ChunkLocator chunkLocator()
    {
        ChunkLocator current = chunkLocator;
        if (current == null || current.client() != client)
        {
            current = new ChunkLocator(client);
            chunkLocator = current;
        }
        return current;
    }

    /** Recompute the player's current chunk and show everything the active rules mean. */
    private void refreshPanel()
    {
        refreshDecisions();
        show(getBundle(), effectsOf(getBundle(), decisions));
    }

    /**
     * What a rule set means for the sidebar, the HUD, the map and the
     * warnings. Reads the game, so it runs on the client thread, but changes
     * nothing.
     */
    private RulesEffects effectsOf(FateLockedBundle rules, DecisionService ruleDecisions)
    {
        return new RulesEffects(
            overTierGear(ruleDecisions),
            lockedSlayerTask(ruleDecisions));
    }

    /** Show what the rules mean: the HUD fields, then each other change on its own. */
    private void show(FateLockedBundle rules, RulesEffects effects)
    {
        overTierSummary = overTierSummary(effects.overTierGear);
        slayerTaskWarn = effects.lockedSlayerTask == null ? null : effects.lockedSlayerTask.getLabel();
        showIsolated("sidebar", this::refreshSidebar);
        showIsolated("gear warning", () -> warnOverTierGear(effects.overTierGear));
        showIsolated("Slayer warning", () -> warnLockedSlayerTask(effects.lockedSlayerTask));
    }

    /**
     * What a sync opened, said once: a chat line, the banner, and a glow on the world map over
     * each chunk it opened until the player stands in it. Nothing when the setting is off.
     */
    private void announceUnlocks(UnlockNews news)
    {
        if (news.isEmpty() || !config.announceUnlocks()) return;
        writeTravelChat(news.line());
        unlockNews = news;
        unlockShownAt = System.nanoTime();
        if (!news.getChunks().isEmpty())
        {
            Set<CanonicalChunk> next = new LinkedHashSet<>(glowing);
            next.addAll(news.getChunks());
            glowing = Collections.unmodifiableList(new ArrayList<>(next));
        }
    }

    /** The player stands in a chunk: it no longer glows. */
    private void visited(CanonicalChunk chunk)
    {
        List<CanonicalChunk> now = glowing;
        if (chunk == null || now.isEmpty() || !now.contains(chunk)) return;
        List<CanonicalChunk> next = new ArrayList<>(now);
        next.remove(chunk);
        glowing = Collections.unmodifiableList(next);
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

    /** Everything a rule set means, worked out before anything changes. */
    private static final class RulesEffects
    {
        final List<OverTierItem> overTierGear;
        /** The current Slayer task's decision when the rules lock it; null otherwise. */
        final Decision lockedSlayerTask;

        RulesEffects(
            List<OverTierItem> overTierGear,
            Decision lockedSlayerTask)
        {
            this.overTierGear = overTierGear;
            this.lockedSlayerTask = lockedSlayerTask;
        }
    }

    /** Place a click-to-jump marker on each area the tracker says you haven't unlocked yet. */
    private void refreshWorldMapMarkers()
    {
        placeLockedAreaPins(lockedAreaPins(decisions));
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
    /** The areas the tracker says are locked; none on another character. */
    private List<WorldMapPoint> lockedAreaPins(DecisionService ruleDecisions)
    {
        if (!config.worldMapMarkers()) return Collections.emptyList();

        List<WorldMapPoint> pins = new ArrayList<>();
        for (Map.Entry<String, Set<CanonicalChunk>> e : ruleDecisions.areas().entrySet())
        {
            String area = e.getKey();
            if (!ruleDecisions.area(area).isLocked()) continue; // only pin what's still locked

            Set<CanonicalChunk> chunks = e.getValue();
            if (chunks.isEmpty()) continue;
            long sx = 0, sy = 0;
            for (CanonicalChunk c : chunks) { sx += c.getCx(); sy += c.getCy(); }
            int cx = (int) (sx / chunks.size());
            int cy = (int) (sy / chunks.size());
            WorldPoint wp = new WorldPoint((cx << 6) + 32, (cy << 6) + 32, 0);

            WorldMapPoint point = new LockedAreaPoint(wp, lockedPinImage());
            point.setName(area);
            point.setTooltip(area + ": " + Terms.LOCKED);
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

    /** A small padlock pin in the palette's locked colour, drawn again only when the palette changes. */
    private BufferedImage lockedPinImage()
    {
        Palette current = palette;
        if (lockedPinImage != null && lockedPinPalette == current) return lockedPinImage;
        int s = 15;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(current.lockedEdge());
        g.fillOval(1, 1, s - 2, s - 2);
        g.setColor(Palette.UNDERLAY);
        g.drawOval(1, 1, s - 2, s - 2);
        g.setColor(Color.WHITE);
        g.fillRect(s / 2 - 2, s / 2, 5, 4);          // lock body
        g.drawArc(s / 2 - 2, s / 2 - 3, 4, 5, 0, 180); // shackle
        g.dispose();
        lockedPinImage = img;
        lockedPinPalette = current;
        return img;
    }

    // ── Infoboxes: Keys, Fate Points and progress (A15, E7) ──────────────────

    private void refreshInfoBoxes()
    {
        infoBoxManager.removeIf(b -> b instanceof FateLockedInfoBox);
        if (!config.showInfoBoxes()) return;
        for (FateLockedInfoBox.Kind kind : FateLockedInfoBox.Kind.values())
        {
            FateLockedInfoBox box = new FateLockedInfoBox(kind, this);
            Art art = kind.art();
            if (art.kind() == Art.Kind.ITEM)
            {
                // RuneLite fills an item's image in once it loads.
                box.setImage(itemManager.getImage(art.id(), art.detail(), false));
                infoBoxManager.addInfoBox(box);
            }
            else
            {
                infoBoxManager.addInfoBox(box);
                spriteManager.getSpriteAsync(art.id(), art.detail(), image -> {
                    box.setImage(image);
                    infoBoxManager.updateInfoBoxImage(box);
                });
            }
        }
    }

    /** What the player asked for in the sidebar, on the Swing thread. */
    private void onSidebarAction(CardAction action)
    {
        TrackerConnectionController controller = connectionController;
        switch (action)
        {
            case CONNECT:
                beginTrackerPairing();
                break;
            case TURN_ON_SYNC:
                turnOnOnlineSync();
                break;
            case REPAIR:
                if (panel.confirmRepair())
                {
                    beginTrackerPairing();
                }
                break;
            case CANCEL_REPAIR:
                if (controller != null)
                {
                    controller.cancelRepair();
                }
                break;
            case CANCEL_PAIRING:
                if (controller != null)
                {
                    controller.forgetPairing();
                }
                break;
            case DISCONNECT:
                if (controller != null && panel.confirmDisconnect())
                {
                    controller.forgetPairing();
                }
                break;
            case OPEN_PAGE_AGAIN:
                String code = controller == null ? "" : controller.activeCode();
                if (!code.isEmpty())
                {
                    launchTrackerBrowser(PairingSupport.trackerPairingUrl(code));
                }
                break;
            case CHECK_NOW:
                checkTrackerNow();
                break;
            case IMPORT_CLIPBOARD:
                reimportFromClipboard();
                break;
            case LOAD_BACKUP_FILE:
                loadNewestBackupFile();
                break;
            case PAUSE_STRICT_MODE:
                gate.run(this::pauseStrictModeForSixtySeconds);
                break;
            case RESUME_STRICT_MODE:
                gate.run(() -> {
                    strictPause.resume();
                    updateStrictModePanel();
                });
                break;
            default:
                break;
        }
    }

    /** Strict Mode's switch in the sidebar, on the Swing thread. */
    private void setStrictMode(boolean on)
    {
        try
        {
            configManager.setConfiguration(FateLockedConfig.GROUP, "strictMode", on);
        }
        catch (RuntimeException error)
        {
            log.warn("Could not save Strict Mode: {}", error.getMessage());
            panel.flashStatus(Notices.STRICT_NOT_SAVED, false);
            panel.restoreStrictMode();
        }
    }

    /**
     * The online-sync switch, on the Swing thread. Turning it on asks for consent
     * first; the pairing is kept either way.
     */
    private void setOnlineSync(boolean on)
    {
        if (on && !panel.confirmNetworkConnection())
        {
            panel.restoreConnection();
            return;
        }
        gate.run(() -> {
            try
            {
                if (on)
                {
                    connectionSettings.allowNetworkAccess();
                }
                else
                {
                    connectionSettings.refuseNetworkAccess();
                }
            }
            catch (RuntimeException error)
            {
                log.warn("Could not save online sync: {}", error.getMessage());
                panel.flashStatus(on ? Notices.SYNC_NOT_ON : Notices.SYNC_NOT_OFF, false);
                panel.restoreConnection();
            }
        });
    }

    /**
     * On the client thread: work the sidebar out from the plugin's state (C7) and post
     * what changed. Cheap enough for every tick; the presenters are pure.
     */
    void refreshSidebar()
    {
        SidebarPublisher models = sidebarModels();
        if (models == null)
        {
            return;
        }
        ActiveRules current = active;
        DecisionService ruleDecisions = decisions;
        FateLockedBundle bundle = current.getBundle();
        TrackerConnectionSnapshot connection = trackerSnapshot();
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        String player = loggedInName();
        models.status(StatusCardPresenter.present(StatusFacts.builder()
            .connection(connection)
            .source(current.getSource())
            .arrival(current.getArrival())
            .arrivedAt(current.getArrivedAt())
            .exportedAt(bundle.exportedAt())
            .legacy(current.getSnapshot().isLegacy())
            .trust(ruleDecisions.trust())
            .bound(ruleDecisions.isBound())
            .boundAccount(AccountBinding.boundAccount(bundle))
            .loggedInAs(player)
            .paired(trackerPaired())
            .fresh(rulesAreFresh())
            .now(now)
            .zone(zone)
            .build()));
        models.here(hereModel(ruleDecisions));
        StrictModeStatusView strict = strictModeStatus;
        if (strict != null)
        {
            models.strictMode(StrictModeSectionPresenter.present(strict, recentStopped));
        }
        models.run(RunPresenter.present(bundle, ruleDecisions, player));
        TrackerConnectionSettings settings = connectionSettings;
        models.connection(ConnectionPresenter.present(connection, settings != null && settings.networkAccessAllowed(),
            settings != null && settings.isPaired(), current.getSource(), bundle.exportedAt(), now, zone));
        // A login, a logout, another character or new rules can start or stop the noticing.
        if (!java.util.Objects.equals(rollInboxQuiet(), shownQuiet))
        {
            updatePanelRollInbox();
        }
    }

    /** The sidebar's publisher: made at startUp, or on first use by a test that sets the panel alone. */
    private SidebarPublisher sidebarModels()
    {
        if (sidebarModels == null && panel != null)
        {
            sidebarModels = new SidebarPublisher(panel);
        }
        return sidebarModels;
    }

    /**
     * Here for the place the player stands in, worked out again only when it, the rules or
     * what the game says for its undecided rows change.
     */
    private HereModel hereModel(DecisionService ruleDecisions)
    {
        CanonicalChunk chunk = client.getLocalPlayer() == null ? null : chunkLocator().player();
        GameFacts facts = gameFacts(ruleDecisions, chunk);
        if (hereModel == null || ruleDecisions != hereDecisions || !java.util.Objects.equals(chunk, hereChunk)
            || !facts.equals(hereFacts))
        {
            hereModel = herePresenter.present(ruleDecisions, chunk, facts);
            hereDecisions = ruleDecisions;
            hereChunk = chunk;
            hereFacts = facts;
        }
        return hereModel;
    }

    /**
     * The player clicked a row of Here (the owner's review, 28 Sept): put the game's arrow on
     * the nearest one in the chunk they stand in, and say so. With none loaded near them, show
     * the way to the nearest one seen there: the game's arrow on that spot, which the minimap
     * points the way to, a pin on the world map, and Shortest Path's route when it runs. The
     * same row again takes it down. Client thread.
     */
    void pointTo(String category, String row)
    {
        Pointer before = pointer;
        clearPointer();
        CanonicalChunk chunk = chunkLocator().player();
        if (before != null && before.getCategory().equals(category) && before.getRow().equals(row)
            && before.getChunk().equals(chunk))
        {
            return;
        }
        PointTarget target = PointTarget.of(category, row);
        SceneSearch.Found found = target == null ? null : SceneSearch.nearest(client, chunkLocator(), chunk, target);
        if (found != null)
        {
            pointAt(category, row, chunk, found, false, false);
            return;
        }
        WorldPoint from = target == null || chunk == null ? null : chunkLocator().playerWorld();
        SpotMemory.Spot spot = from == null ? null : spots.nearest(chunk, category, target.getLabel(),
            new SpotMemory.Spot(from.getX(), from.getY(), from.getPlane()));
        if (spot == null)
        {
            panel.showHerePointer(PointerText.notFound(row), false);
            return;
        }
        WorldPoint to = new WorldPoint(spot.getX(), spot.getY(), spot.getPlane());
        client.setHintArrow(to);
        boolean routed = shortestPath().route(from, to);
        pin(to, row);
        pointer = new Pointer(category, row, chunk, null, null, client.getHintArrowPoint(), to, true, routed);
        String place = HerePresenter.placeName(decisions, chunk);
        panel.showHerePointer(routed ? PointerText.routed(row, place) : PointerText.remembered(row, place), true);
    }

    /** The game's arrow on something loaded, and the line saying so. */
    private void pointAt(String category, String row, CanonicalChunk chunk, SceneSearch.Found found,
        boolean remembered, boolean routed)
    {
        if (found.getNpc() != null)
        {
            client.setHintArrow(found.getNpc());
        }
        else
        {
            client.setHintArrow(found.getPoint());
        }
        pointer = new Pointer(category, row, chunk, found.getNpc(), found.getPoint(), client.getHintArrowPoint(),
            null, remembered, routed);
        panel.showHerePointer(PointerText.pointing(row, found.isSameFloor()), true);
    }

    /**
     * Takes down the arrow the card put up, if it's still the one showing, with its pin and
     * route, and its line. Client thread.
     */
    void clearPointer()
    {
        Pointer shown = pointer;
        pointer = null;
        pointerLooks = false;
        if (shown != null && ours(shown))
        {
            client.clearHintArrow();
        }
        endWay(shown);
        panel.showHerePointer(null, false);
    }

    /** The way to a spot seen before is over: its pin comes off the world map, and Shortest Path's route down. */
    private void endWay(Pointer shown)
    {
        if (shown == null || !shown.isRemembered())
        {
            return;
        }
        worldMapPointManager.removeIf(WayPoint.class::isInstance);
        if (shown.isRouted())
        {
            shortestPath().clear();
        }
    }

    /**
     * Each tick, while the card's arrow is up: gone once the player reaches it, or leaves the
     * chunk when it was on something loaded there all along; forgotten if the game or another
     * plugin has put up an arrow of its own since. The way to a spot seen before goes on from
     * place to place (keepWay).
     */
    private void keepPointer(CanonicalChunk current)
    {
        Pointer shown = pointer;
        if (shown == null)
        {
            return;
        }
        if (!ours(shown))
        {
            pointer = null;
            pointerLooks = false;
            endWay(shown);
            panel.showHerePointer(null, false);
            return;
        }
        if (shown.getSpot() != null)
        {
            keepWay(shown);
            return;
        }
        Player local = client.getLocalPlayer();
        LocalPoint at = shown.getNpc() != null ? shown.getNpc().getLocalLocation() : shown.getPoint();
        LocalPoint player = local == null ? null : local.getLocalLocation();
        if (!shown.isRemembered() && !shown.getChunk().equals(current)
            || at != null && player != null && player.distanceTo(at) <= POINTER_REACHED)
        {
            clearPointer();
        }
    }

    /**
     * On the way to a spot seen before: the arrow moves onto the thing once one loads; and at
     * the spot with none loaded, it has moved or gone, so the spot is forgotten.
     */
    private void keepWay(Pointer shown)
    {
        if (pointerLooks)
        {
            pointerLooks = false;
            PointTarget target = PointTarget.of(shown.getCategory(), shown.getRow());
            SceneSearch.Found found = target == null ? null
                : SceneSearch.nearest(client, chunkLocator(), shown.getChunk(), target);
            if (found != null)
            {
                pointAt(shown.getCategory(), shown.getRow(), shown.getChunk(), found, true, shown.isRouted());
                return;
            }
        }
        WorldPoint at = chunkLocator().playerWorld();
        if (at != null && at.distanceTo(shown.getSpot()) <= POINTER_REACHED / Perspective.LOCAL_TILE_SIZE)
        {
            WorldPoint spot = shown.getSpot();
            spots.forget(shown.getChunk(), shown.getCategory(), shown.getRow(),
                new SpotMemory.Spot(spot.getX(), spot.getY(), spot.getPlane()));
            clearPointer();
            panel.showHerePointer(PointerText.gone(shown.getRow(), HerePresenter.placeName(decisions,
                shown.getChunk())), false);
        }
    }

    /**
     * Turning the plugin off: its arrow comes down, on the client thread, if it's still the one
     * showing, with its pin and route.
     */
    private void dropPointer()
    {
        Pointer shown = pointer;
        pointer = null;
        pointerLooks = false;
        if (shown != null)
        {
            clientThread.invoke(() -> {
                if (ours(shown))
                {
                    client.clearHintArrow();
                }
                endWay(shown);
            });
        }
    }

    /** Whether the arrow showing is still the card's own. */
    private boolean ours(Pointer shown)
    {
        if (!client.hasHintArrow())
        {
            return false;
        }
        return shown.getNpc() != null ? client.getHintArrowNpc() == shown.getNpc()
            : shown.getArrow() != null && shown.getArrow().equals(client.getHintArrowPoint());
    }

    /** The arrow the card put up: for which row, in which chunk, and on what. */
    @lombok.Value
    static class Pointer
    {
        String category;
        String row;
        CanonicalChunk chunk;
        /** The NPC it follows, or null for an object's point or a spot. */
        NPC npc;
        LocalPoint point;
        /** Where the game put it, to tell it from an arrow the game or another plugin puts up. */
        WorldPoint arrow;
        /** The spot seen before that it shows the way to, until the thing itself loads; else null. */
        WorldPoint spot;
        /** Whether it began as the way to a spot seen before: it goes on from place to place, with its pin. */
        boolean remembered;
        /** Whether Shortest Path was asked for the way. */
        boolean routed;
    }

    /** A pin on the world map at the spot the card shows the way to, with the game's own destination flag. */
    private void pin(WorldPoint to, String row)
    {
        BufferedImage flag = spriteManager == null ? null : spriteManager.getSprite(SpriteID.MAPMARKER, 0);
        if (flag == null || worldMapPointManager == null)
        {
            return;
        }
        WayPoint point = new WayPoint(to, flag);
        point.setName(row);
        point.setTooltip(row + " (seen here)");
        point.setTarget(to);
        point.setJumpOnClick(true);
        point.setSnapToEdge(true);
        worldMapPointManager.add(point);
    }

    /** The world map pin for a spot the card shows the way to, so it can remove exactly its own. */
    static final class WayPoint extends WorldMapPoint
    {
        WayPoint(WorldPoint point, BufferedImage image)
        {
            super(point, image);
        }
    }

    /** Shortest Path, as RuneLite runs it now. */
    ShortestPathHandOff shortestPath()
    {
        if (shortestPath == null)
        {
            shortestPath = new ShortestPathHandOff(pluginManager, eventBus);
        }
        return shortestPath;
    }

    /** An NPC came into view: remembered where the Here card could point at it. */
    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        NPC npc = event.getNpc();
        if (npc == null || decisions.trust() != Trust.TRUSTED)
        {
            return;
        }
        NPCComposition shown = npc.getTransformedComposition();
        seen(shown != null ? shown.getName() : npc.getName(), shown != null ? shown.getActions() : null,
            chunkLocator().world(npc));
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        seen(event.getGameObject());
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event)
    {
        seen(event.getWallObject());
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        seen(event.getDecorativeObject());
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event)
    {
        seen(event.getGroundObject());
    }

    /** An object loaded: remembered where the Here card could point at it. */
    private void seen(TileObject object)
    {
        if (object == null || decisions.trust() != Trust.TRUSTED)
        {
            return;
        }
        ObjectComposition shown = SceneSearch.shown(client, object);
        if (shown != null)
        {
            seen(shown.getName(), shown.getActions(), chunkLocator().world(object));
        }
    }

    /**
     * Something seen at a spot, by the name and options it shows: remembered for each row of
     * its chunk's card that points at it, and looked for at the next tick when it's what the
     * arrow shows the way to. Only in the real world, and for these rules' own character.
     */
    private void seen(String name, String[] options, WorldPoint at)
    {
        if (name == null || at == null)
        {
            return;
        }
        CanonicalChunk chunk = CanonicalChunk.ofTile(at.getX(), at.getY());
        Pointables here = pointables(chunk);
        if (!here.names.contains(name.trim().toLowerCase(Locale.ROOT)))
        {
            return;
        }
        SpotMemory.Spot spot = new SpotMemory.Spot(at.getX(), at.getY(), at.getPlane());
        Pointer shown = pointer;
        for (PointTarget target : here.targets)
        {
            if (!target.matches(name, options))
            {
                continue;
            }
            spots.see(chunk, target.getCategory(), target.getLabel(), spot);
            if (shown != null && shown.getSpot() != null && shown.getChunk().equals(chunk)
                && shown.getCategory().equals(target.getCategory()) && shown.getRow().equals(target.getLabel()))
            {
                pointerLooks = true;
            }
        }
    }

    /** What a chunk's card can point at, read once for the rules in force. */
    private Pointables pointables(CanonicalChunk chunk)
    {
        if (pointablesFor != decisions)
        {
            pointables.clear();
            pointablesFor = decisions;
        }
        return pointables.computeIfAbsent(chunk, c -> new Pointables(HerePresenter.pointable(decisions, c)));
    }

    /** What a chunk's card can point at, and every name those go by, lower case. */
    private static final class Pointables
    {
        final List<PointTarget> targets;
        final Set<String> names = new HashSet<>();

        Pointables(List<PointTarget> targets)
        {
            this.targets = targets;
            for (PointTarget target : targets)
            {
                names.addAll(target.getNames());
            }
        }
    }

    /** Where things were seen, read from this computer as the plugin starts, off the client thread. */
    private void loadSpots()
    {
        Path path = dataDirectory().toPath().resolve(SpotMemory.FILE);
        fileWriter.submit(() -> {
            try
            {
                spots.load(gson, path);
            }
            catch (IOException ex)
            {
                log.warn("Could not read where things were seen: {}", ex.getMessage());
            }
        });
    }

    /** Spots seen since the last save are written at most every half minute. */
    private void saveSpotsIfDue()
    {
        int now = client.getTickCount();
        if (spots.changed() && (now - spotsSavedTick >= SPOTS_SAVE_TICKS || now < spotsSavedTick))
        {
            spotsSavedTick = now;
            saveSpots();
        }
    }

    /** Spots seen since the last save, written off the client thread. */
    private void saveSpots()
    {
        if (!spots.changed())
        {
            return;
        }
        Path path = dataDirectory().toPath().resolve(SpotMemory.FILE);
        fileWriter.submit(() -> {
            try
            {
                spots.save(gson, path);
            }
            catch (IOException ex)
            {
                log.warn("Could not save where things were seen: {}", ex.getMessage());
            }
        });
    }

    /**
     * What the game says, for the rows here the tracker leaves undecided: the quests they
     * name, quest points, levels, the world, and what the player carries (RowChecks). Read
     * once a tick, and only where such a row is. Client thread; elsewhere, nothing.
     */
    GameFacts gameFacts(DecisionService ruleDecisions, CanonicalChunk chunk)
    {
        if (chunk == null || !client.isClientThread() || client.getGameState() != GameState.LOGGED_IN)
        {
            return GameFacts.NONE;
        }
        int tick = client.getTickCount();
        if (tick == factsTick && chunk.equals(factsChunk) && ruleDecisions == factsDecisions)
        {
            return lastFacts;
        }
        List<String> undecided = new ArrayList<>();
        ruleDecisions.details(chunk).ifPresent(snapshot -> {
            for (List<ChunkPermissionRow> rows : snapshot.getCategories().values())
            {
                for (ChunkPermissionRow row : rows)
                {
                    if (row.getStatus() == PermissionStatus.UNKNOWN)
                    {
                        undecided.add(row.getDetail());
                    }
                }
            }
        });
        GameFacts facts = GameFacts.NONE;
        if (!undecided.isEmpty())
        {
            Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
            for (Quest quest : RowChecks.questsNamed(undecided))
            {
                quests.put(quest, quest.getState(client));
            }
            Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
            for (Skill skill : Skill.values())
            {
                if (!"Overall".equals(skill.getName()))
                {
                    levels.put(skill, client.getRealSkillLevel(skill));
                }
            }
            Set<String> carried = new HashSet<>();
            boolean light = false;
            for (int id : new int[] {InventoryID.INV, InventoryID.WORN})
            {
                ItemContainer container = client.getItemContainer(id);
                if (container == null)
                {
                    continue;
                }
                for (Item item : container.getItems())
                {
                    if (item.getId() <= 0)
                    {
                        continue;
                    }
                    carried.add(itemManager.getItemComposition(item.getId()).getName().toLowerCase(Locale.ROOT));
                    light |= RowChecks.LIGHT_SOURCES.contains(item.getId());
                }
            }
            java.util.EnumSet<WorldType> world = client.getWorldType();
            facts = new GameFacts(world == null ? null : world.contains(WorldType.MEMBERS),
                client.getVarpValue(VarPlayerID.QP), levels, quests, carried, light);
        }
        factsTick = tick;
        factsChunk = chunk;
        factsDecisions = ruleDecisions;
        lastFacts = facts;
        return facts;
    }

    /**
     * Once a second: at the login screen there are no game ticks, so this keeps ages
     * ("synced 2 min ago") and a pause's countdown current there.
     */
    @Schedule(period = 1, unit = java.time.temporal.ChronoUnit.SECONDS)
    public void refreshSidebarWhileLoggedOut()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            gate.run(this::updateStrictModePanel);
        }
    }

    /**
     * Paired with online sync off: ask for consent, then turn sync on. The
     * saved pairing is picked up again; no new code is made.
     */
    private void turnOnOnlineSync()
    {
        if (!panel.confirmNetworkConnection())
        {
            return;
        }
        gate.run(() -> {
            try
            {
                connectionSettings.allowNetworkAccess();
            }
            catch (RuntimeException error)
            {
                panel.flashStatus(Notices.SYNC_NOT_ON, false);
            }
        });
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
                    panel.flashStatus(Notices.SYNC_NOT_ON, false);
                    return;
                }
            }
            if (!connectionSettings.networkAccessAllowed()) return;
            String url = connectionController.beginPairing();
            String code = connectionController.activeCode();
            SwingUtilities.invokeLater(onClient.guard(
                () -> openTrackerPairing(url, code)));
        });
    }

    /**
     * RuneLite's LinkBrowser opens the page on its own thread and shows its
     * own copy-the-link dialog if the browser won't open, so there is no
     * failure to report here.
     */
    private void openTrackerPairing(String url, String code)
    {
        if (connectionSettings.networkAccessAllowed()
            && samePairing(code, connectionController.activeCode()))
        {
            launchTrackerBrowser(url);
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

    private static NavigationButton buildNavigationButton(FateLockedPanel target)
    {
        return NavigationButton.builder()
            .tooltip("Fate Locked Ironman")
            .icon(navigationIcon())
            .priority(7)
            .panel(target)
            .build();
    }

    /**
     * The sidebar button's icon: the crystal key, the web app's own icon, drawn at the
     * 16 px RuneLite shows it. RuneLite rescales a larger icon smoothly, which blurred the
     * old 24 px key, and game art isn't loaded when the button is added.
     */
    static BufferedImage navigationIcon()
    {
        return ImageUtil.loadImageResource(FateLockedPlugin.class, "nav_icon.png");
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
        return trackerSnapshot().getLastSync();
    }

    /** The connection as the controller last showed it; not connected before it exists. */
    private TrackerConnectionSnapshot trackerSnapshot()
    {
        TrackerConnectionController controller = connectionController;
        TrackerConnectionSnapshot shown = controller == null ? null : controller.snapshot();
        return shown == null ? TrackerConnectionSnapshot.disconnected() : shown;
    }

    /**
     * The sidebar's Check now, on the Swing thread: make a check due, then
     * run the tracker tick at once rather than waiting up to 4 seconds.
     */
    private void checkTrackerNow()
    {
        TrackerConnectionController controller = connectionController;
        if (controller != null && controller.checkNow())
        {
            executor.execute(gate.guard(this::pollTrackerConnection));
        }
    }

    private void pollTrackerConnection()
    {
        TrackerConnectionController controller = connectionController;
        if (controller == null) return;
        try
        {
            controller.pollIfDue();
        }
        catch (RuntimeException error)
        {
            // A scheduled task that throws is never run again: log it and
            // keep checking.
            if (trackerTickFailureLimiter.shouldReport(
                String.valueOf(error), System.currentTimeMillis()))
            {
                log.warn("Tracker check failed", error);
            }
        }
    }

    private void updatePanelRollInbox()
    {
        FateLockedBundle rules = getBundle();
        DetectedEventStore store = detectedEvents;
        List<DetectedEventStore.Entry> events = store == null
            ? java.util.Collections.<DetectedEventStore.Entry>emptyList()
            : SpentBosses.without(rules, store.offered(rules == null ? null : rules.getRunId()));
        shownWarningCount = activeWarningCount();
        shownQuiet = rollInboxQuiet();
        SidebarPublisher models = sidebarModels();
        if (models != null)
        {
            models.rollInbox(RollInboxPresenter.present(events, shownWarningCount, historySaveFailed,
                AccountBinding.boundAccount(rules) != null, rollInboxNotice, shownQuiet));
        }
    }

    /** Why RuneLite notices nothing for the character logged in, as the Roll inbox says it; null while it does. */
    private String rollInboxQuiet()
    {
        return DetectionGate.quiet(getBundle(), loggedInName(), client.getWorldType());
    }

    /** Why a copy didn't happen. */
    static final String COPY_FAILED = "Couldn't copy: the clipboard is busy. Try again.";

    /** What a copy did, and where the player pastes it. */
    static String copied(int events)
    {
        return "Copied " + events + (events == 1 ? " event" : " events") + ". In the tracker's Roll Inbox, choose "
            + Terms.PASTE_FROM_RUNELITE + ".";
    }

    /**
     * Copy for tracker (Stage 4, C2), from the card's button on the Swing thread: every event the Roll
     * inbox offers, in the form the tracker's Paste from RuneLite reads. They turn Copied only once
     * the clipboard has them. The tracker keeps an event it has once, so copying again is harmless.
     */
    private void copyForTracker()
    {
        DetectedEventStore store = detectedEvents;
        FateLockedBundle rules = getBundle();
        List<DetectedEventStore.Entry> offered = store == null || rules == null
            ? java.util.Collections.<DetectedEventStore.Entry>emptyList()
            : SpentBosses.without(rules, store.offered(rules.getRunId()));
        if (offered.isEmpty()) return;
        List<FateEvent> events = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (DetectedEventStore.Entry entry : offered)
        {
            events.add(entry.getEvent());
            ids.add(entry.getEvent().getEventId());
        }
        ClientThreadGate onClient = gate;
        if (!panel.copyToClipboard(store.copyOf(events)))
        {
            rollInboxNotice = COPY_FAILED;
            onClient.run(this::updatePanelRollInbox);
            return;
        }
        rollInboxNotice = copied(events.size());
        fileWriter.submit(() -> {
            try
            {
                store.mark(ids, DetectedEventStore.Status.COPIED);
            }
            catch (IOException ex)
            {
                log.warn("Could not mark the copied events", ex);
            }
            onClient.run(this::updatePanelRollInbox);
        });
    }

    /** Dismiss, on a row of the Roll inbox, from the Swing thread: the event isn't offered again. */
    private void dismissEvent(String eventId)
    {
        DetectedEventStore store = detectedEvents;
        if (store == null || eventId == null) return;
        rollInboxNotice = null;
        ClientThreadGate onClient = gate;
        fileWriter.submit(() -> {
            try
            {
                store.mark(java.util.Collections.singletonList(eventId), DetectedEventStore.Status.DISMISSED);
            }
            catch (IOException ex)
            {
                log.warn("Could not dismiss a detected event", ex);
            }
            onClient.run(this::updatePanelRollInbox);
        });
    }

    /** Keep the sidebar's Warnings count current as you move, change gear and get tasks. */
    private void refreshWarningCount()
    {
        if (activeWarningCount() != shownWarningCount) updatePanelRollInbox();
    }

    private int activeWarningCount()
    {
        int warnings = 0;
        if (lastStatus == PermissionStatus.LOCKED) warnings++;
        if (slayerTaskWarn != null && !slayerTaskWarn.trim().isEmpty()) warnings++;
        if (overTierSummary != null && !overTierSummary.trim().isEmpty()) warnings++;
        return warnings;
    }
}
