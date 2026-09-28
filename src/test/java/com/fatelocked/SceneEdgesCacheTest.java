package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import org.junit.Before;
import org.junit.Test;

/** U3, A9: the loaded scene is worked out once, for the game view and the minimap alike. */
public class SceneEdgesCacheTest
{
    private final ChunkLocator locator = mock(ChunkLocator.class);
    private final WorldView view = mock(WorldView.class);
    private final SceneEdgesCache cache = new SceneEdgesCache();
    private DecisionService mine;

    @Before
    public void setUp() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        mine = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example");
        when(view.getSizeX()).thenReturn(104);
        when(view.getSizeY()).thenReturn(104);
        // Four chunks around Lumbridge, split 64 tiles into the scene.
        when(locator.sceneZone(anyInt(), anyInt())).thenAnswer(zone -> new CanonicalChunk(
            50 + (int) zone.getArgument(0) / 8, 50 + (int) zone.getArgument(1) / 8));
    }

    @Test
    public void eachZoneIsTintedAsTheRulesTintItsChunk()
    {
        SceneEdges scene = cache.get(mine, view, 0, 0, locator);

        SceneEdges.Zone[][] zones = new SceneEdges.Zone[13][13];
        for (int zx = 0; zx < 13; zx++)
        {
            for (int zy = 0; zy < 13; zy++)
            {
                CanonicalChunk chunk = new CanonicalChunk(50 + zx / 8, 50 + zy / 8);
                zones[zx][zy] = new SceneEdges.Zone(TintPolicy.at(mine, chunk), chunk);
            }
        }
        SceneEdges want = SceneEdges.of(zones);
        assertEquals(want.edges(), scene.edges());
        assertEquals(want.locked(), scene.locked());
        assertFalse(scene.edges().isEmpty());
    }

    @Test
    public void theSceneIsWorkedOutOnceUntilItOrTheRulesChange()
    {
        SceneEdges first = cache.get(mine, view, 0, 0, locator);
        verify(locator, times(169)).sceneZone(anyInt(), anyInt());

        clearInvocations(locator);
        assertSame(first, cache.get(mine, view, 0, 0, locator));
        verify(locator, never()).sceneZone(anyInt(), anyInt());

        // A scene load, another plane, other rules, and another base each bring a new scene.
        cache.get(mine, view, 0, 1, locator);
        cache.get(mine, view, 1, 1, locator);
        DecisionService again = DecisionService.create(mine.rules(), "iron example", "iron example");
        cache.get(again, view, 1, 1, locator);
        when(view.getBaseX()).thenReturn(3200);
        cache.get(again, view, 1, 1, locator);
        when(view.getBaseY()).thenReturn(3200);
        cache.get(again, view, 1, 1, locator);
        verify(locator, times(5 * 169)).sceneZone(anyInt(), anyInt());
    }

    /** The plugin keeps one scene for both overlays, keyed by its own count of scene loads. */
    @Test
    public void thePluginKeepsOneSceneForBothOverlays() throws Exception
    {
        FateLockedPlugin plugin = new FateLockedPlugin();
        Client client = mock(Client.class);
        Field field = FateLockedPlugin.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(plugin, client);
        field = FateLockedPlugin.class.getDeclaredField("chunkLocator");
        field.setAccessible(true);
        field.set(plugin, locator);
        when(locator.client()).thenReturn(client);

        SceneEdges first = plugin.sceneEdges(mine, view, 0);
        assertSame(first, plugin.sceneEdges(mine, view, 0));

        net.runelite.api.events.GameStateChanged loading = new net.runelite.api.events.GameStateChanged();
        loading.setGameState(net.runelite.api.GameState.LOADING);
        plugin.onGameStateChanged(loading);
        clearInvocations(locator);
        plugin.sceneEdges(mine, view, 0);
        verify(locator, times(169)).sceneZone(anyInt(), anyInt());
    }
}
