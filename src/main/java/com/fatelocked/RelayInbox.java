package com.fatelocked;

import com.fatelocked.events.FateEvent;
import com.fatelocked.ui.Terms;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Send to tracker's side of the relay contract, POST /r/&lt;code&gt;/inbox, decided here without side
 * effects: which events can go, how they are split into requests, what each reply means and what
 * the Roll inbox card says about it. TrackerInboxSender sends the requests.
 */
final class RelayInbox
{
    /** The most events the relay takes in one request. */
    static final int MAX_EVENTS_PER_REQUEST = 100;
    /** The largest body the relay takes. */
    static final int MAX_BODY_BYTES = 64 * 1024;
    /** The largest event the relay keeps; the tracker can't read a bigger one. */
    static final int MAX_EVENT_BYTES = 8 * 1024;
    static final int MAX_ID_CHARS = 128;
    /**
     * The relay refuses an event older than 30 days by its own clock. An hour less leaves room for
     * a clock that is a little off, rather than a whole request refused for one event.
     */
    static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 - 60L * 60 * 1000;
    /** The relay refuses an event more than 10 minutes ahead of its clock. */
    static final long MAX_AHEAD_MS = 5L * 60 * 1000;

    /** What a request, or a whole send, came to. */
    enum Outcome
    {
        /** The relay stored the request's events. */
        SENT,
        /** A 404 that isn't the relay's "no profile": a relay without the route yet. */
        NOT_YET,
        /** The relay has no profile for this code, or the tracker disconnected it. */
        NOT_CONNECTED,
        /** 429: the relay's write limit. */
        BUSY,
        /** 400 or 413: the relay turned the body down. */
        REFUSED,
        /** Any other reply that isn't a success. */
        UNAVAILABLE,
        /** A success the plugin can't read, such as a captive portal's page. */
        UNREADABLE,
        /** No reply: no network, a timeout, a dropped connection. */
        UNREACHABLE,
        /** Online sync was turned off, or the pairing changed, part way. */
        STOPPED,
        /** None of the events can go to the relay. */
        NOTHING_SENDABLE
    }

    /** One request's events, and its body. */
    static final class Batch
    {
        final List<String> ids;
        final String body;

        Batch(List<String> ids, String body)
        {
            this.ids = Collections.unmodifiableList(new ArrayList<>(ids));
            this.body = body;
        }
    }

    private RelayInbox()
    {
    }

    /** The path a code's events are sent to, after the relay's base URL. */
    static String path(String code)
    {
        return "/r/" + code + "/inbox";
    }

    /** Whether the relay will take this event: an id it reads, and a time it accepts. */
    static boolean sendable(FateEvent event, long now)
    {
        if (event == null || event.getEventId() == null) return false;
        int idLength = event.getEventId().length();
        return idLength >= 1 && idLength <= MAX_ID_CHARS
            && event.getOccurredAt() >= now - MAX_AGE_MS
            && event.getOccurredAt() <= now + MAX_AHEAD_MS;
    }

    /**
     * The requests that send these events, in order: each at most 100 events and 64 KiB, with the
     * body Copy for tracker makes. Events the relay won't take are left out; they stay New, for
     * Copy for tracker.
     *
     * @param copy what Copy for tracker puts on the clipboard for a list of events
     */
    static List<Batch> batches(List<FateEvent> events, Function<List<FateEvent>, String> copy, long now)
    {
        // A body is the empty one with its events, and a comma between each two, inside the brackets.
        int empty = bytes(copy.apply(Collections.<FateEvent>emptyList()));
        List<Batch> batches = new ArrayList<>();
        List<FateEvent> current = new ArrayList<>();
        int size = empty;
        for (FateEvent event : events)
        {
            if (!sendable(event, now)) continue;
            int alone = bytes(copy.apply(Collections.singletonList(event)));
            int eventBytes = alone - empty;
            if (eventBytes > MAX_EVENT_BYTES || alone > MAX_BODY_BYTES) continue;
            int added = current.isEmpty() ? eventBytes : eventBytes + 1;
            if (!current.isEmpty() && (current.size() >= MAX_EVENTS_PER_REQUEST || size + added > MAX_BODY_BYTES))
            {
                batches.add(batch(current, copy));
                current = new ArrayList<>();
                size = empty;
                added = eventBytes;
            }
            current.add(event);
            size += added;
        }
        if (!current.isEmpty())
        {
            batches.add(batch(current, copy));
        }
        return batches;
    }

    private static Batch batch(List<FateEvent> events, Function<List<FateEvent>, String> copy)
    {
        List<String> ids = new ArrayList<>();
        for (FateEvent event : events)
        {
            ids.add(event.getEventId());
        }
        return new Batch(ids, copy.apply(events));
    }

    private static int bytes(String text)
    {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * What a reply to one request means.
     *
     * @param body the reply's body, or null when it wasn't read or was too long
     */
    static Outcome classify(Gson gson, int status, String body)
    {
        if (status >= 200 && status < 300)
        {
            JsonObject reply = object(gson, body);
            JsonElement accepted = reply == null ? null : reply.get("accepted");
            return accepted instanceof JsonPrimitive && ((JsonPrimitive) accepted).isNumber()
                ? Outcome.SENT : Outcome.UNREADABLE;
        }
        switch (status)
        {
            case 404:
                return noProfile(object(gson, body)) ? Outcome.NOT_CONNECTED : Outcome.NOT_YET;
            case 429:
                return Outcome.BUSY;
            case 400:
            case 413:
                return Outcome.REFUSED;
            default:
                return Outcome.UNAVAILABLE;
        }
    }

    /** The relay's 404 for a code with no profile, {"error":"no profile"}, or its gone marker. */
    private static boolean noProfile(JsonObject reply)
    {
        if (reply == null) return false;
        JsonElement error = reply.get("error");
        JsonElement gone = reply.get("gone");
        return error instanceof JsonPrimitive && "no profile".equals(error.getAsString())
            || gone instanceof JsonPrimitive && ((JsonPrimitive) gone).isBoolean() && gone.getAsBoolean();
    }

    private static JsonObject object(Gson gson, String body)
    {
        if (body == null) return null;
        try
        {
            JsonElement parsed = gson.fromJson(body, JsonElement.class);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }
        catch (JsonParseException | IllegalStateException ex)
        {
            return null;
        }
    }

    static final String NOT_YET_NOTICE = "The relay can't take events yet. Use " + Terms.COPY_FOR_TRACKER + ".";
    static final String NOT_CONNECTED_NOTICE = "The tracker isn't connected. Connect it, or use "
        + Terms.COPY_FOR_TRACKER + ".";
    static final String BUSY_NOTICE = "Too many sends. Try again in a minute.";
    static final String UNREACHABLE_NOTICE = "Couldn't reach the relay. Nothing was sent.";
    static final String UNAVAILABLE_NOTICE = "The relay isn't working right now. Nothing was sent.";
    static final String REFUSED_NOTICE = "The relay turned these events down. Use "
        + Terms.COPY_FOR_TRACKER + ".";
    static final String UNREADABLE_NOTICE = "The relay sent a reply RuneLite can't read. Use "
        + Terms.COPY_FOR_TRACKER + ".";
    static final String STOPPED_NOTICE = "Sending stopped when online sync or the pairing changed. Nothing was sent.";
    static final String NOTHING_SENDABLE_NOTICE = "These events can't be sent. Use "
        + Terms.COPY_FOR_TRACKER + ".";

    /**
     * What the Roll inbox card says after a send.
     *
     * @param sent    the events the relay took
     * @param total   the events the player sent
     * @param outcome SENT when every request went through, or what stopped the rest
     */
    static String notice(int sent, int total, Outcome outcome)
    {
        if (outcome == Outcome.SENT)
        {
            String waiting = (sent == 1 ? "It's" : "They're") + " waiting in your tracker's Roll Inbox.";
            return sent == total
                ? "Sent " + sent + ". " + waiting
                : "Sent " + sent + " of " + total + ". " + waiting + " Use " + Terms.COPY_FOR_TRACKER + " for the rest.";
        }
        String why = why(outcome);
        return sent == 0 ? why
            : "Sent " + sent + " of " + total + ". " + why.replace("Nothing was sent.", "The rest weren't sent.");
    }

    private static String why(Outcome outcome)
    {
        switch (outcome)
        {
            case NOT_YET:
                return NOT_YET_NOTICE;
            case NOT_CONNECTED:
                return NOT_CONNECTED_NOTICE;
            case BUSY:
                return BUSY_NOTICE;
            case REFUSED:
                return REFUSED_NOTICE;
            case UNREADABLE:
                return UNREADABLE_NOTICE;
            case UNREACHABLE:
                return UNREACHABLE_NOTICE;
            case STOPPED:
                return STOPPED_NOTICE;
            case NOTHING_SENDABLE:
                return NOTHING_SENDABLE_NOTICE;
            case UNAVAILABLE:
            default:
                return UNAVAILABLE_NOTICE;
        }
    }
}
