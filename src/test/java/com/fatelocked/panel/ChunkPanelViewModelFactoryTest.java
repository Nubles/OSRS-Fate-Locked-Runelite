package com.fatelocked.panel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ChunkPanelViewModelFactoryTest
{
    private static final Gson GSON = new Gson();
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final CanonicalChunk FALADOR = new CanonicalChunk(46, 52);
    private static final CanonicalChunk SEERS = new CanonicalChunk(42, 54);

    private final ChunkPanelViewModelFactory factory = new ChunkPanelViewModelFactory();

    private static String text(String name) throws Exception
    {
        try (InputStream in = ChunkPanelViewModelFactoryTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static FateLockedBundle fixture() throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON, text("bundles/v4-rules.json"));
    }

    /** The fixture's rules are for Nubles. */
    private static DecisionService playing(FateLockedBundle rules, String player)
    {
        return DecisionService.create(RulesSnapshot.of(rules), "nubles", player);
    }

    @Test
    public void createsOrderedCompactCategories() throws Exception
    {
        ChunkPanelViewModel view = factory.create(playing(fixture(), "nubles"), LUMBRIDGE, Instant.now());

        assertEquals(
            Arrays.asList("SKILLING", "BANKS", "SHOPS", "QUESTS", "COMBAT"),
            view.getCategories().stream()
                .map(ChunkPanelViewModel.CategoryView::getId)
                .collect(Collectors.toList()));

        ChunkPanelViewModel.RowView bank =
            view.category("BANKS").getRows().get(0);
        ChunkPanelViewModel.RowView quest =
            view.category("QUESTS").getRows().get(0);
        ChunkPanelViewModel.RowView combat =
            view.category("COMBAT").getRows().get(0);

        assertEquals("Available", bank.getStatusText());
        assertNull(bank.getDetail());
        assertEquals("○", quest.getStatusGlyph());
        assertNull(quest.getDetail());
        assertEquals("✕", combat.getStatusGlyph());
        assertEquals(PermissionStatus.LOCKED, combat.getStatus());
        assertTrue(view.getFreshnessLabel().startsWith("Synced"));
        assertNull(view.getTrustReason());
        assertEquals(view.getFreshnessLabel(), view.getStatusNote());
    }

    @Test
    public void saysWhenTheRulesWereSyncedAsATimeThatCannotGoStale() throws Exception
    {
        // The card is rebuilt only on a chunk change: "2m ago" would stay
        // on screen long after it stopped being true.
        Instant synced = Instant.now().minusSeconds(2 * 60);

        ChunkPanelViewModel view = factory.create(playing(fixture(), "nubles"), LUMBRIDGE, synced);

        assertEquals("Synced " + LocalTimeText.of(synced), view.getFreshnessLabel());
    }

    @Test
    public void omitsEmptyCategoriesAndCountsVisibleRows() throws Exception
    {
        ChunkPanelViewModel view = factory.create(playing(fixture(), "nubles"), LUMBRIDGE, null);

        assertNull(view.category("TRAVEL"));
        assertEquals(3, view.getAllowedCount());
        assertEquals(1, view.getNotReadyCount());
        assertEquals(1, view.getLockedCount());
        assertEquals(0, view.getUnknownCount());
        assertEquals("Offline snapshot", view.getFreshnessLabel());
    }

    /** U4: another character's card is Unknown, says why, and invents no locks. */
    @Test
    public void anotherCharacterSeesUnknownAndWhy() throws Exception
    {
        ChunkPanelViewModel view = factory.create(playing(fixture(), "zezima"), LUMBRIDGE, Instant.now());

        assertEquals(PermissionStatus.UNKNOWN, view.getEntryStatus());
        assertTrue(view.getCategories().isEmpty());
        assertEquals(0, view.getAllowedCount() + view.getNotReadyCount() + view.getLockedCount());
        assertEquals("Wrong account", view.getTrustReason());
        assertEquals("Wrong account", view.getStatusNote());
        // Names aren't decisions: the place is still named.
        assertEquals("Lumbridge", view.getName());
        assertEquals("Misthalin", view.getRegion());
    }

    /** A chunk the tracker lists without a name takes the area name the bundle also carries. */
    @Test
    public void anUnnamedChunkFallsBackToItsAreaName() throws Exception
    {
        JsonObject json = GSON.fromJson(text("bundles/v4-rules.json"), JsonObject.class);
        JsonObject lumbridge = json.getAsJsonObject("rules").getAsJsonObject("chunks").getAsJsonObject("50,50");
        lumbridge.remove("name");
        lumbridge.remove("region");
        FateLockedBundle unnamed = FateLockedBundle.loadFromJson(GSON, json.toString());

        for (String player : List.of("nubles", "zezima"))
        {
            ChunkPanelViewModel view = factory.create(playing(unnamed, player), LUMBRIDGE, null);
            assertEquals(player, "Lumbridge · Misthalin", view.getName());
            assertEquals(player, "Misthalin", view.getRegion());
        }
    }

    @Test
    public void withNoRulesTheCardSaysSo()
    {
        ChunkPanelViewModel view = factory.create(
            DecisionService.create(RulesSnapshot.empty(), null, null), LUMBRIDGE, null);

        assertEquals(PermissionStatus.UNKNOWN, view.getEntryStatus());
        assertEquals("No tracker rules are loaded", view.getStatusNote());
        assertEquals("Unknown chunk", view.getName());
        assertNull(view.getRegion());
        assertTrue(view.getCategories().isEmpty());
    }

    /** An older export: the card now says what the HUD and the alerts say. */
    @Test
    public void anOlderExportAgreesWithTheHud() throws Exception
    {
        FateLockedBundle legacy = legacyWithContent();
        DecisionService nubles = playing(legacy, "nubles");

        for (CanonicalChunk chunk : List.of(LUMBRIDGE, FALADOR, SEERS))
        {
            PermissionStatus hud = legacy.lockStateAt(chunk) == FateLockedBundle.LockState.LOCKED
                ? PermissionStatus.LOCKED : PermissionStatus.ALLOWED;
            assertEquals(chunk.toString(), hud, factory.create(nubles, chunk, null).getEntryStatus());
        }
        assertEquals(PermissionStatus.LOCKED, factory.create(nubles, SEERS, null).getEntryStatus());
        assertEquals(PermissionStatus.ALLOWED, factory.create(nubles, FALADOR, null).getEntryStatus());

        ChunkPanelViewModel falador = factory.create(nubles, FALADOR, null);
        assertEquals("Falador · Asgarnia", falador.getName());
        assertEquals("Asgarnia", falador.getRegion());
        assertEquals(Arrays.asList("SHOPS", "COMBAT"), falador.getCategories().stream()
            .map(ChunkPanelViewModel.CategoryView::getId).collect(Collectors.toList()));
        assertEquals("? Guard", falador.category("COMBAT").getRows().get(0).getStatusGlyph()
            + " " + falador.category("COMBAT").getRows().get(0).getName());
        assertEquals(2, falador.getUnknownCount());

        ChunkPanelViewModel other = factory.create(playing(legacy, "zezima"), FALADOR, null);
        assertEquals(PermissionStatus.UNKNOWN, other.getEntryStatus());
        assertTrue(other.getCategories().isEmpty());
        assertEquals("Wrong account", other.getStatusNote());
        assertEquals("Falador · Asgarnia", other.getName());
    }

    /** The v3 fixture, with Seers' Village locked and something listed in Falador. */
    private static FateLockedBundle legacyWithContent() throws Exception
    {
        JsonObject json = GSON.fromJson(text("bundles/v3-standard.json"), JsonObject.class);
        JsonArray seers = GSON.fromJson("[{\"cx\":42,\"cy\":54}]", JsonArray.class);
        json.getAsJsonObject("chunks").add("Kandarin", seers);
        json.getAsJsonObject("subAreaChunks").add("Seers' Village", seers.deepCopy());
        json.getAsJsonObject("regionGroups").add("Kandarin", GSON.fromJson("[\"Seers' Village\"]", JsonArray.class));
        json.add("chunkContent", GSON.fromJson(
            "{\"46,52\":{\"mon\":[\"Guard\"],\"shop\":[\"Falador General Store\"]}}", JsonObject.class));
        return FateLockedBundle.loadFromJson(GSON, json.toString());
    }
}
