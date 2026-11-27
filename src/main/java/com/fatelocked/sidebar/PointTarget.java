package com.fatelocked.sidebar;

import com.fatelocked.rules.ChunkPermissionRow;
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
 * chests, bankers and deposit boxes, and a deposit service by its NPC ("Peer the Seer deposit
 * service": Peer the Seer). A shop goes by whoever runs it: the keepers the tracker's table
 * ({@link ShopKeepers}) names for it ("Fine Fashions": Rometti), and else what its name gives
 * ("Bob's Brilliant Axes": Bob, "Agelus' Farm Shop": Agelus), a general store's shop keeper or
 * an inn's bartender. A minigame's row names a place, not a thing, so it has no target.
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
    /** An owner whose name ends in s: "Agelus' Farm Shop". */
    private static final Pattern OWNER_S = Pattern.compile("^(.+?s)'\\s");
    /** What the game calls the deposit-only facilities at the tracker's bank places. */
    private static final Set<String> DEPOSIT = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
        "bank deposit box", "bank deposit chest", "deposit pool")));
    /** A bank place that is one NPC: "Peer the Seer deposit service". */
    private static final String DEPOSIT_SERVICE = " deposit service";
    /** A bank place whose deposit box is a chest the game calls just that: "Zul-Andra deposit chest". */
    private static final String DEPOSIT_CHEST = " deposit chest";
    private static final Pattern INN = Pattern.compile("\\b(inn|arms|bar|pub|tavern)\\b");

    /** The category it's in, as the tracker keys it: SKILLING, SHOPS and so on. */
    String category;
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

    /**
     * The target for a row of the tracker's rules, or null for one the card can't point at: a
     * category with no one thing to point at, or a minigame's row, which names a place.
     */
    public static PointTarget ofRow(String category, ChunkPermissionRow row)
    {
        if (row == null || row.getName() == null || row.isMinigame())
        {
            return null;
        }
        return of(category, HerePresenter.rowName(row.getName()));
    }

    /** The target for a row of a category, or null for a category the card can't point in. */
    public static PointTarget of(String category, String row)
    {
        return of(category, row, ShopKeepers.shared());
    }

    /** The same, with this table of who runs each shop. */
    static PointTarget of(String category, String row, ShopKeepers keepers)
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
                names.addAll(DEPOSIT);
                if (name.endsWith(DEPOSIT_SERVICE) && name.length() > DEPOSIT_SERVICE.length())
                {
                    names.add(name.substring(0, name.length() - DEPOSIT_SERVICE.length()).trim());
                }
                if (name.endsWith(DEPOSIT_CHEST))
                {
                    names.add("chest");
                }
                break;
            case "SHOPS":
                names.add(name);
                names.addAll(keepers.of(label));
                Matcher owner = OWNER.matcher(base);
                Matcher ownerS = OWNER_S.matcher(base);
                if (owner.find())
                {
                    names.add(key(owner.group(1)));
                }
                else if (ownerS.find())
                {
                    names.add(key(ownerS.group(1)));
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
        return new PointTarget(category, label, Collections.unmodifiableSet(names),
            Collections.unmodifiableSet(actions));
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
