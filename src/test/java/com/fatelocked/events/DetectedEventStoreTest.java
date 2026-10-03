package com.fatelocked.events;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.fatelocked.events.DetectedEventStore.Status.COPIED;
import static com.fatelocked.events.DetectedEventStore.Status.DISMISSED;
import static com.fatelocked.events.DetectedEventStore.Status.NEW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DetectedEventStoreTest
{
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_790_000_000_000L;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    private final AtomicLong clock = new AtomicLong(NOW);
    private Path file;

    @Before
    public void setUp() throws IOException
    {
        file = folder.newFolder("accounts").toPath().resolve(DetectedEventStore.FILE);
    }

    private DetectedEventStore store() throws IOException
    {
        return new DetectedEventStore(gson, file, com.fatelocked.storage.LocalFileMerge::replace, clock::get);
    }

    private static FateEvent event(String id, String run, FateEventType type, long occurredAt)
    {
        return FateEvent.builder()
            .protocolVersion(1).eventId(id).runId(run).account("Nubles").runRevision(3)
            .eventType(type).canonicalLabel(type == FateEventType.PET_DROP ? null : "Label " + id)
            .occurredAt(occurredAt).sessionSequence(1).bundleVersion(4).rulesVersion("1").contentVersion(1)
            .detectorId("quest-state-v1").detectorVersion(1).confidence(EventConfidence.EXACT)
            .evidence(Collections.<String, Object>singletonMap("count", 12L))
            .build();
    }

    private static FateEvent quest(String id, long occurredAt)
    {
        return event(id, "run-1", FateEventType.QUEST, occurredAt);
    }

    private static List<String> ids(List<DetectedEventStore.Entry> entries)
    {
        return entries.stream().map(entry -> entry.getEvent().getEventId()).collect(Collectors.toList());
    }

    @Test
    public void recordsAnEventOnceByItsIdAndKeepsItNew() throws IOException
    {
        DetectedEventStore store = store();
        assertTrue(store.record(quest("a", NOW)));
        assertFalse(store.record(quest("a", NOW + 1)));

        DetectedEventStore reloaded = store();
        assertEquals(Collections.singletonList("a"), ids(reloaded.entries()));
        assertEquals(NEW, reloaded.entries().get(0).getStatus());
    }

    @Test
    public void movesAStateForwardOnly() throws IOException
    {
        DetectedEventStore store = store();
        store.record(quest("a", NOW));
        store.record(quest("b", NOW));
        store.mark(Arrays.asList("a", "b", "missing"), COPIED);
        store.mark(Collections.singletonList("a"), DISMISSED);
        store.mark(Arrays.asList("a", "b"), NEW);

        DetectedEventStore reloaded = store();
        assertEquals(DISMISSED, reloaded.entries().get(0).getStatus());
        assertEquals(COPIED, reloaded.entries().get(1).getStatus());
    }

    @Test
    public void writesNothingWhenAStateWouldGoBack() throws IOException
    {
        AtomicLong writes = new AtomicLong();
        DetectedEventStore store = new DetectedEventStore(gson, file, (target, bytes) -> {
            writes.incrementAndGet();
            com.fatelocked.storage.LocalFileMerge.replace(target, bytes);
        }, clock::get);
        store.record(quest("a", NOW));
        store.mark(Collections.singletonList("a"), COPIED);
        long before = writes.get();
        store.mark(Collections.singletonList("a"), NEW);
        store.mark(Collections.singletonList("a"), COPIED);
        assertEquals(before, writes.get());
    }

    @Test
    public void offersThisRunsNewAndCopiedEventsNewestFirstPetsIncluded() throws IOException
    {
        DetectedEventStore store = store();
        store.record(quest("old", NOW - 2 * DAY));
        store.record(quest("new", NOW - DAY));
        store.record(event("other-run", "run-2", FateEventType.QUEST, NOW));
        store.record(event("pet", "run-1", FateEventType.PET_DROP, NOW - DAY / 2));
        store.record(quest("dismissed", NOW));
        store.mark(Collections.singletonList("dismissed"), DISMISSED);
        store.mark(Collections.singletonList("old"), COPIED);

        // A pet is offered too: the tracker asks which pet it was.
        assertEquals(Arrays.asList("pet", "new", "old"), ids(store.offered("run-1")));
        assertEquals(Collections.singletonList("other-run"), ids(store.offered("run-2")));
        assertEquals(Collections.emptyList(), ids(store.offered(null)));
    }

    @Test
    public void forgetsWhatTheTrackerWouldRefuseAndKeepsTheNewest250() throws IOException
    {
        DetectedEventStore store = store();
        store.record(quest("month-old", NOW - 31 * DAY));
        for (int i = 0; i < 260; i++)
        {
            store.record(quest("q" + i, NOW - 260 + i));
        }
        List<String> kept = ids(store().entries());
        assertEquals(250, kept.size());
        assertEquals("q10", kept.get(0));
        assertEquals("q259", kept.get(249));
        assertFalse(kept.contains("month-old"));

        // An event ages out of what's offered, and of the file at the next write.
        clock.set(NOW + 30 * DAY);
        assertEquals(Collections.emptyList(), ids(store.offered("run-1")));
    }

    @Test
    public void dropsEventsTheTrackerWouldRefuseFromTheFile() throws IOException
    {
        DetectedEventStore store = store();
        store.record(quest("month-old", NOW - 31 * DAY));
        store.record(quest("today", NOW));
        assertEquals(Collections.singletonList("today"), ids(store().entries()));
    }

    @Test
    public void keepsAnotherRuneLitesEventsAndStatesWhenBothWrite() throws IOException
    {
        DetectedEventStore first = store();
        DetectedEventStore second = store();
        first.record(quest("from-first", NOW));
        second.record(quest("from-second", NOW + 1));
        first.mark(Collections.singletonList("from-first"), COPIED);
        // The second still thinks from-first is New; its next write must not undo the copy.
        second.record(quest("later", NOW + 2));

        DetectedEventStore reloaded = store();
        assertEquals(Arrays.asList("from-first", "from-second", "later"), ids(reloaded.entries()));
        assertEquals(COPIED, reloaded.entries().get(0).getStatus());
    }

    @Test
    public void movesADamagedFileAsideAndStartsEmpty() throws IOException
    {
        Files.write(file, "{\"entries\":{".getBytes(StandardCharsets.UTF_8));
        DetectedEventStore store = store();
        assertEquals(Collections.emptyList(), store.entries());
        assertFalse(Files.exists(file));
        assertEquals(1, Files.list(file.getParent())
            .filter(path -> path.getFileName().toString().startsWith(DetectedEventStore.FILE + ".corrupt-")).count());
    }

    @Test
    public void leavesOutEntriesItCantUse() throws IOException
    {
        String json = "{\"version\":1,\"entries\":["
            + "{\"event\":{\"eventId\":\"ok\",\"runId\":\"run-1\",\"eventType\":\"QUEST\",\"confidence\":\"EXACT\",\"occurredAt\":" + NOW + "},\"status\":\"NEW\"},"
            + "{\"event\":{\"eventId\":\"future-type\",\"eventType\":\"SAILING_RACE\",\"confidence\":\"EXACT\",\"occurredAt\":" + NOW + "},\"status\":\"NEW\"},"
            + "{\"event\":{\"eventId\":\" \",\"eventType\":\"QUEST\",\"confidence\":\"EXACT\",\"occurredAt\":" + NOW + "},\"status\":\"NEW\"},"
            + "{\"event\":{\"eventId\":\"no-state\",\"eventType\":\"QUEST\",\"confidence\":\"EXACT\",\"occurredAt\":" + NOW + "}},"
            + "null]}";
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        assertEquals(Collections.singletonList("ok"), ids(store().entries()));
    }

    @Test
    public void givesBackWholeNumbersAsTheyWereRecorded() throws IOException
    {
        store().record(quest("a", NOW));
        assertEquals(12L, store().entries().get(0).getEvent().getEvidence().get("count"));
    }

    @Test
    public void leavesEverythingAsItWasWhenAWriteFails() throws IOException
    {
        DetectedEventStore store = new DetectedEventStore(gson, file, (target, bytes) -> {
            throw new IOException("disk full");
        }, clock::get);
        try
        {
            store.record(quest("a", NOW));
            fail("the write should fail");
        }
        catch (IOException expected)
        {
            // As the disk full says.
        }
        assertEquals(Collections.emptyList(), store.entries());
        assertFalse(Files.exists(file));
    }

    @Test
    public void copiesEventsInTheFormTheTrackerReadsWithNullsKept() throws IOException
    {
        DetectedEventStore store = store();
        List<FateEvent> events = new ArrayList<>();
        events.add(quest("a", NOW));
        events.add(event("pet", "run-1", FateEventType.PET_DROP, NOW));
        JsonObject copy = new JsonParser().parse(store.copyOf(events)).getAsJsonObject();

        assertEquals("fate-locked-runelite-events", copy.get("format").getAsString());
        assertEquals(2, copy.getAsJsonArray("events").size());
        JsonObject first = copy.getAsJsonArray("events").get(0).getAsJsonObject();
        assertEquals("a", first.get("eventId").getAsString());
        assertEquals(1, first.get("protocolVersion").getAsInt());
        assertEquals("QUEST", first.get("eventType").getAsString());
        assertTrue(copy.getAsJsonArray("events").get(1).getAsJsonObject().has("canonicalLabel"));
        assertTrue(copy.getAsJsonArray("events").get(1).getAsJsonObject().get("canonicalLabel").isJsonNull());
    }
}
