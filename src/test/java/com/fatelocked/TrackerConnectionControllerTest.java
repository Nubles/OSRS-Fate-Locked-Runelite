package com.fatelocked;

import com.google.gson.Gson;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TrackerConnectionControllerTest
{
    private static final String INITIAL_CODE =
        "0123456789abcdef0123456789abcdef";

    private final Gson gson = new Gson();
    private final Map<String, String> configuration =
        new ConcurrentHashMap<>();
    private final List<String> unsetKeys = new CopyOnWriteArrayList<>();
    private final RecordingDispatcher dispatcher =
        new RecordingDispatcher();
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
        dispatcher.tasks();
    private final RecordingImporter importer = new RecordingImporter();
    private final RecordingListener listener = new RecordingListener();
    private final MutableClock clock = new MutableClock(
        Instant.parse("2026-07-27T10:00:00Z"));

    private MockWebServer server;
    private TrackerConnectionSettings settings;
    private TrackerConnectionController controller;
    private int acknowledgementCount;

    @Before
    public void setUp() throws Exception
    {
        configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY,
            INITIAL_CODE);
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfiguration(anyString(), anyString()))
            .thenAnswer(invocation ->
                configuration.get(invocation.getArgument(1)));
        doAnswer(invocation -> {
            configuration.put(
                invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(configManager).setConfiguration(
            anyString(), anyString(), anyString());
        doAnswer(invocation -> {
            String key = invocation.getArgument(1);
            configuration.remove(key);
            unsetKeys.add(key);
            return null;
        }).when(configManager).unsetConfiguration(anyString(), anyString());
        settings = new TrackerConnectionSettings(configManager);

        server = new MockWebServer();
        server.start();
        Interceptor redirectToServer = chain -> {
            Request original = chain.request();
            return chain.proceed(original.newBuilder()
                .url(server.url(original.url().encodedPath()))
                .build());
        };
        OkHttpClient http = new OkHttpClient.Builder()
            .addInterceptor(redirectToServer)
            .build();
        controller = new TrackerConnectionController(
            http, gson, settings, clock, dispatcher,
            importer, listener);
    }

    @After
    public void tearDown() throws Exception
    {
        controller.stop();
        server.shutdown();
    }

    @Test
    public void beginPairingReplacesTheCodeAndReturnsTheBrowserUrl()
    {
        String url = controller.beginPairing();

        assertTrue(settings.pairingCode().matches("[0-9a-f]{32}"));
        assertNotEquals(INITIAL_CODE, settings.pairingCode());
        assertEquals(PairingSupport.trackerPairingUrl(
            settings.pairingCode()), url);
        assertEquals(TrackerConnectionState.WAITING,
            listener.last().getState());
        assertNull(listener.last().getAcceptedVersion());
        assertNull(listener.last().getLastSync());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertEquals(0, acknowledgementCount);
    }

    @Test
    public void successfulImportAdvancesVersionAndPostsOneAck()
        throws Exception
    {
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"token\":\"state-token\"}"));

        controller.poll();
        RecordedRequest relay = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        assertEquals(TrackerConnectionState.IMPORTING,
            listener.last().getState());
        assertNull(controller.snapshot().getAcceptedVersion());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(1, dispatcher.dispatchedCount());
        assertEquals(0, dispatcher.executedCount());

        runClientTasks();
        RecordedRequest ack = takeAck();
        assertEquals(1, dispatcher.executedCount());

        assertEquals(TrackerConnectionState.CONNECTED,
            listener.last().getState());
        assertEquals("6", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals("/r/" + settings.pairingCode(), relay.getPath());
        assertEquals("/r/" + settings.pairingCode() + "/state",
            ack.getPath());
        assertEquals(0, clientTasks.size());
        assertAckVersion(ack, 6);
        assertEquals(1, acknowledgementCount);
        waitFor(() -> "state-token".equals(configuration.get(
            "stateToken." + settings.pairingCode())));
        assertEquals(3, unsetKeys.size());
        assertTrue(unsetKeys.contains("onlineSync"));
        assertTrue(unsetKeys.contains("syncCode"));
        assertTrue(unsetKeys.contains("relayUrl"));
    }

    @Test
    public void failedImportKeepsThePreviousVersionAndPostsNoAck()
        throws Exception
    {
        importer.rejectNextPayload();
        server.enqueue(relayResponse(7, "{bad", "\"7\""));

        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertEquals(TrackerConnectionState.IMPORT_FAILED,
            listener.last().getState());
        assertNull(controller.snapshot().getAcceptedVersion());
        assertNull(controller.snapshot().getLastSync());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(0, unsetKeys.size());
    }

    @Test
    public void failedReplacementImportKeepsTheAcceptedSnapshot()
        throws Exception
    {
        connect(5, "\"5\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        importer.rejectNextPayload();
        server.enqueue(relayResponse(6, "{bad", "\"6\""));

        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertEquals(TrackerConnectionState.IMPORT_FAILED,
            controller.snapshot().getState());
        assertEquals("5", controller.snapshot().getAcceptedVersion());
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(1, acknowledgementCount);
    }

    @Test
    public void externalPairingChangeInvalidatesTheOldCallbackAndUnblocksPolling()
        throws Exception
    {
        String replacementCode =
            "fedcba9876543210fedcba9876543210";
        server.enqueue(new MockResponse()
            .setResponseCode(404)
            .setHeadersDelay(300, TimeUnit.MILLISECONDS));
        server.enqueue(new MockResponse().setResponseCode(404));

        controller.poll();
        takeRelay();
        configuration.put(
            TrackerConnectionSettings.PAIRING_CODE_KEY, replacementCode);
        Thread.sleep(350);
        controller.poll();
        RecordedRequest replacement = takeRelay();
        waitFor(() -> controller.snapshot().getState()
            == TrackerConnectionState.EXPIRED);

        assertEquals("/r/" + replacementCode, replacement.getPath());
        assertEquals(replacementCode, settings.pairingCode());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(0, acknowledgementCount);
    }

    @Test
    public void configuredIdentityChangeDropsTheOldValidatorAndActivePoll()
        throws Exception
    {
        connect(5, "\"5\"");
        clock.advanceSeconds(30);
        String replacementCode =
            "fedcba9876543210fedcba9876543210";
        server.enqueue(new MockResponse()
            .setResponseCode(404)
            .setHeadersDelay(500, TimeUnit.MILLISECONDS));
        server.enqueue(relayResponse(1, validV4Payload(), "\"1\""));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        controller.poll();
        takeRelay();
        configuration.put(
            TrackerConnectionSettings.PAIRING_CODE_KEY, replacementCode);
        controller.poll();
        RecordedRequest replacement = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        RecordedRequest acknowledgement = takeAck();
        Thread.sleep(550);

        assertEquals("/r/" + replacementCode, replacement.getPath());
        assertNull(replacement.getHeader("If-None-Match"));
        assertEquals("/r/" + replacementCode + "/state",
            acknowledgement.getPath());
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("1", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(2, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertEquals(2, acknowledgementCount);
    }

    @Test
    public void staleOfflinePublicationCannotOutliveANewerWaitingState()
        throws Exception
    {
        listener.blockNext(TrackerConnectionState.OFFLINE);
        server.enqueue(new MockResponse()
            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        controller.poll();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        listener.awaitBlocked();
        Thread pairing = new Thread(controller::beginPairing);
        pairing.start();
        try
        {
            pairing.join(500);
        }
        finally
        {
            listener.releaseBlocked();
        }
        pairing.join(2_000);

        assertEquals(TrackerConnectionState.WAITING,
            controller.snapshot().getState());
        assertEquals(TrackerConnectionState.WAITING,
            listener.last().getState());
        assertTrue(settings.isPaired());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertEquals(0, acknowledgementCount);
    }

    @Test
    public void onlyOneRelayPollCanBeInFlight() throws Exception
    {
        server.enqueue(new MockResponse()
            .setResponseCode(404)
            .setHeadersDelay(300, TimeUnit.MILLISECONDS));

        controller.poll();
        controller.poll();

        takeRelay();
        assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS));
        waitFor(() -> controller.snapshot().getState()
            == TrackerConnectionState.EXPIRED);
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertEquals(0, acknowledgementCount);
    }

    @Test
    public void reconnectInvalidatesAnOlderCallback() throws Exception
    {
        server.enqueue(relayResponse(1, validV4Payload(), "\"1\"")
            .setHeadersDelay(500, TimeUnit.MILLISECONDS));
        server.enqueue(relayResponse(2, validV4Payload(), "\"2\""));
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{}"));

        controller.poll();
        RecordedRequest oldRelay = takeRelay();
        String oldCode = settings.pairingCode();
        controller.beginPairing();
        String newCode = settings.pairingCode();
        controller.poll();
        RecordedRequest newRelay = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        takeAck();
        Thread.sleep(550);

        assertNotEquals(oldCode, newCode);
        assertEquals("/r/" + oldCode, oldRelay.getPath());
        assertEquals("/r/" + newCode, newRelay.getPath());
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("2", controller.snapshot().getAcceptedVersion());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertEquals(1, acknowledgementCount);
    }

    @Test
    public void stopInvalidatesAQueuedClientThreadCommit() throws Exception
    {
        server.enqueue(relayResponse(2, validV4Payload(), "\"2\""));

        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        controller.stop();
        runClientTasks();

        assertEquals(TrackerConnectionState.DISCONNECTED,
            controller.snapshot().getState());
        assertNull(controller.snapshot().getAcceptedVersion());
        assertEquals(INITIAL_CODE, settings.pairingCode());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
    }

    @Test
    public void pairingReplacementInvalidatesAQueuedClientThreadCommit()
        throws Exception
    {
        server.enqueue(relayResponse(2, validV4Payload(), "\"2\""));

        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        controller.beginPairing();
        runClientTasks();

        assertEquals(TrackerConnectionState.WAITING,
            controller.snapshot().getState());
        assertNull(controller.snapshot().getAcceptedVersion());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
    }

    @Test
    public void queuedPairingCannotOvertakeABlockingClientThreadImport()
        throws Exception
    {
        importer.blockNextPayload();
        server.enqueue(relayResponse(2, validV4Payload(), "\"2\""));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        Runnable importTask = clientTasks.poll();
        Thread clientThread = new Thread(importTask);
        clientThread.start();
        importer.awaitBlocked();

        dispatcher.accept(controller::beginPairing);
        assertEquals(INITIAL_CODE, settings.pairingCode());
        assertEquals(1, clientTasks.size());

        importer.releaseBlocked();
        clientThread.join(2_000);
        assertTrue(!clientThread.isAlive());
        takeAck();
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals(1, importer.acceptedPayloads().size());

        runClientTasks();

        assertNotEquals(INITIAL_CODE, settings.pairingCode());
        assertEquals(TrackerConnectionState.WAITING,
            controller.snapshot().getState());
        List<TrackerConnectionState> states = listener.states();
        int connected = states.indexOf(TrackerConnectionState.CONNECTED);
        int waiting = states.lastIndexOf(TrackerConnectionState.WAITING);
        assertTrue(connected >= 0);
        assertTrue(waiting > connected);
        assertEquals(0, clientTasks.size());
        assertEquals(1, acknowledgementCount);
    }

    @Test
    public void responseEtagMustMatchTheBodyVersion() throws Exception
    {
        connect(5, "\"5\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        int imports = importer.acceptedPayloads().size();
        int acknowledgements = acknowledgementCount;

        int[][] mismatches = {{4, 6}, {6, 4}};
        for (int[] mismatch : mismatches)
        {
            server.enqueue(relayResponse(
                mismatch[1], validV4Payload(),
                "\"" + mismatch[0] + "\""));
            pollUntilRelay();
        }
        Thread.sleep(50);

        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("5", controller.snapshot().getAcceptedVersion());
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        assertEquals(imports, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(acknowledgements, acknowledgementCount);
    }

    @Test
    public void olderEqualAndMalformedVersionsAreRejected() throws Exception
    {
        connect(5, "\"5\"");
        int imports = importer.acceptedPayloads().size();
        int acks = acknowledgementCount;

        MockResponse[] invalid = {
            relayResponse(4, validV4Payload(), "\"4\""),
            relayResponse(5, validV4Payload(), "W/\"5\""),
            relayResponse(6, validV4Payload(), null),
            relayResponse(6, validV4Payload(), "not-a-version"),
            relayResponse(6, validV4Payload(), "\"bad\""),
            relayResponse(6, validV4Payload(), "\"06\""),
            relayResponse(0, validV4Payload(), "\"1\"")
        };
        for (MockResponse response : invalid)
        {
            server.enqueue(response);
            pollUntilRelay();
        }
        Thread.sleep(50);

        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("5", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(imports, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(acks, acknowledgementCount);
    }

    @Test
    public void canonicalWeakEtagRevalidatesThrough304() throws Exception
    {
        connect(6, "W/\"6\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        clock.advanceSeconds(30);
        server.enqueue(new MockResponse()
            .setResponseCode(304)
            .addHeader("ETag", "W/\"6\""));

        controller.poll();
        RecordedRequest revalidation = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        runClientTasks();

        assertEquals("6", revalidation.getHeader("If-None-Match"));
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("6", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(1, acknowledgementCount);
    }

    @Test
    public void mismatched304DoesNotRefreshFreshness() throws Exception
    {
        connect(6, "\"6\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        clock.advanceSeconds(30);
        server.enqueue(new MockResponse()
            .setResponseCode(304)
            .addHeader("ETag", "\"7\""));

        controller.poll();
        takeRelay();
        Thread.sleep(50);

        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("6", controller.snapshot().getAcceptedVersion());
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(1, acknowledgementCount);
    }

    @Test
    public void stale304AfterSessionInvalidationCannotRefreshFreshness()
        throws Exception
    {
        connect(6, "\"6\"");
        clock.advanceSeconds(30);
        server.enqueue(new MockResponse()
            .setResponseCode(304)
            .addHeader("ETag", "\"6\""));

        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        controller.beginPairing();
        runClientTasks();

        assertEquals(TrackerConnectionState.WAITING,
            controller.snapshot().getState());
        assertNull(controller.snapshot().getAcceptedVersion());
        assertNull(controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(1, acknowledgementCount);
    }

    @Test
    public void rejectedOldResponseCannotClearANewerInFlightToken()
        throws Exception
    {
        server.enqueue(relayResponse(0, validV4Payload(), "\"1\"")
            .setHeadersDelay(300, TimeUnit.MILLISECONDS));
        server.enqueue(relayResponse(2, validV4Payload(), "\"2\"")
            .setHeadersDelay(800, TimeUnit.MILLISECONDS));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        controller.poll();
        takeRelay();
        controller.beginPairing();
        controller.poll();
        takeRelay();
        Thread.sleep(450);
        controller.poll();
        assertNull(server.takeRequest(150, TimeUnit.MILLISECONDS));
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        takeAck();

        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("2", controller.snapshot().getAcceptedVersion());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertEquals(1, acknowledgementCount);
    }

    @Test
    public void networkFailurePublishesOfflineWithoutClearingPairing()
        throws Exception
    {
        server.enqueue(new MockResponse()
            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        controller.poll();
        assertNotNull(
            server.takeRequest(2, TimeUnit.SECONDS));
        waitFor(() -> controller.snapshot().getState()
            == TrackerConnectionState.OFFLINE);

        assertEquals(INITIAL_CODE, settings.pairingCode());
        assertNull(controller.snapshot().getAcceptedVersion());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
    }

    @Test
    public void notFoundPublishesExpiredWithoutReplacingTheBundle()
        throws Exception
    {
        connect(5, "\"5\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        server.enqueue(new MockResponse().setResponseCode(404));

        controller.poll();
        takeRelay();
        waitFor(() -> controller.snapshot().getState()
            == TrackerConnectionState.EXPIRED);

        assertEquals(INITIAL_CODE, settings.pairingCode());
        assertEquals("5", controller.snapshot().getAcceptedVersion());
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
        assertEquals(1, acknowledgementCount);
    }

    @Test
    public void browserLaunchFailureKeepsThePairingRequestRetryable()
        throws Exception
    {
        controller.beginPairing();
        String code = settings.pairingCode();

        controller.reportBrowserLaunchFailure();

        assertEquals(TrackerConnectionState.OFFLINE,
            controller.snapshot().getState());
        assertEquals("Could not open the web tracker",
            controller.snapshot().getMessage());
        assertEquals(code, settings.pairingCode());
        server.enqueue(new MockResponse().setResponseCode(404));
        controller.poll();
        RecordedRequest retry = takeRelay();
        waitFor(() -> controller.snapshot().getState()
            == TrackerConnectionState.EXPIRED);
        assertEquals("/r/" + code, retry.getPath());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoAck();
    }

    @Test
    public void legacySettingsAreClearedOncePerSuccessfulPairingIdentity()
        throws Exception
    {
        connect(1, "\"1\"");
        connect(2, "\"2\"");
        assertEquals(3, unsetKeys.size());

        controller.beginPairing();
        connect(1, "\"1\"");

        assertEquals(6, unsetKeys.size());
        assertEquals(3, importer.acceptedPayloads().size());
        assertEquals(3, acknowledgementCount);
        assertEquals(0, clientTasks.size());
    }

    private void connect(int version, String etag) throws Exception
    {
        server.enqueue(relayResponse(version, validV4Payload(), etag));
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{}"));
        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        takeAck();
    }

    private RecordedRequest takeRelay() throws Exception
    {
        RecordedRequest request =
            server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        return request;
    }

    private RecordedRequest takeAck() throws Exception
    {
        RecordedRequest request =
            server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        acknowledgementCount++;
        return request;
    }

    private void assertAckVersion(
        RecordedRequest acknowledgement, int expectedVersion)
    {
        Map<?, ?> outer = gson.fromJson(
            acknowledgement.getBody().readUtf8(), Map.class);
        Map<?, ?> payload = gson.fromJson(
            (String) outer.get("payload"), Map.class);
        assertEquals(expectedVersion,
            ((Number) payload.get("version")).intValue());
    }

    private void assertNoAck() throws Exception
    {
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
    }

    private RecordedRequest pollUntilRelay() throws Exception
    {
        for (int attempt = 0; attempt < 100; attempt++)
        {
            controller.poll();
            RecordedRequest next =
                server.takeRequest(20, TimeUnit.MILLISECONDS);
            if (next != null)
            {
                assertEquals("GET", next.getMethod());
                return next;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("poll token was not cleared");
    }

    private void runClientTasks()
    {
        Runnable task;
        while ((task = clientTasks.poll()) != null)
        {
            task.run();
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception
    {
        for (int attempt = 0; attempt < 200; attempt++)
        {
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("condition was not reached");
    }

    private MockResponse relayResponse(
        int version, String payload, String etag)
    {
        MockResponse response = new MockResponse()
            .setResponseCode(200)
            .setBody(gson.toJson(new RelayEnvelope(version, payload)));
        if (etag != null) response.addHeader("ETag", etag);
        return response;
    }

    private String validV4Payload() throws IOException
    {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("bundles/v4-rules.json"))
        {
            if (input == null) throw new IOException("Missing v4 fixture");
            return new String(
                input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class RelayEnvelope
    {
        private final int version;
        private final String payload;

        private RelayEnvelope(int version, String payload)
        {
            this.version = version;
            this.payload = payload;
        }
    }

    private static final class RecordingImporter
        implements TrackerConnectionController.RelayBundleImporter
    {
        private final List<String> accepted = new ArrayList<>();
        private boolean rejectNext;
        private volatile CountDownLatch blocked;
        private volatile CountDownLatch release;

        @Override
        public boolean importBundle(String payload)
        {
            CountDownLatch currentBlock = blocked;
            if (currentBlock != null)
            {
                currentBlock.countDown();
                try
                {
                    if (!release.await(2, TimeUnit.SECONDS))
                    {
                        throw new AssertionError("import was not released");
                    }
                }
                catch (InterruptedException error)
                {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
                blocked = null;
            }
            if (rejectNext)
            {
                rejectNext = false;
                return false;
            }
            accepted.add(payload);
            return true;
        }

        void rejectNextPayload()
        {
            rejectNext = true;
        }

        void blockNextPayload()
        {
            blocked = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        void awaitBlocked() throws Exception
        {
            assertTrue("import did not block",
                blocked.await(2, TimeUnit.SECONDS));
        }

        void releaseBlocked()
        {
            release.countDown();
        }

        List<String> acceptedPayloads()
        {
            return accepted;
        }
    }

    private static final class RecordingDispatcher
        implements Consumer<Runnable>
    {
        private final ConcurrentLinkedQueue<Runnable> tasks =
            new ConcurrentLinkedQueue<>();
        private final AtomicInteger dispatched = new AtomicInteger();
        private final AtomicInteger executed = new AtomicInteger();

        @Override
        public void accept(Runnable task)
        {
            dispatched.incrementAndGet();
            tasks.add(() -> {
                executed.incrementAndGet();
                task.run();
            });
        }

        ConcurrentLinkedQueue<Runnable> tasks()
        {
            return tasks;
        }

        int dispatchedCount()
        {
            return dispatched.get();
        }

        int executedCount()
        {
            return executed.get();
        }
    }

    private static final class RecordingListener
        implements Consumer<TrackerConnectionSnapshot>
    {
        private final List<TrackerConnectionSnapshot> snapshots =
            new CopyOnWriteArrayList<>();
        private volatile TrackerConnectionState blockedState;
        private volatile CountDownLatch blocked;
        private volatile CountDownLatch release;

        @Override
        public void accept(TrackerConnectionSnapshot snapshot)
        {
            if (snapshot.getState() == blockedState)
            {
                blockedState = null;
                blocked.countDown();
                try
                {
                    if (!release.await(2, TimeUnit.SECONDS))
                    {
                        throw new AssertionError(
                            "listener publication was not released");
                    }
                }
                catch (InterruptedException error)
                {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            }
            snapshots.add(snapshot);
        }

        List<TrackerConnectionState> states()
        {
            List<TrackerConnectionState> states = new ArrayList<>();
            for (TrackerConnectionSnapshot snapshot : snapshots)
            {
                states.add(snapshot.getState());
            }
            return states;
        }

        void blockNext(TrackerConnectionState state)
        {
            blockedState = state;
            blocked = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        void awaitBlocked() throws Exception
        {
            assertTrue("listener did not block",
                blocked.await(2, TimeUnit.SECONDS));
        }

        void releaseBlocked()
        {
            release.countDown();
        }

        TrackerConnectionSnapshot last()
        {
            return snapshots.get(snapshots.size() - 1);
        }
    }

    private static final class MutableClock extends Clock
    {
        private Instant instant;

        private MutableClock(Instant instant)
        {
            this.instant = instant;
        }

        void advanceSeconds(long seconds)
        {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return instant;
        }
    }
}
