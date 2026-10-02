package com.fatelocked;

import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What crossing into a chunk tells the player: nothing before rules are
 * loaded or in chunks the tracker hasn't mapped, a line when the area
 * changes, and one locked warning on the way into a locked area (U10). A
 * locked area's line says why, in the tracker's words (E8), and in words,
 * not marks.
 */
public class FateLockedChunkEntryTest
{
    private static final String RULES = "{\"version\":3,"
        + "\"chunks\":{\"Misthalin\":[{\"cx\":50,\"cy\":50}],"
        + "\"Asgarnia\":[{\"cx\":46,\"cy\":52},{\"cx\":47,\"cy\":52}]},"
        + "\"unlockedRegions\":[\"Misthalin\"]}";
    /** Two locked areas side by side: Asgarnia at Falador, Kandarin just west of it. */
    private static final String TWO_LOCKED_AREAS = "{\"version\":3,"
        + "\"chunks\":{\"Misthalin\":[{\"cx\":50,\"cy\":50}],"
        + "\"Asgarnia\":[{\"cx\":46,\"cy\":52}],"
        + "\"Kandarin\":[{\"cx\":45,\"cy\":52}]},"
        + "\"unlockedRegions\":[\"Misthalin\"]}";
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final CanonicalChunk FALADOR = new CanonicalChunk(46, 52);
    private static final CanonicalChunk FALADOR_EAST = new CanonicalChunk(47, 52);
    private static final CanonicalChunk UNMAPPED = new CanonicalChunk(1, 1);
    private static final int LOCKED_SOUND = 2277;
    /** vanilla-mid, bound to Iron Example: Rimmington is the one LOCKED chunk on this walk. */
    private static final CanonicalChunk RIMMINGTON = new CanonicalChunk(46, 50);
    private static final CanonicalChunk[] LUMBRIDGE_TO_FALADOR = {
        new CanonicalChunk(50, 50), new CanonicalChunk(49, 50), new CanonicalChunk(48, 50),
        new CanonicalChunk(47, 50), RIMMINGTON, new CanonicalChunk(46, 51), new CanonicalChunk(46, 52),
    };
    /** vanilla-mid: South Taverley is LOCKED and East Catherby NOT_READY. */
    private static final CanonicalChunk SOUTH_TAVERLEY = new CanonicalChunk(45, 53);
    private static final CanonicalChunk EAST_CATHERBY = new CanonicalChunk(44, 53);
    /** vanilla-mid: in Zeah, LOCKED by the tracker and outside the old area lists. */
    private static final CanonicalChunk ZEAH = new CanonicalChunk(16, 44);

    private final Client client = mock(Client.class);
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final FateLockedPanel panel = mock(FateLockedPanel.class);
    private final ChatMessageManager chat = mock(ChatMessageManager.class);
    private final Notifier notifier = mock(Notifier.class);
    private final Player player = mock(Player.class);
    private FateLockedPlugin plugin;

    @Before
    public void setUp() throws Exception
    {
        plugin = new FateLockedPlugin();
        set("client", client);
        set("config", config);
        set("panel", panel);
        set("chatMessageManager", chat);
        set("notifier", notifier);
        when(client.getLocalPlayer()).thenReturn(player);
        when(config.announceAreaChanges()).thenReturn(true);
        when(config.lockedAreaAlert()).thenReturn(FateLockedConfig.LockedAreaAlert.CHAT_SOUND_FADE);
        when(config.useNotifier()).thenReturn(true);
    }

    @Test
    public void nothingIsAnnouncedBeforeRulesAreLoaded() throws Exception
    {
        FateLockedBundle none = FateLockedBundle.empty();
        assertTrue(none.isEmpty());
        set("active", new ActiveRules(none, FateLockedPlugin.RulesSource.NONE));

        walk(LUMBRIDGE, FALADOR, UNMAPPED);

        verify(chat, never()).queue(any(QueuedMessage.class));
        verify(client, never()).playSoundEffect(anyInt());
        verify(notifier, never()).notify(anyString());
    }

    @Test
    public void unmappedChunksAreNeverAnnounced() throws Exception
    {
        loadRules();

        walk(UNMAPPED);
        verify(chat, never()).queue(any(QueuedMessage.class));

        walk(LUMBRIDGE);
        verify(chat, times(1)).queue(any(QueuedMessage.class));
    }

    /** Decision 6: once per locked area, and the same area again only after a minute. */
    @Test
    public void theLockedWarningSoundsOncePerAreaAndAgainAfterAMinute() throws Exception
    {
        loadRules();

        walk(LUMBRIDGE, FALADOR, FALADOR_EAST, FALADOR);
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
        verify(notifier, times(1)).notify("You've entered a locked area: Asgarnia");

        walk(LUMBRIDGE, FALADOR);
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);

        when(client.getTickCount()).thenReturn(LockedAreaAlerts.QUIET_TICKS);
        walk(LUMBRIDGE, FALADOR);
        verify(client, times(2)).playSoundEffect(LOCKED_SOUND);
    }

    /** U20: the fade starts on the way into a locked area, when the alert setting has one. */
    @Test
    public void theFadeFollowsTheAlert() throws Exception
    {
        loadRules();
        when(config.lockedAreaAlert()).thenReturn(FateLockedConfig.LockedAreaAlert.CHAT_SOUND);
        walk(LUMBRIDGE, FALADOR);
        assertEquals("no fade in this setting", FateLockedPlugin.NO_FADE, plugin.getLockedFadeAt());

        when(config.lockedAreaAlert()).thenReturn(FateLockedConfig.LockedAreaAlert.CHAT_FADE);
        when(client.getTickCount()).thenReturn(LockedAreaAlerts.QUIET_TICKS);
        long before = System.nanoTime();
        walk(LUMBRIDGE, FALADOR);
        assertTrue("the fade began on this step", plugin.getLockedFadeAt() >= before);
    }

    /** A login starts afresh: standing in the same locked area, the next step alerts again. */
    @Test
    public void aNewSessionAlertsAgain() throws Exception
    {
        loadRules();
        walk(LUMBRIDGE, FALADOR);
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);

        Method forget = FateLockedPlugin.class.getDeclaredMethod("forgetLoginWarnings");
        forget.setAccessible(true);
        forget.invoke(plugin);
        walk(FALADOR);

        verify(client, times(2)).playSoundEffect(LOCKED_SOUND);
    }

    /** D1: routine announcements are their own setting; the locked-area alert keeps its line and sound. */
    @Test
    public void theLockedWarningDoesNotNeedRoutineAnnouncements() throws Exception
    {
        loadRules();
        when(config.announceAreaChanges()).thenReturn(false);

        walk(LUMBRIDGE, FALADOR);

        verify(chat, times(1)).queue(any(QueuedMessage.class));
        verify(client).playSoundEffect(LOCKED_SOUND);
        verify(notifier).notify("You've entered a locked area: Asgarnia");
    }

    /** Chat alone plays no sound; the notification still comes with its line (the owner's call T8). */
    @Test
    public void aChatAlertPlaysNoSound() throws Exception
    {
        loadRules();
        when(config.lockedAreaAlert()).thenReturn(FateLockedConfig.LockedAreaAlert.CHAT);

        walk(LUMBRIDGE, FALADOR);

        verify(client, never()).playSoundEffect(anyInt());
        verify(notifier).notify("You've entered a locked area: Asgarnia");
    }

    /**
     * The owner's call T8 in the accuracy review: the notification goes with the locked-area
     * alert's chat line, as the notifications setting says, whether or not a sound plays. A
     * routine line brings none, and neither does a line with notifications off.
     */
    @Test
    public void theNotificationGoesWithTheLockedAreasLine() throws Exception
    {
        set("active", new ActiveRules(FateLockedBundle.loadFromJson(new Gson(), TWO_LOCKED_AREAS),
            FateLockedPlugin.RulesSource.NONE));
        when(config.lockedAreaAlert()).thenReturn(FateLockedConfig.LockedAreaAlert.CHAT);

        walk(LUMBRIDGE);
        assertEquals(1, chatLines().size());
        verify(notifier, never()).notify(anyString());

        // Asgarnia, then Kandarin straight from it: a line each, and a notification each.
        walk(FALADOR, new CanonicalChunk(45, 52));
        assertEquals(3, chatLines().size());
        verify(client, never()).playSoundEffect(anyInt());
        verify(notifier).notify("You've entered a locked area: Asgarnia");
        verify(notifier).notify("You've entered a locked area: Kandarin");

        when(config.useNotifier()).thenReturn(false);
        when(client.getTickCount()).thenReturn(LockedAreaAlerts.QUIET_TICKS);
        walk(LUMBRIDGE, FALADOR);
        assertEquals(5, chatLines().size());
        verify(notifier, times(2)).notify(anyString());
    }

    /** D1: with the alert off, a locked area says nothing; routine announcements are their own setting. */
    @Test
    public void anAlertSetToOffPostsNoLockedLine() throws Exception
    {
        loadRules();
        when(config.lockedAreaAlert()).thenReturn(FateLockedConfig.LockedAreaAlert.OFF);

        walk(LUMBRIDGE, FALADOR);

        List<String> lines = chatLines();
        assertEquals(lines.toString(), 1, lines.size());
        assertTrue(lines.get(0), lines.get(0).endsWith(": Unlocked"));
        verify(client, never()).playSoundEffect(anyInt());
        verify(notifier, never()).notify(anyString());
    }

    @Test
    public void theWarningsCountFollowsThePlayer() throws Exception
    {
        loadRules();

        walk(LUMBRIDGE);
        verify(panel, times(1)).showRollInbox(RollInboxModel.builder().build());
        walk(FALADOR);
        verify(panel, times(1)).showRollInbox(RollInboxModel.builder().warnings(1).build());
        walk(FALADOR_EAST);
        verify(panel, times(1)).showRollInbox(RollInboxModel.builder().warnings(1).build());
        walk(LUMBRIDGE);
        verify(panel, times(2)).showRollInbox(RollInboxModel.builder().build());
    }

    /** B6, U10: a golden walk warns once, on the way into Rimmington, and announces each area once. */
    @Test
    public void aGoldenWalkFromLumbridgeToFaladorWarnsOnceAtRimmington() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");
        DecisionService decisions = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example");
        List<String> areas = new ArrayList<>();
        for (CanonicalChunk step : LUMBRIDGE_TO_FALADOR)
        {
            String area = decisions.areaName(step);
            if (areas.isEmpty() || !areas.get(areas.size() - 1).equals(area)) areas.add(area);
        }

        walk(LUMBRIDGE_TO_FALADOR);

        List<String> lines = chatLines();
        assertEquals("one line per area: " + areas, areas.size(), lines.size());
        assertTrue("fewer than one per chunk", lines.size() < LUMBRIDGE_TO_FALADOR.length);
        List<String> locked = new ArrayList<>();
        for (String line : lines) if (line.contains(": Locked")) locked.add(line);
        assertEquals(1, locked.size());
        assertTrue(locked.get(0), locked.get(0).contains(mid.labelAt(RIMMINGTON))
            && locked.get(0).endsWith(": Locked — Unlock Rimmington"));
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
        verify(notifier).notify("You've entered a locked area: " + mid.labelAt(RIMMINGTON));
    }

    /** The sea has no area names: its chunks are one area under the tracker's reason. */
    @Test
    public void theSeaIsOneArea() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");
        DecisionService decisions = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example");
        CanonicalChunk[] sea = adjacentUnnamed(decisions);

        walk(sea);

        assertEquals(1, chatLines().size());
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
    }

    /** Two neighbouring locked chunks with no area name and the same reason: open sea. */
    private static CanonicalChunk[] adjacentUnnamed(DecisionService decisions)
    {
        for (CanonicalChunk chunk : decisions.mappedChunks())
        {
            CanonicalChunk east = new CanonicalChunk(chunk.getCx() + 1, chunk.getCy());
            Decision here = decisions.chunk(chunk);
            Decision there = decisions.chunk(east);
            if (decisions.areaName(chunk) == null && decisions.areaName(east) == null
                && here.getStatus() == PermissionStatus.LOCKED && there.getStatus() == PermissionStatus.LOCKED
                && here.getReason() != null && here.getReason().equals(there.getReason()))
            {
                return new CanonicalChunk[] {chunk, east};
            }
        }
        throw new AssertionError("no open sea in vanilla-mid");
    }

    /** NOT_READY is owned: it reads as unlocked, never alerts and isn't a warning. */
    @Test
    public void notReadyNeverAlerts() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");
        assertEquals(PermissionStatus.NOT_READY, mid.permissionsAt(EAST_CATHERBY).get().getEntry());
        assertEquals(PermissionStatus.LOCKED, mid.permissionsAt(SOUTH_TAVERLEY).get().getEntry());

        walk(FALADOR, SOUTH_TAVERLEY, EAST_CATHERBY);

        List<String> lines = chatLines();
        assertEquals(lines.toString(), 3, lines.size());
        String why = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example")
            .chunk(EAST_CATHERBY).getReason();
        assertTrue(lines.get(2), lines.get(2).endsWith(": Not ready" + (why == null ? "" : " — " + why)));
        assertTrue(lines.get(1), lines.get(1).endsWith(": Locked — Unlock Taverley"));
        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
        verify(panel, times(1)).showRollInbox(RollInboxModel.builder().warnings(1).build());
        verify(panel, times(2)).showRollInbox(RollInboxModel.builder().build());
    }

    /**
     * Another character's rules say nothing about this one: walking brings no area line, sound
     * or alert. The one line says whose run it is, and it always shows (decision 10).
     */
    @Test
    public void anotherCharacterWalksInSilence() throws Exception
    {
        playing("Someone Else");

        walk(LUMBRIDGE_TO_FALADOR);

        List<String> lines = chatLines();
        assertEquals(lines.toString(), 1, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("you're logged in as"));
        verify(client, never()).playSoundEffect(anyInt());
        // Notifications are on here, so that line is also the one notification.
        verify(notifier, times(1)).notify(anyString());
        verify(notifier).notify("You're logged in as Someone Else, not the bound account Iron Example");
        verify(panel, never()).showRollInbox(RollInboxModel.builder().warnings(1).build());
    }

    /**
     * The accuracy review, P-10: on another character the Roll inbox says why RuneLite notices
     * nothing, rather than promising events, and stops saying it on the run's own character.
     */
    @Test
    public void theRollInboxSaysWhyNothingIsNoticedOnAnotherCharacter() throws Exception
    {
        playing("Someone Else");
        walk(LUMBRIDGE);
        assertEquals("RuneLite notices nothing on this character: your run is linked to Iron Example.",
            lastRollInbox().getQuiet());

        when(player.getName()).thenReturn("Iron Example");
        walk(LUMBRIDGE);
        assertNull(lastRollInbox().getQuiet());
    }

    private RollInboxModel lastRollInbox()
    {
        ArgumentCaptor<RollInboxModel> shown = ArgumentCaptor.forClass(RollInboxModel.class);
        verify(panel, atLeast(1)).showRollInbox(shown.capture());
        return shown.getValue();
    }

    /** A chunk the tracker locks outside the old area lists is announced without an area. */
    @Test
    public void aChunkOnlyTheTrackerMapsIsAnnounced() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");
        assertEquals(FateLockedBundle.LockState.UNAUTHORED, mid.lockStateAt(ZEAH));

        walk(LUMBRIDGE, ZEAH);

        List<String> lines = chatLines();
        assertTrue(lines.get(1), lines.get(1).contains("Chunk (16, 44)")
            && lines.get(1).endsWith(": Locked — Needs Sailing and Pandemonium"));
        assertFalse(lines.get(1), lines.get(1).contains("null"));
        verify(notifier).notify("You've entered a locked area: chunk (16, 44)");
    }

    /**
     * B14: inside an instance the rules judge the chunk it is a copy of. From
     * Lumbridge Castle the player enters an instance copied from Seers'
     * Village (LOCKED in vanilla-mid): one alert, none while inside, and an
     * instance zone with no template changes nothing.
     */
    @Test
    public void anInstanceCopiedFromALockedChunkAlertsOnce() throws Exception
    {
        FateLockedBundle mid = playing("Iron Example");
        CanonicalChunk seers = new CanonicalChunk(42, 54);
        assertEquals(PermissionStatus.LOCKED, mid.permissionsAt(seers).get().getEntry());

        walk(new CanonicalChunk(50, 50));
        verify(client, never()).playSoundEffect(LOCKED_SOUND);

        enterInstanceOf(seers, 5, 5);
        enterInstanceOf(seers, 6, 7);
        enterInstanceOf(null, 6, 7);

        verify(client, times(1)).playSoundEffect(LOCKED_SOUND);
        verify(notifier).notify("You've entered a locked area: " + mid.labelAt(seers));
        List<String> lines = chatLines();
        assertEquals(2, lines.size());
        assertTrue(lines.get(1), lines.get(1).contains(mid.labelAt(seers)) && lines.get(1).contains(": Locked"));
    }

    /**
     * Stand in an instance whose every zone is a copy of the source chunk's
     * south-west zone (null: no template anywhere), at scene zone x, y.
     */
    private void enterInstanceOf(CanonicalChunk source, int zoneX, int zoneY)
    {
        int[][][] templates = new int[4][13][13];
        for (int[][] plane : templates) for (int[] row : plane) Arrays.fill(row, -1);
        if (source != null)
        {
            // RuneLite's encoding: the source zone in 8-tile units, plane 0, no rotation.
            int template = ((source.getCx() << 3) << 14) | ((source.getCy() << 3) << 3);
            for (int[] row : templates[0]) Arrays.fill(row, template);
        }
        Scene scene = mock(Scene.class);
        when(scene.isInstance()).thenReturn(true);
        when(scene.getInstanceTemplateChunks()).thenReturn(templates);
        WorldView instance = mock(WorldView.class);
        when(instance.isTopLevel()).thenReturn(true);
        when(instance.getScene()).thenReturn(scene);
        when(player.getWorldView()).thenReturn(instance);
        when(player.getLocalLocation()).thenReturn(
            new LocalPoint((zoneX * 8 + 3) * 128 + 64, (zoneY * 8 + 3) * 128 + 64, WorldView.TOPLEVEL));
        plugin.onGameTick(null);
    }

    /** vanilla-mid, with a character logged in. */
    private FateLockedBundle playing(String name) throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        set("active", new ActiveRules(mid, FateLockedPlugin.RulesSource.RELAY));
        when(player.getName()).thenReturn(name);
        return mid;
    }

    private List<String> chatLines()
    {
        ArgumentCaptor<QueuedMessage> queued = ArgumentCaptor.forClass(QueuedMessage.class);
        verify(chat, atLeast(0)).queue(queued.capture());
        List<String> lines = new ArrayList<>();
        for (QueuedMessage message : queued.getAllValues()) lines.add(message.getRuneLiteFormattedMessage());
        return lines;
    }

    private void loadRules() throws Exception
    {
        FateLockedBundle rules = FateLockedBundle.loadFromJson(new Gson(), RULES);
        assertFalse(rules.isEmpty());
        assertEquals(FateLockedBundle.LockState.UNLOCKED, rules.lockStateAt(LUMBRIDGE));
        assertEquals(FateLockedBundle.LockState.LOCKED, rules.lockStateAt(FALADOR));
        assertEquals(FateLockedBundle.LockState.LOCKED, rules.lockStateAt(FALADOR_EAST));
        assertEquals(FateLockedBundle.LockState.UNAUTHORED, rules.lockStateAt(UNMAPPED));
        set("active", new ActiveRules(rules, FateLockedPlugin.RulesSource.NONE));
    }

    /** Stand in each chunk for one game tick. */
    /** E6: the HUD is worked out each tick for where the player stands, and kept while nothing changes. */
    @Test
    public void theHudFollowsThePlayer() throws Exception
    {
        loadRules();
        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.COMPACT);

        walk(LUMBRIDGE);
        HudModel atLumbridge = plugin.hudModel();
        assertEquals("Misthalin", HudPresenterTest.lines(atLumbridge).get("Here"));
        assertEquals("Unlocked", HudPresenterTest.lines(atLumbridge).get("Status"));
        walk(LUMBRIDGE);
        assertSame("a model like the last is kept, so the panel isn't built again", atLumbridge, plugin.hudModel());

        walk(FALADOR);
        assertEquals("Locked", HudPresenterTest.lines(plugin.hudModel()).get("Status"));

        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.OFF);
        walk(FALADOR);
        assertSame(HudModel.NONE, plugin.hudModel());

        // Where the player's chunk can't be found, the HUD says nothing of the last one.
        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.COMPACT);
        walk(FALADOR);
        assertEquals("Locked", HudPresenterTest.lines(plugin.hudModel()).get("Status"));
        when(player.getLocalLocation()).thenReturn(null);
        plugin.onGameTick(null);
        assertSame(HudModel.NONE, plugin.hudModel());
    }

    /** E6: Detailed lists what the place holds, as the sidebar's Here card does, and the run's progress. */
    @Test
    public void theDetailedHudListsWhatThePlaceHolds() throws Exception
    {
        playing("Iron Example");
        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.DETAILED);
        walk(LUMBRIDGE);

        HudModel model = plugin.hudModel();
        assertTrue(model.isDetailed());
        java.util.Map<String, String> lines = HudPresenterTest.lines(model);
        assertEquals(ProgressText.hudLine(plugin.decisions().progress()), lines.get("Unlocked"));
        com.fatelocked.sidebar.HereModel here =
            new com.fatelocked.sidebar.HerePresenter().present(plugin.decisions(), LUMBRIDGE);
        assertFalse("Lumbridge holds something", here.getGroups().isEmpty());
        for (com.fatelocked.sidebar.HereModel.Group group : here.getGroups())
        {
            assertTrue(group.getTitle(), lines.containsKey(group.getTitle()));
        }
    }

    /**
     * The owner's review, 28 Sept: Here is worked out again when the game says more. The caves'
     * guard needs only The Lost Tribe started, which the tracker can't see but the game can.
     */
    @Test
    public void hereFollowsWhatTheGameSays() throws Exception
    {
        playing("Iron Example");
        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.DETAILED);
        when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(net.runelite.api.GameState.LOGGED_IN);
        when(client.getWorldType()).thenReturn(java.util.EnumSet.of(net.runelite.api.WorldType.MEMBERS));
        // The quest-state script's answer: 1 is still to start, 0 under way.
        int[] answer = {1};
        when(client.getIntStack()).thenReturn(answer);
        when(client.getTickCount()).thenReturn(1000);
        walk(LUMBRIDGE);
        assertEquals("Not ready", HudPresenterTest.lines(plugin.hudModel()).get("Cave goblin guard"));

        answer[0] = 0;
        when(client.getTickCount()).thenReturn(1001);
        walk(LUMBRIDGE);
        assertEquals("Can do", HudPresenterTest.lines(plugin.hudModel()).get("Cave goblin guard"));
    }

    /** E6: the way to the nearest bank is found again when the player moves, and when the rules change. */
    @Test
    public void theHudsNearestBankFollowsThePlayerAndTheRules() throws Exception
    {
        // custom-lumbridge-banks-off allows the banks at Lumbridge Castle and South Draynor.
        String json = GoldenBundleContractTest.gunzip(
            GoldenBundleContractTest.bytes("custom-lumbridge-banks-off.bundle.json.gz"));
        set("active", new ActiveRules(FateLockedBundle.loadFromJson(new Gson(), json), FateLockedPlugin.RulesSource.RELAY));
        when(player.getName()).thenReturn("Iron Example");
        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.COMPACT);
        CanonicalChunk draynor = new CanonicalChunk(48, 50);

        walk(draynor);
        assertEquals("Here", HudPresenterTest.lines(plugin.hudModel()).get("Bank"));
        walk(new CanonicalChunk(46, 50));
        assertEquals("Draynor Vill… · 2 E", HudPresenterTest.lines(plugin.hudModel()).get("Bank"));
        walk(draynor);

        // The tracker locks South Draynor's chunk and bank: in the same chunk, Lumbridge is nearest.
        JsonObject locked = new Gson().fromJson(json, JsonObject.class);
        locked.getAsJsonObject("rules").getAsJsonObject("chunks").getAsJsonObject("48,50").addProperty("entry", "LOCKED");
        locked.getAsJsonObject("rules").getAsJsonObject("banks").getAsJsonObject("12338").addProperty("status", "LOCKED");
        set("active", new ActiveRules(FateLockedBundle.loadFromJson(new Gson(), locked.toString()),
            FateLockedPlugin.RulesSource.RELAY));
        walk(draynor);
        assertEquals("Lumbridge · 2 E", HudPresenterTest.lines(plugin.hudModel()).get("Bank"));
    }

    private void walk(CanonicalChunk... chunks)
    {
        for (CanonicalChunk chunk : chunks)
        {
            TestWorld.standAt(client, player, new WorldPoint((chunk.getCx() << 6) + 8, (chunk.getCy() << 6) + 8, 0));
            // The tick event carries nothing the plugin reads.
            plugin.onGameTick(null);
        }
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
