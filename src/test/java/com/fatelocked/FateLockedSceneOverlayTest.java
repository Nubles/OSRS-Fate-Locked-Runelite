package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.ui.Palette;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

/** U3: the scene overlay draws the rules' edges on the tile lines, and nothing for anyone else. */
public class FateLockedSceneOverlayTest
{
    private final Client client = mock(Client.class);
    private final FateLockedPlugin plugin = mock(FateLockedPlugin.class);
    private final FateLockedConfig config = mock(FateLockedConfig.class);
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
        when(client.getTopLevelWorldView()).thenReturn(view);
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

    /** The lines and the fog are the player's to turn off: with both off, the game view draws nothing. */
    @Test
    public void bordersAndShadingOffDrawNothing()
    {
        when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.OFF);
        when(config.shadeNearbyLocked()).thenReturn(false);
        Graphics2D graphics = mock(Graphics2D.class);

        assertNull(overlay.render(graphics));

        verifyNoInteractions(graphics);
        verify(plugin, never()).decisions();
    }

    /** Turning the lines off leaves the fog to its own setting. */
    @Test
    public void shadingAloneStillDraws()
    {
        when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.OFF);
        when(config.shadeNearbyLocked()).thenReturn(true);
        when(plugin.decisions()).thenReturn(DecisionService.create(mine.rules(), "iron example", "someone else"));

        overlay.render(mock(Graphics2D.class));

        verify(plugin).decisions();
    }

    /** The lines and the fog reach the drawing as the player left them, each on its own. */
    @Test
    public void theSettingsReachTheDrawing()
    {
        ChunkLocator locator = mock(ChunkLocator.class);
        when(plugin.decisions()).thenReturn(mine);
        when(plugin.chunkLocator()).thenReturn(locator);
        when(locator.playerInScene()).thenReturn(new Located(null, null, 0, 60, 60));
        when(plugin.sceneEdges(mine, view, 0)).thenReturn(FateLockedMinimapOverlayTest.eastHalfLocked());
        when(plugin.palette()).thenReturn(Palette.defaults());
        Graphics2D graphics = mock(Graphics2D.class);

        try (MockedStatic<ChunkBorderRenderer> renderer = mockStatic(ChunkBorderRenderer.class))
        {
            when(config.shadeNearbyLocked()).thenReturn(false);
            overlay.render(graphics);
            renderer.verify(() -> ChunkBorderRenderer.draw(eq(graphics), anyList(),
                eq(FateLockedConfig.ChunkBorders.LOCKED_EDGES), eq(false), any(), eq(60), eq(60), anyInt(), anyInt(),
                any()));

            when(config.chunkBorders()).thenReturn(FateLockedConfig.ChunkBorders.OFF);
            when(config.shadeNearbyLocked()).thenReturn(true);
            overlay.render(graphics);
            renderer.verify(() -> ChunkBorderRenderer.draw(eq(graphics), anyList(),
                eq(FateLockedConfig.ChunkBorders.OFF), eq(true), any(), eq(60), eq(60), anyInt(), anyInt(), any()));
        }
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
}
