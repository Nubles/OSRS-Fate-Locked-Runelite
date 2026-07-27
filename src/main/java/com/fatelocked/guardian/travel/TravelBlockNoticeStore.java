package com.fatelocked.guardian.travel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Holds only the current transient notice and short-lived chat suppression keys. */
public final class TravelBlockNoticeStore
{
    private static final Duration NOTICE_LIFETIME = Duration.ofSeconds(4);
    private static final Duration CHAT_SUPPRESSION = Duration.ofSeconds(10);
    private static final int MAX_CHAT_FINGERPRINTS = 32;

    private final Clock clock;
    private final LinkedHashMap<String, Instant> lastChatAt =
        new LinkedHashMap<>(MAX_CHAT_FINGERPRINTS + 1, 0.75f, true);
    private TravelBlockNotice notice;

    public TravelBlockNoticeStore(Clock clock)
    {
        if (clock == null)
        {
            throw new IllegalArgumentException("clock is required");
        }
        this.clock = clock;
    }

    public synchronized void show(
        String fingerprint, String headline, String reason, String alternative)
    {
        Instant now = clock.instant();
        notice = new TravelBlockNotice(fingerprint, headline, reason, alternative,
            now.plus(NOTICE_LIFETIME));
    }

    public synchronized Optional<TravelBlockNotice> current()
    {
        if (notice == null)
        {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(notice.getExpiresAt()))
        {
            notice = null;
            return Optional.empty();
        }
        return Optional.of(notice);
    }

    public synchronized boolean shouldWriteChat(String fingerprint)
    {
        Instant now = clock.instant();
        Instant lastWritten = lastChatAt.get(fingerprint);
        if (lastWritten != null && now.isBefore(lastWritten.plus(CHAT_SUPPRESSION)))
        {
            return false;
        }

        lastChatAt.put(fingerprint, now);
        while (lastChatAt.size() > MAX_CHAT_FINGERPRINTS)
        {
            String oldest = lastChatAt.keySet().iterator().next();
            lastChatAt.remove(oldest);
        }
        return true;
    }
}
