package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.client.config.ConfigManager;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every reply in the relay fixture, through the real connection controller
 * and a real socket, ends in a state the sidebar explains: Stage 1's "every
 * relay outcome maps to a visible state". RelayTransportFixtureTest runs the
 * transport cases.
 */
public class RelayFixtureStatesTest
{
    private static final String CODE = "0123456789abcdef0123456789abcdef";
    /** "a 304 for a version the plugin does not hold": nothing changes on screen. */
    private static final String NOTHING_CHANGES = "unconfirmed";
    /** The state and reason each outcome shows. */
    private static final Map<String, Shown> SHOWS = Map.of(
        "rules", new Shown(TrackerConnectionState.CONNECTED, SyncReason.NONE),
        "unchanged", new Shown(TrackerConnectionState.CONNECTED, SyncReason.NONE),
        "stale", new Shown(TrackerConnectionState.WAITING, SyncReason.OLDER_RULES),
        "missing", new Shown(TrackerConnectionState.WAITING, SyncReason.NO_RECENT_UPDATE),
        "gone", new Shown(TrackerConnectionState.EXPIRED, SyncReason.GONE),
        "unreadable", new Shown(TrackerConnectionState.OFFLINE, SyncReason.UNREADABLE),
        "busy", new Shown(TrackerConnectionState.OFFLINE, SyncReason.BUSY),
        "unavailable", new Shown(TrackerConnectionState.OFFLINE, SyncReason.UNAVAILABLE),
        "unreachable", new Shown(TrackerConnectionState.OFFLINE, SyncReason.UNREACHABLE));

    private final Gson gson = new Gson();

    @Test
    public void everyOutcomeInTheFixtureHasAState() throws Exception
    {
        for (String outcome : RelayContractFixtureTest.fixture().getAsJsonObject("outcomes").keySet())
        {
            assertTrue(outcome + " has no state",
                SHOWS.containsKey(outcome) || outcome.equals(NOTHING_CHANGES));
        }
    }

    @Test
    public void everyReplyEndsInTheStateItsOutcomeShows() throws Exception
    {
        List<String> mismatches = new ArrayList<>();
        for (JsonElement element : RelayContractFixtureTest.fixture().getAsJsonArray("cases"))
        {
            JsonObject relayCase = element.getAsJsonObject();
            if (relayCase.get("from").getAsString().equals("transport")) continue;
            String name = relayCase.get("name").getAsString();
            String outcome = relayCase.get("outcome").getAsString();
            TrackerConnectionSnapshot[] beforeAndAfter = replay(relayCase);
            TrackerConnectionSnapshot after = beforeAndAfter[1];

            if (outcome.equals(NOTHING_CHANGES))
            {
                if (!after.equals(beforeAndAfter[0])) mismatches.add(name + ": the status changed");
                continue;
            }
            Shown expected = SHOWS.get(outcome);
            if (expected.state != after.getState() || expected.reason != after.getReason())
            {
                mismatches.add(name + ": " + after.getState() + "/" + after.getReason());
            }
            SyncView view = SyncView.of(after, Instant.now(), ZoneId.of("UTC"));
            if (view.status == null || view.status.isEmpty())
            {
                mismatches.add(name + ": no status");
            }
            if (after.getState() != TrackerConnectionState.CONNECTED && view.detail == null)
            {
                mismatches.add(name + ": nothing says why");
            }
        }
        assertEquals(List.of(), mismatches);
    }

    /** The status before and after the plugin's check gets this case's reply. */
    private TrackerConnectionSnapshot[] replay(JsonObject relayCase) throws Exception
    {
        Map<String, String> configuration = new ConcurrentHashMap<>();
        configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, CODE);
        configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "true");
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfiguration(anyString(), anyString()))
            .thenAnswer(invocation -> configuration.get(invocation.getArgument(1)));

        MockWebServer server = new MockWebServer();
        server.start();
        Interceptor redirectToServer = chain -> {
            Request original = chain.request();
            return chain.proceed(original.newBuilder()
                .url(server.url(original.url().encodedPath()))
                .build());
        };
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(redirectToServer).build();
        TrackerConnectionController controller = new TrackerConnectionController(
            http, gson, new TrackerConnectionSettings(configManager), Clock.systemUTC(),
            Runnable::run, new AcceptingImporter(), snapshot -> { });
        try
        {
            if (relayCase.has("held") && !relayCase.get("held").isJsonNull())
            {
                controller.seedAcceptedVersion(relayCase.get("held").getAsString());
            }
            TrackerConnectionSnapshot before = controller.snapshot();
            server.enqueue(response(relayCase.getAsJsonObject("response")));
            controller.poll();
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
            waitFor(() -> !controller.pollInFlight());
            return new TrackerConnectionSnapshot[] {before, controller.snapshot()};
        }
        finally
        {
            controller.stop();
            server.shutdown();
        }
    }

    private static MockResponse response(JsonObject reply)
    {
        MockResponse response = new MockResponse().setResponseCode(reply.get("status").getAsInt());
        if (reply.has("headers"))
        {
            for (Map.Entry<String, JsonElement> header : reply.getAsJsonObject("headers").entrySet())
            {
                response.addHeader(header.getKey(), header.getValue().getAsString());
            }
        }
        if (reply.has("body") && !reply.get("body").isJsonNull())
        {
            response.setBody(reply.get("body").getAsString());
        }
        return response;
    }

    private static void waitFor(BooleanSupplier condition) throws Exception
    {
        for (int attempt = 0; attempt < 300; attempt++)
        {
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        throw new AssertionError("condition was not reached");
    }

    /** Takes whatever rules the relay sends: this test is about the connection. */
    private static final class AcceptingImporter
        implements TrackerConnectionController.RelayBundleImporter<String>
    {
        @Override
        public TrackerConnectionController.Prepared<String> prepare(String payload)
        {
            return TrackerConnectionController.Prepared.ok(payload);
        }

        @Override
        public boolean commit(String payload, String version)
        {
            return true;
        }
    }

    private static final class Shown
    {
        private final TrackerConnectionState state;
        private final SyncReason reason;

        private Shown(TrackerConnectionState state, SyncReason reason)
        {
            this.state = state;
            this.reason = reason;
        }
    }
}
