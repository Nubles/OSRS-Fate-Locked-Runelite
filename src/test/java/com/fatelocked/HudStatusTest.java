package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.components.LineComponent;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B5: the HUD's "Here" and "Status" lines, from the decision service. On
 * vanilla-mid, Glarial's Tomb now reads "Not ready" (the HUD said
 * "Unlocked"), another character reads "Wrong account" (the HUD showed the
 * other character's locks), and the sea the rules don't cover reads
 * "Unknown". The "Here" label is the area name, as before.
 */
public class HudStatusTest
{
    private static final Gson GSON = new Gson();
    private static final CanonicalChunk SEERS = new CanonicalChunk(42, 54);
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final CanonicalChunk GLARIALS_TOMB = new CanonicalChunk(39, 53);
    private static final CanonicalChunk OCEAN = new CanonicalChunk(40, 41);

    @Test
    public void theStatusLineSaysWhatTheRulesSay() throws Exception
    {
        DecisionService mid = playing("vanilla-mid", "iron example");

        assertEquals(new HudStatus("LOCKED", HudStatus.RED), status(mid, SEERS));
        assertEquals(new HudStatus("Unlocked", HudStatus.GREEN), status(mid, LUMBRIDGE));
        assertEquals(new HudStatus("Not ready", HudStatus.AMBER), status(mid, GLARIALS_TOMB));
        // The sea has no snapshot; the rules' chunk entries lock it without Sailing (R1).
        assertEquals(new HudStatus("LOCKED", HudStatus.RED), status(mid, OCEAN));
    }

    @Test
    public void anotherCharacterSeesWrongAccountNotTheOtherCharactersLocks() throws Exception
    {
        DecisionService other = playing("vanilla-mid", "someone else");

        for (CanonicalChunk chunk : List.of(SEERS, LUMBRIDGE, GLARIALS_TOMB, OCEAN))
        {
            assertEquals(chunk.toString(), new HudStatus("Wrong account", HudStatus.AMBER), status(other, chunk));
        }
    }

    /** Every golden chunk the rules decide shows its entry; the Here label is the old area name. */
    @Test
    public void everyGoldenChunkShowsItsEntryUnderTheSameName() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            FateLockedBundle bundle = FateLockedBundle.loadFromJson(GSON, json);
            JsonObject chunks = GSON.fromJson(json, JsonObject.class).getAsJsonObject("rules").getAsJsonObject("chunks");
            DecisionService trusted = DecisionService.create(RulesSnapshot.of(bundle), null, null);
            DecisionService other = DecisionService.create(RulesSnapshot.of(bundle), "iron example", "someone else");
            List<String> mismatches = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : chunks.entrySet())
            {
                CanonicalChunk chunk = GoldenBundleContractTest.chunk(entry.getKey());
                String want = text(entry.getValue().getAsJsonObject().get("entry").getAsString());
                String got = status(trusted, chunk).getText();
                if (!want.equals(got)
                    || !String.valueOf(bundle.labelAt(chunk)).equals(String.valueOf(trusted.areaName(chunk)))
                    || !String.valueOf(bundle.labelAt(chunk)).equals(String.valueOf(other.areaName(chunk))))
                {
                    mismatches.add(entry.getKey() + " want " + want + " got " + got);
                }
            }
            assertTrue(id, chunks.size() > 600);
            assertEquals(id, List.of(), mismatches);
        }
    }

    /** The HUD itself draws these lines: another character in Seers' Village. */
    @Test
    public void theHudDrawsTheDecisionServicesAnswer() throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz"));
        FateLockedBundle mid = FateLockedBundle.loadFromJson(GSON, json);
        FateLockedPlugin plugin = mock(FateLockedPlugin.class);
        when(plugin.getBundle()).thenReturn(mid);
        FateLockedConfig config = mock(FateLockedConfig.class);
        when(config.showHud()).thenReturn(true);
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Someone Else");
        TestWorld.standAt(client, player, new WorldPoint((SEERS.getCx() << 6) + 10, (SEERS.getCy() << 6) + 10, 0));
        when(client.getLocalPlayer()).thenReturn(player);
        FateLockedHudOverlay hud = new FateLockedHudOverlay(client, plugin, config);
        hud.setClearChildren(false);

        when(plugin.decisions()).thenReturn(DecisionService.create(RulesSnapshot.of(mid), "iron example", "someone else"));
        assertEquals("Wrong account", drawn(hud).get("Status"));
        hud.getPanelComponent().getChildren().clear();

        when(plugin.decisions()).thenReturn(DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example"));
        Map<String, String> lines = drawn(hud);
        assertEquals("LOCKED", lines.get("Status"));
        assertTrue(lines.get("Here"), lines.get("Here").startsWith("Seers' Village"));
    }

    /** Draw the HUD once; its lines, left text to right text. */
    static Map<String, String> drawn(FateLockedHudOverlay hud) throws Exception
    {
        hud.render(new BufferedImage(400, 600, BufferedImage.TYPE_INT_ARGB).createGraphics());
        Field left = LineComponent.class.getDeclaredField("left");
        Field right = LineComponent.class.getDeclaredField("right");
        left.setAccessible(true);
        right.setAccessible(true);
        Map<String, String> lines = new HashMap<>();
        for (Object child : hud.getPanelComponent().getChildren())
        {
            if (child instanceof LineComponent) lines.put((String) left.get(child), (String) right.get(child));
        }
        return lines;
    }

    /** B16: the HUD's Strict Mode line, from the same status as the sidebar; none when off. */
    @Test
    public void theHudShowsStrictModesStatus() throws Exception
    {
        FateLockedPlugin plugin = mock(FateLockedPlugin.class);
        when(plugin.getBundle()).thenReturn(FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz"))));
        when(plugin.decisions()).thenReturn(DecisionService.create(RulesSnapshot.empty(), null, null));
        FateLockedConfig config = mock(FateLockedConfig.class);
        when(config.showHud()).thenReturn(true);
        FateLockedHudOverlay hud = new FateLockedHudOverlay(mock(Client.class), plugin, config);
        hud.setClearChildren(false);

        when(plugin.getStrictModeStatus()).thenReturn(
            com.fatelocked.guardian.StrictModeStatusView.of(true, true, 42, null));
        assertEquals("Paused · 42s", drawn(hud).get("Strict"));
        hud.getPanelComponent().getChildren().clear();

        when(plugin.getStrictModeStatus()).thenReturn(
            com.fatelocked.guardian.StrictModeStatusView.of(true, false, 0, "you are not logged in"));
        assertEquals("Inactive", drawn(hud).get("Strict"));
        hud.getPanelComponent().getChildren().clear();

        when(plugin.getStrictModeStatus()).thenReturn(
            com.fatelocked.guardian.StrictModeStatusView.of(false, false, 0, null));
        assertEquals(null, drawn(hud).get("Strict"));
    }

    /** An older export's Status is its own lock state, as before. */
    @Test
    public void anOlderExportShowsItsOwnLockState() throws Exception
    {
        FateLockedBundle v3 = FateLockedBundle.loadFromJson(GSON, text());
        DecisionService nubles = DecisionService.create(RulesSnapshot.of(v3), "nubles", "nubles");
        CanonicalChunk falador = new CanonicalChunk(46, 52);

        assertEquals(FateLockedBundle.LockState.UNLOCKED, v3.lockStateAt(falador));
        assertEquals(new HudStatus("Unlocked", HudStatus.GREEN), status(nubles, falador));
        assertEquals("Falador · Asgarnia", nubles.areaName(falador));
    }

    private static HudStatus status(DecisionService decisions, CanonicalChunk chunk)
    {
        return HudStatus.of(decisions.chunk(chunk));
    }

    private static String text(String entry)
    {
        switch (entry)
        {
            case "ALLOWED": return "Unlocked";
            case "LOCKED": return "LOCKED";
            case "NOT_READY": return "Not ready";
            default: return "Unknown";
        }
    }

    private static DecisionService playing(String id, String player) throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        return DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), "iron example", player);
    }

    private static String text() throws Exception
    {
        try (InputStream in = HudStatusTest.class.getClassLoader().getResourceAsStream("bundles/v3-standard.json"))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
