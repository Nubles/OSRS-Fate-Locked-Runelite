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

    /** None is loaded near the player, and none has been seen in the place they stand in. */
    public static String notFound(String row)
    {
        return "Can't find " + row + " near you here. Once you've been near one, clicking it shows the way back.";
    }

    /**
     * None is loaded near the player: the way to the nearest seen in the place, drawn by
     * Shortest Path. The line stays up as the player crosses other places, so it names this one.
     */
    public static String routed(String row, String place)
    {
        return "Shortest Path shows the way to the nearest " + row + " you've seen " + in(place) + ".";
    }

    /** None is loaded near the player: the game's arrow and a pin point the way to the nearest seen in the place. */
    public static String remembered(String row, String place)
    {
        return "The minimap's arrow points the way to the nearest " + row + " you've seen " + in(place)
            + ", and the world map has a pin.";
    }

    /** At the spot, and nothing there now: that spot is forgotten. */
    public static String gone(String row, String place)
    {
        return "The " + row + " you saw " + in(place) + " has moved or gone, so that spot is forgotten.";
    }

    private static String in(String place)
    {
        return place == null ? "here" : "in " + place;
    }
}
