package com.fatelocked;

import com.fatelocked.guardian.travel.IntentClassifier;
import com.fatelocked.guardian.travel.TravelMatch;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.TravelTable;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Runs the web app's golden bundles through the real codec and rule engine
 * and compares the answers with the app's own (see
 * src/test/resources/contracts/PINNED for the web commit they came from).
 */
@RunWith(Parameterized.class)
public class GoldenBundleContractTest
{
    static final String DIR = "contracts/golden-bundles/";
    private static final Gson GSON = new Gson();

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> scenarios() throws IOException
    {
        List<Object[]> ids = new ArrayList<>();
        for (JsonElement scenario : json("manifest.json").getAsJsonArray("scenarios"))
        {
            ids.add(new Object[] {scenario.getAsJsonObject().get("id").getAsString()});
        }
        return ids;
    }

    private final String id;
    private final byte[] gzipped;
    private final FateLockedBundle bundle;
    private final JsonObject expected;

    public GoldenBundleContractTest(String id) throws IOException
    {
        this.id = id;
        gzipped = bytes(id + ".bundle.json.gz");
        bundle = FateLockedBundle.loadFromJson(GSON, gunzip(gzipped));
        expected = json(id + ".expect.json");
    }

    @Test
    public void landChunksMatchTheTracker()
    {
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("chunks").entrySet())
        {
            CanonicalChunk chunk = chunk(entry.getKey());
            if (!isLand(chunk)) continue;
            FateLockedBundle.LockState want = entry.getValue().getAsBoolean()
                ? FateLockedBundle.LockState.UNLOCKED : FateLockedBundle.LockState.LOCKED;
            FateLockedBundle.LockState got = bundle.lockStateAt(chunk);
            if (got != want) mismatches.add(entry.getKey() + " want " + want + " got " + got);
        }
        assertEquals(id + " land chunks", List.of(), mismatches);
    }

    /**
     * Every land, ocean and interior chunk reads the tracker's entry (R1), and
     * a place without a snapshot is named as the tracker names it.
     */
    @Test
    public void everyPlaceMatchesTheTracker() throws IOException
    {
        DecisionService engine = DecisionService.create(RulesSnapshot.of(bundle), null, null);
        List<String> mismatches = new ArrayList<>();
        int places = 0;
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("entries").entrySet())
        {
            CanonicalChunk chunk = chunk(entry.getKey());
            Decision decision = engine.chunk(chunk);
            if (!entry.getValue().getAsString().equals(decision.getStatus().name()))
            {
                mismatches.add(entry.getKey() + " want " + entry.getValue().getAsString() + " got " + decision.getStatus());
            }
            if (!isLand(chunk)) places++;
        }
        assertEquals(id + " entries", List.of(), mismatches);
        assertTrue(id + " has ocean or interior chunks", places > 0);

        List<String> misnamed = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : json("places.json").getAsJsonObject("places").entrySet())
        {
            CanonicalChunk chunk = chunk(entry.getKey());
            if (bundle.permissionsAt(chunk).isPresent()) continue;
            JsonObject place = entry.getValue().getAsJsonObject();
            String want = "ocean".equals(place.get("kind").getAsString()) ? "Ocean"
                : place.has("name") ? place.get("name").getAsString()
                : place.has("area") ? place.get("area").getAsString() : null;
            String got = engine.chunk(chunk).getLabel();
            if (!Objects.equals(want, got)) misnamed.add(entry.getKey() + " want " + want + " got " + got);
        }
        assertEquals(id + " place names", List.of(), misnamed);
    }

    @Test
    public void namedAreasMatchTheTracker()
    {
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("areas").entrySet())
        {
            boolean want = entry.getValue().getAsBoolean();
            if (bundle.isUnlocked(entry.getKey()) != want)
            {
                mismatches.add(entry.getKey() + " want " + want);
            }
        }
        assertEquals(id + " named areas", List.of(), mismatches);
    }

    @Test
    public void banksMatchTheTracker()
    {
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("banks").entrySet())
        {
            int bankId = Integer.parseInt(entry.getKey());
            CanonicalChunk chunk = new CanonicalChunk(bankId / 256, bankId % 256);
            boolean want = entry.getValue().getAsBoolean();
            if (bundle.isBankUnlocked(chunk) != want) mismatches.add(entry.getKey() + " want " + want);
        }
        assertEquals(id + " banks", List.of(), mismatches);
    }

    @Test
    public void frontierMatchesTheTracker()
    {
        if (!expected.has("frontier")) return;
        TreeSet<String> want = new TreeSet<>();
        for (JsonElement key : expected.getAsJsonArray("frontier")) want.add(key.getAsString());
        TreeSet<String> got = new TreeSet<>();
        DecisionService engine = DecisionService.create(RulesSnapshot.of(bundle), null, null);
        for (String key : expected.getAsJsonObject("chunks").keySet())
        {
            CanonicalChunk chunk = chunk(key);
            if (isLand(chunk) && engine.isFrontier(chunk)) got.add(key);
        }
        assertEquals(id + " frontier", want, got);
    }

    /**
     * F2 (G7, G13): every option of every travel method, clicked on each of
     * its ids as the game gives them, is classified back to that method and
     * option by id alone; and each answer the golden pins is the tracker's.
     */
    @Test
    public void travelMatchesTheTracker()
    {
        TravelTable table = bundle.getRules().getTravel();
        IntentClassifier classifier = new IntentClassifier();
        List<String> mismatches = new ArrayList<>();
        int clicks = 0;
        for (TravelTable.Method method : table.methods())
        {
            for (String option : texts(method))
            {
                for (MenuFacts click : clicks(method, option))
                {
                    clicks++;
                    TravelMatch match = classifier.classify(click, table);
                    if (match == null || match.getMethod() != method || match.getOption() != method.option(option))
                    {
                        mismatches.add(method.getId() + "|" + option + " on " + click);
                    }
                }
            }
        }
        assertTrue(id + " clicks " + clicks, clicks > 1000);
        assertEquals(id + " travel", List.of(), mismatches);

        for (Map.Entry<String, JsonElement> answer : expected.getAsJsonObject("travel").entrySet())
        {
            String[] key = answer.getKey().split("\\|", 2);
            String option = key[1].startsWith("code:") ? "Last-destination (" + key[1].substring(5) + ")" : key[1];
            TravelMatch match = classifier.classify(clicks(table.method(key[0]), option).get(0), table);
            assertEquals(id + " " + answer.getKey(), answer.getValue().getAsString(), match.getOption().getStatus().name());
        }
    }

    /** The menu texts a method's options are clicked as: each option, and a fairy ring's Last-destination for each code. */
    static List<String> texts(TravelTable.Method method)
    {
        List<String> texts = new ArrayList<>(method.getOptions().keySet());
        for (String code : method.getCodes().keySet()) texts.add("Last-destination (" + code + ")");
        return texts;
    }

    /** Clicks on a method as the game gives them: its spell on the spellbook, or each id in the inventory or the scene. */
    static List<MenuFacts> clicks(TravelTable.Method method, String option)
    {
        List<MenuFacts> clicks = new ArrayList<>();
        if (method.getMatch() == TravelTable.Match.SPELL)
        {
            clicks.add(MenuFacts.builder().kind(MenuFacts.Kind.WIDGET).interfaceGroup(InterfaceID.MAGIC_SPELLBOOK)
                .spellbook(TravelTable.SPELLBOOKS.indexOf(method.getSpellbook())).option(option)
                .target(method.getSpell()).build());
            return clicks;
        }
        for (int itemOrObject : method.getIds())
        {
            MenuFacts.MenuFactsBuilder click = MenuFacts.builder().option(option).target(method.getLabel());
            switch (method.getMatch())
            {
                case ITEMS:
                    click.kind(MenuFacts.Kind.WIDGET).interfaceGroup(InterfaceID.INVENTORY).itemId(itemOrObject);
                    break;
                case OBJECTS:
                    click.kind(MenuFacts.Kind.OBJECT).objectId(itemOrObject);
                    break;
                default:
                    click.kind(MenuFacts.Kind.NPC).npcId(itemOrObject);
                    break;
            }
            clicks.add(click.build());
        }
        return clicks;
    }

    @Test
    public void rulesEntriesSurviveTheCodec()
    {
        JsonObject rulesChunks = GSON.fromJson(gunzip(gzipped), JsonObject.class)
            .getAsJsonObject("rules").getAsJsonObject("chunks");
        DecisionService engine = DecisionService.create(RulesSnapshot.of(bundle), null, null);
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : rulesChunks.entrySet())
        {
            String want = entry.getValue().getAsJsonObject().get("entry").getAsString();
            String got = engine.chunk(chunk(entry.getKey())).getStatus().name();
            if (!want.equals(got)) mismatches.add(entry.getKey() + " want " + want + " got " + got);
        }
        assertTrue(id + " has v4 rules", rulesChunks.size() > 0);
        assertEquals(id + " v4 entries", List.of(), mismatches);
    }

    @Test
    public void compressedBundleDecodesToTheSameRules()
    {
        FateLockedBundle compressed = FateLockedBundle.loadFromJson(GSON,
            "FLGZ:" + Base64.getEncoder().encodeToString(gzipped));
        for (String key : expected.getAsJsonObject("chunks").keySet())
        {
            CanonicalChunk chunk = chunk(key);
            assertEquals(id + " " + key, bundle.lockStateAt(chunk), compressed.lockStateAt(chunk));
        }
        for (String name : expected.getAsJsonObject("areas").keySet())
        {
            assertEquals(id + " " + name, bundle.isUnlocked(name), compressed.isUnlocked(name));
        }
    }

    /** A chunk the plugin's legacy engine maps to a named area or continent. */
    private boolean isLand(CanonicalChunk chunk)
    {
        return bundle.regionAt(chunk) != null || bundle.subAreaAt(chunk) != null;
    }

    static CanonicalChunk chunk(String key)
    {
        String[] parts = key.split(",");
        return new CanonicalChunk(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    static JsonObject json(String name) throws IOException
    {
        return GSON.fromJson(new String(bytes(name), StandardCharsets.UTF_8), JsonObject.class);
    }

    static byte[] bytes(String name) throws IOException
    {
        try (InputStream in = GoldenBundleContractTest.class.getClassLoader()
            .getResourceAsStream(DIR + name))
        {
            if (in == null) throw new IOException("missing golden file " + name);
            return in.readAllBytes();
        }
    }

    static String gunzip(byte[] gzipped)
    {
        try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gzipped));
             ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("corrupt golden bundle", ex);
        }
    }
}
