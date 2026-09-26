package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clue scroll completions. When "You have completed 12 hard Treasure
 * Trails." appears, RuneLite's loot tracker posts the reward as loot from
 * an event named "Clue Scroll (Hard)". An item called "Casket" in other
 * loot, such as Tempoross's reward pool, is not a completion, and the
 * reward caskets' item names never appear as loot.
 */
public class ClueCompletionDetector
{
    private static final Pattern CLUE_EVENT = Pattern.compile(
        "Clue Scroll \\((Beginner|Easy|Medium|Hard|Elite|Master)\\)", Pattern.CASE_INSENSITIVE);

    public Optional<DetectedEvent> detect(String lootType, String name)
    {
        if (!"EVENT".equals(lootType) || name == null)
        {
            return Optional.empty();
        }
        Matcher clue = CLUE_EVENT.matcher(name.trim());
        if (!clue.matches())
        {
            return Optional.empty();
        }
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.CLUE_CASKET)
            .canonicalLabel(name.trim())
            .confidence(EventConfidence.EXACT)
            .detectorId("clue-completion-loot-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>singletonMap(
                "tier", clue.group(1).toLowerCase(Locale.ROOT)))
            .build());
    }
}
