package com.fatelocked.badges;

import java.util.Locale;

/**
 * Character names as the badge list keys them, and as the relay accepts them. Names are
 * compared case-insensitively with spaces, '_', '-' and the game's non-breaking spaces alike,
 * as Jagex compares them; icon and colour tags the game puts in a name are ignored.
 */
public final class BadgeNames
{
    /** The relay's limit on a name, after trimming. */
    static final int MAX_LENGTH = 12;

    private BadgeNames()
    {
    }

    /**
     * The name as the badge list keys it: tags dropped, '_', '-' and non-breaking spaces as
     * spaces, runs of spaces as one, trimmed and lower case. Empty for null.
     */
    public static String normalise(String name)
    {
        if (name == null || name.isEmpty())
        {
            return "";
        }
        StringBuilder out = new StringBuilder(name.length());
        boolean inTag = false;
        boolean space = false;
        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (inTag)
            {
                inTag = c != '>';
                continue;
            }
            if (c == '<')
            {
                inTag = true;
                continue;
            }
            if (c == ' ' || c == '_' || c == '-' || c == ' ' || Character.isWhitespace(c))
            {
                space = out.length() > 0;
                continue;
            }
            if (space)
            {
                out.append(' ');
                space = false;
            }
            out.append(c);
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * The name to send the relay: tags dropped, non-breaking spaces as spaces, trimmed. Null
     * unless it is 1 to 12 letters, digits, spaces, '-' and '_', as the relay requires.
     */
    public static String shareable(String displayName)
    {
        if (displayName == null)
        {
            return null;
        }
        String name = displayName.replaceAll("<[^>]*>", "").replace(' ', ' ').trim();
        if (name.isEmpty() || name.length() > MAX_LENGTH)
        {
            return null;
        }
        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            boolean letterOrDigit = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!letterOrDigit && c != ' ' && c != '-' && c != '_')
            {
                return null;
            }
        }
        return name;
    }
}
