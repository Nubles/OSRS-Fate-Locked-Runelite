package com.fatelocked;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.EventListener;
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
import java.time.Duration;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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
    private final List<Request> pluginRequests = new CopyOnWriteArrayList<>();
    /** Every relay request, and those that have ended, however they ended. */
    private final List<Call> sentCalls = new CopyOnWriteArrayList<>();
    private final List<Call> endedCalls = new CopyOnWriteArrayList<>();
    private final RecordingDispatcher dispatcher =
        new RecordingDispatcher();
    private final ConcurrentLinkedQueue<Runnable> clientTasks =
        dispatcher.tasks();
    private final RecordingImporter importer = new RecordingImporter();
    private final RecordingListener listener = new RecordingListener();
    private final MutableClock clock = new MutableClock(
        Instant.parse("2026-07-27T10:00:00Z"));

    private MockWebServer server;
    private OkHttpClient http;
    private TrackerConnectionSettings settings;
    private TrackerConnectionController controller;

    @Before
    public void setUp() throws Exception
    {
        configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY,
            INITIAL_CODE);
        configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "true");
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
            pluginRequests.add(original);
            return chain.proceed(original.newBuilder()
                .url(server.url(original.url().encodedPath()))
                .build());
        };
        http = new OkHttpClient.Builder()
            .addInterceptor(redirectToServer)
            .eventListenerFactory(call -> {
                sentCalls.add(call);
                return new EventListener()
                {
                    @Override
                    public void callEnd(Call ended)
                    {
                        endedCalls.add(ended);
                    }

                    @Override
                    public void callFailed(Call failed, IOException error)
                    {
                        endedCalls.add(failed);
                    }
                };
            })
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
    public void savedPairingAndLegacyOptInCannotSendRequestsWithoutNewConsent()
        throws Exception
    {
        configuration.remove(FateLockedConfig.NETWORK_ACCESS_KEY);
        configuration.put("onlineSync", "true");

        controller.poll();
        controller.pollIfDue();

        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
        assertTrue(pluginRequests.isEmpty());
        assertEquals(INITIAL_CODE, settings.pairingCode());
        assertEquals(TrackerConnectionState.DISCONNECTED, controller.snapshot().getState());
    }

    @Test(expected = IllegalStateException.class)
    public void pairingCannotBypassConsent()
    {
        configuration.remove(FateLockedConfig.NETWORK_ACCESS_KEY);
        controller.beginPairing();
    }

    @Test
    public void aStoppedControllerRefusesToPair() throws Exception
    {
        controller.stop();

        // A Connect queued before the plugin was turned off runs after it.
        assertThrows(IllegalStateException.class, controller::beginPairing);
        controller.pollIfDue();

        assertEquals(INITIAL_CODE, settings.pairingCode());
        assertEquals(TrackerConnectionState.DISCONNECTED,
            controller.snapshot().getState());
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
        assertTrue(pluginRequests.isEmpty());
    }

    @Test
    public void acceptingConsentResumesAnExistingPairing() throws Exception
    {
        configuration.remove(FateLockedConfig.NETWORK_ACCESS_KEY);
        controller.pollIfDue();
        settings.allowNetworkAccess();
        controller.networkAccessChanged();
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));

        controller.pollIfDue();
        assertEquals("/r/" + INITIAL_CODE, takeRelay().getPath());
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
    }

    @Test
    public void revokingConsentBlocksRequestsAndDiscardsQueuedImports() throws Exception
    {
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);

        configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "false");
        controller.networkAccessChanged();
        controller.poll();
        controller.pollIfDue();
        // Re-enabling must not revive a response from before revocation.
        settings.allowNetworkAccess();
        controller.networkAccessChanged();
        runClientTasks();

        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
        assertTrue(importer.acceptedPayloads().isEmpty());
        assertNull(controller.snapshot().getLastSync());
        assertNull(controller.snapshot().getAcceptedVersion());
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
    }

    @Test
    public void successfulImportAdvancesVersionWithOneFixedGet()
        throws Exception
    {
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));

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
        assertEquals(1, dispatcher.executedCount());

        assertEquals(TrackerConnectionState.CONNECTED,
            listener.last().getState());
        assertEquals("6", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals("/r/" + settings.pairingCode(), relay.getPath());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
        assertEquals(1, pluginRequests.size());
        Request request = pluginRequests.get(0);
        assertEquals("GET", request.method());
        assertEquals("https", request.url().scheme());
        assertEquals("fate-relay.fatelocked.workers.dev",
            request.url().host());
        assertEquals("/r/" + settings.pairingCode(),
            request.url().encodedPath());
        assertNull(request.body());
        assertEquals(0, unsetKeys.size());
    }

    @Test
    public void thePayloadIsParsedBeforeTheClientThreadIsAsked()
        throws Exception
    {
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));

        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);

        // The reply's own thread prepared it; the client thread only commits.
        List<String> preparedOn = importer.preparedOnThreads();
        assertEquals(1, preparedOn.size());
        assertNotEquals(Thread.currentThread().getName(), preparedOn.get(0));
        assertEquals(0, importer.acceptedPayloads().size());

        runClientTasks();
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(1, importer.preparedOnThreads().size());
    }

    @Test
    public void aPayloadThatCannotBeParsedFailsWithoutTheClientThread()
        throws Exception
    {
        importer.failToPrepareNextPayload();
        server.enqueue(relayResponse(7, "{bad", "\"7\""));

        controller.poll();
        takeRelay();
        waitFor(() -> listener.last().getState()
            == TrackerConnectionState.IMPORT_FAILED);

        assertEquals(0, dispatcher.dispatchedCount());
        assertEquals(0, clientTasks.size());
        assertNull(controller.snapshot().getAcceptedVersion());
        assertEquals(0, importer.acceptedPayloads().size());
        waitFor(() -> !controller.pollInFlight());
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
        assertNoFurtherRequest();
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
        assertNoFurtherRequest();
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
        waitFor(() -> SyncReason.NO_RECENT_UPDATE.status
            .equals(controller.snapshot().getMessage()));

        assertEquals("/r/" + replacementCode, replacement.getPath());
        assertEquals(replacementCode, settings.pairingCode());
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
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

        controller.poll();
        takeRelay();
        configuration.put(
            TrackerConnectionSettings.PAIRING_CODE_KEY, replacementCode);
        controller.poll();
        RecordedRequest replacement = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        Thread.sleep(550);

        assertEquals("/r/" + replacementCode, replacement.getPath());
        assertNull(replacement.getHeader("If-None-Match"));
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("1", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(2, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
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
        waitFor(() -> SyncReason.NO_RECENT_UPDATE.status
            .equals(controller.snapshot().getMessage()));
        assertEquals(0, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
    }

    @Test
    public void reconnectInvalidatesAnOlderCallback() throws Exception
    {
        server.enqueue(relayResponse(1, validV4Payload(), "\"1\"")
            .setHeadersDelay(500, TimeUnit.MILLISECONDS));
        server.enqueue(relayResponse(2, validV4Payload(), "\"2\""));

        controller.poll();
        RecordedRequest oldRelay = takeRelay();
        String oldCode = settings.pairingCode();
        controller.beginPairing();
        String newCode = settings.pairingCode();
        controller.poll();
        RecordedRequest newRelay = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        Thread.sleep(550);

        assertNotEquals(oldCode, newCode);
        assertEquals("/r/" + oldCode, oldRelay.getPath());
        assertEquals("/r/" + newCode, newRelay.getPath());
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("2", controller.snapshot().getAcceptedVersion());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
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
        assertNoFurtherRequest();
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
        assertNoFurtherRequest();
    }

    @Test
    public void queuedPairingCannotOvertakeABlockingClientThreadImport()
        throws Exception
    {
        importer.blockNextPayload();
        server.enqueue(relayResponse(2, validV4Payload(), "\"2\""));

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
        assertNoFurtherRequest();
    }

    @Test
    public void responseEtagMustMatchTheBodyVersion() throws Exception
    {
        connect(5, "\"5\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        int imports = importer.acceptedPayloads().size();

        int[][] mismatches = {{4, 6}, {6, 4}};
        for (int[] mismatch : mismatches)
        {
            server.enqueue(relayResponse(
                mismatch[1], validV4Payload(),
                "\"" + mismatch[0] + "\""));
            pollUntilRelay();
        }
        Thread.sleep(50);

        // Unreadable, and said so; the rules and their version stay.
        assertEquals(TrackerConnectionState.OFFLINE,
            controller.snapshot().getState());
        assertEquals(SyncReason.UNREADABLE.status,
            controller.snapshot().getMessage());
        assertEquals("5", controller.snapshot().getAcceptedVersion());
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        assertEquals(imports, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
    }

    @Test
    public void responseWithoutEtagUsesEnvelopeVersion() throws Exception
    {
        server.enqueue(relayResponse(6, validV4Payload(), null));

        controller.poll();
        RecordedRequest request = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertNull(request.getHeader("If-None-Match"));
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("6", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
    }

    @Test
    public void olderAndMalformedVersionsAreNeverImported() throws Exception
    {
        connect(5, "\"5\"");
        int imports = importer.acceptedPayloads().size();

        MockResponse[] invalid = {
            relayResponse(4, validV4Payload(), "\"4\""),
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

        assertEquals("5", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(imports, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
    }

    @Test
    public void theHeldVersionSentInFullCountsAsACheck() throws Exception
    {
        connect(5, "\"5\"");
        int prepared = importer.preparedOnThreads().size();

        // Something in front of the relay drops If-None-Match and sends the
        // rules the plugin holds in full.
        clock.advanceSeconds(SyncMachine.CONNECTED_POLL_SECONDS);
        server.enqueue(relayResponse(5, validV4Payload(), "W/\"5\""));
        controller.pollIfDue();
        assertEquals("5", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(prepared, importer.preparedOnThreads().size());
        // Checked again after the usual minute, not after a back-off.
        clock.advanceSeconds(SyncMachine.CONNECTED_POLL_SECONDS - 1);
        controller.pollIfDue();
        assertNoFurtherRequest();
        clock.advanceSeconds(1);
        server.enqueue(new MockResponse().setResponseCode(304));
        controller.pollIfDue();
        takeRelay();
    }

    @Test
    public void aVersionThatCannotBeImportedIsNotDownloadedAgain() throws Exception
    {
        connect(5, "\"5\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        // Say the tracker publishes a bundle this plugin can't read yet.
        importer.failToPrepareNextPayload();
        clock.advanceSeconds(SyncMachine.CONNECTED_POLL_SECONDS);
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
        controller.pollIfDue();
        assertEquals("5", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> controller.snapshot().getState()
            == TrackerConnectionState.IMPORT_FAILED);
        waitFor(() -> !controller.pollInFlight());

        // The next check asks only whether something newer has arrived.
        clock.advanceSeconds(SyncMachine.FAILURE_BACKOFF_SECONDS);
        server.enqueue(new MockResponse().setResponseCode(304).addHeader("ETag", "6"));
        controller.pollIfDue();
        assertEquals("6", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> !controller.pollInFlight());

        assertEquals(TrackerConnectionState.IMPORT_FAILED, controller.snapshot().getState());
        assertEquals("5", controller.snapshot().getAcceptedVersion());
        // Not confirmed: the relay has newer rules the plugin can't use.
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        assertEquals(2, importer.preparedOnThreads().size());
    }

    @Test
    public void aNewerBundleFormatSaysToUpdateThePluginUntilSomethingElseArrives()
        throws Exception
    {
        connect(5, "\"5\"");
        importer.readNextPayloadAsAFutureFormat();
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
        controller.poll();
        takeRelay();
        waitFor(() -> controller.snapshot().getState() == TrackerConnectionState.IMPORT_FAILED);
        waitFor(() -> !controller.pollInFlight());
        assertEquals(SyncReason.FUTURE_FORMAT, controller.snapshot().getReason());

        // Later checks hear that the relay still has only that version.
        server.enqueue(new MockResponse().setResponseCode(304).addHeader("ETag", "6"));
        controller.poll();
        assertEquals("6", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> !controller.pollInFlight());
        assertEquals(SyncReason.FUTURE_FORMAT, controller.snapshot().getReason());

        // Rules the plugin can read clear it.
        server.enqueue(relayResponse(7, validV4Payload(), "\"7\""));
        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
        assertEquals(SyncReason.NONE, controller.snapshot().getReason());
    }

    @Test
    public void rulesThatCannotBeReadSayToSendThemAgain() throws Exception
    {
        connect(5, "\"5\"");
        importer.failToPrepareNextPayload();
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
        controller.poll();
        takeRelay();
        waitFor(() -> controller.snapshot().getState() == TrackerConnectionState.IMPORT_FAILED);

        assertEquals(SyncReason.INVALID_RULES, controller.snapshot().getReason());
    }

    @Test
    public void aReplyIgnoringTheValidatorIsNotTriedAgain() throws Exception
    {
        connect(5, "\"5\"");
        importer.rejectNextPayload();
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        assertEquals(TrackerConnectionState.IMPORT_FAILED, controller.snapshot().getState());

        // Something in front of the relay answers in full anyway.
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
        controller.poll();
        assertEquals("6", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> !controller.pollInFlight());

        assertEquals(2, importer.preparedOnThreads().size());
        assertEquals(0, clientTasks.size());
        assertEquals(TrackerConnectionState.IMPORT_FAILED, controller.snapshot().getState());
    }

    @Test
    public void newerRulesAfterARejectedVersionAreTried() throws Exception
    {
        connect(5, "\"5\"");
        importer.rejectNextPayload();
        server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        server.enqueue(relayResponse(7, validV4Payload(), "\"7\""));
        controller.poll();
        assertEquals("6", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
        assertEquals("7", controller.snapshot().getAcceptedVersion());
        server.enqueue(new MockResponse().setResponseCode(304));
        controller.poll();
        assertEquals("7", takeRelay().getHeader("If-None-Match"));
    }

    @Test
    public void withConsentOffTheTicksChangeNothing() throws Exception
    {
        connect(5, "\"5\"");
        configuration.remove(FateLockedConfig.NETWORK_ACCESS_KEY);
        int before = listener.snapshots().size();

        for (int tick = 0; tick < 5; tick++)
        {
            controller.pollIfDue();
        }

        // One reset to "Not connected", then silence.
        assertEquals(before + 1, listener.snapshots().size());
        assertEquals(TrackerConnectionState.DISCONNECTED, controller.snapshot().getState());
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void aConfirmationForAnotherVersionWaitsAFullInterval() throws Exception
    {
        connect(6, "\"6\"");
        clock.advanceSeconds(SyncMachine.CONNECTED_POLL_SECONDS);
        server.enqueue(new MockResponse().setResponseCode(304).addHeader("ETag", "5"));

        controller.pollIfDue();
        takeRelay();
        waitFor(() -> !controller.pollInFlight());
        clock.advanceSeconds(SyncMachine.WAITING_POLL_SECONDS);
        controller.pollIfDue();

        assertNoFurtherRequest();
        assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
    }

    @Test
    public void theSameStatusIsNotPublishedTwice() throws Exception
    {
        connect(6, "\"6\"");
        // The relay's copy lapsed, and says so at every check.
        for (int reply = 0; reply < 2; reply++)
        {
            server.enqueue(new MockResponse().setResponseCode(404));
            pollUntilRelay();
            waitFor(() -> !controller.pollInFlight());
        }

        long lapsed = listener.snapshots().stream()
            .filter(s -> s.getReason() == SyncReason.NO_RECENT_UPDATE)
            .count();
        assertEquals(1, lapsed);
    }

    @Test
    public void aFailedCheckSaysWhenTheNextOneIs() throws Exception
    {
        connect(6, "\"6\"");
        for (long wait : new long[] {SyncMachine.FAILURE_BACKOFF_SECONDS,
            2 * SyncMachine.FAILURE_BACKOFF_SECONDS})
        {
            server.enqueue(new MockResponse().setResponseCode(503));
            pollUntilRelay();
            waitFor(() -> !controller.pollInFlight());

            assertEquals(SyncReason.UNAVAILABLE, controller.snapshot().getReason());
            assertEquals(clock.instant().plusSeconds(wait), controller.snapshot().getNextCheck());
        }
        // Each new wait is a new status, so the sidebar shows it.
        assertEquals(2, listener.snapshots().stream()
            .filter(s -> s.getReason() == SyncReason.UNAVAILABLE)
            .count());
    }

    @Test
    public void theFirstStatusSaysWhetherSyncIsOffOrAboutToCheck() throws Exception
    {
        assertEquals(SyncReason.CHECKING, controller.snapshot().getReason());

        configuration.remove(FateLockedConfig.NETWORK_ACCESS_KEY);
        RecordingListener syncOff = new RecordingListener();
        TrackerConnectionController paired = new TrackerConnectionController(
            http, gson, settings, clock, dispatcher, importer, syncOff);
        configuration.remove(TrackerConnectionSettings.PAIRING_CODE_KEY);
        RecordingListener unpaired = new RecordingListener();
        TrackerConnectionController fresh = new TrackerConnectionController(
            http, gson, settings, clock, dispatcher, importer, unpaired);
        try
        {
            assertEquals(SyncReason.SYNC_OFF, syncOff.last().getReason());
            assertEquals(SyncReason.NOT_PAIRED, unpaired.last().getReason());
        }
        finally
        {
            paired.stop();
            fresh.stop();
        }
    }

    @Test
    public void anUnreadableReplySaysSoAndBacksOff() throws Exception
    {
        connect(6, "\"6\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        clock.advanceSeconds(SyncMachine.CONNECTED_POLL_SECONDS);
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "text/html")
            .setBody("<html><body>Sign in to the Wi-Fi</body></html>"));

        controller.pollIfDue();
        takeRelay();
        waitFor(() -> SyncReason.UNREADABLE.status.equals(
            controller.snapshot().getMessage()));

        // The rules and their version stay; only the status says what happened.
        assertEquals(TrackerConnectionState.OFFLINE, controller.snapshot().getState());
        assertEquals("6", controller.snapshot().getAcceptedVersion());
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        waitFor(() -> !controller.pollInFlight());
        clock.advanceSeconds(SyncMachine.FAILURE_BACKOFF_SECONDS - 1);
        controller.pollIfDue();
        assertNoFurtherRequest();
    }

    @Test
    public void aReplyThatBreaksOffIsUnreachable() throws Exception
    {
        connect(6, "\"6\"");
        clock.advanceSeconds(SyncMachine.CONNECTED_POLL_SECONDS);
        server.enqueue(relayResponse(7, validV4Payload(), "\"7\"")
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));

        controller.pollIfDue();
        takeRelay();
        waitFor(() -> SyncReason.UNREACHABLE.status.equals(
            controller.snapshot().getMessage()));

        assertEquals(TrackerConnectionState.OFFLINE, controller.snapshot().getState());
        assertEquals("6", controller.snapshot().getAcceptedVersion());
        assertEquals(1, importer.acceptedPayloads().size());
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
        assertNoFurtherRequest();
    }

    @Test
    public void notModifiedWithoutEtagRefreshesAcceptedFreshness()
        throws Exception
    {
        connect(6, "\"6\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        clock.advanceSeconds(30);
        server.enqueue(new MockResponse().setResponseCode(304));

        controller.poll();
        RecordedRequest revalidation = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        runClientTasks();

        assertEquals("6",
            revalidation.getHeader("If-None-Match"));
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("6",
            controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(),
            controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
    }

    @Test
    public void rulesRestoredFromTheLastStartAreConfirmedByA304() throws Exception
    {
        controller.seedAcceptedVersion("41");
        server.enqueue(new MockResponse()
            .setResponseCode(304)
            .addHeader("ETag", "\"41\""));

        controller.poll();
        RecordedRequest first = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        // Not connected until the relay says they are current.
        assertNull(controller.snapshot().getLastSync());
        runClientTasks();

        assertEquals("41", first.getHeader("If-None-Match"));
        assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
        assertEquals("41", controller.snapshot().getAcceptedVersion());
        assertEquals(clock.instant(), controller.snapshot().getLastSync());
        assertEquals(0, importer.acceptedPayloads().size());
    }

    @Test
    public void newerRulesStillArriveInFullAfterASeed() throws Exception
    {
        controller.seedAcceptedVersion("41");
        server.enqueue(relayResponse(42, validV4Payload(), "\"42\""));

        controller.poll();
        assertEquals("41", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertEquals("42", controller.snapshot().getAcceptedVersion());
        assertEquals(1, importer.acceptedPayloads().size());
    }

    @Test
    public void aSeedIsIgnoredOnceThisStartHasAcceptedRules() throws Exception
    {
        connect(6, "\"6\"");

        controller.seedAcceptedVersion("41");

        assertEquals("6", controller.snapshot().getAcceptedVersion());
        server.enqueue(new MockResponse().setResponseCode(304));
        controller.poll();
        assertEquals("6", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
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
        assertNoFurtherRequest();
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
        assertNoFurtherRequest();
    }

    @Test
    public void rejectedOldResponseCannotClearANewerInFlightToken()
        throws Exception
    {
        server.enqueue(relayResponse(0, validV4Payload(), "\"1\"")
            .setHeadersDelay(300, TimeUnit.MILLISECONDS));
        server.enqueue(relayResponse(2, validV4Payload(), "\"2\"")
            .setHeadersDelay(800, TimeUnit.MILLISECONDS));

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

        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("2", controller.snapshot().getAcceptedVersion());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
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
        assertNoFurtherRequest();
    }

    @Test
    public void aRelayThatStaysBusyIsCheckedWhenItAsks() throws Exception
    {
        for (int reply = 0; reply < 2; reply++)
        {
            server.enqueue(new MockResponse().setResponseCode(429).addHeader("Retry-After", "120"));
            controller.pollIfDue();
            takeRelay();
            waitFor(() -> !controller.pollInFlight());

            // Two minutes each time, as asked, rather than doubled.
            assertEquals(clock.instant().plusSeconds(120), controller.snapshot().getNextCheck());
            clock.advanceSeconds(120);
        }
    }

    @Test
    public void loggedOutTheTrackerIsCheckedLessOftenAndALoginChecksAtOnce() throws Exception
    {
        controller.loggedIn(false);
        connect(5, "\"5\"");

        clock.advanceSeconds(SyncMachine.CONNECTED_POLL_SECONDS);
        controller.pollIfDue();
        assertNoFurtherRequest();

        controller.loggedIn(true);
        server.enqueue(new MockResponse().setResponseCode(304));
        controller.pollIfDue();
        assertEquals("5", takeRelay().getHeader("If-None-Match"));
    }

    @Test
    public void checkNowChecksAtOnceAtMostEveryTenSeconds() throws Exception
    {
        connect(5, "\"5\"");
        controller.pollIfDue();
        assertNoFurtherRequest();

        assertTrue(controller.checkNow());
        server.enqueue(new MockResponse().setResponseCode(304));
        controller.pollIfDue();
        assertEquals("5", takeRelay().getHeader("If-None-Match"));
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertFalse(controller.checkNow());
        clock.advanceSeconds(SyncMachine.CHECK_NOW_SECONDS);
        assertTrue(controller.checkNow());
    }

    @Test
    public void checkNowNeedsAPairingWithOnlineSyncOn() throws Exception
    {
        configuration.remove(FateLockedConfig.NETWORK_ACCESS_KEY);
        assertFalse(controller.checkNow());

        configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "true");
        configuration.remove(TrackerConnectionSettings.PAIRING_CODE_KEY);
        assertFalse(controller.checkNow());

        configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, INITIAL_CODE);
        controller.stop();
        assertFalse(controller.checkNow());
    }

    @Test
    public void automaticPollingBacksOffRateLimitsThenUsesHealthyCadence()
        throws Exception
    {
        server.enqueue(new MockResponse()
            .setResponseCode(429)
            .addHeader("Retry-After", "120"));

        controller.pollIfDue();
        takeRelay();
        waitFor(() -> controller.snapshot().getState()
            == TrackerConnectionState.OFFLINE);
        assertEquals("Tracker relay is busy; retrying later",
            controller.snapshot().getMessage());

        controller.pollIfDue();
        assertNoFurtherRequest();
        clock.advanceSeconds(119);
        controller.pollIfDue();
        assertNoFurtherRequest();

        clock.advanceSeconds(1);
        server.enqueue(relayResponse(1, validV4Payload(), "\"1\""));
        controller.pollIfDue();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());

        clock.advanceSeconds(
            SyncMachine.CONNECTED_POLL_SECONDS - 1);
        controller.pollIfDue();
        assertNoFurtherRequest();
        clock.advanceSeconds(1);
        server.enqueue(new MockResponse()
            .setResponseCode(304)
            .addHeader("ETag", "\"1\""));
        controller.pollIfDue();
        takeRelay();
    }

    @Test
    public void notFoundAfterAnImportSaysNoRecentUpdateAndKeepsTheBundle()
        throws Exception
    {
        connect(5, "\"5\"");
        Instant acceptedAt = controller.snapshot().getLastSync();
        server.enqueue(new MockResponse().setResponseCode(404));

        controller.poll();
        takeRelay();
        waitFor(() -> SyncReason.NO_RECENT_UPDATE.status
            .equals(controller.snapshot().getMessage()));
        // The pairing still works: the relay's copy lapsed, which is not red.
        assertEquals(TrackerConnectionState.WAITING,
            controller.snapshot().getState());

        assertEquals(INITIAL_CODE, settings.pairingCode());
        // The rules stay; only their version is forgotten.
        assertNull(controller.snapshot().getAcceptedVersion());
        assertEquals(acceptedAt, controller.snapshot().getLastSync());
        assertEquals(1, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
        assertNoFurtherRequest();
    }

    @Test
    public void afterANotFoundTheNextVersionIsImportedEvenIfOlder() throws Exception
    {
        connect(5, "\"5\"");
        server.enqueue(new MockResponse().setResponseCode(404));
        controller.poll();
        takeRelay();
        waitFor(() -> !controller.pollInFlight());

        // Say the relay's storage was restored from an older copy.
        server.enqueue(relayResponse(4, validV4Payload(), "\"4\""));
        controller.poll();
        RecordedRequest next = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertNull(next.getHeader("If-None-Match"));
        assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
        assertEquals("4", controller.snapshot().getAcceptedVersion());
        assertEquals(2, importer.acceptedPayloads().size());
    }

    @Test
    public void legacySettingsAreClearedWhenPairingIdentityChanges()
        throws Exception
    {
        connect(1, "\"1\"");
        connect(2, "\"2\"");
        assertEquals(0, unsetKeys.size());

        controller.beginPairing();
        assertEquals(3, unsetKeys.size());
        assertTrue(unsetKeys.contains("onlineSync"));
        assertTrue(unsetKeys.contains("syncCode"));
        assertTrue(unsetKeys.contains("relayUrl"));
        connect(1, "\"1\"");

        assertEquals(3, unsetKeys.size());
        assertEquals(3, importer.acceptedPayloads().size());
        assertEquals(0, clientTasks.size());
    }

    @Test
    public void aLocalImportMakesTheNextCheckFetchTheTrackerCopyInFull()
        throws Exception
    {
        connect(5, "\"5\"");

        controller.localRulesReplacedTrackerRules();

        // Due at once, sent without the old validator, and the same version
        // imports again instead of being answered "unchanged".
        server.enqueue(relayResponse(5, validV4Payload(), "\"5\""));
        controller.pollIfDue();
        RecordedRequest refetch = takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertNull(refetch.getHeader("If-None-Match"));
        assertEquals(TrackerConnectionState.CONNECTED,
            controller.snapshot().getState());
        assertEquals("5", controller.snapshot().getAcceptedVersion());
        assertEquals(2, importer.acceptedPayloads().size());
        assertNoFurtherRequest();
    }

    @Test
    public void aLocalImportBeforeAnyTrackerImportChangesNothing()
        throws Exception
    {
        controller.localRulesReplacedTrackerRules();

        assertEquals(TrackerConnectionState.DISCONNECTED,
            controller.snapshot().getState());
        assertNoFurtherRequest();
    }

    @Test
    public void aNewPairingWaitsForTheBrowserInsteadOfExpiring() throws Exception
    {
        controller.beginPairing();
        assertEquals(TrackerConnectionState.WAITING, controller.snapshot().getState());
        assertEquals(SyncReason.CONFIRM_IN_BROWSER.status,
            controller.snapshot().getMessage());

        // The browser has not published yet: the relay answers 404.
        server.enqueue(new MockResponse().setResponseCode(404));
        controller.pollIfDue();
        takeRelay();
        waitFor(() -> !controller.pollInFlight());

        assertEquals(TrackerConnectionState.WAITING, controller.snapshot().getState());
        assertEquals(SyncReason.CONFIRM_IN_BROWSER.status,
            controller.snapshot().getMessage());

        // Checked again after 5 seconds, not after a growing back-off.
        clock.advanceSeconds(4);
        controller.pollIfDue();
        assertNoFurtherRequest();
        clock.advanceSeconds(1);
        server.enqueue(relayResponse(1, validV4Payload(), "\"1\""));
        controller.pollIfDue();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();

        assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
    }

    @Test
    public void aPairingChecksLessOftenAfterTwoMinutes() throws Exception
    {
        controller.beginPairing();
        clock.advanceSeconds(SyncMachine.PAIRING_FAST_POLL_WINDOW_SECONDS);

        server.enqueue(new MockResponse().setResponseCode(404));
        controller.pollIfDue();
        takeRelay();
        waitFor(() -> !controller.pollInFlight());

        clock.advanceSeconds(SyncMachine.PAIRING_SLOW_POLL_SECONDS - 1);
        controller.pollIfDue();
        assertNoFurtherRequest();
        clock.advanceSeconds(1);
        server.enqueue(new MockResponse().setResponseCode(404));
        controller.pollIfDue();
        takeRelay();
    }

    @Test
    public void aPairingWithNoProfileAfterTenMinutesSaysSo() throws Exception
    {
        controller.beginPairing();
        clock.advanceSeconds(SyncMachine.PAIRING_CONFIRM_SECONDS);

        server.enqueue(new MockResponse().setResponseCode(404));
        controller.pollIfDue();
        takeRelay();
        waitFor(() -> SyncReason.NO_PROFILE.status
            .equals(controller.snapshot().getMessage()));

        assertEquals(TrackerConnectionState.EXPIRED, controller.snapshot().getState());
    }

    @Test
    public void aReplyThatTricklesInIsCutOffByTheCallTimeout() throws Exception
    {
        RecordingListener shown = new RecordingListener();
        TrackerConnectionController shortCalls = new TrackerConnectionController(
            http, Duration.ofMillis(500), gson, settings, clock, dispatcher, importer, shown);
        try
        {
            // Each piece arrives well inside the read timeout, but the whole
            // reply would take about 20 seconds.
            server.enqueue(relayResponse(1, validV4Payload(), "\"1\"")
                .throttleBody(16, 100, TimeUnit.MILLISECONDS));
            shortCalls.poll();
            takeRelay();
            waitFor(() -> !shortCalls.pollInFlight());

            assertEquals(TrackerConnectionState.OFFLINE, shortCalls.snapshot().getState());
            assertEquals(SyncReason.UNREACHABLE.status, shortCalls.snapshot().getMessage());
            assertTrue(importer.preparedOnThreads().isEmpty());
        }
        finally
        {
            shortCalls.stop();
        }
    }

    @Test
    public void aReplyWithNoLengthStopsBeingReadAtTheCap() throws Exception
    {
        StringBuilder payload = new StringBuilder();
        while (payload.length() <= TrackerConnectionController.MAX_REPLY_BYTES)
        {
            payload.append("0123456789abcdef");
        }
        // Chunked, so nothing says how long it is until it ends.
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("ETag", "\"1\"")
            .setChunkedBody(gson.toJson(new RelayEnvelope(1, payload.toString())), 64 * 1024));

        controller.poll();
        takeRelay();
        waitFor(() -> !controller.pollInFlight());

        assertEquals(TrackerConnectionState.OFFLINE, controller.snapshot().getState());
        assertEquals(SyncReason.UNREADABLE.status, controller.snapshot().getMessage());
        assertTrue(importer.preparedOnThreads().isEmpty());
    }

    @Test
    public void stoppingCancelsTheCheckInFlight() throws Exception
    {
        Call check = sendCheckThatHangs();

        controller.stop();

        assertCancelled(check);
        // The cancelled check's failure shows nothing.
        waitFor(() -> http.dispatcher().runningCallsCount() == 0);
        assertEquals(TrackerConnectionState.DISCONNECTED, listener.last().getState());
    }

    @Test
    public void withdrawingConsentCancelsTheCheckInFlight() throws Exception
    {
        Call check = sendCheckThatHangs();

        configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "false");
        controller.networkAccessChanged();

        assertCancelled(check);
    }

    @Test
    public void pairingAgainCancelsTheCheckInFlight() throws Exception
    {
        Call check = sendCheckThatHangs();

        controller.beginPairing();

        assertCancelled(check);
    }

    @Test
    public void aPairingCodeChangedElsewhereCancelsTheCheckInFlight() throws Exception
    {
        Call check = sendCheckThatHangs();

        // A RuneLite profile switch, say, brings another pairing code.
        configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY,
            "fedcba9876543210fedcba9876543210");
        server.enqueue(new MockResponse().setResponseCode(404));
        controller.poll();

        assertCancelled(check);
    }

    /** Send a check the relay never answers. */
    private Call sendCheckThatHangs() throws Exception
    {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        controller.poll();
        takeRelay();
        assertEquals(1, sentCalls.size());
        assertTrue(controller.pollInFlight());
        return sentCalls.get(0);
    }

    private void assertCancelled(Call check) throws Exception
    {
        assertTrue(check.isCanceled());
        // It ends now, not when the read would have timed out.
        waitFor(() -> endedCalls.contains(check));
    }

    private void connect(int version, String etag) throws Exception
    {
        server.enqueue(relayResponse(version, validV4Payload(), etag));
        controller.poll();
        takeRelay();
        waitFor(() -> clientTasks.size() == 1);
        runClientTasks();
    }

    private RecordedRequest takeRelay() throws Exception
    {
        RecordedRequest request =
            server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("GET", request.getMethod());
        return request;
    }

    private void assertNoFurtherRequest() throws Exception
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
        private final String token;

        private RelayEnvelope(int version, String payload)
        {
            this.version = version;
            this.payload = payload;
            this.token = "legacy-token-that-must-be-ignored";
        }
    }

    private static final class RecordingImporter
        implements TrackerConnectionController.RelayBundleImporter<String>
    {
        private final List<String> accepted = new ArrayList<>();
        private final List<String> preparedOn = new ArrayList<>();
        private boolean rejectNext;
        private volatile boolean unreadableNext;
        private volatile boolean futureFormatNext;
        private volatile CountDownLatch blocked;
        private volatile CountDownLatch release;

        @Override
        public TrackerConnectionController.Prepared<String> prepare(String payload)
        {
            synchronized (preparedOn)
            {
                preparedOn.add(Thread.currentThread().getName());
            }
            if (unreadableNext)
            {
                unreadableNext = false;
                return TrackerConnectionController.Prepared.refused(
                    TrackerConnectionController.ImportVerdict.INVALID);
            }
            if (futureFormatNext)
            {
                futureFormatNext = false;
                return TrackerConnectionController.Prepared.refused(
                    TrackerConnectionController.ImportVerdict.FUTURE_FORMAT);
            }
            return TrackerConnectionController.Prepared.ok(payload);
        }

        @Override
        public boolean commit(String payload, String version)
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

        void failToPrepareNextPayload()
        {
            unreadableNext = true;
        }

        void readNextPayloadAsAFutureFormat()
        {
            futureFormatNext = true;
        }

        List<String> preparedOnThreads()
        {
            synchronized (preparedOn)
            {
                return new ArrayList<>(preparedOn);
            }
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

        List<TrackerConnectionSnapshot> snapshots()
        {
            return snapshots;
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
