package com.fatelocked;

import com.fatelocked.rules.PermissionStatus;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B2: the " (Locked)" menu tags come from the decision service, so they
 * say what the sidebar says. On a golden bundle a Guard in a locked chunk is
 * tagged, while not ready, allowed and ocean chunks aren't; another
 * character, or nobody logged in, sees no tags; an older export tags as it
 * did.
 */
public class MenuTagTest
{
    private static final Gson GSON = new Gson();
    private static final String TAG = " <col=f87171>(Locked)</col>";
    /** vanilla-mid: Seers' Village is LOCKED, Glarial's Tomb NOT_READY, Lumbridge Castle ALLOWED. */
    private static final CanonicalChunk SEERS = new CanonicalChunk(42, 54);
    private static final CanonicalChunk GLARIALS_TOMB = new CanonicalChunk(39, 53);
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    /** Sea west of Kandarin: in the tracker's map, not in its rules (R4). */
    private static final CanonicalChunk OCEAN = new CanonicalChunk(40, 41);
    /** In Zeah: LOCKED in the rules, but outside the areas older exports carry. */
    private static final CanonicalChunk ZEAH = new CanonicalChunk(16, 44);

    private final FateLockedPlugin plugin = new FateLockedPlugin();
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final Client client = mock(Client.class);

    @Before
    public void setUp() throws Exception
    {
        when(config.tagLockedOptions()).thenReturn(true);
        set("config", config);
        set("client", client);
    }

    @Test
    public void aGoldenNpcInALockedChunkIsTagged() throws Exception
    {
        FateLockedBundle mid = golden("vanilla-mid");
        assertEquals(PermissionStatus.LOCKED, mid.permissionsAt(SEERS).get().getEntry());
        assertEquals(PermissionStatus.NOT_READY, mid.permissionsAt(GLARIALS_TOMB).get().getEntry());
        assertEquals(PermissionStatus.ALLOWED, mid.permissionsAt(LUMBRIDGE).get().getEntry());
        assertEquals(false, mid.permissionsAt(OCEAN).isPresent());
        assertEquals(PermissionStatus.LOCKED, mid.permissionsAt(ZEAH).get().getEntry());
        assertEquals(FateLockedBundle.LockState.UNAUTHORED, mid.lockStateAt(ZEAH));
        playing(mid, "Iron Example");

        assertEquals("Guard" + TAG, npcTarget(SEERS));
        assertEquals("the rules decide, not the old areas", "Guard" + TAG, npcTarget(ZEAH));
        assertEquals("NOT_READY never tags", "Guard", npcTarget(GLARIALS_TOMB));
        assertEquals("Guard", npcTarget(LUMBRIDGE));
        assertEquals("the sea is locked without Sailing (R1)", "Guard" + TAG, npcTarget(OCEAN));
    }

    /** E1: the tag is the word players read everywhere, and every reader of menu text removes it. */
    @Test
    public void theTagIsTheWordPlayersRead()
    {
        assertEquals(com.fatelocked.ui.Terms.LOCKED_TAG, " " + MenuFacts.LOCKED_MARK);
    }

    /** E1: the tag takes the palette's locked colour, including a player's own. */
    @Test
    public void theTagTakesThePalettesLockedColour() throws Exception
    {
        playing(golden("vanilla-mid"), "Iron Example");
        when(config.colourPreset()).thenReturn(FateLockedConfig.ColourPreset.CUSTOM);
        when(config.unlockedColor()).thenReturn(new java.awt.Color(0, 128, 255, 110));
        when(config.frontierColor()).thenReturn(new java.awt.Color(255, 255, 0, 100));
        when(config.lockedColor()).thenReturn(new java.awt.Color(128, 0, 128, 110));
        GearDecisionTest.configChanged(plugin, "lockedColor");

        assertEquals("Guard <col=800080>(Locked)</col>", npcTarget(SEERS));
    }

    @Test
    public void anotherCharacterOrNobodySeesNoTags() throws Exception
    {
        playing(golden("vanilla-mid"), "Someone Else");
        assertEquals("Guard", npcTarget(SEERS));

        playing(golden("vanilla-mid"), null);
        assertEquals("Guard", npcTarget(SEERS));
    }

    /** An older export still tags from its own areas, but only for its character now. */
    @Test
    public void anOlderExportTagsAsBeforeForItsCharacter() throws Exception
    {
        FateLockedBundle legacy = legacyWithSeersLocked();
        assertEquals(FateLockedBundle.LockState.LOCKED, legacy.lockStateAt(SEERS));

        playing(legacy, "Nubles");
        assertEquals("Guard" + TAG, npcTarget(SEERS));
        assertEquals("Guard", npcTarget(LUMBRIDGE));

        playing(legacy, "Zezima");
        assertEquals("Guard", npcTarget(SEERS));
    }

    /**
     * vanilla-mid's travel table locks Ardougne Teleport: one place, locked (F4). One setting
     * tags both travel and what stands in a locked chunk (D1), and a tag is added once.
     */
    @Test
    public void theSettingChoosesWhetherToTagAndATagIsAddedOnce() throws Exception
    {
        playing(golden("vanilla-mid"), "Iron Example");

        assertEquals("<col=00ff00>Ardougne Teleport</col>" + TAG, target(ardougneTeleport()));
        assertEquals("Guard" + TAG, npcTarget(SEERS));

        when(config.tagLockedOptions()).thenReturn(false);
        assertEquals("<col=00ff00>Ardougne Teleport</col>", target(ardougneTeleport()));
        assertEquals("Guard", npcTarget(SEERS));

        when(config.tagLockedOptions()).thenReturn(true);
        MenuEntry tagged = npcEntry(SEERS);
        tagged.setTarget("Guard" + TAG);
        assertEquals("Guard" + TAG, target(tagged));
    }

    /**
     * F1: an option that could never be tagged is passed over before anything is read, the
     * setting included; one that could be has its text read once, by one reader.
     */
    @Test
    public void optionsThatCouldNeverBeTaggedReadNothing() throws Exception
    {
        playing(golden("vanilla-mid"), "Iron Example");
        MenuFactsReader reader = spy(new MenuFactsReader(client));
        set("menuFactsReader", reader);
        clearInvocations(config);

        for (MenuAction type : new MenuAction[] {MenuAction.WALK, MenuAction.CANCEL, MenuAction.EXAMINE_NPC,
            MenuAction.EXAMINE_OBJECT, MenuAction.PLAYER_FIRST_OPTION, MenuAction.RUNELITE})
        {
            MenuEntry entry = entry("Walk here", "Guard", type);
            plugin.onMenuEntryAdded(new MenuEntryAdded(entry));
            verify(entry, never()).getOption();
            verify(entry, never()).getTarget();
        }
        verify(reader, never()).read(any());
        verify(config, never()).tagLockedOptions();

        MenuEntry guard = npcEntry(SEERS);
        plugin.onMenuEntryAdded(new MenuEntryAdded(guard));
        verify(reader, times(1)).read(guard);
        verify(guard, times(1)).getOption();
        assertEquals("Guard" + TAG, guard.getTarget());
    }

    /** The rules in force, for a character logged in (or nobody), as the plugin refreshes them. */
    private void playing(FateLockedBundle rules, String name) throws Exception
    {
        set("active", new ActiveRules(rules, FateLockedPlugin.RulesSource.RELAY));
        Player player = name == null ? null : mock(Player.class);
        if (player != null) when(player.getName()).thenReturn(name);
        when(client.getLocalPlayer()).thenReturn(player);
        Method refresh = FateLockedPlugin.class.getDeclaredMethod("refreshDecisions");
        refresh.setAccessible(true);
        refresh.invoke(plugin);
    }

    private static MenuEntry ardougneTeleport()
    {
        return TravelClicks.cast("Ardougne Teleport").getMenuEntry();
    }

    private String npcTarget(CanonicalChunk chunk)
    {
        return target(npcEntry(chunk));
    }

    private String target(MenuEntry entry)
    {
        plugin.onMenuEntryAdded(new MenuEntryAdded(entry));
        return entry.getTarget();
    }

    private MenuEntry npcEntry(CanonicalChunk chunk)
    {
        NPC guard = mock(NPC.class);
        TestWorld.standAt(client, guard,
            new WorldPoint((chunk.getCx() << 6) + 20, (chunk.getCy() << 6) + 20, 0));
        MenuEntry entry = entry("Talk-to", "Guard", MenuAction.NPC_FIRST_OPTION);
        when(entry.getNpc()).thenReturn(guard);
        return entry;
    }

    /** A menu entry whose target keeps what is set on it. */
    private static MenuEntry entry(String option, String target, MenuAction type)
    {
        MenuEntry entry = mock(MenuEntry.class);
        String[] current = { target };
        when(entry.getOption()).thenReturn(option);
        when(entry.getType()).thenReturn(type);
        when(entry.getTarget()).thenAnswer(call -> current[0]);
        doAnswer(call -> {
            current[0] = call.getArgument(0);
            return entry;
        }).when(entry).setTarget(anyString());
        return entry;
    }

    private static FateLockedBundle golden(String id) throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")));
    }

    /** The v3 fixture (bound to Nubles), with Seers' Village added and not unlocked. */
    private static FateLockedBundle legacyWithSeersLocked() throws Exception
    {
        String text;
        try (InputStream in = MenuTagTest.class.getClassLoader().getResourceAsStream("bundles/v3-standard.json"))
        {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        JsonObject json = GSON.fromJson(text, JsonObject.class);
        JsonArray seers = GSON.fromJson("[{\"cx\":42,\"cy\":54}]", JsonArray.class);
        json.getAsJsonObject("chunks").add("Kandarin", seers);
        json.getAsJsonObject("subAreaChunks").add("Seers' Village", seers.deepCopy());
        json.getAsJsonObject("regionGroups").add("Kandarin", GSON.fromJson("[\"Seers' Village\"]", JsonArray.class));
        return FateLockedBundle.loadFromJson(GSON, json.toString());
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
