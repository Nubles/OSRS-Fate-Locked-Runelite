package com.fatelocked.detection;

import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Map;
import java.util.Set;

/**
 * One thing RuneLite saw, as plain data (Stage 4): what the detectors read. The plugin turns
 * RuneLite's events into these; contracts/detected-events.json gives the same kinds by name.
 */
public abstract class Signal
{
    private Signal()
    {
    }

    /** A chat message: its type, as RuneLite names it, and its text with colour tags. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Chat extends Signal
    {
        String type;
        String message;
    }

    /** The game's notification popup: its title and main text as the game sets them. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Popup extends Signal
    {
        String title;
        String text;
    }

    /** Real skill levels read at login or when the plugin starts: the baseline. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Levels extends Signal
    {
        Map<String, Integer> levels;
    }

    /** A skill's real level after a change. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Level extends Signal
    {
        String skill;
        int level;
    }

    /** Every quest the game says is finished, by RuneLite's quest name. The first is the baseline. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Quests extends Signal
    {
        Set<String> finished;
    }

    /** A full reading of the diary varbits, by id: the baseline. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Varbits extends Signal
    {
        Map<Integer, Integer> values;
    }

    /** One varbit's new value. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Varbit extends Signal
    {
        int id;
        int value;
    }

    /**
     * The Slayer task as the game's variables give it: the task's name from the game's task table,
     * the amount left and assigned, the master's value, the task streak for that master, and
     * whether it's a boss task.
     */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Slayer extends Signal
    {
        String task;
        int amount;
        int assigned;
        int master;
        int streak;
        boolean bossTask;
    }
}
