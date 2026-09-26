package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CombatAchievementDetector
{
    /**
     * The game's line, read as RuneLite's screenshot plugin reads it:
     * "Congratulations, you've completed a grandmaster combat task:
     * {@literal @}ach_comp@Egniol Diet II&lt;/col&gt; (6 points)." The colour
     * marker, the closing tag and the points are not part of the task's
     * name. Works on the line with or without its tags.
     */
    private static final Pattern TASK = Pattern.compile(
        "(?:an? (?<tier>\\w+) )?combat task: (?:@[^@]+@|<col=[0-9a-fA-F]+>)?"
            + "(?<task>.+?)(?:</col>)?(?: \\(\\d+ points?\\))?[.!]?$");

    public DetectedEvent detect(String message)
    {
        String text = message == null ? "" : message.trim();
        Matcher match = TASK.matcher(text);
        String label = null;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("signature", "combat-task");
        if (match.find())
        {
            label = match.group("task").trim();
            if (label.isEmpty()) label = null;
            if (match.group("tier") != null)
            {
                evidence.put("tier", match.group("tier").toLowerCase());
            }
        }
        return DetectedEvent.builder()
            .type(FateEventType.COMBAT_ACHIEVEMENT)
            .canonicalLabel(label)
            .confidence(label == null ? EventConfidence.UNCERTAIN : EventConfidence.EXACT)
            .detectorId("combat-achievement-chat-v1")
            .detectorVersion(1)
            .evidence(evidence)
            .build();
    }
}
