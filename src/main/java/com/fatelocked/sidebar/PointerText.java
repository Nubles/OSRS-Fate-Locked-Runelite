package com.fatelocked.sidebar;

/** What the Here card says about its arrow, after the player clicks a row. */
public final class PointerText
{
    private PointerText()
    {
    }

    /** The arrow is on the nearest one; on the player's floor, or another. */
    public static String pointing(String row, boolean sameFloor)
    {
        return sameFloor ? "The arrow points at the nearest " + row + "."
            : "The nearest " + row + " is on another floor. The arrow marks where.";
    }

    /** None is loaded near the player, in the place they stand in. */
    public static String notFound(String row)
    {
        return "Can't find " + row + " near you here. It may be inside or underground.";
    }
}
