package com.fatelocked.events;

import com.fatelocked.FateLockedBundle;
import com.fatelocked.detectors.DetectedEvent;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class FateEventFactoryTest
{
    private static final FateLockedBundle BUNDLE = FateLockedBundle.loadFromJson(new Gson(),
        "{\"version\":3,\"runId\":\"run-1\",\"runRevision\":12,"
            + "\"rulesVersion\":\"1\",\"contentVersion\":4,"
            + "\"chunks\":{\"Misthalin\":[{\"cx\":50,\"cy\":50}]}}");

    private static FateEvent quest(FateEventFactory factory, String name, String account, String count)
    {
        return factory.create(FateEventType.QUEST, name, EventConfidence.EXACT,
            Collections.<String, Object>singletonMap("quest", name), BUNDLE, account, "quest-state-v1", 1, count);
    }

    @Test
    public void createsOneOccurrenceWithTheBundlesIdentity()
    {
        FateEventFactory factory = new FateEventFactory();
        FateEvent first = quest(factory, "Dragon Slayer I", "Nubles", DetectedEvent.ONCE);
        FateEvent second = quest(factory, "Demon Slayer", "Nubles", DetectedEvent.ONCE);

        assertTrue(first.getEventId().matches("^fl1-[0-9a-f]{32}$"));
        assertNotEquals(first.getEventId(), second.getEventId());
        assertEquals("run-1", first.getRunId());
        assertEquals(12, first.getRunRevision());
        assertEquals("1", first.getRulesVersion());
        assertEquals(4, first.getContentVersion());
        assertEquals(1, first.getSessionSequence());
        assertEquals(2, second.getSessionSequence());
    }

    @Test
    public void givesTheSameThingTheSameIdEvenFromAnotherRuneLite()
    {
        // A quest seen twice, or by two clients, is one event.
        assertEquals(quest(new FateEventFactory(), "Dragon Slayer I", "Nubles", DetectedEvent.ONCE).getEventId(),
            quest(new FateEventFactory(), " dragon  slayer i ", "NUBLES", DetectedEvent.ONCE).getEventId());
        // A kill count tells repeats of one boss apart.
        FateEventFactory factory = new FateEventFactory();
        assertNotEquals(quest(factory, "Vorkath", "Nubles", "12").getEventId(),
            quest(factory, "Vorkath", "Nubles", "13").getEventId());
    }

    @Test
    public void givesEveryOccurrenceItsOwnIdWithoutACount()
    {
        FateEventFactory factory = new FateEventFactory();
        assertNotEquals(quest(factory, "Pet drop", "Nubles", null).getEventId(),
            quest(factory, "Pet drop", "Nubles", null).getEventId());
    }
}
