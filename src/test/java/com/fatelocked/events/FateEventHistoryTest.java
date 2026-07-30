package com.fatelocked.events;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FateEventHistoryTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    private Path historyPath;
    private Path legacyPath;

    @Before
    public void setUp()
    {
        Path root = folder.getRoot().toPath();
        historyPath = root.resolve("event-history.json");
        legacyPath = root.resolve("event-outbox.json");
    }

    @Test
    public void acceptedEventSurvivesRestart() throws Exception
    {
        FateEventHistory history =
            new FateEventHistory(gson, historyPath, legacyPath);

        assertTrue(history.record(event("evt-1")));

        assertEquals(Collections.singletonList("evt-1"),
            eventIds(new FateEventHistory(
                gson, historyPath, legacyPath).events()));
    }

    @Test
    public void duplicateNullAndBlankEventIdsAreRejected() throws Exception
    {
        FateEventHistory history =
            new FateEventHistory(gson, historyPath, legacyPath);

        assertFalse(history.record(null));
        assertFalse(history.record(event(null)));
        assertFalse(history.record(event("   ")));
        assertTrue(history.record(event("evt-1")));
        assertFalse(history.record(event("evt-1")));
        assertEquals(Collections.singletonList("evt-1"),
            eventIds(history.events()));
    }

    @Test
    public void acceptingThe251stEventDiscardsOnlyTheOldest() throws Exception
    {
        FateEventHistory history =
            new FateEventHistory(gson, historyPath, legacyPath);

        for (int i = 1; i <= 251; i++)
        {
            assertTrue(history.record(event("evt-" + i)));
        }

        assertEquals(250, history.events().size());
        assertEquals("evt-2", history.events().get(0).getEventId());
        assertEquals("evt-251",
            history.events().get(249).getEventId());
    }

    @Test
    public void corruptHistoryIsMovedAsideAndStartsEmpty() throws Exception
    {
        Files.write(historyPath,
            "{not-json".getBytes(StandardCharsets.UTF_8));

        FateEventHistory history =
            new FateEventHistory(gson, historyPath, legacyPath);

        assertTrue(history.events().isEmpty());
        assertFalse(Files.exists(historyPath));
        try (java.util.stream.Stream<Path> files =
                 Files.list(historyPath.getParent()))
        {
            assertEquals(1L, files
                .filter(path -> path.getFileName().toString()
                    .startsWith("event-history.json.corrupt-"))
                .count());
        }
    }

    @Test
    public void migrationKeepsNewest250AndLeavesLegacyBytesUntouched()
        throws Exception
    {
        List<FateEvent> legacyEvents = new ArrayList<>();
        for (int i = 1; i <= 260; i++)
        {
            legacyEvents.add(event("evt-" + i));
        }
        Map<String, Object> legacyState = new LinkedHashMap<>();
        legacyState.put("pending", legacyEvents);
        byte[] legacyBytes = gson.toJson(legacyState)
            .getBytes(StandardCharsets.UTF_8);
        Files.write(legacyPath, legacyBytes);

        FateEventHistory migrated =
            new FateEventHistory(gson, historyPath, legacyPath);

        assertEquals(250, migrated.events().size());
        assertEquals("evt-11", migrated.events().get(0).getEventId());
        assertEquals("evt-260",
            migrated.events().get(249).getEventId());
        assertArrayEquals(legacyBytes, Files.readAllBytes(legacyPath));
        assertTrue(Files.exists(historyPath));
    }

    @Test
    public void failedWriteLeavesMemoryAndDurableHistoryUnchanged()
        throws Exception
    {
        FateEventHistory initial =
            new FateEventHistory(gson, historyPath, legacyPath);
        assertTrue(initial.record(event("evt-1")));
        FateEventHistory.Persistence failing =
            (target, bytes) -> { throw new IOException("disk full"); };
        FateEventHistory reloaded =
            new FateEventHistory(
                gson, historyPath, legacyPath, failing);

        try
        {
            reloaded.record(event("evt-2"));
            fail("expected write failure");
        }
        catch (IOException expected)
        {
            assertEquals("disk full", expected.getMessage());
        }

        assertEquals(Collections.singletonList("evt-1"),
            eventIds(reloaded.events()));
        assertEquals(Collections.singletonList("evt-1"),
            eventIds(new FateEventHistory(
                gson, historyPath, legacyPath).events()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void eventSnapshotsCannotMutateTheHistory() throws Exception
    {
        FateEventHistory history =
            new FateEventHistory(gson, historyPath, legacyPath);
        history.record(event("evt-1"));

        history.events().add(event("evt-2"));
    }

    private FateEvent event(String eventId)
    {
        return FateEvent.builder()
            .protocolVersion(1)
            .eventId(eventId)
            .runId("run-1")
            .account("Nubles")
            .runRevision(1)
            .eventType(FateEventType.QUEST)
            .canonicalLabel("Dragon Slayer")
            .occurredAt(1_000L)
            .sessionSequence(1)
            .bundleVersion(4)
            .rulesVersion("1")
            .contentVersion(1)
            .detectorId("quest-widget-v1")
            .detectorVersion(1)
            .confidence(EventConfidence.EXACT)
            .evidence(Collections.<String, Object>emptyMap())
            .build();
    }

    private static List<String> eventIds(List<FateEvent> events)
    {
        return events.stream()
            .map(FateEvent::getEventId)
            .collect(Collectors.toList());
    }
}
