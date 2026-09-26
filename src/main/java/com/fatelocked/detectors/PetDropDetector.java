package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

/**
 * A new pet, from the game's two lines for one, as RuneLite's screenshot
 * plugin reads them. Which pet is left for the player to say: the follower
 * at that moment may be another pet, and a table from follower ids to pets
 * had the wrong ids for two of its three.
 */
public final class PetDropDetector
{
    /** The pet starts following the player. */
    private static final String FOLLOWED = "you have a funny feeling like you're being followed";
    /** The player already has a follower, so the pet goes in the backpack. */
    private static final String BACKPACK = "you feel something weird sneaking into your backpack";

    private long lastEventAt;

    /**
     * A new pet, or nothing. "... like you would have been followed" is a
     * pet the player already owns, which is not a new one.
     */
    public Optional<DetectedEvent> detect(String message, long now)
    {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        String signature = normalized.contains(FOLLOWED) ? "followed"
            : normalized.contains(BACKPACK) ? "backpack" : null;
        if (signature == null || now - lastEventAt < 5000)
        {
            return Optional.empty();
        }
        lastEventAt = now;
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.PET_DROP)
            .canonicalLabel(null)
            .confidence(EventConfidence.UNCERTAIN)
            .detectorId("pet-drop-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>singletonMap("signature", signature))
            .build());
    }
}
