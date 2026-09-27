package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Before;
import org.junit.Test;

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
