package com.fatelocked;

import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.travel.TravelActionResolver;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FateLockedPluginRelayTrustTest
{
    @Test
    public void malformedRelay200CannotRefreshAnExpiredRetainedSnapshot()
        throws Exception
    {
        FateLockedPlugin plugin = new FateLockedPlugin();
        Gson gson = new Gson();
        FateLockedBundle expiredBundle = FateLockedBundle.loadFromJson(
            gson,
            fixtureText("bundles/v4-rules.json")
                .replace("\"entry\": \"ALLOWED\"", "\"entry\": \"LOCKED\""));
        Instant expiredSync = Instant.now().minus(Duration.ofMinutes(16));
        String retainedVersion = "\"retained\"";

        FateLockedConfig config = mock(FateLockedConfig.class);
        when(config.onlineSync()).thenReturn(true);
        when(config.strictMode()).thenReturn(true);
        when(config.syncCode()).thenReturn("sync-code");
        when(config.relayUrl()).thenReturn("https://relay.example");

        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getName()).thenReturn("Nubles");
        when(player.getWorldLocation()).thenReturn(
            new WorldPoint(49 << 6, 50 << 6, 0));

        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(invocation ->
        {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(clientThread).invoke(any(Runnable.class));

        OkHttpClient http = mock(OkHttpClient.class);
        Call relayCall = mock(Call.class);
        Call ackCall = mock(Call.class);
        AtomicReference<Request> relayRequest = new AtomicReference<>();
        when(http.newCall(any(Request.class))).thenAnswer(invocation ->
        {
            Request request = invocation.getArgument(0);
            if ("GET".equals(request.method()))
            {
                relayRequest.set(request);
                return relayCall;
            }
            return ackCall;
        });
        doAnswer(invocation ->
        {
            Callback callback = invocation.getArgument(0);
            Response response = new Response.Builder()
                .request(relayRequest.get())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("ETag", "\"malformed\"")
                .body(ResponseBody.create(
                    MediaType.parse("application/json"),
                    "{\"version\":2,\"payload\":\"{bad\"}"))
                .build();
            callback.onResponse(relayCall, response);
            return null;
        }).when(relayCall).enqueue(any(Callback.class));

        TravelBlockNoticeStore noticeStore =
            new TravelBlockNoticeStore(Clock.systemUTC());
        TravelAvailability availability = mock(TravelAvailability.class);
        TravelGuardianCoordinator coordinator = new TravelGuardianCoordinator(
            new TravelActionResolver(),
            new TravelRuleEvaluator(),
            new TravelAlternativeFinder(),
            noticeStore,
            new StrictModeClickHandler(new StrictModeGuard()));
        TravelGuardianPluginShell shell = new TravelGuardianPluginShell(
            coordinator,
            availability,
            message -> { },
            entry -> { },
            (event, context) -> { },
            (stage, error) -> { },
            Clock.systemUTC());

        setField(plugin, "gson", gson);
        setField(plugin, "bundle", expiredBundle);
        setField(plugin, "lastTrackerSync", expiredSync);
        setField(plugin, "lastRelayVersion", retainedVersion);
        setField(plugin, "config", config);
        setField(plugin, "client", client);
        setField(plugin, "clientThread", clientThread);
        setField(plugin, "panel", mock(FateLockedPanel.class));
        setField(plugin, "okHttpClient", http);
        setField(plugin, "travelGuardianShell", shell);

        invoke(plugin, "pollRelay");

        MenuOptionClicked click = namedTeleportClick("Teleport", "Lumbridge");
        plugin.onMenuOptionClicked(click);

        verify(click, never()).consume();
        assertFalse(noticeStore.current().isPresent());
        assertSame(expiredBundle, plugin.getBundle());
        assertEquals(expiredSync, field(plugin, "lastTrackerSync"));
        assertEquals(retainedVersion, field(plugin, "lastRelayVersion"));
    }

    private static MenuOptionClicked namedTeleportClick(
        String option, String target)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);
        return click;
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
