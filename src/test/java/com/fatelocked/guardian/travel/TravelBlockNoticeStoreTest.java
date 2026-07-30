package com.fatelocked.guardian.travel;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TravelBlockNoticeStoreTest
{
    @Test
    public void noticeExpiresAtTheFourSecondBoundaryAndChatDeduplicatesForTenSeconds()
    {
        MutableClock clock = new MutableClock();
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(clock);

        store.show("fairy-rings:canifis", "Travel blocked - Fairy ring to Canifis",
            "Morytania is locked", "Varrock teleport tablet");

        assertTrue(store.current().isPresent());
        assertTrue(store.shouldWriteChat("fairy-rings:canifis"));
        assertFalse(store.shouldWriteChat("fairy-rings:canifis"));

        clock.advance(Duration.ofSeconds(4));
        assertFalse(store.current().isPresent());

        clock.advance(Duration.ofSeconds(5).plusMillis(999));
        assertFalse(store.shouldWriteChat("fairy-rings:canifis"));
        clock.advance(Duration.ofMillis(1));
        assertTrue(store.shouldWriteChat("fairy-rings:canifis"));
    }

    @Test
    public void repeatedShowRefreshesOnlyTheBannerExpiry()
    {
        MutableClock clock = new MutableClock();
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(clock);

        store.show("walk:locked", "Travel blocked - Walk here", "Locked", null);
        assertTrue(store.shouldWriteChat("walk:locked"));
        clock.advance(Duration.ofSeconds(3));

        store.show("walk:locked", "Travel blocked - Walk here", "Locked", null);
        clock.advance(Duration.ofSeconds(1));
        assertTrue(store.current().isPresent());
        assertFalse(store.shouldWriteChat("walk:locked"));

        clock.advance(Duration.ofSeconds(3));
        assertFalse(store.current().isPresent());
        clock.advance(Duration.ofSeconds(3));
        assertTrue(store.shouldWriteChat("walk:locked"));
    }

    @Test
    public void chatFingerprintsAreBoundedToThirtyTwoEntries()
    {
        MutableClock clock = new MutableClock();
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(clock);

        for (int index = 0; index < 33; index++)
        {
            assertTrue(store.shouldWriteChat("fingerprint-" + index));
        }

        assertTrue(store.shouldWriteChat("fingerprint-0"));
        assertFalse(store.shouldWriteChat("fingerprint-32"));
    }

    @Test
    public void noticeIsAnImmutableSnapshotWithAnExactExpiry()
    {
        MutableClock clock = new MutableClock();
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(clock);

        store.show("boat:port", "Travel blocked - Boat", "Locked", null);

        TravelBlockNotice notice = store.current().get();
        assertEquals("boat:port", notice.getFingerprint());
        assertEquals("Travel blocked - Boat", notice.getHeadline());
        assertEquals("Locked", notice.getReason());
        assertEquals(Instant.parse("2026-07-24T10:00:04Z"), notice.getExpiresAt());
    }

    private static final class MutableClock extends Clock
    {
        private Instant now = Instant.parse("2026-07-24T10:00:00Z");

        void advance(Duration duration)
        {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
