package com.fatelocked.sidebar;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * What a row of the Here card points at in the game, when the player clicks it (the owner's
 * review, 28 Sept): the names the thing goes by there, an object's or an NPC's. The tracker
 * names skilling spots, monsters and patches as the game does, but for the fly-fishing spots,
 * which the game calls rod fishing spots. A bank goes by its booths,
 * chests and bankers; a shop by whoever runs it, which its name often gives
 * ("Bob's Brilliant Axes": Bob) or which a general store's shop keeper is.
 */
@Value
public class PointTarget
{
    /** Categories whose rows the card can point at; quests and travel have no one thing to point at. */
    static final Set<String> CATEGORIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "SKILLING", "COMBAT", "BANKS", "SHOPS", "FARMING", "ACTIVITIES")));

    /** What a fishing spot offers, as its options say. */
    private static final Set<String> FISHING = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "net", "small net", "big net", "bait", "lure", "cage", "harpoon", "use-rod", "fish")));
    private static final String FISHING_SPOT = "fishing spot";
    /** What the game calls the spots fished with a rod: lure and bait. */
    private static final String ROD_FISHING_SPOT = "rod fishing spot";
    private static final Pattern VARIANT = Pattern.compile("^(.*\\S)\\s*\\(([^()]*)\\)$");
    private static final Pattern OWNER = Pattern.compile("^(.+?)'s\\s");
    private static final Pattern INN = Pattern.compile("\\b(inn|arms|bar|pub|tavern)\\b");

    /** The row as the card shows it. */
    String label;
    /** The names it goes by in the game, lower case. */
    Set<String> names;
    /** Options it must offer, lower case, such as a fishing spot's lure and bait; empty for any. */
    Set<String> actions;

    /** Whether the card can point at a category's rows. */
    public static boolean pointable(String category)
    {
        return CATEGORIES.contains(category);
    }

    /** The target for a row of a category, or null for a category the card can't point in. */
    public static PointTarget of(String category, String row)
    {
        if (!pointable(category) || row == null || row.trim().isEmpty())
        {
            return null;
        }
        String label = row.trim();
        String base = label;
        String variant = null;
        Matcher split = VARIANT.matcher(label);
        if (split.matches())
        {
            base = split.group(1);
            variant = split.group(2);
        }
        String name = key(base);
        Set<String> names = new LinkedHashSet<>();
        Set<String> actions = new LinkedHashSet<>();
        switch (category)
        {
            case "BANKS":
                names.addAll(Arrays.asList("bank booth", "bank chest", "banker"));
                break;
            case "SHOPS":
                names.add(name);
                Matcher owner = OWNER.matcher(base);
                if (owner.find())
                {
                    names.add(key(owner.group(1)));
                }
                if (name.contains("general store"))
                {
                    names.addAll(Arrays.asList("shop keeper", "shop assistant"));
                }
                if (INN.matcher(name).find())
                {
                    names.addAll(Arrays.asList("bartender", "barmaid"));
                }
                break;
            case "FARMING":
                names.add(name);
                if (name.endsWith(" patch"))
                {
                    names.add(name.substring(0, name.length() - " patch".length()));
                }
                break;
            default:
                names.add(name);
                if (name.equals(FISHING_SPOT))
                {
                    names.add(ROD_FISHING_SPOT);
                }
                if (variant != null && name.equals(FISHING_SPOT))
                {
                    for (String option : variant.split(","))
                    {
                        actions.add(key(option));
                    }
                    if (!FISHING.containsAll(actions))
                    {
                        // A bracket that names a place or a kind, not what the spot offers.
                        actions.clear();
                    }
                }
        }
        return new PointTarget(label, Collections.unmodifiableSet(names), Collections.unmodifiableSet(actions));
    }

    /** Whether a thing in the game, by its name and options, is this target. */
    public boolean matches(String name, String[] options)
    {
        if (name == null || !names.contains(key(name)))
        {
            return false;
        }
        if (actions.isEmpty())
        {
            return true;
        }
        Set<String> offered = new HashSet<>();
        if (options != null)
        {
            for (String option : options)
            {
                if (option != null)
                {
                    offered.add(key(option));
                }
            }
        }
        return offered.containsAll(actions);
    }

    private static String key(String text)
    {
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
