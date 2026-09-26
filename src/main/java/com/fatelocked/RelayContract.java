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
        /** The rules the plugin holds are still current: a 304, or their version sent in full. */
        UNCHANGED,
        /** A 304 for a version the plugin does not hold; nothing changes. */
        UNCONFIRMED,
        /** Older rules than the plugin holds, or rules it cannot compare with them; not imported. */
        STALE,
        /** Still the version the plugin could not import; not downloaded or tried again. */
        STILL_REJECTED,
        /** No profile for this code. */
        MISSING,
        /** The owner disconnected this code in the web tracker. */
        GONE,
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
     * @param rejected the version the plugin could not import, or null; when
     *     set, it was the request's validator
     * @param etag the reply's ETag header, or null
     * @param retryAfter the reply's Retry-After header, or null
     * @param body the reply's body; read only for a 2xx or 404 reply
     */
    static Reply classify(Gson gson, String held, String rejected,
        int status, String etag, String retryAfter, String body)
    {
        if (status == 304)
        {
            String validator = rejected != null ? rejected : held;
            String version = etag == null ? validator : canonicalVersion(etag);
            if (held != null && held.equals(version))
            {
                return Reply.of(Outcome.UNCHANGED);
            }
            return Reply.of(rejected != null && rejected.equals(version)
                ? Outcome.STILL_REJECTED : Outcome.UNCONFIRMED);
        }
        if (status == 404)
        {
            return Reply.of(isGone(gson, body) ? Outcome.GONE : Outcome.MISSING);
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
        if (rejected != null && version.equals(parseVersion(rejected)))
        {
            // A reply that ignored the validator: still nothing new to try.
            return Reply.of(Outcome.STILL_REJECTED);
        }
        Integer previous = held == null ? null : parseVersion(held);
        if (version.equals(previous))
        {
            // Something in front of the relay dropped If-None-Match: the
            // rules the plugin holds, confirmed all the same.
            return Reply.of(Outcome.UNCHANGED);
        }
        if (held != null && (previous == null || version < previous))
        {
            return Reply.of(Outcome.STALE);
        }
        return new Reply(Outcome.RULES, version, envelope.payload, 0, null);
    }

    /**
     * A 2xx reply longer than any real bundle. The connection stops reading
     * it at its cap, so it never reaches classify.
     */
    static Reply oversized()
    {
        return Reply.unreadable("a reply larger than any real bundle");
    }

    /** Whether a 404's body is the relay's {"gone":true} marker; anything else is a plain 404. */
    private static boolean isGone(Gson gson, String body)
    {
        if (body == null) return false;
        try
        {
            Gone marker = gson.fromJson(body, Gone.class);
            return marker != null && marker.gone;
        }
        catch (JsonParseException | IllegalStateException ex)
        {
            return false;
        }
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

    private static final class Gone
    {
        private boolean gone;
    }

    private static final class Envelope
    {
        private int version;
        private String payload;
    }
}
