package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fatelocked.SpotMemory.Spot;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WallObjectSpawned;
import org.junit.Before;
import org.junit.Test;

/**
 * The owner's review, 28 Sept: what Here's card can point at is remembered where it loads,
 * for the chunk and row it belongs to, in the real world and for the rules' own character.
 */
public class FateLockedSpotsTest
{
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final Spot FAR = new Spot(3200, 3200, 0);

    private final Client client = mock(Client.class);
    private final ChunkLocator locator = mock(ChunkLocator.class);
    private final FateLockedPlugin plugin = new FateLockedPlugin();
    private DecisionService mine;
    private int ids;

    @Before
    public void setUp() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        mine = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example");
        PluginTestSupport.set(plugin, "client", client);
        PluginTestSupport.set(plugin, "chunkLocator", locator);
        PluginTestSupport.set(plugin, "decisions", mine);
        when(locator.client()).thenReturn(client);
    }

    /** Each kind of object: as a game object, a wall, a wall decoration and on the ground. */
    @Test
    public void anObjectTheCardNamesIsRememberedWhereItLoads() throws Exception
    {
        GameObjectSpawned game = new GameObjectSpawned();
        game.setGameObject(object(GameObject.class, "Dead tree", new WorldPoint(3210, 3220, 0)));
        plugin.onGameObjectSpawned(game);
        WallObjectSpawned wall = new WallObjectSpawned();
        wall.setWallObject(object(WallObject.class, "Dead tree", new WorldPoint(3230, 3220, 0)));
        plugin.onWallObjectSpawned(wall);
        DecorativeObjectSpawned decoration = new DecorativeObjectSpawned();
        decoration.setDecorativeObject(object(DecorativeObject.class, "Dead tree", new WorldPoint(3240, 3220, 0)));
        plugin.onDecorativeObjectSpawned(decoration);
        GroundObjectSpawned ground = new GroundObjectSpawned();
        ground.setGroundObject(object(GroundObject.class, "dead tree ", new WorldPoint(3250, 3220, 0)));
        plugin.onGroundObjectSpawned(ground);

        for (int x : new int[] {3210, 3230, 3240, 3250})
        {
            assertEquals(new Spot(x, 3220, 0), spots().nearest(LUMBRIDGE, "SKILLING", "Dead tree",
                new Spot(x, 3221, 0)));
        }
        assertTrue(spots().changed());
    }

    @Test
    public void anNpcTheCardNamesIsRememberedWhereItComesIntoView() throws Exception
    {
        plugin.onNpcSpawned(new NpcSpawned(npc("Cave goblin guard", new WorldPoint(3220, 3225, 0))));

        assertEquals(new Spot(3220, 3225, 0), spots().nearest(LUMBRIDGE, "COMBAT", "Cave goblin guard", FAR));
    }

    /** An object that shows another now, as a patch does as it grows, goes by the one it shows. */
    @Test
    public void anObjectGoesByWhatItShowsNow() throws Exception
    {
        GameObject changing = object(GameObject.class, "Dead tree", new WorldPoint(3210, 3220, 0));
        ObjectComposition base = client.getObjectDefinition(changing.getId());
        ObjectComposition now = composition("Hollow stump");
        when(base.getImpostorIds()).thenReturn(new int[] {1, 2});
        when(base.getImpostor()).thenReturn(now);
        GameObjectSpawned game = new GameObjectSpawned();
        game.setGameObject(changing);

        plugin.onGameObjectSpawned(game);

        assertNull(spots().nearest(LUMBRIDGE, "SKILLING", "Dead tree", FAR));
    }

    /** Names the card doesn't list, places off the real world and another character's rules add nothing. */
    @Test
    public void onlyWhatTheCardNamesInTheRealWorldForTheRulesOwnCharacter() throws Exception
    {
        plugin.onNpcSpawned(new NpcSpawned(npc("Nobody at all", new WorldPoint(3220, 3225, 0))));
        plugin.onNpcSpawned(new NpcSpawned(npc("Cave goblin guard", null)));
        assertFalse(spots().changed());

        PluginTestSupport.set(plugin, "decisions",
            DecisionService.create(mine.rules(), "iron example", "someone else"));
        plugin.onNpcSpawned(new NpcSpawned(npc("Cave goblin guard", new WorldPoint(3220, 3225, 0))));
        GameObjectSpawned game = new GameObjectSpawned();
        game.setGameObject(object(GameObject.class, "Dead tree", new WorldPoint(3210, 3220, 0)));
        plugin.onGameObjectSpawned(game);

        assertFalse(spots().changed());
        assertNull(spots().nearest(LUMBRIDGE, "COMBAT", "Cave goblin guard", FAR));
    }

    /** What the arrow shows the way to loads: it's looked for at the next tick, and nothing else is. */
    @Test
    public void thePointerLooksWhenItsThingLoads() throws Exception
    {
        WorldPoint spot = new WorldPoint(3210, 3220, 0);
        PluginTestSupport.set(plugin, "pointer", new FateLockedPlugin.Pointer("SKILLING", "Dead tree",
            new CanonicalChunk(51, 50), null, null, spot, spot, true, false));
        GameObjectSpawned first = new GameObjectSpawned();
        first.setGameObject(object(GameObject.class, "Dead tree", new WorldPoint(3215, 3230, 0)));
        plugin.onGameObjectSpawned(first);
        assertFalse("a dead tree of another chunk's", (Boolean) PluginTestSupport.get(plugin, "pointerLooks"));

        PluginTestSupport.set(plugin, "pointer", new FateLockedPlugin.Pointer("SKILLING", "Dead tree", LUMBRIDGE,
            null, null, spot, spot, true, false));
        plugin.onNpcSpawned(new NpcSpawned(npc("Cave goblin guard", new WorldPoint(3220, 3225, 0))));
        assertFalse("another row", (Boolean) PluginTestSupport.get(plugin, "pointerLooks"));

        GameObjectSpawned here = new GameObjectSpawned();
        here.setGameObject(object(GameObject.class, "Dead tree", new WorldPoint(3215, 3234, 0)));
        plugin.onGameObjectSpawned(here);
        assertTrue((Boolean) PluginTestSupport.get(plugin, "pointerLooks"));
    }

    /**
     * Spots seen are written to the plugin's folder at most every half minute, and at once on
     * logging out; what's there is read as the plugin starts.
     */
    @Test
    public void spotsAreSavedNowAndThenAndReadAtStart() throws Exception
    {
        java.nio.file.Path folder = java.nio.file.Files.createTempDirectory("fate-locked-spots");
        FateLockedPlugin saving = new FateLockedPlugin()
        {
            @Override
            java.io.File dataDirectory()
            {
                return folder.toFile();
            }
        };
        PluginTestSupport.set(saving, "client", client);
        PluginTestSupport.set(saving, "chunkLocator", locator);
        PluginTestSupport.set(saving, "decisions", mine);
        PluginTestSupport.set(saving, "gson", new Gson());
        PluginTestSupport.runQueuedWorkInline(saving);
        java.nio.file.Path file = folder.resolve(SpotMemory.FILE);
        GameObjectSpawned tree = new GameObjectSpawned();
        tree.setGameObject(object(GameObject.class, "Dead tree", new WorldPoint(3210, 3220, 0)));
        saving.onGameObjectSpawned(tree);

        when(client.getTickCount()).thenReturn(40);
        due(saving);
        assertFalse("not yet", java.nio.file.Files.exists(file));
        when(client.getTickCount()).thenReturn(50);
        due(saving);
        assertTrue(java.nio.file.Files.exists(file));

        GameObjectSpawned another = new GameObjectSpawned();
        another.setGameObject(object(GameObject.class, "Dead tree", new WorldPoint(3240, 3220, 0)));
        saving.onGameObjectSpawned(another);
        when(client.getTickCount()).thenReturn(99);
        due(saving);
        assertFalse(new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8)
            .contains("3240"));
        net.runelite.api.events.GameStateChanged out = new net.runelite.api.events.GameStateChanged();
        out.setGameState(net.runelite.api.GameState.LOGIN_SCREEN);
        try
        {
            saving.onGameStateChanged(out);
        }
        catch (RuntimeException ignored)
        {
            // Logging out goes on to parts this test doesn't set up; the spots are saved first.
        }
        assertTrue("saved on logging out", new String(java.nio.file.Files.readAllBytes(file),
            java.nio.charset.StandardCharsets.UTF_8).contains("3240"));

        FateLockedPlugin next = new FateLockedPlugin()
        {
            @Override
            java.io.File dataDirectory()
            {
                return folder.toFile();
            }
        };
        PluginTestSupport.set(next, "gson", new Gson());
        PluginTestSupport.runQueuedWorkInline(next);
        java.lang.reflect.Method load = FateLockedPlugin.class.getDeclaredMethod("loadSpots");
        load.setAccessible(true);
        load.invoke(next);
        assertEquals(new Spot(3240, 3220, 0), ((SpotMemory) PluginTestSupport.get(next, "spots"))
            .nearest(LUMBRIDGE, "SKILLING", "Dead tree", new Spot(3241, 3221, 0)));
    }

    private static void due(FateLockedPlugin plugin) throws Exception
    {
        java.lang.reflect.Method due = FateLockedPlugin.class.getDeclaredMethod("saveSpotsIfDue");
        due.setAccessible(true);
        due.invoke(plugin);
    }

    private SpotMemory spots() throws Exception
    {
        return (SpotMemory) PluginTestSupport.get(plugin, "spots");
    }

    private <T extends TileObject> T object(Class<T> kind, String name, WorldPoint at)
    {
        T object = mock(kind);
        int id = ++ids;
        when(object.getId()).thenReturn(id);
        ObjectComposition shown = composition(name);
        when(client.getObjectDefinition(id)).thenReturn(shown);
        when(locator.world(object)).thenReturn(at);
        return object;
    }

    private static ObjectComposition composition(String name)
    {
        ObjectComposition shown = mock(ObjectComposition.class);
        when(shown.getName()).thenReturn(name);
        when(shown.getActions()).thenReturn(new String[] {"Chop down", null, null, null, null});
        return shown;
    }

    private NPC npc(String name, WorldPoint at)
    {
        NPC npc = mock(NPC.class);
        NPCComposition shown = mock(NPCComposition.class);
        when(shown.getName()).thenReturn(name);
        when(shown.getActions()).thenReturn(new String[] {null, "Attack", null, null, null});
        when(npc.getTransformedComposition()).thenReturn(shown);
        when(locator.world(npc)).thenReturn(at);
        return npc;
    }
}
