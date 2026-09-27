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
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B5: the HUD's "Here" and "Status" lines, from the decision service. On
 * vanilla-mid, Glarial's Tomb now reads "Not ready" (the HUD said
 * "Unlocked"), another character reads "Wrong account" (the HUD showed the
 * other character's locks), and the sea the rules don't cover reads
 * "Unknown". E8: the "Here" label is the tracker's area, which names every
 * golden chunk as the plugin's area lists did, and a locked or not-ready
 * chunk says why in a "Why" line, in the tracker's words.
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

        assertEquals(new HudStatus("LOCKED", HudStatus.RED, "Unlock Seers' Village"), status(mid, SEERS));
        assertEquals(new HudStatus("Unlocked", HudStatus.GREEN, null), status(mid, LUMBRIDGE));
        assertEquals(new HudStatus("Not ready", HudStatus.AMBER, "No route from Lumbridge"), status(mid, GLARIALS_TOMB));
        // The sea has no snapshot; the rules' chunk entries lock it without Sailing (R1), and say no more.
        assertEquals(new HudStatus("LOCKED", HudStatus.RED, null), status(mid, OCEAN));
    }

    @Test
    public void anotherCharacterSeesWrongAccountNotTheOtherCharactersLocks() throws Exception
    {
        DecisionService other = playing("vanilla-mid", "someone else");

        for (CanonicalChunk chunk : List.of(SEERS, LUMBRIDGE, GLARIALS_TOMB, OCEAN))
        {
            assertEquals(chunk.toString(), new HudStatus("Wrong account", HudStatus.AMBER, null), status(other, chunk));
        }
    }

    /**
     * Every golden chunk the rules decide shows its entry and the tracker's
     * reason, under the tracker's area: the same name as the old area lists.
     */
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
            int reasons = 0;
            for (Map.Entry<String, JsonElement> entry : chunks.entrySet())
            {
                CanonicalChunk chunk = GoldenBundleContractTest.chunk(entry.getKey());
                JsonObject snapshot = entry.getValue().getAsJsonObject();
                String status = snapshot.get("entry").getAsString();
                String want = text(status);
                String why = "LOCKED".equals(status) || "NOT_READY".equals(status) ? string(snapshot, "entryReason") : null;
                String area = string(snapshot, "area");
                String region = string(snapshot, "region");
                String here = area == null ? null : region == null || region.equals(area) ? area : area + " · " + region;
                HudStatus got = status(trusted, chunk);
                if (why != null) reasons++;
                if (!want.equals(got.getText()) || !Objects.equals(why, got.getWhy())
                    || !Objects.equals(here, trusted.areaName(chunk))
                    || !Objects.equals(here, other.areaName(chunk))
                    || !Objects.equals(bundle.labelAt(chunk), trusted.areaName(chunk)))
                {
                    mismatches.add(entry.getKey() + " want " + want + " (" + why + ") in " + here
                        + " got " + got + " in " + trusted.areaName(chunk));
                }
            }
            assertTrue(id, chunks.size() > 600);
            assertTrue(id, reasons > 100);
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
        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.COMPACT);
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Someone Else");
        TestWorld.standAt(client, player, new WorldPoint((SEERS.getCx() << 6) + 10, (SEERS.getCy() << 6) + 10, 0));
        when(client.getLocalPlayer()).thenReturn(player);
        FateLockedHudOverlay hud = new FateLockedHudOverlay(client, plugin, config);
        hud.setClearChildren(false);

        when(plugin.decisions()).thenReturn(DecisionService.create(RulesSnapshot.of(mid), "iron example", "someone else"));
        Map<String, String> other = drawn(hud);
        assertEquals("Wrong account", other.get("Status"));
        assertFalse("another character's reasons aren't this one's", other.containsKey("Why"));
        hud.getPanelComponent().getChildren().clear();

        when(plugin.decisions()).thenReturn(DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example"));
        Map<String, String> lines = drawn(hud);
        assertEquals("LOCKED", lines.get("Status"));
        assertEquals("Unlock Seers' Village", lines.get("Why"));
        assertTrue(lines.get("Here"), lines.get("Here").startsWith("Seers' Village"));

        hud.getPanelComponent().getChildren().clear();
        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.OFF);
        assertTrue("the HUD's Off draws nothing", drawn(hud).isEmpty());
    }

    /** E8: the tracker's area names a chunk, with its region, ahead of the area lists older rules use. */
    @Test
    public void theTrackersAreaNamesTheChunk() throws Exception
    {
        JsonObject root = GSON.fromJson(fixtureText("bundles/v4-rules.json"), JsonObject.class);
        JsonObject lumbridge = root.getAsJsonObject("rules").getAsJsonObject("chunks").getAsJsonObject("50,50");
        assertEquals("older rules: the area lists", "Lumbridge · Misthalin", anyone(root).areaName(LUMBRIDGE));

        lumbridge.addProperty("area", "Lumbridge Swamp");
        assertEquals("Lumbridge Swamp · Misthalin", anyone(root).areaName(LUMBRIDGE));
        lumbridge.addProperty("area", "Misthalin");
        assertEquals("Misthalin", anyone(root).areaName(LUMBRIDGE));
        lumbridge.addProperty("area", "Lumbridge Swamp");
        lumbridge.addProperty("region", " ");
        assertEquals("Lumbridge Swamp", anyone(root).areaName(LUMBRIDGE));
        lumbridge.addProperty("area", " ");
        assertEquals("a blank area: the area lists", "Lumbridge · Misthalin", anyone(root).areaName(LUMBRIDGE));
    }

    /** E8: a reason is trimmed, and a blank one is none. */
    @Test
    public void aBlankReasonIsNone() throws Exception
    {
        JsonObject root = GSON.fromJson(fixtureText("bundles/v4-rules.json"), JsonObject.class);
        JsonObject lumbridge = root.getAsJsonObject("rules").getAsJsonObject("chunks").getAsJsonObject("50,50");
        lumbridge.addProperty("entry", "LOCKED");
        lumbridge.addProperty("entryReason", " Unlock Lumbridge ");
        assertEquals(new HudStatus("LOCKED", HudStatus.RED, "Unlock Lumbridge"), status(anyone(root), LUMBRIDGE));
        lumbridge.addProperty("entryReason", "  ");
        assertEquals(new HudStatus("LOCKED", HudStatus.RED, null), status(anyone(root), LUMBRIDGE));
    }

    /** E8: in an interior the Here label is its name from the rules' places; one with none, and the sea, have none. */
    @Test
    public void anInteriorIsNamedByItsPlace() throws Exception
    {
        DecisionService interiors = playing("vanilla-interiors", "someone else");
        assertEquals("Kurask Lair", interiors.areaName(new CanonicalChunk(18, 143)));
        assertEquals(null, interiors.areaName(new CanonicalChunk(18, 155)));
        assertEquals(null, playing("vanilla-mid", "iron example").areaName(OCEAN));
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
        when(config.hudMode()).thenReturn(FateLockedConfig.HudMode.COMPACT);
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
        assertEquals(new HudStatus("Unlocked", HudStatus.GREEN, null), status(nubles, falador));
        FateLockedBundle asgarniaLocked = FateLockedBundle.loadFromJson(GSON,
            "{\"version\":3,\"chunks\":{\"Asgarnia\":[{\"cx\":46,\"cy\":52}]},\"unlockedRegions\":[]}");
        assertEquals("an older export gives no reasons", new HudStatus("LOCKED", HudStatus.RED, null),
            status(DecisionService.create(RulesSnapshot.of(asgarniaLocked), null, null), falador));
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
        return fixtureText("bundles/v3-standard.json");
    }

    private static String fixtureText(String name) throws Exception
    {
        try (InputStream in = HudStatusTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The rules as any character sees them: names aren't decisions. */
    private static DecisionService anyone(JsonObject root)
    {
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, root.toString())), null, null);
    }

    /** A snapshot's string field; null when absent, null or not a string. */
    private static String string(JsonObject snapshot, String field)
    {
        JsonElement value = snapshot.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
}
