package com.fatelocked.guardian;

import com.google.gson.Gson;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
