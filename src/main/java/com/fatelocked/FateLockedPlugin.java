package com.fatelocked;

import com.google.gson.Gson;
import com.fatelocked.events.FateEventHistory;
import com.fatelocked.events.FateEventFactory;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.ItemTier;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.Trust;
import com.fatelocked.panel.ChunkPanelViewModel;
import com.fatelocked.panel.ChunkPanelViewModelFactory;
import com.fatelocked.panel.LocalTimeText;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.HerePresenter;
import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.sidebar.StrictModeSectionPresenter;
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
import com.fatelocked.detectors.CollectionLogDetector;
import com.fatelocked.detectors.ClueCompletionDetector;
import com.fatelocked.detectors.CombatAchievementDetector;
import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.detectors.QuestDetector;
import com.fatelocked.detectors.RaidDetector;
import com.fatelocked.detectors.SkillLevelDetector;
import com.fatelocked.detectors.SlayerTaskDetector;
import com.fatelocked.detectors.DiaryTierReviewDetector;
import com.fatelocked.detectors.PetDropDetector;
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
import net.runelite.client.task.Schedule;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Duration;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final RepeatedValueLimiter trackerTickFailureLimiter =
        new RepeatedValueLimiter(TimeUnit.MINUTES.toMillis(15));
    private FateEventHistory eventHistory;
    private boolean historySaveFailed;
    private StrictModeAuditLog strictAuditLog;
    /** The account the history, audit log and Slayer task files belong to. */
    private long accountFilesHash;
    private final FateEventFactory eventFactory = new FateEventFactory();
    private final SkillLevelDetector skillLevelDetector = new SkillLevelDetector();
    private final QuestDetector questDetector = new QuestDetector();
    private final CombatAchievementDetector combatAchievementDetector = new CombatAchievementDetector();
    private final CollectionLogDetector collectionLogDetector = new CollectionLogDetector();
    private final ClueCompletionDetector clueCompletionDetector = new ClueCompletionDetector();
    private final RaidDetector raidDetector = new RaidDetector();
    private final BossKillDetectorV2 bossKillDetectorV2 = new BossKillDetectorV2();
    private final DiaryTierReviewDetector diaryTierReviewDetector = new DiaryTierReviewDetector();
    private final PetDropDetector petDropDetector = new PetDropDetector();
    private SlayerTaskDetector slayerTaskDetector;
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
    /** The bound account and character the decision service was built for (client thread). */
    private String decisionsBound = "";
    private String decisionsPlayer = "";
    private final ChunkPanelViewModelFactory chunkPanelFactory =
        new ChunkPanelViewModelFactory();
    /** The sidebar's models, posted only when they change (C7, A9). */
    private SidebarPublisher sidebarModels;
    private final HerePresenter herePresenter = new HerePresenter();
    /** Here, as last worked out, for the decision service and chunk it was worked out for. */
    private HereModel hereModel;
    private DecisionService hereDecisions;
    private CanonicalChunk hereChunk;
    /** What Strict Mode stopped lately, newest first, for its section. */
    private List<String> recentStopped = java.util.Collections.emptyList();
    private final GuardedActionFactory guardedActionFactory = new GuardedActionFactory();
    /** What a click is, by id in the tracker's travel table: Strict Mode and the tags read the same answer (F4). */
    private final IntentClassifier intentClassifier = new IntentClassifier();
    /** Where the player and menu targets are, for the client this plugin reads (B14). */
    private volatile ChunkLocator chunkLocator;
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

    /** How long the locked-entry screen flash lasts. */
    public static final long LOCKED_FLASH_MS = 1600;
    @Getter private volatile long lockedFlashUntil;

    private CanonicalChunk lastChunk;
    /** The decision for the chunk the player was last in, for the once-on-the-way-in alert. */
    private PermissionStatus lastStatus;
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
    /** The logged-in account's finished diary tiers; null until its files open. */
    private DiaryTierMemory diaryTiers;
    /** Whether this session's full reading of the diary tiers is still to come. */
    private boolean diaryReadingDue = true;
    /**
     * Diary regions in DIARY_VARBITS order (4 tiers each): the name the
     * tracker's tier ids use, then the region's full name.
     */
    private static final String[][] DIARY_REGIONS = {
        {"Ardougne", "Ardougne"}, {"Desert", "Desert"}, {"Falador", "Falador"},
        {"Fremennik", "Fremennik"}, {"Kandarin", "Kandarin"}, {"Karamja", "Karamja"},
        {"Kourend", "Kourend & Kebos"}, {"Lumbridge", "Lumbridge & Draynor"},
        {"Morytania", "Morytania"}, {"Varrock", "Varrock"},
        {"Western", "Western Provinces"}, {"Wilderness", "Wilderness"},
    };
    private static final String[] DIARY_TIERS = { "Easy", "Medium", "Hard", "Elite" };
    /** Varbit id → the tracker's tier id ("Lumbridge Easy"); key set doubles as the per-event filter. */
    private static final Map<Integer, String> DIARY_TIER_IDS = new HashMap<>();
    /** Varbit id → the tier's full name ("Lumbridge & Draynor Easy"), for chat. */
    private static final Map<Integer, String> DIARY_VARBIT_NAMES = new HashMap<>();
    /** The tracker's tier id → the tier's full name. */
    private static final Map<String, String> DIARY_TIER_NAMES = new HashMap<>();
    static
    {
        for (int i = 0; i < DIARY_VARBITS.length; i++)
        {
            String tier = " " + DIARY_TIERS[i % 4];
            DIARY_TIER_IDS.put(DIARY_VARBITS[i], DIARY_REGIONS[i / 4][0] + tier);
            DIARY_VARBIT_NAMES.put(DIARY_VARBITS[i], DIARY_REGIONS[i / 4][1] + tier);
            DIARY_TIER_NAMES.put(DIARY_REGIONS[i / 4][0] + tier, DIARY_REGIONS[i / 4][1] + tier);
        }
    }
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

    /** The client's own broadcast on a new Collection Log entry: "New item added to your collection log: X". */
    private static final Pattern COLLECTION_LOG_ITEM =
        Pattern.compile("new item added to your collection log:\\s*(.+)", Pattern.CASE_INSENSITIVE);
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

        panel.onAction(this::onSidebarAction);
        panel.onStrictModeToggle(this::setStrictMode);
        panel.onSyncToggle(this::setOnlineSync);
        panel.onIntroDismiss(() -> configManager.setConfiguration(
            FateLockedConfig.GROUP, "strictModeIntroSeen", true));
        panel.setRollInboxLink(FateLockedPanel.TRACKER_URL);
        navButton = buildNavigationButton(panel);
        clientToolbar.addNavigation(navButton);

        started.run(() -> {
            startSessionTracking();
            updateStrictModePanel();
            updateStrictAuditPanel();
            updatePanelRollInbox();
            refreshSidebar();
            refreshInfoBoxes();
        });
        loadSavedRules();
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
        keyManager.unregisterKeyListener(pauseStrictHotkey);
        worldMapPointManager.removeIf(LockedAreaPoint.class::isInstance);
        infoBoxManager.removeIf(b -> b instanceof FateLockedInfoBox);
        active = ActiveRules.NONE;
        // A pause belongs to this start: turning the plugin off and on ends it.
        strictPause.resume();
        decisions = DecisionService.create(RulesSnapshot.empty(), null, null);
        decisionsBound = "";
        decisionsPlayer = "";
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
        if (state == GameState.LOGIN_SCREEN)
        {
            // Logged out: the next login warns and announces afresh.
            awaitingLogin = true;
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
            AccountFiles files = AccountFiles.open(gson, dataPath, accountHash, name, boundCharacter);
            onClient.run(() -> useAccountFiles(files));
        });
    }

    private void useAccountFiles(AccountFiles files)
    {
        // Another account logged in while these opened: its own are coming.
        if (client.getAccountHash() != files.accountHash) return;
        accountFilesHash = files.accountHash;
        eventHistory = files.history;
        historySaveFailed = files.history == null;
        strictAuditLog = files.auditLog;
        slayerTaskDetector = files.slayer;
        diaryTiers = files.diaryTiers;
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
        diaryReadingDue = true;
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

        petDropDetector.detect(Text.removeTags(raw), System.currentTimeMillis())
            .ifPresent(this::record);
        if (slayerTaskDetector != null && accountFilesInUse()
            && (m.contains("completed your task") || m.contains("return to a slayer master")))
        {
            SlayerTaskDetector detector = slayerTaskDetector;
            String signature = Text.removeTags(raw);
            ClientThreadGate onClient = gate;
            fileWriter.submit(() -> {
                Optional<DetectedEvent> completed;
                try
                {
                    completed = detector.completion(signature);
                }
                catch (IOException ex)
                {
                    log.debug("Could not update Slayer state", ex);
                    return;
                }
                completed.ifPresent(event -> onClient.run(() -> record(event)));
            });
        }

        // Combat achievements stay on chat (their varbits are progress counts with
        // totals that shift as Jagex adds tasks). Diaries are detected via varbit
        // (onVarbitChanged), quests via the reward widget — both more reliable.
        if (m.contains("combat task:"))
        {
            // The raw line: its closing tag marks where the task's name ends.
            DetectedEvent detected = combatAchievementDetector.detect(raw);
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
            SlayerAssignment assignment = SlayerAssignment.fromChat(
                Text.removeTags(raw), client.getVarbitValue(VarbitID.SLAYER_MASTER));
            if (assignment != null)
            {
                slayerAssignment = assignment;
                if (slayerTaskDetector != null && accountFilesInUse())
                {
                    SlayerTaskDetector detector = slayerTaskDetector;
                    String task = assignment.getTask();
                    fileWriter.submit(() -> {
                        try
                        {
                            detector.assignment(task, null, 0, false);
                        }
                        catch (IOException ex)
                        {
                            log.debug("Could not save Slayer assignment", ex);
                        }
                    });
                }
                if (config.warnLockedSlayer())
                {
                    recomputeSlayer();
                }
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
        if (!config.warnLockedSlayer() || assignment == null) return null;
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
            .append(ChatColorType.NORMAL).append("Your slayer task (")
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
            && config.warnLockedBank())
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
    ChunkPanelViewModel viewModelFor(DecisionService ruleDecisions, CanonicalChunk chunk)
    {
        if (chunk == null) return null;
        return chunkPanelFactory.create(
            ruleDecisions,
            chunk,
            trackerPaired() ? trackerLastSync() : null);
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
                String name = QuestDetector.questName(Text.removeTags(w.getText()));
                if (name != null) return name;
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
        clueCompletionDetector.detect(type, ev.getName()).ifPresent(this::record);
        java.util.Optional<DetectedEvent> detected =
            bossKillDetectorV2.detect(type, ev.getName(), client.getGameCycle());
        if (!detected.isPresent())
        {
            detected = raidDetector.detect(type, ev.getName(), ev.getCombatLevel());
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
        // A diary tier's varbit becomes 1 when the tier is finished.
        // VarbitChanged fires for every varbit in the game, so this is a
        // set-lookup filter.
        String tierId = DIARY_TIER_IDS.get(ev.getVarbitId());
        if (tierId == null || ev.getValue() != 1) return;
        DiaryTierMemory memory = diaryTiers;
        // Until this session's full reading, changes are the login's own
        // tiers arriving from the server; the reading covers them.
        if (diaryReadingDue || memory == null || !accountFilesInUse()) return;
        ClientThreadGate onClient = gate;
        fileWriter.submit(() -> {
            boolean fresh;
            try
            {
                fresh = memory.finishedNow(tierId);
            }
            catch (IOException ex)
            {
                log.debug("Could not save the finished diary tiers", ex);
                return;
            }
            if (fresh) onClient.run(() -> diaryTierFinished(tierId));
        });
    }

    /**
     * At the first tick of a session once the account's files are open,
     * when every tier has come from the server: read all 48 tiers. Tiers
     * finished since the account was last seen, even with RuneLite closed,
     * count now.
     */
    private void readDiaryTiersIfDue()
    {
        DiaryTierMemory memory = diaryTiers;
        if (!diaryReadingDue || memory == null || !accountFilesInUse()) return;
        diaryReadingDue = false;
        List<String> finished = new ArrayList<>();
        for (int id : DIARY_VARBITS)
        {
            if (client.getVarbitValue(id) == 1) finished.add(DIARY_TIER_IDS.get(id));
        }
        ClientThreadGate onClient = gate;
        fileWriter.submit(() -> {
            List<String> fresh;
            try
            {
                fresh = memory.reading(finished);
            }
            catch (IOException ex)
            {
                log.debug("Could not read the finished diary tiers", ex);
                return;
            }
            for (String tier : fresh)
            {
                onClient.run(() -> diaryTierFinished(tier));
            }
        });
    }

    private void diaryTierFinished(String tierId)
    {
        diaryTierReviewDetector.onVarbit(tierId, 0, 1).ifPresent(this::record);
        if (config.rollNudges())
        {
            nudge("Diary complete: " + DIARY_TIER_NAMES.get(tierId)
                + " — may be worth a roll; log it in the tracker.");
        }
    }

    private void record(DetectedEvent detected)
    {
        if (detected == null || eventHistory == null || !accountFilesInUse()
            || !detectionCounts()) return;
        FateLockedBundle currentBundle = getBundle();
        String account = loggedInName();
        FateEvent event = eventFactory.create(
            detected.getType(), detected.getCanonicalLabel(), detected.getConfidence(),
            detected.getEvidence(), currentBundle, account,
            detected.getDetectorId(), detected.getDetectorVersion());
        FateEventHistory history = eventHistory;
        ClientThreadGate onClient = gate;
        fileWriter.submit(() -> {
            // Null: the write failed; false: a duplicate, nothing written.
            Boolean recorded;
            try
            {
                recorded = history.record(event);
            }
            catch (IOException ex)
            {
                log.warn("Could not persist local Fate event history", ex);
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
                }
                updatePanelRollInbox();
            });
        });
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
    /**
     * Whether detections count here: the rules' bound character, logged in on
     * a world whose progress is the account's own. Detections and reminders
     * both go through it, so a main account or a Leagues world sharing this
     * RuneLite gets neither.
     */
    private boolean detectionCounts()
    {
        return DetectionGate.allows(getBundle(), loggedInName(), client.getWorldType());
    }

    private void nudge(String text)
    {
        if (!detectionCounts()) return;
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
        if (!config.warnOverTierGear()) return Collections.emptyList();
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
        if (!config.warnAccountMismatch()) return;
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

        refreshDecisions();
        readDiaryTiersIfDue();
        updateStrictModePanel();

        // Once per login, flag if the character doesn't match the bound account.
        checkBoundAccount();

        CanonicalChunk current = chunkLocator().player();
        if (current == null) return;

        FateLockedBundle b = getBundle();
        Decision entry = decisions.chunk(current);
        PermissionStatus status = entry.getStatus();
        String label = decisions.areaName(current);

        boolean changed = !current.equals(lastChunk);
        if (changed)
        {
            // Only the rules' own answers are announced (B6): never a chunk
            // they don't map (dungeons, instances, every chunk before rules
            // load), nor another character's rules. NOT_READY is owned, so
            // it reads as unlocked and never alerts.
            if (config.chatOnEnter() && status != PermissionStatus.UNKNOWN)
            {
                announceEntry(current, label, status != PermissionStatus.LOCKED ? null : entry);
            }
            // Flash, sound and notification once on the way INTO locked
            // territory, not at every chunk inside it, whatever the chat
            // setting.
            if (status == PermissionStatus.LOCKED && lastStatus != PermissionStatus.LOCKED)
            {
                lockedFlashUntil = System.currentTimeMillis() + LOCKED_FLASH_MS;
                if (config.warnOnLocked())
                {
                    client.playSoundEffect(2277); // death squelch — good "you done messed up" cue
                    notifyIfEnabled(label == null
                        ? "Entered LOCKED chunk (" + current.getCx() + ", " + current.getCy() + ")"
                        : "Entered LOCKED chunk: " + label);
                }
            }
            lastChunk = current;
            lastStatus = status;
        }
        refreshWarningCount();
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
    }

    /** Strict Mode's chat line, which names it and says how to pause (B15). */
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
     * Tag right-click menu entries with a red (LOCKED) marker: the "are you
     * sure?" before you ever click. The decision service decides (B2), so a
     * tag never disagrees with the sidebar or Strict Mode, and another
     * character, or nobody logged in, sees none.
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (!config.tagLockedMenus() && !config.tagLockedTeleports()) return;
        DecisionService ruleDecisions = decisions;
        if (ruleDecisions.trust() != Trust.TRUSTED) return;

        MenuEntry entry = event.getMenuEntry();
        if (!taggedLocked(entry, ruleDecisions)) return;
        String t = entry.getTarget();
        String base = t == null ? "" : t;
        if (!base.contains("(LOCKED)"))
        {
            entry.setTarget(base + " <col=ef4444>(LOCKED)</col>");
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
        TravelMatch travel = intentClassifier.classify(new MenuFactsReader(client).read(entry), ruleDecisions.travelTable());
        if (travel != null)
        {
            return config.tagLockedTeleports() && travel.getOption().destination() != null
                && ruleDecisions.travel(travel.getMethod(), travel.getOption()).isLocked();
        }
        GuardedAction action = guardedActionFactory.from(entry, chunkLocator());
        return config.tagLockedMenus() && action.getChunk() != null
            && ruleDecisions.chunk(action.getChunk()).isLocked();
    }

    /** Chat line for entering a mapped chunk; {@code region} is null for a chunk only the tracker names. */
    /** One chat line per chunk entered; a locked one says why, in the tracker's words (E8). */
    private void announceEntry(CanonicalChunk chunk, String region, Decision locked)
    {
        ChatMessageBuilder msg = new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT).append("[Fate Locked] ")
            .append(ChatColorType.NORMAL).append("Chunk ")
            .append("(" + chunk.getCx() + ", " + chunk.getCy() + ")");
        if (region != null)
        {
            msg.append(ChatColorType.NORMAL).append(" · ")
               .append(ChatColorType.HIGHLIGHT).append(region);
        }
        msg.append(ChatColorType.NORMAL).append(locked == null ? " ✓ unlocked"
            : locked.getReason() == null ? " ⚠ LOCKED" : " ⚠ LOCKED: " + locked.getReason());

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
        active = new ActiveRules(candidate.bundle, candidate.snapshot, source, arrival, arrivedAt);
        refreshDecisions();
        show(candidate.bundle, effects);
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
            () -> ProgressText.infoBoxText(decisions.progress()),
            () -> ProgressText.infoBoxTooltip(decisions.progress())));
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

    /** Here for the place the player stands in, worked out again only when it or the rules change. */
    private HereModel hereModel(DecisionService ruleDecisions)
    {
        CanonicalChunk chunk = client.getLocalPlayer() == null ? null : chunkLocator().player();
        if (hereModel == null || ruleDecisions != hereDecisions || !java.util.Objects.equals(chunk, hereChunk))
        {
            hereModel = herePresenter.present(ruleDecisions, chunk);
            hereDecisions = ruleDecisions;
            hereChunk = chunk;
        }
        return hereModel;
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
        List<FateEvent> events = eventHistory == null
            ? java.util.Collections.<FateEvent>emptyList()
            : eventHistory.events();
        int needsReview = 0;
        for (FateEvent event : events)
        {
            if (event.getConfidence() == EventConfidence.UNCERTAIN) needsReview++;
        }
        shownWarningCount = activeWarningCount();
        SidebarPublisher models = sidebarModels();
        if (models != null)
        {
            models.rollInbox(new RollInboxModel(events.size(), needsReview, shownWarningCount, historySaveFailed));
        }
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
