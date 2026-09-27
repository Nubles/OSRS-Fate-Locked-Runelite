package com.fatelocked.sidebar;

import com.fatelocked.ui.Palette;
import java.util.Collections;
import java.util.List;
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

    /** One category of content, such as Quests or Shops. */
    @Value
    public static class Group
    {
        /** The tracker's category id, such as QUESTS. */
        String category;
        String title;
        List<Row> rows;

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
}
