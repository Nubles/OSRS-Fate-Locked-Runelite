package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Before;
import org.junit.Test;

/** U3: the scene overlay draws the rules' edges, worked out once per scene, and nothing else. */
public class FateLockedSceneOverlayTest
{
    private final Client client = mock(Client.class);
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final ChunkLocator locator = mock(ChunkLocator.class);
    private final WorldView view = mock(WorldView.class);
    private FateLockedSceneOverlay overlay;
    private DecisionService mine;

    @Before
    public void setUp() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        mine = DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example");
        when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.LOCKED_EDGES);
        when(plugin.chunkLocator()).thenReturn(locator);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(view.getSizeX()).thenReturn(104);
        when(view.getSizeY()).thenReturn(104);
        // Four chunks around Lumbridge, split 64 tiles into the scene.
        when(locator.sceneZone(anyInt(), anyInt())).thenAnswer(zone -> new CanonicalChunk(
            50 + (int) zone.getArgument(0) / 8, 50 + (int) zone.getArgument(1) / 8));
        overlay = new FateLockedSceneOverlay(client, plugin, config);
    }

    /** The old outline stood half a tile inside the chunk, at the tiles' centres. */
    @Test
    public void aCornerSitsExactlyOnTheTileLine()
    {
        LocalPoint corner = FateLockedSceneOverlay.corner(64, 10, view);
        assertEquals(64 * 128, corner.getX());
        assertEquals(10 * 128, corner.getY());
    }

    /** Instances can load at the same base, so every scene load counts as a new scene. */
    @Test
    public void aSceneLoadIsANewScene()
    {
        FateLockedPlugin real = new FateLockedPlugin();
        net.runelite.api.events.GameStateChanged loading = new net.runelite.api.events.GameStateChanged();
        loading.setGameState(net.runelite.api.GameState.LOADING);

        real.onGameStateChanged(loading);
        real.onGameStateChanged(loading);

        assertEquals(2, real.sceneGeneration());
    }

    @Test
    public void anotherCharacterGetsNoBorders()
    {
        when(plugin.decisions()).thenReturn(DecisionService.create(mine.rules(), "iron example", "someone else"));
        Graphics2D graphics = mock(Graphics2D.class);

        assertNull(overlay.render(graphics));

        verifyNoInteractions(graphics);
        verify(plugin, never()).chunkLocator();
    }

    @Test
    public void theEdgesAreWorkedOutOncePerScene()
    {
        List<SceneEdges.Run> first = overlay.edges(mine, view, 0);
        verify(locator, times(169)).sceneZone(anyInt(), anyInt());
        SceneEdges.Zone[][] zones = new SceneEdges.Zone[13][13];
        for (int zx = 0; zx < 13; zx++)
        {
            for (int zy = 0; zy < 13; zy++)
            {
                CanonicalChunk chunk = new CanonicalChunk(50 + zx / 8, 50 + zy / 8);
                zones[zx][zy] = new SceneEdges.Zone(TintPolicy.at(mine, chunk), chunk);
            }
        }
        assertEquals("each zone as the rules tint its chunk", SceneEdges.of(zones), first);
        assertFalse(first.isEmpty());

        clearInvocations(locator);
        assertSame(first, overlay.edges(mine, view, 0));
        verify(locator, never()).sceneZone(anyInt(), anyInt());

        when(plugin.sceneGeneration()).thenReturn(1);
        overlay.edges(mine, view, 0);
        verify(locator, times(169)).sceneZone(anyInt(), anyInt());

        // Another plane, other rules, and another base each bring a new scene.
        clearInvocations(locator);
        overlay.edges(mine, view, 1);
        DecisionService again = DecisionService.create(mine.rules(), "iron example", "iron example");
        overlay.edges(again, view, 1);
        when(view.getBaseX()).thenReturn(3200);
        overlay.edges(again, view, 1);
        when(view.getBaseY()).thenReturn(3200);
        overlay.edges(again, view, 1);
        verify(locator, times(4 * 169)).sceneZone(anyInt(), anyInt());
    }
}
