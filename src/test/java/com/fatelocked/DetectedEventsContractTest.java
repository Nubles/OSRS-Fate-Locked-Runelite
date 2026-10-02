package com.fatelocked;

import com.fatelocked.detection.DetectionTables;
import com.fatelocked.detection.Detectors;
import com.fatelocked.detection.DiaryTiers;
import com.fatelocked.detection.Signal;
import com.fatelocked.detectors.DetectedEvent;
import com.fatelocked.events.EventIds;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.Quest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The plugin's side of contracts/golden-bundles/detected-events.json (Stage 4): RuneLite's
 * detectors make exactly each case's events of its real game signals, with the tables the web
 * sends in every bundle. The web's detectedEventsContract.test.ts classifies the same events.
 */
public class DetectedEventsContractTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void everyCaseMakesExactlyItsEvents() throws Exception
    {
        DetectionTables tables = tables();
        JsonArray cases = contract().getAsJsonArray("cases");
        assertTrue(cases.size() > 30);
        for (JsonElement item : cases)
        {
            JsonObject contractCase = item.getAsJsonObject();
            String id = contractCase.get("id").getAsString();
            Detectors detectors = new Detectors(tables, null, null);
            List<DetectedEvent> made = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonElement signal : contractCase.getAsJsonArray("signals"))
            {
                for (DetectedEvent event : detectors.on(signal(signal.getAsJsonObject())))
                {
                    // The store keeps one event per id: something seen twice is recorded once.
                    if (event.getCount() == null || ids.add(EventIds.of("Iron Example", "contract-run",
                        event.getType(), event.getCanonicalLabel(), event.getCount())))
                    {
                        made.add(event);
                    }
                }
            }
            JsonArray expected = contractCase.getAsJsonArray("events");
            assertEquals(id, expected.size(), made.size());
            for (int i = 0; i < made.size(); i++)
            {
                JsonObject want = expected.get(i).getAsJsonObject();
                DetectedEvent got = made.get(i);
                String at = id + " #" + i;
                assertEquals(at, want.get("eventType").getAsString(), got.getType().name());
                assertEquals(at, want.get("canonicalLabel").getAsString(), got.getCanonicalLabel());
                assertEquals(at, want.get("confidence").getAsString(), got.getConfidence().name());
                assertEquals(at, want.get("detectorId").getAsString(), got.getDetectorId());
                assertEquals(at, want.get("detectorVersion").getAsInt(), got.getDetectorVersion());
                assertEquals(at, want.get("evidence"), GSON.toJsonTree(got.getEvidence()));
            }
        }
    }

    @Test
    public void everySignalTheContractNamesIsRead() throws Exception
    {
        Set<String> kinds = new TreeSet<>(contract().getAsJsonObject("signals").keySet());
        Set<String> seen = new TreeSet<>();
        for (JsonElement item : contract().getAsJsonArray("cases"))
        {
            for (JsonElement signal : item.getAsJsonObject().getAsJsonArray("signals"))
            {
                seen.add(signal.getAsJsonObject().get("kind").getAsString());
            }
        }
        assertEquals(kinds, seen);
    }

    /** Every quest RuneLite knows has the tracker's id, but the whole of Recipe for Disaster: its last part is RFD: Finale. */
    @Test
    public void everyQuestRuneLiteKnowsHasTheTrackersId() throws Exception
    {
        DetectionTables tables = tables();
        List<String> unnamed = new ArrayList<>();
        Set<String> named = new TreeSet<>();
        for (Quest quest : Quest.values())
        {
            String id = tables.questId(quest.getName());
            if (id == null) unnamed.add(quest.getName());
            else named.add(id);
        }
        assertEquals(List.of("Recipe for Disaster"), unnamed);

        // And every quest the tracker lists is one RuneLite can see finished.
        Set<String> listed = new TreeSet<>();
        for (JsonElement quest : detection().getAsJsonArray("quests"))
        {
            listed.add(quest.getAsJsonObject().get("id").getAsString());
        }
        assertEquals(listed, named);
    }

    @Test
    public void everyDiaryTierIsTheTrackers() throws Exception
    {
        Set<String> listed = new TreeSet<>();
        for (JsonElement tier : detection().getAsJsonArray("diaryTiers"))
        {
            listed.add(tier.getAsString());
        }
        assertEquals(listed, new TreeSet<>(DiaryTiers.TIER_IDS.values()));
    }

    private static Signal signal(JsonObject signal)
    {
        String kind = signal.get("kind").getAsString();
        switch (kind)
        {
            case "chat":
                return new Signal.Chat(signal.get("type").getAsString(), signal.get("message").getAsString());
            case "popup":
                return new Signal.Popup(signal.get("title").getAsString(), signal.get("text").getAsString());
            case "levels":
            {
                Map<String, Integer> levels = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> level : signal.getAsJsonObject("levels").entrySet())
                {
                    levels.put(level.getKey(), level.getValue().getAsInt());
                }
                return new Signal.Levels(levels);
            }
            case "level":
                return new Signal.Level(signal.get("skill").getAsString(), signal.get("level").getAsInt());
            case "quests":
            {
                Set<String> finished = new HashSet<>();
                for (JsonElement quest : signal.getAsJsonArray("finished"))
                {
                    finished.add(quest.getAsString());
                }
                return new Signal.Quests(finished);
            }
            case "varbits":
            {
                Map<Integer, Integer> values = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> value : signal.getAsJsonObject("values").entrySet())
                {
                    values.put(Integer.parseInt(value.getKey()), value.getValue().getAsInt());
                }
                return new Signal.Varbits(values);
            }
            case "varbit":
                return new Signal.Varbit(signal.get("id").getAsInt(), signal.get("value").getAsInt());
            case "slayer":
                return new Signal.Slayer(signal.get("task").getAsString(), signal.get("amount").getAsInt(),
                    signal.get("assigned").getAsInt(), signal.get("master").getAsInt(), signal.get("streak").getAsInt(),
                    signal.has("bossTask") && signal.get("bossTask").getAsBoolean());
            default:
                throw new AssertionError("a signal this build can't read: " + kind);
        }
    }

    /** The web's tables, as every bundle sends them. */
    private static DetectionTables tables() throws Exception
    {
        FateLockedBundle bundle = FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-fresh.bundle.json.gz")));
        DetectionTables tables = bundle.getRules().getDetection();
        assertNotNull("the golden bundle sends rules.detection", tables);
        return tables;
    }

    private static JsonObject detection() throws Exception
    {
        return GSON.fromJson(GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-fresh.bundle.json.gz")),
            JsonObject.class).getAsJsonObject("rules").getAsJsonObject("detection");
    }

    private static JsonObject contract() throws Exception
    {
        return GoldenBundleContractTest.json("detected-events.json");
    }
}
