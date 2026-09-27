package com.fatelocked;

import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.Trust;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The decision service against the web app's golden bundles: it must give
 * the tracker's own answer wherever the rules have one, Unknown everywhere
 * else, and nothing at all on another character.
 */
@RunWith(Parameterized.class)
public class DecisionServiceGoldenTest
{
    private static final Gson GSON = new Gson();

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> scenarios() throws IOException
    {
        return GoldenBundleContractTest.scenarios();
    }

    private final String id;
    private final JsonObject wire;
    private final JsonObject expected;
    private final RulesSnapshot rules;
    private final String account;

    public DecisionServiceGoldenTest(String id) throws IOException
    {
        this.id = id;
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        wire = GSON.fromJson(json, JsonObject.class);
        expected = GoldenBundleContractTest.json(id + ".expect.json");
        rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json));
        JsonElement bound = wire.getAsJsonObject("rules").get("account");
        account = bound == null || bound.isJsonNull() ? null : normalize(bound.getAsString());
    }

    @Test
    public void everyChunkTheRulesDecideGetsTheirAnswer()
    {
        DecisionService service = trusted();
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : rulesChunks().entrySet())
        {
            Decision decision = service.chunk(GoldenBundleContractTest.chunk(entry.getKey()));
            String want = entry.getValue().getAsJsonObject().get("entry").getAsString();
            if (!want.equals(decision.getStatus().name()) || decision.getSource() != Decision.Source.CHUNK)
            {
                mismatches.add(entry.getKey() + " want " + want + " got " + decision);
            }
        }
        assertTrue(id + " has rules", rulesChunks().size() > 600);
        assertEquals(id + " entries", List.of(), mismatches);
    }

    /** Owned or not, as the tracker says, the sea included (R1); NOT_READY is owned but not yet usable. */
    @Test
    public void everyTrackerChunkIsOwnedOrLocked()
    {
        DecisionService service = trusted();
        List<String> mismatches = new ArrayList<>();
        int sea = 0;
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("chunks").entrySet())
        {
            Decision decision = service.chunk(GoldenBundleContractTest.chunk(entry.getKey()));
            if (!rulesChunks().has(entry.getKey())) sea++;
            boolean owned = decision.getStatus() == PermissionStatus.ALLOWED
                || decision.getStatus() == PermissionStatus.NOT_READY;
            if (owned != entry.getValue().getAsBoolean() || decision.getStatus() == PermissionStatus.UNKNOWN)
            {
                mismatches.add(entry.getKey() + " want owned=" + entry.getValue() + " got " + decision);
            }
        }
        assertEquals(id + " tracker chunks", List.of(), mismatches);
        // Ocean chunks without a snapshot, which only the rules' chunk entries decide.
        assertTrue(id + " has sea without a snapshot", sea > 0);
    }

    @Test
    public void anotherCharacterGetsNoAnswers()
    {
        if (account == null) return;
        DecisionService service = DecisionService.create(rules, account, "someone else");
        assertEquals(Trust.WRONG_CHARACTER, service.trust());
        for (String key : rulesChunks().keySet())
        {
            CanonicalChunk chunk = GoldenBundleContractTest.chunk(key);
            Decision decision = service.chunk(chunk);
            assertEquals(id + " " + key, PermissionStatus.UNKNOWN, decision.getStatus());
            assertEquals(id + " " + key, Decision.Source.TRUST, decision.getSource());
            assertFalse(id + " " + key, service.details(chunk).isPresent());
            assertEquals(id + " " + key, Decision.Source.TRUST, service.bankAt(chunk).getSource());
            assertEquals(id + " " + key, Decision.Source.TRUST, service.bankRoll(chunk).getSource());
        }
        assertEquals(Trust.LOGGED_OUT, DecisionService.create(rules, account, null).trust());
        assertEquals(Trust.TRUSTED, DecisionService.create(rules, account, account).trust());
    }

    @Test
    public void anUnboundProfileIsTrustedButNotBound()
    {
        DecisionService service = DecisionService.create(rules, null, "anyone");
        assertEquals(Trust.TRUSTED, service.trust());
        assertFalse(service.isBound());
        assertTrue(service.details(GoldenBundleContractTest.chunk(rulesChunks().keySet().iterator().next())).isPresent());
    }

    /** A bank is its row: locked with its chunk, otherwise as the tracker rates it. */
    @Test
    public void banksAreTheirRows()
    {
        DecisionService service = trusted();
        int banks = 0;
        for (Map.Entry<String, JsonElement> entry : rulesChunks().entrySet())
        {
            JsonObject snapshot = entry.getValue().getAsJsonObject();
            JsonObject row = firstRow(snapshot, "BANK");
            if (row == null) continue;
            banks++;
            String want = "LOCKED".equals(snapshot.get("entry").getAsString())
                ? "LOCKED" : row.get("status").getAsString();
            Decision got = service.bankAt(GoldenBundleContractTest.chunk(entry.getKey()));
            assertEquals(id + " " + entry.getKey(), want, got.getStatus().name());
            assertEquals(id + " " + entry.getKey(), Decision.Source.ROW, got.getSource());
        }
        assertTrue(id + " has bank rows", banks > 50);
    }

    /** R5: the tracker's bank table decides each bank wherever its facilities are, as the goldens pin it. */
    @Test
    public void everyBankIsDecidedWhereItsFacilitiesAre() throws Exception
    {
        DecisionService service = trusted();
        JsonObject status = expected.getAsJsonObject("bankStatus");
        JsonObject bankAt = GoldenBundleContractTest.json("banks.json").getAsJsonObject("bankAt");
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : bankAt.entrySet())
        {
            String bank = entry.getValue().getAsString();
            String want = status.get(bank).getAsString();
            Decision got = service.bankAt(GoldenBundleContractTest.chunk(entry.getKey()));
            if (!want.equals(got.getStatus().name())) mismatches.add(entry.getKey() + " (" + bank + ") want " + want + " got " + got);
        }
        assertTrue(id + " has facility chunks", bankAt.size() > 120);
        assertEquals(id + " banks where their facilities are", List.of(), mismatches);
    }

    /** B3: whether each bank is rolled, as the tracker says, apart from its area. */
    @Test
    public void everyBankIsRolledAsTheTrackerSays()
    {
        DecisionService service = trusted();
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : expected.getAsJsonObject("banks").entrySet())
        {
            int bankId = Integer.parseInt(entry.getKey());
            Decision roll = service.bankRoll(new CanonicalChunk(bankId / 256, bankId % 256));
            PermissionStatus want = entry.getValue().getAsBoolean() ? PermissionStatus.ALLOWED : PermissionStatus.LOCKED;
            if (roll.getStatus() != want || roll.getSource() != Decision.Source.BANK_ROLL)
            {
                mismatches.add(entry.getKey() + " want " + want + " got " + roll);
            }
        }
        assertTrue(id + " has banks", expected.getAsJsonObject("banks").size() > 100);
        assertEquals(id + " bank rolls", List.of(), mismatches);
    }

    /** The tracker's rows already agree; this pins the rule for rows that don't, without the bank table. */
    @Test
    public void aLockedChunkLocksEverythingInIt()
    {
        JsonObject copy = wire.deepCopy();
        com.google.gson.JsonArray capabilities = new com.google.gson.JsonArray();
        for (JsonElement capability : copy.getAsJsonObject("rules").getAsJsonArray("capabilities"))
        {
            if (!"banks".equals(capability.getAsString())) capabilities.add(capability);
        }
        copy.getAsJsonObject("rules").add("capabilities", capabilities);
        String key = null;
        for (Map.Entry<String, JsonElement> entry : copy.getAsJsonObject("rules").getAsJsonObject("chunks").entrySet())
        {
            JsonObject row = firstRow(entry.getValue().getAsJsonObject(), "BANK");
            if (row == null) continue;
            entry.getValue().getAsJsonObject().addProperty("entry", "LOCKED");
            row.addProperty("status", "ALLOWED");
            key = entry.getKey();
            break;
        }
        RulesSnapshot edited = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, copy.toString()));
        DecisionService service = DecisionService.create(edited, account, account);
        CanonicalChunk chunk = GoldenBundleContractTest.chunk(key);
        assertEquals(id + " " + key, PermissionStatus.LOCKED, service.bankAt(chunk).getStatus());
        assertEquals(id + " " + key, PermissionStatus.LOCKED, service.target(chunk, "BANK", "").getStatus());
    }

    @Test
    public void mobilityIsUnlockedOnlyWhenRolled()
    {
        DecisionService service = trusted();
        JsonObject manifest = wire.getAsJsonObject("rules");
        List<String> rolled = new ArrayList<>();
        for (JsonElement name : manifest.getAsJsonObject("unlocks").getAsJsonArray("mobility")) rolled.add(name.getAsString());
        int known = 0;
        for (JsonElement element : manifest.getAsJsonArray("knownMobility"))
        {
            String name = element.getAsString();
            known++;
            PermissionStatus want = rolled.contains(name) ? PermissionStatus.ALLOWED : PermissionStatus.LOCKED;
            assertEquals(id + " " + name, want, service.mobility(name).getStatus());
        }
        assertTrue(id + " knows mobility unlocks", known > 5);
        assertEquals(PermissionStatus.UNKNOWN, service.mobility("Not A Real Unlock").getStatus());

        // The goldens roll no mobility unlock yet, so roll one here.
        JsonObject copy = wire.deepCopy();
        JsonObject copyRules = copy.getAsJsonObject("rules");
        String first = copyRules.getAsJsonArray("knownMobility").get(0).getAsString();
        copyRules.getAsJsonObject("unlocks").getAsJsonArray("mobility").add(first);
        RulesSnapshot rolledRules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, copy.toString()));
        Decision afterRoll = DecisionService.create(rolledRules, account, account).mobility(first);
        assertEquals(id + " " + first, PermissionStatus.ALLOWED, afterRoll.getStatus());
        assertEquals(Decision.Source.MOBILITY, afterRoll.getSource());
    }

    @Test
    public void itemsFollowTheirSlotTier()
    {
        DecisionService service = trusted();
        JsonObject manifest = wire.getAsJsonObject("rules");
        JsonObject equipment = manifest.getAsJsonObject("unlocks").getAsJsonObject("equipment");
        int checked = 0;
        for (Map.Entry<String, JsonElement> entry : manifest.getAsJsonObject("itemRules").entrySet())
        {
            JsonObject rule = entry.getValue().getAsJsonObject();
            String slot = rule.get("slot").getAsString();
            if (!equipment.has(slot)) continue;
            boolean over = rule.get("tier").getAsInt() > equipment.get(slot).getAsInt();
            Decision got = service.item(Integer.parseInt(entry.getKey()));
            assertEquals(id + " item " + entry.getKey(), over ? PermissionStatus.LOCKED : PermissionStatus.ALLOWED, got.getStatus());
            if (++checked >= 400) break;
        }
        assertTrue(id + " has item rules", checked > 0);
    }

    private DecisionService trusted()
    {
        DecisionService service = DecisionService.create(rules, account, account);
        assertEquals(Trust.TRUSTED, service.trust());
        return service;
    }

    private JsonObject rulesChunks()
    {
        return wire.getAsJsonObject("rules").getAsJsonObject("chunks");
    }

    private static JsonObject firstRow(JsonObject snapshot, String kind)
    {
        JsonObject categories = snapshot.getAsJsonObject("categories");
        if (categories == null) return null;
        for (Map.Entry<String, JsonElement> category : categories.entrySet())
        {
            for (JsonElement element : category.getValue().getAsJsonArray())
            {
                JsonObject row = element.getAsJsonObject();
                JsonElement target = row.get("targetKind");
                if (target != null && !target.isJsonNull() && kind.equalsIgnoreCase(target.getAsString())) return row;
            }
        }
        return null;
    }

    /** The tracker's normalizeAccountName, as AccountBinding applies it. */
    private static String normalize(String name)
    {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
