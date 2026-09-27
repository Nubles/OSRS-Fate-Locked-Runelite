package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.Test;

public class NoticesTest
{
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    public void aNoticeSaysWhatHappenedAndWhenNeverACountOfRegions()
    {
        assertEquals("Imported rules from the clipboard, exported at 11:58.",
            Notices.imported(NOW.minusSeconds(120), NOW, ZoneOffset.UTC));
        assertEquals("Loaded the newest backup file, exported at Mon 12:00.",
            Notices.loadedBackupFile(NOW.minusSeconds(3 * 24 * 3600), NOW, ZoneOffset.UTC));
        assertEquals("Loaded the newest backup file.", Notices.loadedBackupFile(null, NOW, ZoneOffset.UTC));
        assertEquals("Restored the rules saved at 09:15.",
            Notices.restored(Instant.parse("2026-10-01T09:15:00Z"), NOW, ZoneOffset.UTC));
    }

    @Test
    public void everyNoticeIsASentence() throws Exception
    {
        for (Field field : Notices.class.getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class)
            {
                continue;
            }
            field.setAccessible(true);
            String notice = (String) field.get(null);
            assertTrue(notice, Character.isUpperCase(notice.charAt(0)));
            assertTrue(notice, notice.endsWith("."));
            assertFalse(notice, notice.contains("\u2014"));
            assertFalse(notice, notice.contains("regions"));
        }
    }
}
