package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Progress;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B10: unlock progress in the HUD and the infobox comes from the decision
 * service, with today's counts until the tracker's own (E5): vanilla-mid
 * stays 13/177 areas. Another character sees none.
 */
public class ProgressTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void everyGoldenKeepsTodaysCounts() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            FateLockedBundle bundle = golden(id);
            Progress progress = trusted(bundle).progress();

            assertEquals(id, new Progress(bundle.getUnlockedAreas(), bundle.getTotalAreas(),
                bundle.getUnlockedChunks(), bundle.getTotalChunks()), progress);
        }
        Progress mid = trusted(golden("vanilla-mid")).progress();
        assertEquals(13, mid.getUnlockedAreas());
        assertEquals(177, mid.getTotalAreas());
    }

    @Test
    public void theHudAndInfoBoxShowItOnlyForTheRulesCharacter() throws Exception
    {
        FateLockedBundle mid = golden("vanilla-mid");
        Progress progress = trusted(mid).progress();
        String percent = progress.percent() + "%";

        assertEquals("13/177 · " + percent, ProgressText.hudLine(progress));
        assertEquals(percent, ProgressText.infoBoxText(progress));
        assertEquals("Unlock progress: 13/177 areas · " + progress.getUnlockedChunks() + "/"
            + progress.getTotalChunks() + " chunks", ProgressText.infoBoxTooltip(progress));

        DecisionService other = DecisionService.create(RulesSnapshot.of(mid), "iron example", "someone else");
        assertNull(other.progress());
        assertNull(ProgressText.hudLine(null));
        assertEquals("—", ProgressText.infoBoxText(null));
        assertEquals("Unlock progress", ProgressText.infoBoxTooltip(null));
        assertNull("no chunks, no line", ProgressText.hudLine(new Progress(0, 0, 0, 0)));
        assertEquals(-1, new Progress(0, 0, 0, 0).percent());
    }

    /** The HUD draws its "Unlocked" line from the decision service. */
    @Test
    public void theHudDrawsTheLine() throws Exception
    {
        FateLockedBundle mid = golden("vanilla-mid");
        FateLockedPlugin plugin = mock(FateLockedPlugin.class);
        when(plugin.getBundle()).thenReturn(mid);
        FateLockedConfig config = mock(FateLockedConfig.class);
        when(config.showHud()).thenReturn(true);
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Iron Example");
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        when(client.getLocalPlayer()).thenReturn(player);
        FateLockedHudOverlay hud = new FateLockedHudOverlay(client, plugin, config);
        hud.setClearChildren(false);

        when(plugin.decisions()).thenReturn(trusted(mid));
        assertEquals(ProgressText.hudLine(trusted(mid).progress()), HudStatusTest.drawn(hud).get("Unlocked"));
        hud.getPanelComponent().getChildren().clear();

        when(plugin.decisions()).thenReturn(
            DecisionService.create(RulesSnapshot.of(mid), "iron example", "someone else"));
        assertFalse(HudStatusTest.drawn(hud).containsKey("Unlocked"));
    }

    private static DecisionService trusted(FateLockedBundle bundle)
    {
        return DecisionService.create(RulesSnapshot.of(bundle), null, null);
    }

    private static FateLockedBundle golden(String id) throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")));
    }
}
