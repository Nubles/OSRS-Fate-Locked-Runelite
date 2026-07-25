package com.fatelocked;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FateLockedPluginRelayTrustTest
{
    @Test
    public void overlappingPollAttemptsKeepOneActiveRequest() throws Exception
    {
        Harness harness = new Harness();

        harness.poll();
        harness.poll();

        assertEquals(1, harness.polls.size());
    }

    @Test
    public void staleOldCallbackCannotClearOrDisplaceNewerSession()
        throws Exception
    {
        Harness harness = new Harness();
        harness.code.set("old-code");
        harness.poll();
        PendingCall old = harness.polls.get(0);

        harness.change("syncCode", () -> harness.code.set("new-code"));
        harness.poll();
        PendingCall current = harness.polls.get(1);

        old.respond(200, "\"old\"", harness.relayBody("Old Account", 1));
        harness.poll();
        assertEquals("The old callback must not clear the newer in-flight poll",
            2, harness.polls.size());

        current.respond(200, "\"new\"", harness.relayBody("New Account", 2));
        harness.runClientTasks();

        assertEquals("New Account",
            harness.plugin.getBundle().getRules().getAccount());
        assertEquals(1, harness.acks.size());
    }

    @Test
    public void optOutBeforeClientThreadCommitLeavesSnapshotAndSkipsAck()
        throws Exception
    {
        Harness harness = new Harness();
        harness.poll();
        harness.polls.get(0).respond(
            200, "\"new\"", harness.relayBody("New Account", 2));
        assertEquals(1, harness.clientTasks.size());

        harness.change("onlineSync", () -> harness.online.set(false));
        harness.runClientTasks();

        harness.assertInitialSnapshot();
        assertTrue(harness.acks.isEmpty());
    }

    @Test
    public void codeChangeBeforeClientThreadCommitLeavesSnapshotAndSkipsAck()
        throws Exception
    {
        Harness harness = new Harness();
        harness.poll();
        harness.polls.get(0).respond(
            200, "\"new\"", harness.relayBody("New Account", 2));

        harness.change("syncCode", () -> harness.code.set("other-code"));
        harness.runClientTasks();

        harness.assertInitialSnapshot();
        assertTrue(harness.acks.isEmpty());
    }

    @Test
    public void shutdownInvalidationRejectsQueuedClientCommitAndAck()
        throws Exception
    {
        Harness harness = new Harness();
        harness.poll();
        harness.polls.get(0).respond(
            200, "\"new\"", harness.relayBody("New Account", 2));

        invoke(harness.plugin, "stopRelayPoll");
        harness.runClientTasks();

        harness.assertInitialSnapshot();
        assertTrue(harness.acks.isEmpty());
    }

    @Test
    public void validRelay200CommitsOneAtomicSnapshotOnClientThread()
        throws Exception
    {
        Harness harness = new Harness();
        harness.poll();
        harness.polls.get(0).respond(
            200, "\"accepted\"", harness.relayBody("Accepted Account", 3));

        harness.assertInitialSnapshot();
        assertTrue(harness.acks.isEmpty());
        assertEquals(1, harness.clientTasks.size());

        harness.runClientTasks();

        assertEquals("Accepted Account",
            harness.plugin.getBundle().getRules().getAccount());
        assertEquals("\"accepted\"",
            field(harness.plugin, "lastRelayVersion"));
        assertTrue(((Instant) field(harness.plugin, "lastTrackerSync"))
            .isAfter(harness.initialSync));
        assertEquals(1, harness.acks.size());
    }

    @Test
    public void matchingCurrent304RefreshesOnlySnapshotFreshnessOnClientThread()
        throws Exception
    {
        Harness harness = new Harness();
        String retainedVersion = "\"retained\"";
        setField(harness.plugin, "lastRelayVersion", retainedVersion);
        harness.poll();
        assertEquals(retainedVersion,
            harness.polls.get(0).request.header("If-None-Match"));

        harness.polls.get(0).respond(304, retainedVersion, null);
        assertEquals(harness.initialSync,
            field(harness.plugin, "lastTrackerSync"));
        assertEquals(1, harness.clientTasks.size());
        harness.runClientTasks();

        assertSame(harness.initialBundle, harness.plugin.getBundle());
        assertEquals(retainedVersion,
            field(harness.plugin, "lastRelayVersion"));
        Instant refreshed = (Instant) field(
            harness.plugin, "lastTrackerSync");
        assertTrue("initial=" + harness.initialSync + ", refreshed=" + refreshed,
            refreshed.isAfter(harness.initialSync));
        assertTrue(harness.acks.isEmpty());
    }

    @Test
    public void mismatched304CannotRefreshAChangedAcceptedSnapshot()
        throws Exception
    {
        Harness harness = new Harness();
        setField(harness.plugin, "lastRelayVersion", "\"requested\"");
        harness.poll();
        harness.polls.get(0).respond(304, "\"requested\"", null);
        Instant newerSnapshotTime = Instant.now().minus(Duration.ofMinutes(2));
        setField(harness.plugin, "lastRelayVersion", "\"newer\"");
        setField(harness.plugin, "lastTrackerSync", newerSnapshotTime);

        harness.runClientTasks();

        assertSame(harness.initialBundle, harness.plugin.getBundle());
        assertEquals("\"newer\"",
            field(harness.plugin, "lastRelayVersion"));
        assertEquals(newerSnapshotTime,
            field(harness.plugin, "lastTrackerSync"));
        assertTrue(harness.acks.isEmpty());
    }

    @Test
    public void stale304AfterSessionInvalidationCannotRefreshFreshness()
        throws Exception
    {
        Harness harness = new Harness();
        setField(harness.plugin, "lastRelayVersion", "\"retained\"");
        harness.poll();
        harness.polls.get(0).respond(304, "\"retained\"", null);
        harness.change("onlineSync", () -> harness.online.set(false));

        harness.runClientTasks();

        assertSame(harness.initialBundle, harness.plugin.getBundle());
        assertEquals(harness.initialSync,
            field(harness.plugin, "lastTrackerSync"));
        assertEquals(null, field(harness.plugin, "lastRelayVersion"));
        assertTrue(harness.acks.isEmpty());
    }
    @Test
    public void malformedRelay200CannotRefreshAnExpiredRetainedSnapshot()
        throws Exception
    {
        Harness harness = new Harness();
        harness.poll();
        harness.polls.get(0).respond(
            200, "\"malformed\"", "{\"version\":2,\"payload\":\"{bad\"}");
        harness.runClientTasks();

        harness.assertInitialSnapshot();
        assertTrue(harness.acks.isEmpty());
    }

    private static final class Harness
    {
        private final FateLockedPlugin plugin = new FateLockedPlugin();
        private final Gson gson = new Gson();
        private final AtomicBoolean online = new AtomicBoolean(true);
        private final AtomicReference<String> code =
            new AtomicReference<>("sync-code");
        private final AtomicReference<String> relayUrl =
            new AtomicReference<>("https://relay.example/");
        private final List<PendingCall> polls = new ArrayList<>();
        private final List<Request> acks = new ArrayList<>();
        private final List<Runnable> clientTasks = new ArrayList<>();
        private final FateLockedBundle initialBundle;
        private final Instant initialSync =
            Instant.now().minus(Duration.ofMinutes(16));
        private final String initialVersion = null;

        private Harness() throws Exception
        {
            initialBundle = FateLockedBundle.loadFromJson(
                gson, fixtureText("bundles/v4-rules.json"));
            FateLockedConfig config = mock(FateLockedConfig.class);
            when(config.onlineSync()).thenAnswer(invocation -> online.get());
            when(config.syncCode()).thenAnswer(invocation -> code.get());
            when(config.relayUrl()).thenAnswer(invocation -> relayUrl.get());

            ClientThread clientThread = mock(ClientThread.class);
            doAnswer(invocation ->
            {
                clientTasks.add(invocation.getArgument(0));
                return null;
            }).when(clientThread).invoke(any(Runnable.class));

            OkHttpClient http = mock(OkHttpClient.class);
            when(http.newCall(any(Request.class))).thenAnswer(invocation ->
            {
                Request request = invocation.getArgument(0);
                Call call = mock(Call.class);
                if ("GET".equals(request.method()))
                {
                    PendingCall pending = new PendingCall(call, request);
                    polls.add(pending);
                    doAnswer(enqueue ->
                    {
                        pending.callback = enqueue.getArgument(0);
                        return null;
                    }).when(call).enqueue(any(Callback.class));
                }
                else
                {
                    acks.add(request);
                }
                return call;
            });

            setField(plugin, "gson", gson);
            setField(plugin, "bundle", initialBundle);
            setField(plugin, "lastTrackerSync", initialSync);
            setField(plugin, "lastRelayVersion", initialVersion);
            setField(plugin, "config", config);
            setField(plugin, "client", mock(Client.class));
            setField(plugin, "clientThread", clientThread);
            setField(plugin, "panel", mock(FateLockedPanel.class));
            setField(plugin, "okHttpClient", http);
            setField(plugin, "configManager", mock(ConfigManager.class));
        }

        private void poll() throws Exception
        {
            invoke(plugin, "pollRelay");
        }

        private void change(String key, Runnable mutation)
        {
            mutation.run();
            ConfigChanged changed = mock(ConfigChanged.class);
            when(changed.getGroup()).thenReturn(FateLockedConfig.GROUP);
            when(changed.getKey()).thenReturn(key);
            plugin.onConfigChanged(changed);
        }

        private void runClientTasks()
        {
            while (!clientTasks.isEmpty())
            {
                clientTasks.remove(0).run();
            }
        }

        private String relayBody(String account, int version) throws Exception
        {
            String payload = fixtureText("bundles/v4-rules.json")
                .replace("\"account\": \"Nubles\"",
                    "\"account\": \"" + account + "\"");
            Map<String, Object> body = new HashMap<>();
            body.put("version", version);
            body.put("payload", payload);
            return gson.toJson(body);
        }

        private void assertInitialSnapshot() throws Exception
        {
            assertSame(initialBundle, plugin.getBundle());
            assertEquals(initialSync, field(plugin, "lastTrackerSync"));
            assertEquals(initialVersion, field(plugin, "lastRelayVersion"));
        }
    }

    private static final class PendingCall
    {
        private final Call call;
        private final Request request;
        private Callback callback;

        private PendingCall(Call call, Request request)
        {
            this.call = call;
            this.request = request;
        }

        private void respond(int code, String etag, String body) throws Exception
        {
            if (callback == null) throw new AssertionError("call was not enqueued");
            Response.Builder builder = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code == 304 ? "Not Modified" : "OK");
            if (etag != null) builder.header("ETag", etag);
            builder.body(ResponseBody.create(
                MediaType.parse("application/json"),
                body == null ? "" : body));
            callback.onResponse(call, builder.build());
        }
    }

    private static void setField(Object target, String name, Object value)
        throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object field(Object target, String name) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(Object target, String name) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static String fixtureText(String name) throws Exception
    {
        try (InputStream in =
            FateLockedPluginRelayTrustTest.class.getClassLoader()
                .getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}