package com.fatelocked.sidebar;

import com.fatelocked.rules.ChunkPermissionRow;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.PermissionStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * What the tracker's rows say about the things in one chunk, as the game names them: a bank
 * booth, a shop keeper, a yew tree, a monster. The (Locked) tag and the outlines read it, so
 * they say what the Here card says. A row the tracker locks is {@link Look#LOCKED}; a skilling
 * row whose skill tier doesn't reach its level is {@link Look#TIER}, as the tracker counts it
 * (tier N opens levels 1 to N × 10), however high the player's level; an allowed row is
 * {@link Look#OPEN}. Anything else, such as a row not ready for want of a route or a level,
 * or one the tracker leaves undecided, gives no answer, so nothing is tagged or outlined.
 */
public final class LockedThings
{
    /** How a thing looks under the rules. */
    public enum Look
    {
        OPEN,
        TIER,
        LOCKED
    }

    /** Which outline setting a thing falls under. */
    public enum Kind
    {
        BANKS_AND_SHOPS,
        SKILLING,
        MONSTERS
    }

    /** One row, as the game would name it, and what the rules make of it. */
    @Value
    public static class Thing
    {
        PointTarget target;
        /** Null when the row is neither open nor locked by the rules. */
        Look look;
        Kind kind;
        /** A few words for beside the thing: "Woodcutting tier 6", "Unlock Dwarven Mine"; null when open. */
        String label;
        /** The whole reason, for the chat line; null when open. */
        String why;
    }

    public static final LockedThings NONE = new LockedThings(false, Collections.emptyList());

    private final boolean placeLocked;
    private final List<Thing> things;

    private LockedThings(boolean placeLocked, List<Thing> things)
    {
        this.placeLocked = placeLocked;
        this.things = things;
    }

    /** The things a chunk's rows name; none without rows. */
    public static LockedThings of(ChunkPermissionSnapshot snapshot)
    {
        if (snapshot == null || snapshot.getCategories() == null)
        {
            return NONE;
        }
        boolean placeLocked = snapshot.getEntry() == PermissionStatus.LOCKED;
        List<Thing> things = new ArrayList<>();
        for (Map.Entry<String, List<ChunkPermissionRow>> category : snapshot.getCategories().entrySet())
        {
            Kind kind = kind(category.getKey());
            if (kind == null || category.getValue() == null)
            {
                continue;
            }
            for (ChunkPermissionRow row : category.getValue())
            {
                PointTarget target = PointTarget.of(category.getKey(), HerePresenter.rowName(row.getName()));
                if (target != null)
                {
                    things.add(thing(target, kind, row, placeLocked));
                }
            }
        }
        return new LockedThings(placeLocked, Collections.unmodifiableList(things));
    }

    /** Whether the whole chunk is locked, so its own borders and tags already say so. */
    public boolean placeLocked()
    {
        return placeLocked;
    }

    /**
     * The row for a thing in the game, by the name and options it shows, or null when no row
     * names it or the rows that do don't agree it is locked. When several rows name it, the
     * most open one wins, so a thing is never shown as more locked than the rules make it; between
     * two alike, the one that names its options, as "Fishing spot (lure, bait)" does.
     */
    public Thing find(String name, String[] options)
    {
        Thing best = null;
        for (Thing thing : things)
        {
            if (!thing.getTarget().matches(name, options))
            {
                continue;
            }
            if (thing.getLook() == null)
            {
                return null;
            }
            if (best == null || thing.getLook().ordinal() < best.getLook().ordinal()
                || thing.getLook() == best.getLook() && best.getTarget().getActions().isEmpty()
                    && !thing.getTarget().getActions().isEmpty())
            {
                best = thing;
            }
        }
        return best;
    }

    static Kind kind(String category)
    {
        if (category == null)
        {
            return null;
        }
        switch (category)
        {
            case "BANKS":
            case "SHOPS":
                return Kind.BANKS_AND_SHOPS;
            case "SKILLING":
            case "FARMING":
                return Kind.SKILLING;
            case "COMBAT":
            case "ACTIVITIES":
                return Kind.MONSTERS;
            default:
                return null;
        }
    }

    private static Thing thing(PointTarget target, Kind kind, ChunkPermissionRow row, boolean placeLocked)
    {
        List<String> clauses = RowChecks.clauses(row.getDetail());
        if (placeLocked || row.getStatus() == PermissionStatus.LOCKED)
        {
            return new Thing(target, Look.LOCKED, kind, lockedLabel(clauses), lockedWhy(clauses));
        }
        if (row.getStatus() == PermissionStatus.ALLOWED)
        {
            return new Thing(target, Look.OPEN, kind, null, null);
        }
        SkillLine line = clauses.isEmpty() ? null : SkillLine.parse(clauses.get(0));
        if (row.getStatus() == PermissionStatus.NOT_READY && line != null && line.getCap() < line.getNeeded())
        {
            int tier = tierFor(line.getNeeded());
            String why = line.getSkill() + " tier " + tier + " (level " + line.getNeeded() + ") needed; "
                + (line.getCap() <= 0
                    ? line.getSkill() + " isn't unlocked yet."
                    : "yours opens levels 1-" + line.getCap() + ".");
            return new Thing(target, Look.TIER, kind, line.getSkill() + " tier " + tier, why);
        }
        return new Thing(target, null, kind, null, null);
    }

    /** The lowest tier that opens a level: tier N opens levels 1 to N × 10, and tier 10 up to 99. */
    static int tierFor(int level)
    {
        return level > 90 ? 10 : Math.max(1, (level + 9) / 10);
    }

    /** What the tracker says opens it, without the skill lines the Here card shows beside it. */
    private static String lockedLabel(List<String> clauses)
    {
        for (String clause : clauses)
        {
            if (SkillLine.parse(clause) == null)
            {
                return clause;
            }
        }
        return "Locked";
    }

    private static String lockedWhy(List<String> clauses)
    {
        return clauses.isEmpty() ? "Locked" : String.join("; ", clauses);
    }
}
