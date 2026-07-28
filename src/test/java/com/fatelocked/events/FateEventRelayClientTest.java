package com.fatelocked.events;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class FateEventRelayClientTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    private MockWebServer server;
    private FateEventOutbox outbox;
    private Map<String, String> tokens;
    private AtomicReference<String> pairingCode;

    @Before
    public void setUp() throws Exception
    {
        server = new MockWebServer();
        server.start();
        Path path = folder.getRoot().toPath().resolve("outbox.json");
        outbox = new FateEventOutbox(gson, path);
        outbox.enqueue(event("evt-1"));
        tokens = new ConcurrentHashMap<>();
        pairingCode = new AtomicReference<>("ABCD");
    }

    @After
    public void tearDown() throws Exception
    {
        server.shutdown();
    }

    private FateEvent event(String eventId)
    {
        return FateEvent.builder()
            .protocolVersion(1)
            .eventId(eventId)
            .runId("run-1")
            .account("Nubles")
            .runRevision(1)
            .eventType(FateEventType.QUEST)
            .canonicalLabel("Dragon Slayer")
            .occurredAt(System.currentTimeMillis())
            .sessionSequence(1)
            .bundleVersion(3)
            .rulesVersion("1")
            .contentVersion(1)
            .detectorId("quest-widget-v1")
            .detectorVersion(1)
            .confidence(EventConfidence.EXACT)
            .evidence(Collections.<String, Object>emptyMap())
            .build();
    }

    private FateEventRelayClient client()
    {
        return client(Runnable::run);
    }

    private FateEventRelayClient client(Consumer<Runnable> dispatcher)
    {
        return new FateEventRelayClient(
            new OkHttpClient(), gson, () -> true, pairingCode::get,
            dispatcher,
            new FateEventRelayClient.TokenStore()
            {
                @Override
                public String get(String key)
                {
                    return tokens.get(key);
                }

                @Override
                public void put(String key, String value)
                {
                    tokens.put(key, value);
                }
            });
    }

    @Test
    public void flushSendsEnvelopeAndRetainsUntilAcknowledged() throws Exception
    {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"version\":1,\"token\":\"tok\",\"accepted\":[\"evt-1\"],\"duplicates\":[]}"));

        client().flush(server.url("/").toString(), "ABCD", outbox);

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/r/ABCD/events", request.getPath());
        assertEquals("evt-1", gson.fromJson(request.getBody().readUtf8(), com.google.gson.JsonObject.class)
            .getAsJsonArray("events").get(0).getAsJsonObject()
            .get("eventId").getAsString());
        assertTrue(outbox.contains("evt-1"));
    }

    @Test
    public void failedSendCanRetryTheSameOccurrenceId() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(500));
        client().flush(server.url("/").toString(), "ABCD", outbox);
        RecordedRequest failed = server.takeRequest(2, TimeUnit.SECONDS);

        server.enqueue(new MockResponse().setResponseCode(200)
            .setBody("{\"version\":2,\"token\":\"tok\",\"accepted\":[\"evt-1\"],\"duplicates\":[]}"));
        client().flush(server.url("/").toString(), "ABCD", outbox);
        RecordedRequest retry = server.takeRequest(2, TimeUnit.SECONDS);

        String firstId = gson.fromJson(failed.getBody().readUtf8(), com.google.gson.JsonObject.class)
            .getAsJsonArray("events").get(0).getAsJsonObject().get("eventId").getAsString();
        String retryId = gson.fromJson(retry.getBody().readUtf8(), com.google.gson.JsonObject.class)
            .getAsJsonArray("events").get(0).getAsJsonObject().get("eventId").getAsString();
        assertEquals(firstId, retryId);
    }

    @Test
    public void acknowledgementRemovesTerminalEvent() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(200)
            .setBody("{\"version\":1,\"acknowledgements\":["
                + "{\"eventId\":\"evt-1\",\"state\":\"COMPLETED\",\"acknowledgedAt\":1}]}"));

        client().pollAcknowledgements(server.url("/").toString(), "ABCD", outbox);
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        for (int attempt = 0; attempt < 40 && outbox.contains("evt-1"); attempt++)
        {
            Thread.sleep(25);
        }

        assertTrue(!outbox.contains("evt-1"));
    }
    @Test
    public void disabledSyncMakesNoNetworkRequest() throws Exception
    {
        FateEventRelayClient disabled = new FateEventRelayClient(
            new OkHttpClient(), gson, () -> false, pairingCode::get,
            Runnable::run,
            new FateEventRelayClient.TokenStore()
            {
                @Override
                public String get(String key) { return null; }

                @Override
                public void put(String key, String value) { }
            });

        disabled.flush(server.url("/").toString(), "ABCD", outbox);
        disabled.pollAcknowledgements(server.url("/").toString(), "ABCD", outbox);

        assertEquals(null, server.takeRequest(200, TimeUnit.MILLISECONDS));
        assertTrue(outbox.contains("evt-1"));
    }

    @Test
    public void publicPairingSupplierConstructorControlsEventTraffic() throws Exception
    {
        AtomicBoolean paired = new AtomicBoolean(false);
        ConfigManager configManager = mock(ConfigManager.class);
        FateEventRelayClient relay = new FateEventRelayClient(
            new OkHttpClient(), gson, configManager, paired::get,
            pairingCode::get, Runnable::run);

        relay.flush(server.url("/").toString(), "ABCD", outbox);
        assertEquals(null, server.takeRequest(200, TimeUnit.MILLISECONDS));

        paired.set(true);
        relay.flush(server.url("/").toString(), "ABCD", outbox);
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
    }

    @Test
    public void delayedFlushFromReplacedPairingCannotPersistTokenOrResetRetry()
        throws Exception
    {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.setDispatcher(blockingDispatcher(
            requestReceived, releaseResponse,
            new MockResponse().setResponseCode(200)
                .setBody("{\"token\":\"pair-a-token\"}")));
        FateEventRelayClient relay = client();
        setInt(relay, "failureCount", 1);

        relay.flush(server.url("/").toString(), "ABCD", outbox);
        assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
        pairingCode.set("EFGH");
        releaseResponse.countDown();
        waitFor(() -> !inFlight(relay, "flushInFlight", "ABCD"));

        assertEquals(null, tokens.get("eventToken.ABCD"));
        assertEquals(1, getInt(relay, "failureCount"));
    }

    @Test
    public void delayedAcknowledgementFromReplacedPairingCannotRemoveOutboxEvent()
        throws Exception
    {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        server.setDispatcher(blockingDispatcher(
            requestReceived, releaseResponse,
            new MockResponse().setResponseCode(200)
                .setBody("{\"acknowledgements\":["
                    + "{\"eventId\":\"evt-1\",\"state\":\"COMPLETED\"}]}")));
        FateEventRelayClient relay = client();

        relay.pollAcknowledgements(
            server.url("/").toString(), "ABCD", outbox);
        assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
        pairingCode.set("EFGH");
        releaseResponse.countDown();
        waitFor(() -> !inFlight(
            relay, "acknowledgementInFlight", "ABCD"));

        assertTrue(outbox.contains("evt-1"));
    }

    @Test
    public void replacementFlushStartsBeforePreviousPairingCompletes()
        throws Exception
    {
        CountDownLatch firstReceived = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch replacementReceived = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
                throws InterruptedException
            {
                if (request.getPath().contains("/ABCD/"))
                {
                    firstReceived.countDown();
                    releaseFirst.await(2, TimeUnit.SECONDS);
                    return new MockResponse().setResponseCode(200)
                        .setBody("{\"token\":\"pair-a-token\"}");
                }
                replacementReceived.countDown();
                return new MockResponse().setResponseCode(200)
                    .setBody("{\"token\":\"pair-b-token\"}");
            }
        });
        FateEventRelayClient relay = client();

        relay.flush(server.url("/").toString(), "ABCD", outbox);
        assertTrue(firstReceived.await(2, TimeUnit.SECONDS));
        pairingCode.set("EFGH");
        relay.flush(server.url("/").toString(), "EFGH", outbox);
        boolean replacementStarted;
        try
        {
            replacementStarted =
                replacementReceived.await(2, TimeUnit.SECONDS);
        }
        finally
        {
            releaseFirst.countDown();
        }

        assertTrue(replacementStarted);
        waitFor(() -> "pair-b-token".equals(
            tokens.get("eventToken.EFGH")));
        waitFor(() -> !inFlight(relay, "flushInFlight", "ABCD")
            && !inFlight(relay, "flushInFlight", "EFGH"));
        assertEquals(null, tokens.get("eventToken.ABCD"));
    }

    @Test
    public void replacementAcknowledgementStartsBeforePreviousPairingCompletes()
        throws Exception
    {
        CountDownLatch firstReceived = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch replacementReceived = new CountDownLatch(1);
        server.setDispatcher(new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
                throws InterruptedException
            {
                if (request.getPath().contains("/ABCD/"))
                {
                    firstReceived.countDown();
                    releaseFirst.await(2, TimeUnit.SECONDS);
                    return new MockResponse().setResponseCode(200)
                        .setBody("{\"acknowledgements\":["
                            + "{\"eventId\":\"evt-1\","
                            + "\"state\":\"COMPLETED\"}]}");
                }
                replacementReceived.countDown();
                return new MockResponse().setResponseCode(200)
                    .setBody("{\"acknowledgements\":[]}");
            }
        });
        FateEventRelayClient relay = client();

        relay.pollAcknowledgements(
            server.url("/").toString(), "ABCD", outbox);
        assertTrue(firstReceived.await(2, TimeUnit.SECONDS));
        pairingCode.set("EFGH");
        relay.pollAcknowledgements(
            server.url("/").toString(), "EFGH", outbox);
        boolean replacementStarted;
        try
        {
            replacementStarted =
                replacementReceived.await(2, TimeUnit.SECONDS);
        }
        finally
        {
            releaseFirst.countDown();
        }

        assertTrue(replacementStarted);
        waitFor(() -> !inFlight(
            relay, "acknowledgementInFlight", "ABCD")
            && !inFlight(relay, "acknowledgementInFlight", "EFGH"));
        assertTrue(outbox.contains("evt-1"));
    }

    @Test
    public void replacementPairingIsNotBlockedByPreviousPairingBackoff()
        throws Exception
    {
        FateEventRelayClient relay = client();
        server.enqueue(new MockResponse().setResponseCode(500));

        relay.flush(server.url("/").toString(), "ABCD", outbox);
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        waitFor(() -> getInt(relay, "failureCount") == 1
            && !inFlight(relay, "flushInFlight", "ABCD"));

        pairingCode.set("EFGH");
        server.enqueue(new MockResponse().setResponseCode(200)
            .setBody("{\"token\":\"pair-b-token\"}"));
        relay.flush(server.url("/").toString(), "EFGH", outbox);
        RecordedRequest replacement =
            server.takeRequest(2, TimeUnit.SECONDS);

        assertNotNull(replacement);
        assertEquals("/r/EFGH/events", replacement.getPath());
    }

    @Test
    public void queuedMutationRechecksIdentityOnSerializedBoundary()
        throws Exception
    {
        ConcurrentLinkedQueue<Runnable> mutations =
            new ConcurrentLinkedQueue<>();
        FateEventRelayClient relay = client(mutations::add);
        setInt(relay, "failureCount", 1);
        server.enqueue(new MockResponse().setResponseCode(200)
            .setBody("{\"token\":\"pair-a-token\"}"));

        relay.flush(server.url("/").toString(), "ABCD", outbox);
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        waitFor(() -> mutations.size() == 1);
        assertTrue(inFlight(relay, "flushInFlight", "ABCD"));

        pairingCode.set("EFGH");
        mutations.remove().run();

        assertEquals(null, tokens.get("eventToken.ABCD"));
        assertEquals(1, getInt(relay, "failureCount"));
        assertTrue(!inFlight(relay, "flushInFlight", "ABCD"));
    }

    private static Dispatcher blockingDispatcher(
        CountDownLatch requestReceived,
        CountDownLatch releaseResponse,
        MockResponse response)
    {
        return new Dispatcher()
        {
            @Override
            public MockResponse dispatch(RecordedRequest request)
                throws InterruptedException
            {
                requestReceived.countDown();
                if (!releaseResponse.await(2, TimeUnit.SECONDS))
                {
                    return new MockResponse().setResponseCode(500);
                }
                return response;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static boolean inFlight(
        FateEventRelayClient relay, String field, String code)
        throws Exception
    {
        return ((Set<String>) getField(relay, field)).contains(code);
    }

    private static void setInt(
        FateEventRelayClient relay, String field, int value) throws Exception
    {
        Field declared = FateEventRelayClient.class.getDeclaredField(field);
        declared.setAccessible(true);
        declared.setInt(relay, value);
    }

    private static int getInt(
        FateEventRelayClient relay, String field) throws Exception
    {
        return (Integer) getField(relay, field);
    }

    private static Object getField(
        FateEventRelayClient relay, String field) throws Exception
    {
        Field declared = FateEventRelayClient.class.getDeclaredField(field);
        declared.setAccessible(true);
        return declared.get(relay);
    }

    private static void waitFor(CheckedCondition condition) throws Exception
    {
        for (int attempt = 0; attempt < 200; attempt++)
        {
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("condition was not reached");
    }

    private interface CheckedCondition
    {
        boolean getAsBoolean() throws Exception;
    }
}
