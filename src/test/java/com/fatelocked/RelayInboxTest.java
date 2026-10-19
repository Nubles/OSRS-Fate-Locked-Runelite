package com.fatelocked;

import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Send to tracker's side of the relay contract: what goes, in which requests, and what each reply means. */
public class RelayInboxTest
{
    private static final long NOW = 1_790_000_000_000L;
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final String CODE = "0123456789abcdef0123456789abcdef";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    /** Copy for tracker itself, so the requests are pinned to carry its body. */
    private Function<List<FateEvent>, String> copy;

    @Before
    public void setUp() throws Exception
    {
        DetectedEventStore store = new DetectedEventStore(gson,
            folder.newFolder("account").toPath().resolve(DetectedEventStore.FILE));
        copy = store::copyOf;
    }

    @Test
    public void sendsToTheCodesInbox()
    {
        assertEquals("/r/" + CODE + "/inbox", RelayInbox.path(CODE));
    }

    @Test
    public void sendsOnlyWhatTheRelayTakes()
    {
        assertTrue(RelayInbox.sendable(event("a", NOW), NOW));
        assertTrue(RelayInbox.sendable(event(repeat('x', 128), NOW), NOW));
        assertFalse(RelayInbox.sendable(event(repeat('x', 129), NOW), NOW));
        assertFalse(RelayInbox.sendable(event("", NOW), NOW));
        assertFalse(RelayInbox.sendable(event(null, NOW), NOW));
        assertFalse(RelayInbox.sendable(null, NOW));
        // The relay refuses events older than 30 days; an hour's room for its clock.
        assertTrue(RelayInbox.sendable(event("a", NOW - 29 * DAY), NOW));
        assertFalse(RelayInbox.sendable(event("a", NOW - 30 * DAY + 30 * 60 * 1000), NOW));
        assertFalse(RelayInbox.sendable(event("a", NOW + 6 * 60 * 1000), NOW));
    }

    @Test
    public void splitsIntoRequestsOfAtMostAHundredWithCopyForTrackersBody()
    {
        List<FateEvent> events = new ArrayList<>();
        for (int i = 0; i < 250; i++)
        {
            events.add(event("e" + i, NOW - i));
        }

        List<RelayInbox.Batch> batches = RelayInbox.batches(events, copy, NOW);

        assertEquals(List.of(100, 100, 50), batches.stream().map(batch -> batch.ids.size()).collect(Collectors.toList()));
        assertEquals("e0", batches.get(0).ids.get(0));
        assertEquals("e249", batches.get(2).ids.get(49));
        assertEquals(copy.apply(events.subList(100, 200)), batches.get(1).body);
        JsonObject body = gson.fromJson(batches.get(2).body, JsonObject.class);
        assertEquals(DetectedEventStore.FORMAT, body.get("format").getAsString());
        assertEquals(50, body.getAsJsonArray("events").size());
        assertEquals("e200", body.getAsJsonArray("events").get(0).getAsJsonObject().get("eventId").getAsString());
    }

    @Test
    public void keepsEachRequestWithin64KiB()
    {
        // About 7 KiB each: several fit in a request, never 64 KiB of them.
        List<FateEvent> events = new ArrayList<>();
        for (int i = 0; i < 30; i++)
        {
            events.add(event("big" + (i < 10 ? "0" : "") + i, NOW, repeat('z', 7000)));
        }
        int eventBytes = copy.apply(events.subList(0, 1)).getBytes(StandardCharsets.UTF_8).length
            - copy.apply(Collections.emptyList()).getBytes(StandardCharsets.UTF_8).length;

        List<RelayInbox.Batch> batches = RelayInbox.batches(events, copy, NOW);

        int sent = 0;
        for (RelayInbox.Batch batch : batches)
        {
            int bytes = batch.body.getBytes(StandardCharsets.UTF_8).length;
            assertTrue(bytes + " bytes", bytes <= RelayInbox.MAX_BODY_BYTES);
            assertEquals(batch.body, copy.apply(events.subList(sent, sent + batch.ids.size())));
            sent += batch.ids.size();
        }
        assertEquals(30, sent);
        assertTrue(batches.get(0).ids.size() > 1);
        // Full: another event, and its comma, would have gone over.
        int full = batches.get(0).body.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(full + eventBytes + 1 > RelayInbox.MAX_BODY_BYTES);
    }

    @Test
    public void leavesOutWhatTheRelayWontTake()
    {
        List<FateEvent> events = List.of(
            event("ok", NOW),
            event("month-old", NOW - 31 * DAY),
            event(repeat('x', 200), NOW),
            event("too-big", NOW, repeat('z', RelayInbox.MAX_EVENT_BYTES)),
            event("also-ok", NOW - 1));

        List<RelayInbox.Batch> batches = RelayInbox.batches(events, copy, NOW);

        assertEquals(1, batches.size());
        assertEquals(List.of("ok", "also-ok"), batches.get(0).ids);
        assertEquals(Collections.emptyList(),
            RelayInbox.batches(List.of(event("month-old", NOW - 31 * DAY)), copy, NOW));
        assertEquals(Collections.emptyList(), RelayInbox.batches(Collections.emptyList(), copy, NOW));
    }

    @Test
    public void readsEachReply()
    {
        assertEquals(RelayInbox.Outcome.SENT, classify(200, "{\"accepted\":3,\"held\":3}"));
        assertEquals(RelayInbox.Outcome.SENT, classify(200, "{\"accepted\":0,\"held\":250}"));
        // A success that isn't the relay's: a captive portal, or nothing at all.
        assertEquals(RelayInbox.Outcome.UNREADABLE, classify(200, "<html>Sign in to the Wi-Fi</html>"));
        assertEquals(RelayInbox.Outcome.UNREADABLE, classify(200, "{\"held\":3}"));
        assertEquals(RelayInbox.Outcome.UNREADABLE, classify(200, "{\"accepted\":\"3\"}"));
        assertEquals(RelayInbox.Outcome.UNREADABLE, classify(204, null));
        assertEquals(RelayInbox.Outcome.UNREADABLE, classify(200, ""));

        // The relay without the route: its plain-text 404, or any 404 that isn't "no profile".
        assertEquals(RelayInbox.Outcome.NOT_YET, classify(404, "not found"));
        assertEquals(RelayInbox.Outcome.NOT_YET, classify(404, "<html>404</html>"));
        assertEquals(RelayInbox.Outcome.NOT_YET, classify(404, null));
        assertEquals(RelayInbox.Outcome.NOT_YET, classify(404, "{\"error\":\"not found\"}"));
        assertEquals(RelayInbox.Outcome.NOT_CONNECTED, classify(404, "{\"error\":\"no profile\"}"));
        assertEquals(RelayInbox.Outcome.NOT_CONNECTED, classify(404, "{\"gone\":true}"));
        assertEquals(RelayInbox.Outcome.NOT_YET, classify(404, "{\"gone\":false}"));

        assertEquals(RelayInbox.Outcome.BUSY, classify(429, "{\"error\":\"rate limited\"}"));
        assertEquals(RelayInbox.Outcome.REFUSED, classify(400, "{\"error\":\"bad body\"}"));
        assertEquals(RelayInbox.Outcome.REFUSED, classify(413, "payload too large"));
        assertEquals(RelayInbox.Outcome.UNAVAILABLE, classify(500, "{\"error\":\"internal\"}"));
        assertEquals(RelayInbox.Outcome.UNAVAILABLE, classify(503, null));
        assertEquals(RelayInbox.Outcome.UNAVAILABLE, classify(403, "{\"error\":\"forbidden\"}"));
        assertEquals(RelayInbox.Outcome.UNAVAILABLE, classify(301, null));
    }

    /** The card's words after a send, pinned: the player reads them. */
    @Test
    public void saysWhatHappenedInPlainWords()
    {
        assertEquals("Sent 3. They're waiting in your tracker's Roll Inbox.",
            RelayInbox.notice(3, 3, RelayInbox.Outcome.SENT));
        assertEquals("Sent 1. It's waiting in your tracker's Roll Inbox.",
            RelayInbox.notice(1, 1, RelayInbox.Outcome.SENT));
        assertEquals("Sent 2 of 3. They're waiting in your tracker's Roll Inbox. Use Copy for tracker for the rest.",
            RelayInbox.notice(2, 3, RelayInbox.Outcome.SENT));
        assertEquals("The relay can't take events yet. Use Copy for tracker.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.NOT_YET));
        assertEquals("Couldn't reach the relay. Nothing was sent.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.UNREACHABLE));
        assertEquals("The tracker isn't connected. Connect it, or use Copy for tracker.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.NOT_CONNECTED));
        assertEquals("Too many sends. Try again in a minute.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.BUSY));
        assertEquals("The relay turned these events down. Use Copy for tracker.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.REFUSED));
        assertEquals("The relay isn't working right now. Nothing was sent.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.UNAVAILABLE));
        assertEquals("The relay sent a reply RuneLite can't read. Use Copy for tracker.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.UNREADABLE));
        assertEquals("Sending stopped when online sync or the pairing changed. Nothing was sent.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.STOPPED));
        assertEquals("These events can't be sent. Use Copy for tracker.",
            RelayInbox.notice(0, 3, RelayInbox.Outcome.NOTHING_SENDABLE));
        // Part way: what went, then why the rest didn't.
        assertEquals("Sent 100 of 150. Couldn't reach the relay. The rest weren't sent.",
            RelayInbox.notice(100, 150, RelayInbox.Outcome.UNREACHABLE));
        assertEquals("Sent 100 of 150. Too many sends. Try again in a minute.",
            RelayInbox.notice(100, 150, RelayInbox.Outcome.BUSY));
    }

    private RelayInbox.Outcome classify(int status, String body)
    {
        return RelayInbox.classify(gson, status, body);
    }

    private static FateEvent event(String id, long occurredAt)
    {
        return event(id, occurredAt, null);
    }

    private static FateEvent event(String id, long occurredAt, String note)
    {
        return FateEvent.builder()
            .protocolVersion(1).eventId(id).runId("run-1").account("Player").runRevision(3)
            .eventType(FateEventType.QUEST).canonicalLabel("Cook's Assistant")
            .occurredAt(occurredAt).sessionSequence(1).bundleVersion(4).rulesVersion("1").contentVersion(1)
            .detectorId("quest-state-v1").detectorVersion(1).confidence(EventConfidence.EXACT)
            .evidence(note == null ? null : Collections.<String, Object>singletonMap("note", note))
            .build();
    }

    private static String repeat(char c, int times)
    {
        char[] chars = new char[times];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}
