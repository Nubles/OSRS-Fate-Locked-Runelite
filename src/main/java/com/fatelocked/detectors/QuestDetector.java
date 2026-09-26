package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestDetector
{
    /**
     * Quests whose names really end in "Quest", which a title's trailing
     * "Quest" must not be cut from. RuneLite's screenshot plugin keeps the
     * same list.
     */
    private static final List<String> NAMES_ENDING_IN_QUEST = List.of(
        "Doric's", "Heroes'", "Legends'", "Observatory", "Olaf's", "Waterfall");
    /**
     * Recipe for Disaster's parts, which the tracker names "RFD: ...". The
     * parts after the first say who was freed or saved; these titles are
     * matched by that name rather than word for word, as they are not in
     * RuneLite's fixtures.
     */
    private static final Map<String, String> RFD_PARTS = rfdParts();

    /** "'One Small Favour' completed!" */
    private static final Pattern QUOTED = Pattern.compile("'(.+)' completed[!.]?");
    /** "Sins of the Father forgiven!" */
    private static final Pattern FORGIVEN = Pattern.compile("(.+) forgiven[!.]?");
    /** "You have completely completed Rag and Bone Man!": the second part. */
    private static final Pattern COMPLETELY =
        Pattern.compile("(?i:you have completely completed) (?:the )?(.+?)[!.]?");
    /**
     * "You have completed The Corsair Curse!", "... completed the Waterfall
     * Quest!" and "You have... kind of... completed the Hazeel Cult Quest!".
     * A lower-case "the" is the title's; a capital one is the quest's.
     */
    private static final Pattern COMPLETED = Pattern.compile(
        "(?i:you have)[.\\s]*(?:(?i:kind of)[.\\s]*)?(?i:completed) (?:the )?(.+?)[!.]?");

    public DetectedEvent detect(String questName)
    {
        String label = questName == null || questName.trim().isEmpty()
            ? null : questName.trim();
        return DetectedEvent.builder()
            .type(FateEventType.QUEST)
            .canonicalLabel(label)
            .confidence(label == null ? EventConfidence.UNCERTAIN : EventConfidence.EXACT)
            .detectorId("quest-widget-v1")
            .detectorVersion(1)
            .evidence(Collections.<String, Object>singletonMap("rewardWidget", true))
            .build();
    }

    /**
     * The quest a reward scroll's title (its tags removed) names, as the
     * tracker names it, or null for a line that names no quest.
     */
    public static String questName(String title)
    {
        String text = title == null ? "" : title.trim();
        if (text.isEmpty()) return null;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("defeated the culinaromancer")) return "RFD: Finale";
        if (lower.contains("another cook's quest")) return "RFD: The Cook";
        if (lower.contains("freed") || lower.contains("saved"))
        {
            for (Map.Entry<String, String> part : RFD_PARTS.entrySet())
            {
                if (lower.contains(part.getKey())) return part.getValue();
            }
        }
        Matcher match = QUOTED.matcher(text);
        if (match.matches()) return match.group(1).trim();
        match = FORGIVEN.matcher(text);
        if (match.matches()) return match.group(1).trim();
        match = COMPLETELY.matcher(text);
        if (match.matches()) return withoutQuestSuffix(match.group(1)) + " II";
        match = COMPLETED.matcher(text);
        if (match.matches()) return withoutQuestSuffix(match.group(1));
        return null;
    }

    /** The name without a trailing "Quest", unless the quest's name ends in it. */
    private static String withoutQuestSuffix(String name)
    {
        String trimmed = name.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).endsWith(" quest")) return trimmed;
        String stripped = trimmed.substring(0, trimmed.length() - " quest".length()).trim();
        return NAMES_ENDING_IN_QUEST.contains(stripped) ? stripped + " Quest" : stripped;
    }

    private static Map<String, String> rfdParts()
    {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("mountain dwarf", "RFD: Dwarf");
        parts.put("goblin generals", "RFD: Goblins");
        parts.put("pirate pete", "RFD: Pirate Pete");
        parts.put("lumbridge guide", "RFD: Lumbridge Guide");
        parts.put("evil dave", "RFD: Evil Dave");
        parts.put("skrach", "RFD: Skrach Uglogwee");
        parts.put("sir amik varze", "RFD: Sir Amik Varze");
        parts.put("awowogei", "RFD: King Awowogei");
        return Collections.unmodifiableMap(parts);
    }
}
