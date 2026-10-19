package com.fatelocked.badges;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the relay's badge replies mean (CONTRACT section 2). Every failure is quiet: the
 * plugin keeps the list it has and tries again later. A relay too old for badges answers
 * 404 "not found" in plain text, which means no badges.
 */
public final class BadgeReplies
{
    /** The relay sends at most this many badges; more are left out. */
    static final int MAX_BADGES = 5000;
    /** Retry-After is honoured between these, in seconds. */
    static final long MIN_RETRY_SECONDS = 60;
    static final long MAX_RETRY_SECONDS = 60 * 60;

    /** What a GET /badges reply does to the list. */
    public enum Fetch
    {
        /** A new list, with its ETag. */
        LIST,
        /** 304: the list held is current. */
        UNCHANGED,
        /** 404: this relay serves no badges, so there are none. */
        NONE,
        /** Anything else: keep the list held and try again later. */
        KEEP
    }

    /** The outcome of a POST or DELETE of the player's own badge. */
    public enum Sent
    {
        /** Done: the badge is on the list, or off it. */
        DONE,
        /** A POST the relay refused for want of a profile: try again only after a change or a day. */
        NO_PROFILE,
        /** Busy, broken or unreachable: try again after a wait. */
        RETRY
    }

    /** A classified GET /badges reply. */
    public static final class Fetched
    {
        public final Fetch outcome;
        /** The list for LIST and NONE; null otherwise. */
        public final BadgeList list;
        /** The ETag to send next time, for LIST; null otherwise. */
        public final String etag;

        private Fetched(Fetch outcome, BadgeList list, String etag)
        {
            this.outcome = outcome;
            this.list = list;
            this.etag = etag;
        }
    }

    private static final Fetched UNCHANGED = new Fetched(Fetch.UNCHANGED, null, null);
    private static final Fetched NO_BADGES = new Fetched(Fetch.NONE, BadgeList.NONE, null);
    private static final Fetched KEEP = new Fetched(Fetch.KEEP, null, null);

    private BadgeReplies()
    {
    }

    /** A GET /badges reply: its status, ETag header and body (null when unread). */
    public static Fetched fetched(Gson gson, int status, String etag, String body)
    {
        if (status == 304)
        {
            return UNCHANGED;
        }
        if (status == 404)
        {
            return NO_BADGES;
        }
        if (status != 200 || body == null)
        {
            return KEEP;
        }
        BadgeList list = parse(gson, body);
        if (list == null)
        {
            return KEEP;
        }
        String tag = etag == null || etag.trim().isEmpty() ? null : etag.trim();
        return new Fetched(Fetch.LIST, list, tag);
    }

    /**
     * {"badges":[{"n":name,"t":tier}, ...]}: the list, leaving out entries that aren't a name
     * and a tier from 0 to 4; null when the body isn't that shape at all.
     */
    static BadgeList parse(Gson gson, String body)
    {
        JsonObject root;
        try
        {
            JsonElement parsed = gson.fromJson(body, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject())
            {
                return null;
            }
            root = parsed.getAsJsonObject();
        }
        catch (JsonParseException | IllegalStateException error)
        {
            return null;
        }
        JsonElement badges = root.get("badges");
        if (badges == null || !badges.isJsonArray())
        {
            return null;
        }
        JsonArray array = badges.getAsJsonArray();
        Map<String, Integer> byName = new LinkedHashMap<>();
        int read = 0;
        for (JsonElement element : array)
        {
            if (read++ >= MAX_BADGES)
            {
                break;
            }
            if (!element.isJsonObject())
            {
                continue;
            }
            JsonObject badge = element.getAsJsonObject();
            JsonElement name = badge.get("n");
            JsonElement tier = badge.get("t");
            if (name == null || tier == null || !name.isJsonPrimitive() || !name.getAsJsonPrimitive().isString()
                || !tier.isJsonPrimitive() || !tier.getAsJsonPrimitive().isNumber())
            {
                continue;
            }
            double value = tier.getAsDouble();
            if (value != Math.rint(value) || !BadgeTier.valid((int) value))
            {
                continue;
            }
            byName.put(name.getAsString(), (int) value);
        }
        return BadgeList.of(byName);
    }

    /** A POST /r/<code>/badge reply's status. */
    public static Sent posted(int status)
    {
        if (status >= 200 && status < 300)
        {
            return Sent.DONE;
        }
        return status == 404 ? Sent.NO_PROFILE : Sent.RETRY;
    }

    /**
     * A DELETE /r/<code>/badge reply's status. A 404 is done too: only a relay too old for
     * badges answers it, and that relay holds none.
     */
    public static Sent deleted(int status)
    {
        return (status >= 200 && status < 300) || status == 404 ? Sent.DONE : Sent.RETRY;
    }

    /** How long to wait after a refusal, in milliseconds: Retry-After within bounds, else the fallback. */
    public static long waitMillis(String retryAfter, long fallbackMillis)
    {
        if (retryAfter == null)
        {
            return fallbackMillis;
        }
        try
        {
            long seconds = Long.parseLong(retryAfter.trim());
            return Math.max(MIN_RETRY_SECONDS, Math.min(MAX_RETRY_SECONDS, seconds)) * 1000L;
        }
        catch (NumberFormatException error)
        {
            return fallbackMillis;
        }
    }
}
