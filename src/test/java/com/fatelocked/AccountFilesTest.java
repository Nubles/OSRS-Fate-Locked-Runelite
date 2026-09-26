package com.fatelocked;

import com.fatelocked.detectors.SlayerTaskDetector;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventHistory;
import com.fatelocked.events.FateEventType;
import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeAuditLog;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AccountFilesTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    private Path data;

    @Before
    public void setUp()
    {
        data = folder.getRoot().toPath();
    }

    @Test
    public void eachAccountHasItsOwnFiles() throws Exception
    {
        AccountFiles ironman = AccountFiles.open(gson, data, 1L, "Nubles", true);
        AccountFiles main = AccountFiles.open(gson, data, 2L, "Zezima", false);

        ironman.history.record(event("ironman-1", "Nubles"));

        assertEquals(1, ironman.history.events().size());
        assertEquals(0, main.history.events().size());
        assertTrue(Files.exists(data.resolve("accounts/1/event-history.json")));
        assertFalse(Files.exists(data.resolve(AccountFiles.HISTORY)));
    }

    @Test
    public void aNewFolderStartsFromTheSharedHistorysEventsForThatCharacterOnly() throws Exception
    {
        Path shared = data.resolve(AccountFiles.HISTORY);
        FateEventHistory before = new FateEventHistory(gson, shared, null);
        before.record(event("nubles-1", "Nubles"));
        before.record(event("zezima-1", "Zezima"));
        byte[] sharedBytes = Files.readAllBytes(shared);

        AccountFiles ironman = AccountFiles.open(gson, data, 1L, " nubles ", true);

        assertEquals(List.of("nubles-1"), ids(ironman.history.events()));
        // The shared file is only read.
        assertArrayEquals(sharedBytes, Files.readAllBytes(shared));
        // A folder in use doesn't take them again.
        assertEquals(List.of("nubles-1"),
            ids(AccountFiles.open(gson, data, 1L, "Nubles", true).history.events()));
    }

    @Test
    public void theSharedAuditLogAndSlayerTaskComeOnlyToTheBoundCharacter() throws Exception
    {
        new StrictModeAuditLog(gson, data.resolve(AccountFiles.AUDIT_LOG)).append(
            new StrictModeAuditEntry(1, "TRAVEL", "goblin", "50,50", "locked"));
        new SlayerTaskDetector(gson, data.resolve(AccountFiles.SLAYER))
            .assignment("Kurask", null, 120, false);

        AccountFiles bound = AccountFiles.open(gson, data, 1L, "Nubles", true);
        AccountFiles other = AccountFiles.open(gson, data, 2L, "Zezima", false);

        assertEquals(1, bound.auditLog.recent(10).size());
        assertEquals(0, other.auditLog.recent(10).size());
        assertTrue(bound.slayer.completion("task complete").isPresent());
        assertFalse(other.slayer.completion("task complete").isPresent());
    }

    @Test
    public void damagedAccountFilesAreKeptAsideAndStillOpen() throws Exception
    {
        Path own = Files.createDirectories(AccountFiles.folder(data, 1L));
        Files.write(own.resolve(AccountFiles.HISTORY), "[".getBytes(StandardCharsets.UTF_8));
        Files.write(own.resolve(AccountFiles.SLAYER), "{\"name\":".getBytes(StandardCharsets.UTF_8));

        AccountFiles files = AccountFiles.open(gson, data, 1L, "Nubles", true);

        assertNotNull(files.history);
        assertNotNull(files.auditLog);
        assertNotNull(files.slayer);
        try (Stream<Path> kept = Files.list(own))
        {
            assertEquals(2, kept.filter(path -> path.getFileName().toString().contains(".corrupt-"))
                .count());
        }
    }

    private static FateEvent event(String eventId, String account)
    {
        return FateEvent.builder()
            .protocolVersion(1)
            .eventId(eventId)
            .runId("run-1")
            .account(account)
            .runRevision(1)
            .eventType(FateEventType.QUEST)
            .canonicalLabel("Dragon Slayer")
            .confidence(EventConfidence.EXACT)
            .occurredAt(1_000L)
            .sessionSequence(1)
            .bundleVersion(4)
            .rulesVersion("1")
            .contentVersion(1)
            .build();
    }

    private static List<String> ids(List<FateEvent> events)
    {
        return events.stream().map(FateEvent::getEventId).collect(Collectors.toList());
    }
}
