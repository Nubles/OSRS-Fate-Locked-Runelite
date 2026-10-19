package com.fatelocked;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.client.config.ConfigManager;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Send to tracker's requests: what goes to the relay, in which order, and what the card hears. */
public class TrackerInboxSenderTest
{
    private static final String CODE = "0123456789abcdef0123456789abcdef";
    private static final long NOW = 1_790_000_000_000L;
    private static final String ACCEPTED = accepted(100);

    private final Gson gson = new Gson();
    private final Map<String, String> configuration = new ConcurrentHashMap<>();
    private final List<Request> pluginRequests = new CopyOnWriteArrayList<>();
    /** A stand-in for Copy for tracker's body. */
    private final Function<List<FateEvent>, String> copy = events -> {
        JsonObject body = new JsonObject();
        body.addProperty("format", "fate-locked-runelite-events");
        body.add("events", gson.toJsonTree(new ArrayList<>(events)));
        return body.toString();
    };

    private MockWebServer server;
    private TrackerConnectionSettings settings;
    private TrackerInboxSender sender;
    private Recorder recorder;

    @Before
    public void setUp() throws Exception
    {
        configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, CODE);
        configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "true");
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfiguration(anyString(), anyString()))
            .thenAnswer(invocation -> configuration.get(invocation.getArgument(1)));
        settings = new TrackerConnectionSettings(configManager);

        server = new MockWebServer();
        server.start();
        Interceptor redirectToServer = chain -> {
            Request original = chain.request();
            pluginRequests.add(original);
            return chain.proceed(original.newBuilder()
                .url(server.url(original.url().encodedPath()))
                .build());
        };
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(redirectToServer).build();
        sender = new TrackerInboxSender(http, gson, settings, Runnable::run, () -> NOW);
        recorder = new Recorder();
    }

    @After
    public void tearDown() throws Exception
    {
        sender.stop();
        server.shutdown();
    }

    @Test
    public void postsTheEventsToTheCodesInboxAHundredAtATime() throws Exception
    {
        server.enqueue(new MockResponse().setBody(ACCEPTED));
        server.enqueue(new MockResponse().setBody("{\"accepted\":50,\"held\":150}"));
        List<FateEvent> events = events(150);

        assertTrue(sender.send(CODE, events, copy, recorder));
        recorder.await();

        assertEquals(2, server.getRequestCount());
        RecordedRequest first = server.takeRequest();
        assertEquals("POST", first.getMethod());
        assertEquals("/r/" + CODE + "/inbox", first.getPath());
        assertTrue(first.getHeader("Content-Type").startsWith("application/json"));
        assertEquals(copy.apply(events.subList(0, 100)), first.getBody().readUtf8());
        assertEquals(copy.apply(events.subList(100, 150)), server.takeRequest().getBody().readUtf8());
        assertEquals(TrackerConnectionSettings.RELAY_BASE_URL + "/r/" + CODE + "/inbox",
            pluginRequests.get(0).url().toString());

        assertEquals(2, recorder.sent.size());
        assertEquals(ids(events.subList(0, 100)), recorder.sent.get(0));
        assertEquals(ids(events.subList(100, 150)), recorder.sent.get(1));
        assertEquals("Sent 150. They're waiting in your tracker's Roll Inbox.", recorder.notice);
        assertEquals(1, recorder.started);
        assertFalse(sender.sending());
    }

    @Test
    public void stopsAtTheFirstFailureAndKeepsWhatWent() throws Exception
    {
        server.enqueue(new MockResponse().setBody(ACCEPTED));
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "60")
            .setBody("{\"error\":\"rate limited\"}"));
        server.enqueue(new MockResponse().setBody(ACCEPTED));

        sender.send(CODE, events(250), copy, recorder);
        recorder.await();

        assertEquals(2, server.getRequestCount());
        assertEquals(1, recorder.sent.size());
        assertEquals(100, recorder.sent.get(0).size());
        assertEquals("Sent 100 of 250. Too many sends. Try again in a minute.", recorder.notice);
    }

    @Test
    public void eachReplySaysWhatHappenedAndNothingIsRetried() throws Exception
    {
        assertAfterOneRequest(new MockResponse().setResponseCode(404).setBody("not found"),
            "The relay can't take events yet. Use Copy for tracker.");
        assertAfterOneRequest(new MockResponse().setResponseCode(404).setBody("{\"error\":\"no profile\"}"),
            "The tracker isn't connected. Connect it, or use Copy for tracker.");
        assertAfterOneRequest(new MockResponse().setResponseCode(429),
            "Too many sends. Try again in a minute.");
        assertAfterOneRequest(new MockResponse().setResponseCode(400).setBody("{\"error\":\"bad body\"}"),
            "The relay turned these events down. Use Copy for tracker.");
        assertAfterOneRequest(new MockResponse().setResponseCode(503),
            "The relay isn't working right now. Nothing was sent.");
        assertAfterOneRequest(new MockResponse().setBody("<html>Sign in to the Wi-Fi</html>"),
            "The relay sent a reply RuneLite can't read. Use Copy for tracker.");
        assertAfterOneRequest(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
            "Couldn't reach the relay. Nothing was sent.");
        // A redirect isn't followed: the events go to the relay or nowhere.
        assertAfterOneRequest(new MockResponse().setResponseCode(307).setHeader("Location", "/elsewhere"),
            "The relay isn't working right now. Nothing was sent.");
    }

    /**
     * The relay keeps the newest 250 waiting: when it stores fewer of a request's events than it
     * was sent, which it kept isn't known, so none of that request turns Sent and the send stops.
     */
    @Test
    public void aRequestTheRelayKeptOnlyPartOfIsNotMarkedSent() throws Exception
    {
        server.enqueue(new MockResponse().setBody(ACCEPTED));
        server.enqueue(new MockResponse().setBody("{\"accepted\":20,\"held\":250}"));

        sender.send(CODE, events(250), copy, recorder);
        recorder.await();

        assertEquals("and none after it", 2, server.getRequestCount());
        assertEquals(1, recorder.sent.size());
        assertEquals(ids(events(250).subList(0, 100)), recorder.sent.get(0));
        assertEquals("Sent 100 of 250. Your tracker hasn't taken the events already waiting."
            + " Open the tracker, then send again.", recorder.notice);
        assertFalse(sender.sending());

        server.takeRequest();
        server.takeRequest();
        assertAfterOneRequest(new MockResponse().setBody("{\"accepted\":0,\"held\":250}"),
            "Your tracker hasn't taken the events already waiting. Open the tracker, then send again.");
    }

    @Test
    public void aReplyLongerThanAnyRealOneIsNotRead() throws Exception
    {
        char[] padding = new char[(int) TrackerInboxSender.MAX_REPLY_BYTES];
        java.util.Arrays.fill(padding, ' ');
        assertAfterOneRequest(new MockResponse().setBody(ACCEPTED + new String(padding)),
            "The relay sent a reply RuneLite can't read. Use Copy for tracker.");
    }

    @Test
    public void aSecondClickWhileSendingDoesNothing() throws Exception
    {
        server.enqueue(new MockResponse().setBody(accepted(3)).setHeadersDelay(300, TimeUnit.MILLISECONDS));

        assertTrue(sender.send(CODE, events(3), copy, recorder));
        assertTrue(sender.sending());
        assertFalse(sender.send(CODE, events(3), copy, new Recorder()));
        recorder.await();

        assertEquals(1, server.getRequestCount());
        assertFalse(sender.sending());
        // Once it's over, the next click sends again.
        server.enqueue(new MockResponse().setBody(accepted(3)));
        Recorder again = new Recorder();
        assertTrue(sender.send(CODE, events(3), copy, again));
        again.await();
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void sendsNothingWithoutOnlineSyncOrAnything() throws Exception
    {
        assertFalse(sender.send(CODE, Collections.emptyList(), copy, recorder));
        assertFalse(sender.send("", events(3), copy, recorder));
        configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "false");
        assertFalse(sender.send(CODE, events(3), copy, recorder));

        assertEquals(0, server.getRequestCount());
        assertNull(recorder.notice);
        assertEquals(0, recorder.started);
        assertFalse(sender.sending());
    }

    @Test
    public void stopsWhenOnlineSyncGoesOffBetweenRequests() throws Exception
    {
        server.enqueue(new MockResponse().setBody(ACCEPTED));
        server.enqueue(new MockResponse().setBody(ACCEPTED));
        Recorder turnsSyncOff = new Recorder()
        {
            @Override
            public void sent(List<String> eventIds)
            {
                super.sent(eventIds);
                configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "false");
            }
        };

        sender.send(CODE, events(150), copy, turnsSyncOff);
        turnsSyncOff.await();

        assertEquals(1, server.getRequestCount());
        assertEquals("Sent 100 of 150. Sending stopped when online sync or the pairing changed."
            + " The rest weren't sent.", turnsSyncOff.notice);
    }

    @Test
    public void aNewPairingStopsASendForTheOldOne() throws Exception
    {
        configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, "fedcba9876543210fedcba9876543210");

        assertTrue(sender.send(CODE, events(3), copy, recorder));
        recorder.await();

        assertEquals(0, server.getRequestCount());
        assertEquals("Sending stopped when online sync or the pairing changed. Nothing was sent.", recorder.notice);
    }

    @Test
    public void eventsTheRelayWontTakeAreLeftForCopyForTracker() throws Exception
    {
        server.enqueue(new MockResponse().setBody(accepted(2)));
        List<FateEvent> events = new ArrayList<>(events(2));
        events.add(event("month-old", NOW - 31L * 24 * 60 * 60 * 1000));

        sender.send(CODE, events, copy, recorder);
        recorder.await();

        assertEquals(ids(events.subList(0, 2)), recorder.sent.get(0));
        assertEquals("Sent 2 of 3. They're waiting in your tracker's Roll Inbox. Use Copy for tracker for the rest.",
            recorder.notice);

        Recorder none = new Recorder();
        sender.send(CODE, events.subList(2, 3), copy, none);
        none.await();
        assertEquals(1, server.getRequestCount());
        assertEquals("These events can't be sent. Use Copy for tracker.", none.notice);
    }

    @Test
    public void stoppingThePluginCancelsTheSendQuietly() throws Exception
    {
        server.enqueue(new MockResponse().setBody(accepted(3)).setHeadersDelay(2, TimeUnit.SECONDS));

        sender.send(CODE, events(3), copy, recorder);
        assertTrue(sender.sending());
        sender.stop();

        assertFalse(sender.sending());
        assertFalse(recorder.finished.await(500, TimeUnit.MILLISECONDS));
        assertTrue(recorder.sent.isEmpty());
        assertFalse(sender.send(CODE, events(3), copy, new Recorder()));
    }

    private void assertAfterOneRequest(MockResponse response, String notice) throws Exception
    {
        int before = server.getRequestCount();
        server.enqueue(response);
        Recorder heard = new Recorder();
        assertTrue(sender.send(CODE, events(3), copy, heard));
        heard.await();
        assertEquals(notice, heard.notice);
        assertTrue(heard.sent.isEmpty());
        assertFalse(sender.sending());
        // Never sent again by itself.
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS));
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
        assertEquals(before + 1, server.getRequestCount());
    }

    /** The relay's reply when it stored this many of a request's events. */
    private static String accepted(int count)
    {
        return "{\"accepted\":" + count + ",\"held\":" + count + "}";
    }

    private static List<FateEvent> events(int count)
    {
        List<FateEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            events.add(event("evt-" + i, NOW - i));
        }
        return events;
    }

    private static FateEvent event(String id, long occurredAt)
    {
        return FateEvent.builder()
            .protocolVersion(1).eventId(id).runId("run-1").account("Player").runRevision(1)
            .eventType(FateEventType.QUEST).canonicalLabel("Quest " + id).occurredAt(occurredAt)
            .sessionSequence(1).bundleVersion(4).rulesVersion("1").contentVersion(1)
            .detectorId("quest-state-v1").detectorVersion(1).confidence(EventConfidence.EXACT)
            .build();
    }

    private static List<String> ids(List<FateEvent> events)
    {
        List<String> ids = new ArrayList<>();
        for (FateEvent event : events)
        {
            ids.add(event.getEventId());
        }
        return ids;
    }

    private static class Recorder implements TrackerInboxSender.Listener
    {
        final List<List<String>> sent = new CopyOnWriteArrayList<>();
        final CountDownLatch finished = new CountDownLatch(1);
        volatile String notice;
        volatile int started;

        @Override
        public void started()
        {
            assertTrue("before any request is answered", sent.isEmpty() && notice == null);
            started++;
        }

        @Override
        public void sent(List<String> eventIds)
        {
            sent.add(eventIds);
        }

        @Override
        public void finished(String notice)
        {
            this.notice = notice;
            finished.countDown();
        }

        void await() throws InterruptedException
        {
            assertTrue("the send should finish", finished.await(10, TimeUnit.SECONDS));
        }
    }
}
