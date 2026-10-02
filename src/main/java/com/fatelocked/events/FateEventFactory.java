package com.fatelocked.events;

import com.fatelocked.FateLockedBundle;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class FateEventFactory
{
    private final AtomicLong sessionSequence = new AtomicLong();

    /**
     * @param count what tells repeats of this name apart (EventIds); null gives this
     *     occurrence its own id, from the time it was seen
     */
    public FateEvent create(
        FateEventType type,
        String canonicalLabel,
        EventConfidence confidence,
        Map<String, Object> evidence,
        FateLockedBundle bundle,
        String account,
        String detectorId,
        int detectorVersion,
        String count)
    {
        long occurredAt = System.currentTimeMillis();
        long sequence = sessionSequence.incrementAndGet();
        String runId = bundle == null ? null : bundle.getRunId();
        return FateEvent.builder()
            .protocolVersion(1)
            .eventId(EventIds.of(account, runId, type, canonicalLabel,
                count != null ? count : "seen-" + occurredAt + "-" + sequence))
            .runId(runId)
            .account(account)
            .runRevision(bundle == null ? 0 : bundle.getRunRevision())
            .eventType(type)
            .canonicalLabel(canonicalLabel)
            .occurredAt(occurredAt)
            .sessionSequence(sequence)
            .bundleVersion(bundle == null ? 0 : bundle.getVersion())
            .rulesVersion(bundle == null ? "1" : bundle.getRulesVersion())
            .contentVersion(bundle == null ? 0 : bundle.getContentVersion())
            .detectorId(detectorId)
            .detectorVersion(detectorVersion)
            .confidence(confidence)
            .evidence(evidence == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(evidence))
            .build();
    }
}
