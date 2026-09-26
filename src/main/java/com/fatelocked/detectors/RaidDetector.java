package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Raid completions, from the reward chest's loot. Boss kills are
 * BossKillDetectorV2's alone: a combat level says nothing about whether a
 * monster is a boss (a Mithril dragon is level 304, and Obor 106).
 */
public class RaidDetector
{
    private static final Set<String> RAIDS = new HashSet<>(Arrays.asList(
        "chambers of xeric", "theatre of blood", "tombs of amascut"));

    public Optional<DetectedEvent> detect(String lootType, String name, int combatLevel)
    {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        if ("EVENT".equals(lootType) && RAIDS.contains(normalized))
        {
            return Optional.of(DetectedEvent.builder()
                .type(FateEventType.RAID_COMPLETION)
                .canonicalLabel(name)
                .confidence(EventConfidence.EXACT)
                .detectorId("raid-loot-v1")
                .detectorVersion(1)
                .evidence(java.util.Collections.<String, Object>singletonMap(
                    "combatLevel", combatLevel))
                .build());
        }
        return Optional.empty();
    }
}
