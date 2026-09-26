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
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The relay fixture's transport cases, through the real connection
 * controller and a real socket: a reset connection, no answer in time, and
 * a reply larger than any real bundle.
 */
public class RelayTransportFixtureTest
{
    private static final String CODE = "0123456789abcdef0123456789abcdef";

    private final List<String> prepared = new CopyOnWriteArrayList<>();
    private final List<TrackerConnectionSnapshot> published = new CopyOnWriteArrayList<>();
    private MockWebServer server;
    private TrackerConnectionController controller;

    @Before
    public void setUp() throws Exception
    {
        Map<String, String> configuration = new ConcurrentHashMap<>();
        configuration.put(TrackerConnectionSettings.PAIRING_CODE_KEY, CODE);
        configuration.put(FateLockedConfig.NETWORK_ACCESS_KEY, "true");
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getConfiguration(anyString(), anyString()))
            .thenAnswer(invocation -> configuration.get(invocation.getArgument(1)));

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
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build();
        controller = new TrackerConnectionController(
            http, new Gson(), new TrackerConnectionSettings(configManager),
            Clock.systemUTC(), Runnable::run,
            new TrackerConnectionController.RelayBundleImporter<String>()
            {
                @Override
                public String prepare(String payload)
                {
                    prepared.add(payload);
                    return payload;
                }

                @Override
                public boolean commit(String payload, String version)
                {
                    return true;
                }
            },
            published::add);
    }

    @After
    public void tearDown() throws Exception
    {
        controller.stop();
        server.shutdown();
    }

    @Test
    public void everyTransportCaseIsCovered() throws Exception
    {
        for (JsonElement element : RelayContractFixtureTest.fixture().getAsJsonArray("cases"))
        {
            JsonObject relayCase = element.getAsJsonObject();
            if (!relayCase.get("from").getAsString().equals("transport")) continue;
            String transport = relayCase.get("transport").getAsString();
            assertTrue(relayCase.get("name").getAsString(),
                List.of("reset", "timeout", "oversized").contains(transport));
        }
    }

    @Test
    public void aResetConnectionIsUnreachable() throws Exception
    {
        JsonObject relayCase = transportCase("reset");
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        poll(relayCase);

        assertEquals("unreachable", relayCase.get("outcome").getAsString());
        waitFor(this::unreachable);
    }

    @Test
    public void noAnswerInTimeIsUnreachable() throws Exception
    {
        JsonObject relayCase = transportCase("timeout");
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        poll(relayCase);

        assertEquals("unreachable", relayCase.get("outcome").getAsString());
        waitFor(this::unreachable);
    }

    @Test
    public void aReplyLargerThanAnyRealBundleIsUnreadable() throws Exception
    {
        JsonObject relayCase = transportCase("oversized");
        int bodyBytes = relayCase.get("bodyBytes").getAsInt();
        StringBuilder payload = new StringBuilder(bodyBytes);
        while (payload.length() < bodyBytes) payload.append('x');
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("ETag", "42")
            .setBody("{\"version\":42,\"payload\":\"" + payload + "\"}"));

        poll(relayCase);

        assertEquals("unreadable", relayCase.get("outcome").getAsString());
        waitFor(() -> shown(SyncReason.UNREADABLE.status));
        assertTrue(prepared.isEmpty());
    }

    private void poll(JsonObject relayCase)
    {
        controller.seedAcceptedVersion(relayCase.get("held").getAsString());
        controller.poll();
    }

    private boolean unreachable()
    {
        return shown(SyncReason.UNREACHABLE.status);
    }

    private boolean shown(String offlineMessage)
    {
        return published.stream().anyMatch(snapshot ->
            snapshot.getState() == TrackerConnectionState.OFFLINE
                && offlineMessage.equals(snapshot.getMessage()));
    }

    private static JsonObject transportCase(String transport) throws Exception
    {
        for (JsonElement element : RelayContractFixtureTest.fixture().getAsJsonArray("cases"))
        {
            JsonObject relayCase = element.getAsJsonObject();
            if (relayCase.has("transport")
                && relayCase.get("transport").getAsString().equals(transport))
            {
                return relayCase;
            }
        }
        throw new AssertionError("the relay fixture has no " + transport + " case");
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
}
