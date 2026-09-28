package com.fatelocked.sidebar;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * A skilling row's first requirement, as the tracker writes it: the skill, the player's level
 * and the level needed, and the cap their unlocked tier allows ("Woodcutting 15/1 · cap 20").
 */
@Value
public class SkillLine
{
    private static final Pattern LINE = Pattern.compile("^(\\S.*?) (\\d+)/(\\d+) · cap (\\d+)$");

    String skill;
    int level;
    int needed;
    int cap;

    /** The line, or null when the requirement isn't one. */
    public static SkillLine parse(String clause)
    {
        Matcher line = LINE.matcher(clause.trim());
        if (!line.matches())
        {
            return null;
        }
        return new SkillLine(line.group(1), Integer.parseInt(line.group(2)), Integer.parseInt(line.group(3)),
            Integer.parseInt(line.group(4)));
    }

    /** The level is reached, and the cap allows it. */
    public boolean met()
    {
        return level >= needed && cap >= needed;
    }
}
