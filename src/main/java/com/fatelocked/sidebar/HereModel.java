package com.fatelocked.sidebar;

import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Terms;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * The Here card: the place the player stands in, its status and why, counts of what
 * can be done there, and the content by category. With nothing to show (logged out),
 * only {@link #getMessage()} is set.
 */
@Value
public class HereModel
{
    /** Rows shown per category before "+N more" (U16). */
    public static final int ROWS_SHOWN = 5;

    String place;
    /** The status in a word, or null. */
    String word;
    Palette.Tone tone;
    /** The region and coordinates, or null. */
    String where;
    /** Why the place is locked or not ready, or null. */
    String reason;
    List<Count> counts;
    List<Group> groups;
    /** What to say instead of a place, or null. */
    String message;

    public static HereModel message(String message)
    {
        return new HereModel(null, null, Palette.Tone.NEUTRAL, null, null, Collections.emptyList(),
            Collections.emptyList(), message);
    }

    /** A count of rows in one status. */
    @Value
    public static class Count
    {
        int value;
        String label;
        Palette.Tone tone;
    }

    /**
     * One category of content, such as Quests or Shops, which the card opens and closes.
     * Skilling splits further, by skill.
     */
    @Value
    @AllArgsConstructor
    public static class Group
    {
        /** The tracker's category id, such as QUESTS; also what the card remembers it open by. */
        String category;
        String title;
        /** Every row, split or not. */
        List<Row> rows;
        /** The category split further, as Skilling is by skill; empty when it isn't. */
        List<Subgroup> subgroups;

        public Group(String category, String title, List<Row> rows)
        {
            this(category, title, rows, Collections.emptyList());
        }

        /** The rows to show: all of them, or the first few until expanded. */
        public List<Row> shown(boolean expanded)
        {
            return expanded || rows.size() <= ROWS_SHOWN ? rows : rows.subList(0, ROWS_SHOWN);
        }

        /** How many rows "+N more" stands for. */
        public int hidden()
        {
            return Math.max(0, rows.size() - ROWS_SHOWN);
        }

        /** Its rows by status, for the line under its title while it's closed: "3 can do", "2 locked". */
        public List<Count> tally()
        {
            return HereModel.tally(rows);
        }
    }

    /** Part of a category, such as one skill's rows under Skilling, which opens and closes too. */
    @Value
    public static class Subgroup
    {
        /** What the card remembers it open by: the category, then its title ("SKILLING/Fishing"). */
        String key;
        String title;
        /** The skill it stands for, for its icon, or null. */
        String skill;
        /** Beside the title: the player's level and cap, or null. */
        String note;
        List<Row> rows;

        public List<Row> shown(boolean expanded)
        {
            return expanded || rows.size() <= ROWS_SHOWN ? rows : rows.subList(0, ROWS_SHOWN);
        }

        public int hidden()
        {
            return Math.max(0, rows.size() - ROWS_SHOWN);
        }

        public List<Count> tally()
        {
            return HereModel.tally(rows);
        }
    }

    /** One thing in a category: its name, its status in a word, and why. */
    @Value
    public static class Row
    {
        String name;
        String word;
        Palette.Tone tone;
        /** Why it is not ready or locked, or null. */
        String reason;
    }

    /** Rows counted by status, in the card's order, leaving out the statuses none has. */
    static List<Count> tally(List<Row> rows)
    {
        int good = 0;
        int pending = 0;
        int bad = 0;
        for (Row row : rows)
        {
            if (row.getTone() == Palette.Tone.GOOD) good++;
            else if (row.getTone() == Palette.Tone.PENDING) pending++;
            else if (row.getTone() == Palette.Tone.BAD) bad++;
        }
        List<Count> tally = new ArrayList<>();
        add(tally, good, Terms.CAN_DO, Palette.Tone.GOOD);
        add(tally, pending, Terms.NOT_READY, Palette.Tone.PENDING);
        add(tally, bad, Terms.LOCKED, Palette.Tone.BAD);
        return tally;
    }

    private static void add(List<Count> tally, int value, String word, Palette.Tone tone)
    {
        if (value > 0)
        {
            tally.add(new Count(value, word.toLowerCase(Locale.ROOT), tone));
        }
    }
}
