package com.fatelocked.events;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An event's id comes from what it is (Stage 4): the character, the run, the
 * type, its name and a count that tells repeats apart, such as a kill count.
 * Seeing one thing twice gives one id, so it is recorded once, and a second
 * paste into the tracker's Roll Inbox brings nothing new.
 */
public final class EventIds
{
    private static final String PREFIX = "fl1-";
    private static final String SEPARATOR = "\u001f";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private EventIds()
    {
    }

    /**
     * @param count what tells repeats of this name apart; empty for something that
     *     happens once, such as a quest
     */
    public static String of(String account, String runId, FateEventType type, String name, String count)
    {
        String text = fold(account) + SEPARATOR + nonNull(runId) + SEPARATOR
            + (type == null ? "" : type.name()) + SEPARATOR + fold(name) + SEPARATOR + nonNull(count);
        StringBuilder id = new StringBuilder(PREFIX);
        byte[] digest = sha256(text.getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 16; i++)
        {
            id.append(String.format("%02x", digest[i] & 0xff));
        }
        return id.toString();
    }

    /** Names as the tracker compares them: trimmed, spaces collapsed, lower case. */
    static String fold(String value)
    {
        return value == null ? "" : WHITESPACE.matcher(value.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static String nonNull(String value)
    {
        return value == null ? "" : value;
    }

    private static byte[] sha256(byte[] bytes)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        }
        catch (NoSuchAlgorithmException impossible)
        {
            // Every Java runtime provides SHA-256.
            throw new IllegalStateException(impossible);
        }
    }
}
