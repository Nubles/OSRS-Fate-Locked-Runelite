package com.fatelocked.guardian;

import com.google.gson.Gson;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StrictModeAuditLogTest
{
    @Test
    public void persistsOnlyNewestHundredWithoutSensitiveFields() throws Exception
    {
        Path directory = Files.createTempDirectory("fate-strict-log");
        Path file = directory.resolve("events.json");
        StrictModeAuditLog log = new StrictModeAuditLog(new Gson(), file);
        for (int i = 0; i < 101; i++)
        {
            log.append(new StrictModeAuditEntry(
                i, "TRAVEL", "goblin-" + i, "50,50", "locked",
                "BLOCKED", false, i == 100));
        }

        StrictModeAuditLog reloaded = new StrictModeAuditLog(new Gson(), file);
        assertEquals(100, reloaded.recent(200).size());
        StrictModeAuditEntry latest = reloaded.recent(1).get(0);
        assertEquals("goblin-100", latest.getTarget());
        assertEquals("BLOCKED", latest.getOutcome());
        assertFalse(latest.isPaused());
        assertTrue(latest.isAlternativeAvailable());
        String json = Files.readString(file);
        assertFalse(json.contains("account"));
        assertFalse(json.contains("inventory"));
        assertFalse(json.contains("chat"));
        assertFalse(json.contains("token"));
        assertFalse(json.contains("route"));
    }

    @Test
    public void loadsSafeLegacyEntriesAsBlockedWithoutLosingFields()
        throws Exception
    {
        Path directory = Files.createTempDirectory("fate-strict-legacy");
        Path file = directory.resolve("events.json");
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("audit/strict-mode-events-v1.json"))
        {
            Files.copy(input, file);
        }

        StrictModeAuditLog log = new StrictModeAuditLog(new Gson(), file);
        StrictModeAuditEntry entry = log.recent(1).get(0);

        assertEquals(1721815200000L, entry.getTimestamp());
        assertEquals("OBJECT", entry.getActionKind());
        assertEquals("Ancient gate", entry.getTarget());
        assertEquals("50,50", entry.getChunk());
        assertEquals("is locked", entry.getReason());
        assertEquals("BLOCKED", entry.getOutcome());
        assertFalse(entry.isPaused());
        assertFalse(entry.isAlternativeAvailable());
    }

    @Test
    public void normalizedLegacyBlockRemainsVisibleInRecentPreventedLines()
        throws Exception
    {
        Path directory = Files.createTempDirectory("fate-strict-visible");
        Path file = directory.resolve("events.json");
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("audit/strict-mode-events-v1.json"))
        {
            Files.copy(input, file);
        }
        StrictModeAuditLog log = new StrictModeAuditLog(new Gson(), file);

        assertEquals(
            Collections.singletonList("Ancient gate \u2014 is locked"),
            StrictModeAuditPresenter.recentPrevented(log.recent(5)));
    }

    @Test
    public void doesNotPromoteSensitiveOrPartialRecordsToLegacyBlocks()
        throws Exception
    {
        Path directory = Files.createTempDirectory("fate-strict-malformed");
        Path file = directory.resolve("events.json");
        Files.writeString(file,
            "{\"entries\":["
                + "{\"timestamp\":1,\"actionKind\":\"OBJECT\","
                + "\"target\":\"Gate\",\"chunk\":\"50,50\","
                + "\"reason\":\"locked\",\"account\":\"secret\"},"
                + "{\"timestamp\":2,\"actionKind\":\"TRAVEL\","
                + "\"target\":\"Walk here\",\"chunk\":\"51,51\","
                + "\"reason\":\"locked\",\"paused\":true},"
                + "{\"timestamp\":\"3\",\"actionKind\":42,"
                + "\"target\":99,\"chunk\":\"51,51\","
                + "\"reason\":\"locked\"}]}"
        );

        StrictModeAuditLog log = new StrictModeAuditLog(new Gson(), file);

        assertTrue(log.recent(10).isEmpty());
    }
}
