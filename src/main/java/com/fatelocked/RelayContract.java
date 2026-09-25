package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

/**
 * What a reply to the plugin's one request, GET /r/<code>, means. Decided
 * here in one place and without side effects; the connection controller
 * acts on the answer. The web app's contracts/relay/relay-get.json lists
 * the replies, from the relay and from what can stand in front of it, and
 * the outcome each must have.
 */
final class RelayContract
{
    enum Outcome
    {
        /** Newer rules, to parse and import. */
        RULES,
        /** The rules the plugin holds are still current. */
        UNCHANGED,
        /** A 304 for a version the plugin does not hold; nothing changes. */
        UNCONFIRMED,
        /** Rules the plugin already holds, or older ones; not imported. */
        STALE,
        /** No profile for this code. */
        MISSING,
        /** A reply the plugin cannot use. */
        UNREADABLE,
        /** The relay asks the plugin to slow down. */
        BUSY,
        /** The relay or something in front of it failed. */
        UNAVAILABLE
    }

    /** One classified reply. */
    static final class Reply
    {
        final Outcome outcome;
        /** RULES only. */
        final int version;
        /** RULES only. */
        final String payload;
        /** BUSY only: seconds the relay asked for, or 0 when it did not say. */
        final long retryAfterSeconds;
        /** UNREADABLE only: what was wrong with it, for the log. */
        final String detail;

        private Reply(
            Outcome outcome, int version, String payload, long retryAfterSeconds, String detail)
        {
            this.outcome = outcome;
            this.version = version;
            this.payload = payload;
            this.retryAfterSeconds = retryAfterSeconds;
            this.detail = detail;
        }

        private static Reply of(Outcome outcome)
        {
            return new Reply(outcome, 0, null, 0, null);
        }

        private static Reply unreadable(String detail)
        {
            return new Reply(Outcome.UNREADABLE, 0, null, 0, detail);
        }
    }

    private RelayContract()
    {
    }

    /**
     * @param held the version the plugin holds, or null
     * @param etag the reply's ETag header, or null
     * @param retryAfter the reply's Retry-After header, or null
     * @param body the reply's body; read only for a 2xx reply
     */
    static Reply classify(
        Gson gson, String held, int status, String etag, String retryAfter, String body)
    {
        if (status == 304)
        {
            String version = etag == null ? held : canonicalVersion(etag);
            return Reply.of(held != null && held.equals(version)
                ? Outcome.UNCHANGED : Outcome.UNCONFIRMED);
        }
        if (status == 404)
        {
            return Reply.of(Outcome.MISSING);
        }
        if (status == 429)
        {
            return new Reply(Outcome.BUSY, 0, null, retryAfterSeconds(retryAfter), null);
        }
        if (status < 200 || status >= 300)
        {
            return Reply.of(Outcome.UNAVAILABLE);
        }

        Envelope envelope;
        try
        {
            envelope = gson.fromJson(body, Envelope.class);
        }
        catch (JsonParseException | IllegalStateException ex)
        {
            return Reply.unreadable("not a relay reply");
        }
        if (envelope == null || envelope.payload == null)
        {
            return Reply.unreadable("no rules in the reply");
        }
        if (envelope.version <= 0)
        {
            return Reply.unreadable("no valid version");
        }
        Integer version = etag == null ? Integer.valueOf(envelope.version) : parseVersion(etag);
        if (version == null || version != envelope.version)
        {
            return Reply.unreadable("an ETag that disagrees with the reply");
        }
        Integer previous = held == null ? null : parseVersion(held);
        if (held != null && (previous == null || version <= previous))
        {
            return Reply.of(Outcome.STALE);
        }
        return new Reply(Outcome.RULES, version, envelope.payload, 0, null);
    }

    /** A positive version from a bare, quoted or weak ETag, or null. */
    static Integer parseVersion(String raw)
    {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.startsWith("W/"))
        {
            value = value.substring(2);
        }
        if (value.startsWith("\""))
        {
            if (value.length() < 2 || !value.endsWith("\""))
            {
                return null;
            }
            value = value.substring(1, value.length() - 1);
        }
        else if (value.contains("\""))
        {
            return null;
        }
        if (!value.matches("[1-9][0-9]*")) return null;
        try
        {
            return Integer.valueOf(value);
        }
        catch (NumberFormatException error)
        {
            return null;
        }
    }

    /** The version as the plugin sends it back in If-None-Match. */
    static String canonicalVersion(String raw)
    {
        Integer parsed = parseVersion(raw);
        return parsed == null ? raw : String.valueOf(parsed);
    }

    static long retryAfterSeconds(String raw)
    {
        if (raw == null || !raw.trim().matches("[0-9]+"))
        {
            return 0;
        }
        try
        {
            return Long.parseLong(raw.trim());
        }
        catch (NumberFormatException error)
        {
            return 0;
        }
    }

    private static final class Envelope
    {
        private int version;
        private String payload;
    }
}
