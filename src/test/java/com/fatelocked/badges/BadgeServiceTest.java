package com.fatelocked.badges;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.Progress;
import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.IterableHashTable;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ChatIconManager;
import org.junit.Before;
import org.junit.Test;

/** The badge service end to end, with a recording relay and the client's work run inline. */
public class BadgeServiceTest
{
    private static final String CODE = "0123456789abcdef0123456789abcdef";
    private static final String LIST = "{\"badges\":[{\"n\":\"Iron Example\",\"t\":3},{\"n\":\"Clan Mate\",\"t\":0}]}";
    private static final long START = 1_800_000_000_000L;
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);

    /** One request the service made, answered when the test says. */
    private static final class Sent
    {
        final String what;
        final BadgeService.Reply reply;
        boolean cancelled;

        Sent(String what, BadgeService.Reply reply)
        {
            this.what = what;
            this.reply = reply;
        }
    }

    private final List<Sent> sent = new ArrayList<>();
    private final Map<String, String> stored = new HashMap<>();
    private final Client client = mock(Client.class);
    private long now = START;
    private boolean sync = true;
    private boolean inChat = true;
    private boolean aboveHeads = true;
    private boolean share;
    private String code = CODE;
    private String boundName = "Iron Example";
    private Progress progress = new Progress(Progress.AREAS, 100, 187, 0, 0);
    private BadgeService service;

    @Before
    public void setUp()
    {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        service = newService();
    }

    private BadgeService newService()
    {
        BadgeService.Relay relay = new BadgeService.Relay()
        {
            @Override
            public Runnable fetch(String etag, BadgeService.Reply reply)
            {
                return record("GET /badges" + (etag == null ? "" : " If-None-Match " + etag), reply);
            }

            @Override
            public Runnable publish(String to, String name, int tier, BadgeService.Reply reply)
            {
                return record("POST /r/" + to + "/badge " + name + " " + tier, reply);
            }

            @Override
            public Runnable withdraw(String to, BadgeService.Reply reply)
            {
                return record("DELETE /r/" + to + "/badge", reply);
            }
        };
        BadgeService.Settings settings = new BadgeService.Settings()
        {
            @Override
            public boolean sync()
            {
                return sync;
            }

            @Override
            public boolean showInChat()
            {
                return inChat;
            }

            @Override
            public boolean showAboveHeads()
            {
                return aboveHeads;
            }

            @Override
            public boolean share()
            {
                return share;
            }

            @Override
            public String pairingCode()
            {
                return code;
            }
        };
        BadgeService.Store store = new BadgeService.Store()
        {
            @Override
            public String get(String key)
            {
                return stored.get(key);
            }

            @Override
            public void set(String key, String value)
            {
                stored.put(key, value);
            }

            @Override
            public void unset(String key)
            {
                stored.remove(key);
            }
        };
        BadgeService.Run run = new BadgeService.Run()
        {
            @Override
            public String boundName()
            {
                return boundName;
            }

            @Override
            public Progress progress()
            {
                return progress;
            }
        };
        ChatIconManager chatIcons = mock(ChatIconManager.class);
        int[] next = {0};
        when(chatIcons.registerChatIcon(any(BufferedImage.class))).thenAnswer(invocation -> next[0]++);
        when(chatIcons.chatIconIndex(anyInt())).thenAnswer(invocation -> 50 + (int) invocation.getArgument(0));
        BadgeIcons icons = new BadgeIcons();
        icons.register(chatIcons);
        return new BadgeService(relay, new Gson(), settings, store, run, client, icons, () -> now, Runnable::run);
    }

    private Runnable record(String what, BadgeService.Reply reply)
    {
        Sent request = new Sent(what, reply);
        sent.add(request);
        return () -> request.cancelled = true;
    }

    private List<String> requests()
    {
        List<String> what = new ArrayList<>();
        for (Sent request : sent)
        {
            what.add(request.what);
        }
        return what;
    }

    private long count(String method)
    {
        return requests().stream().filter(what -> what.startsWith(method + " ")).count();
    }

    private Sent last()
    {
        return sent.get(sent.size() - 1);
    }

    private void ticks(int count)
    {
        for (int i = 0; i < count; i++)
        {
            service.onGameTick(new GameTick());
        }
    }

    private void settingsChanged()
    {
        ConfigChanged changed = new ConfigChanged();
        changed.setGroup("fatelocked");
        changed.setKey("shareBadge");
        service.onConfigChanged(changed);
    }

    // ── The list ─────────────────────────────────────────────────────────────

    @Test
    public void withoutOnlineSyncNothingIsAskedOrSent()
    {
        sync = false;
        share = true;
        service.start();
        ticks(50);
        now += TimeUnit.DAYS.toMillis(2);
        ticks(50);
        assertEquals(new ArrayList<String>(), requests());
        assertTrue(stored.isEmpty());
    }

    @Test
    public void theListComesAtLoginAndEveryHalfHourWithItsETag()
    {
        service.start();
        ticks(1);
        assertEquals(Arrays.asList("GET /badges"), requests());
        last().reply.replied(200, "\"v1\"", null, LIST);
        assertEquals(BadgeTier.TRAILBLAZER, service.list().tierOf("iron_example"));

        now += 29 * MINUTE;
        ticks(5);
        assertEquals(1, sent.size());
        now += MINUTE;
        ticks(1);
        assertEquals("GET /badges If-None-Match \"v1\"", last().what);
        last().reply.replied(304, "\"v1\"", null, null);
        assertEquals(2, service.list().size());
    }

    @Test
    public void aFailureKeepsTheListAndTriesAgainInFiveMinutes()
    {
        service.start();
        ticks(1);
        last().reply.replied(200, "\"v1\"", null, LIST);
        BadgeList held = service.list();

        now += 30 * MINUTE;
        ticks(1);
        last().reply.failed();
        assertSame(held, service.list());
        now += 4 * MINUTE;
        ticks(3);
        assertEquals(2, sent.size());
        now += MINUTE;
        ticks(1);
        assertEquals(3, sent.size());
        last().reply.replied(503, null, null, null);
        last().reply.replied(200, null, null, "<html>captive portal</html>");
        assertSame(held, service.list());
    }

    @Test
    public void anOldRelaysNotFoundMeansNoBadges()
    {
        service.start();
        ticks(1);
        last().reply.replied(200, "\"v1\"", null, LIST);
        now += 30 * MINUTE;
        ticks(1);
        last().reply.replied(404, null, null, null);
        assertTrue(service.list().isEmpty());
        // And the next ask carries no stale ETag.
        now += 30 * MINUTE;
        ticks(1);
        assertEquals("GET /badges", last().what);
    }

    @Test
    public void theListIsntFetchedWhileNothingShowsIt()
    {
        inChat = false;
        aboveHeads = false;
        service.start();
        ticks(20);
        assertEquals(0, sent.size());

        inChat = true;
        settingsChanged();
        ticks(1);
        assertEquals(Arrays.asList("GET /badges"), requests());
    }

    @Test
    public void theListWaitsForALogin()
    {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        service.start();
        assertEquals(0, sent.size());

        GameStateChanged login = new GameStateChanged();
        login.setGameState(GameState.LOGGED_IN);
        service.onGameStateChanged(login);
        ticks(1);
        assertEquals(Arrays.asList("GET /badges"), requests());
        last().reply.replied(200, "\"v1\"", null, LIST);

        // A loading screen is not a login: no second fetch.
        GameStateChanged loading = new GameStateChanged();
        loading.setGameState(GameState.LOADING);
        service.onGameStateChanged(loading);
        service.onGameStateChanged(login);
        ticks(2);
        assertEquals(1, sent.size());
    }

    @Test
    public void aReplyAfterStoppingChangesNothing()
    {
        service.start();
        ticks(1);
        Sent request = last();
        service.stop();
        assertTrue(request.cancelled);
        request.reply.replied(200, "\"v1\"", null, LIST);
        assertTrue(service.list().isEmpty());
    }

    // ── The player's own badge ───────────────────────────────────────────────

    @Test
    public void sharingPostsOnceThenOnceADay()
    {
        share = true;
        service.start();
        assertEquals("POST /r/" + CODE + "/badge Iron Example 2", sent.get(0).what);
        // The code is remembered before the reply, so the badge can always come down.
        assertEquals(CODE, stored.get(BadgeService.SHARED_CODE_KEY));
        sent.get(0).reply.replied(200, null, null, "{\"ok\":true}");
        assertEquals("2|" + START + "|Iron Example", stored.get(BadgeService.SHARED_KEY));

        ticks(100);
        now += TimeUnit.HOURS.toMillis(23);
        ticks(100);
        assertEquals(1, count("POST"));
        now += TimeUnit.HOURS.toMillis(1);
        ticks(BadgeService.DECIDE_EVERY_TICKS);
        assertEquals(2, count("POST"));
    }

    @Test
    public void aNewMilestoneIsSentAtOnce()
    {
        share = true;
        service.start();
        sent.get(0).reply.replied(200, null, null, null);
        progress = new Progress(Progress.AREAS, 141, 187, 0, 0);
        ticks(BadgeService.DECIDE_EVERY_TICKS);
        assertEquals("POST /r/" + CODE + "/badge Iron Example 3", last().what);
    }

    @Test
    public void whatWasSharedIsRememberedAcrossStarts()
    {
        share = true;
        service.start();
        sent.get(0).reply.replied(200, null, null, null);
        service.stop();

        service = newService();
        service.start();
        ticks(BadgeService.DECIDE_EVERY_TICKS * 3);
        assertEquals(1, count("POST"));
    }

    @Test
    public void onlyTheBoundCharacterShares()
    {
        share = true;
        boundName = null;
        service.start();
        ticks(BadgeService.DECIDE_EVERY_TICKS * 3);
        assertEquals(0, count("POST"));
    }

    @Test
    public void turningSharingOffDeletesItOnce()
    {
        share = true;
        service.start();
        sent.get(0).reply.replied(200, null, null, null);

        share = false;
        settingsChanged();
        assertEquals("DELETE /r/" + CODE + "/badge", last().what);
        last().reply.replied(200, null, null, "{\"deleted\":true}");
        assertNull(stored.get(BadgeService.SHARED_CODE_KEY));
        assertNull(stored.get(BadgeService.SHARED_KEY));
        ticks(BadgeService.DECIDE_EVERY_TICKS * 3);
        settingsChanged();
        assertEquals(1, count("DELETE"));
        assertEquals(1, count("POST"));
    }

    @Test
    public void aFailedDeleteIsTriedAgainLater()
    {
        share = true;
        service.start();
        sent.get(0).reply.replied(200, null, null, null);
        share = false;
        settingsChanged();
        last().reply.failed();
        assertEquals(CODE, stored.get(BadgeService.SHARED_CODE_KEY));
        ticks(BadgeService.DECIDE_EVERY_TICKS * 3);
        assertEquals(1, count("DELETE"));
        now += 5 * MINUTE;
        ticks(BadgeService.DECIDE_EVERY_TICKS);
        assertEquals(2, count("DELETE"));
        Sent retry = sent.stream().filter(request -> request.what.startsWith("DELETE ")).reduce((a, b) -> b).get();
        // 429 waits as long as the relay asks.
        retry.reply.replied(429, null, "600", null);
        now += 5 * MINUTE;
        ticks(BadgeService.DECIDE_EVERY_TICKS);
        assertEquals(2, count("DELETE"));
        now += 5 * MINUTE;
        ticks(BadgeService.DECIDE_EVERY_TICKS);
        assertEquals(3, count("DELETE"));
        assertEquals(1, count("POST"));
    }

    @Test
    public void disconnectingTakesTheBadgeDownFromTheOldCode()
    {
        share = true;
        service.start();
        sent.get(0).reply.replied(200, null, null, null);

        code = "";
        settingsChanged();
        assertEquals("DELETE /r/" + CODE + "/badge", last().what);
        last().reply.replied(200, null, null, null);
        assertTrue(stored.isEmpty());
    }

    /** No profile yet (the tracker hasn't published it): the badge isn't up, so it's sent again. */
    @Test
    public void aPostRefusedForWantOfAProfileIsTriedAgainInTenMinutes()
    {
        share = true;
        service.start();
        sent.get(0).reply.replied(404, null, null, "{\"error\":\"no profile\"}");
        assertEquals("the code, so it can come down", CODE, stored.get(BadgeService.SHARED_CODE_KEY));
        assertNull("but not taken as shared", stored.get(BadgeService.SHARED_KEY));
        assertNull(service.shared().getName());

        ticks(BadgeService.DECIDE_EVERY_TICKS * 5);
        now += 9 * MINUTE;
        ticks(BadgeService.DECIDE_EVERY_TICKS);
        assertEquals(1, count("POST"));
        now += MINUTE;
        ticks(BadgeService.DECIDE_EVERY_TICKS);
        assertEquals(2, count("POST"));
        last().reply.replied(200, null, null, "{\"ok\":true}");
        assertEquals("2|" + now + "|Iron Example", stored.get(BadgeService.SHARED_KEY));
    }

    /** A change on the connection (the pairing confirmed) sends it without the wait. */
    @Test
    public void aPostWaitingForTheProfileGoesWhenTheConnectionChanges()
    {
        share = true;
        service.start();
        sent.get(0).reply.replied(404, null, null, "{\"error\":\"no profile\"}");
        service.connectionChanged();
        assertEquals(2, count("POST"));
        last().reply.replied(200, null, null, null);

        // Otherwise a connection change only looks again: nothing to send.
        service.connectionChanged();
        assertEquals(2, count("POST"));
    }

    /** A failure's wait isn't cut short by a connection change: it isn't about the profile. */
    @Test
    public void aConnectionChangeKeepsAFailuresWait()
    {
        share = true;
        service.start();
        sent.get(0).reply.failed();
        service.connectionChanged();
        assertEquals(1, count("POST"));
        now += 5 * MINUTE;
        service.connectionChanged();
        assertEquals(2, count("POST"));
    }

    /** No code while a first pairing waits for the browser: nothing posts until it confirms. */
    @Test
    public void nothingPostsUntilThePairingsProfileIsThere()
    {
        share = true;
        code = "";
        service.start();
        ticks(BadgeService.DECIDE_EVERY_TICKS * 3);
        assertEquals(0, count("POST"));

        code = CODE;
        service.connectionChanged();
        assertEquals("POST /r/" + CODE + "/badge Iron Example 2", last().what);
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    @Test
    public void aHoldersNameInChatGetsTheirKeyOnce()
    {
        service.start();
        ticks(1);
        last().reply.replied(200, "\"v1\"", null, LIST);

        Object[] objects = {"", "<img=2>Iron Example", "Hello", "00:00"};
        assertEquals("<img=2><img=53>Iron Example", building(ChatMessageType.PUBLICCHAT, objects));
        // The chatbox builds the line again from the message: still one key.
        objects[1] = "<img=2>Iron Example";
        assertEquals("<img=2><img=53>Iron Example", building(ChatMessageType.PUBLICCHAT, objects));
        assertEquals("<img=50>Clan Mate", building(ChatMessageType.CLAN_CHAT, new Object[] {"", "Clan Mate", "", ""}));
        assertEquals("<img=53>Iron Example",
            building(ChatMessageType.PRIVATECHATOUT, new Object[] {"", "Iron Example", "", ""}));
        assertEquals("Someone Else", building(ChatMessageType.PUBLICCHAT, new Object[] {"", "Someone Else", "", ""}));
        assertEquals("Iron Example", building(ChatMessageType.GAMEMESSAGE, new Object[] {"", "Iron Example", "", ""}));

        inChat = false;
        settingsChanged();
        assertEquals("Iron Example", building(ChatMessageType.PUBLICCHAT, new Object[] {"", "Iron Example", "", ""}));
        inChat = true;
        sync = false;
        settingsChanged();
        assertEquals("Iron Example", building(ChatMessageType.PUBLICCHAT, new Object[] {"", "Iron Example", "", ""}));
    }

    @SuppressWarnings("unchecked")
    private String building(ChatMessageType type, Object[] objects)
    {
        MessageNode message = mock(MessageNode.class);
        when(message.getType()).thenReturn(type);
        IterableHashTable<MessageNode> messages = mock(IterableHashTable.class);
        when(messages.get(anyLong())).thenReturn(message);
        when(client.getMessages()).thenReturn(messages);
        when(client.getIntStack()).thenReturn(new int[] {0, 7});
        when(client.getIntStackSize()).thenReturn(2);
        when(client.getObjectStack()).thenReturn(objects);
        when(client.getObjectStackSize()).thenReturn(4);
        ScriptCallbackEvent event = new ScriptCallbackEvent();
        event.setEventName("chatMessageBuilding");
        service.onScriptCallbackEvent(event);
        return (String) objects[1];
    }

    // ── Over heads ───────────────────────────────────────────────────────────

    @Test
    public void holdersOnYourFloorAreFoundOnceATick()
    {
        WorldView view = mock(WorldView.class);
        when(view.getPlane()).thenReturn(0);
        Player near = player("Iron Example", 0, view);
        Player upstairs = player("Clan Mate", 1, view);
        Player stranger = player("Someone Else", 0, view);
        IndexedObjectSet<Player> players = set(near, upstairs, stranger, player(null, 0, view));
        IndexedObjectSet<WorldView> inner = set();
        org.mockito.Mockito.doReturn(players).when(view).players();
        org.mockito.Mockito.doReturn(inner).when(view).worldViews();
        when(client.getTopLevelWorldView()).thenReturn(view);

        service.start();
        ticks(1);
        last().reply.replied(200, "\"v1\"", null, LIST);
        ticks(1);
        BadgeHolders holders = holders();
        assertEquals(1, holders.count());
        assertSame(near, holders.player(0));
        assertEquals(BadgeTier.TRAILBLAZER, holders.tier(0));

        // The Tombs of Amascut hiding other players hides their keys; your own stays.
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_HIDEPLAYERS)).thenReturn(1);
        when(client.getLocalPlayer()).thenReturn(stranger);
        ticks(1);
        assertEquals(0, holders.count());
        when(client.getLocalPlayer()).thenReturn(near);
        ticks(1);
        assertEquals(1, holders.count());
        when(client.getVarbitValue(VarbitID.TOA_CLIENT_HIDEPLAYERS)).thenReturn(0);

        aboveHeads = false;
        settingsChanged();
        ticks(1);
        assertEquals(0, holders.count());
    }

    private BadgeHolders holders()
    {
        try
        {
            java.lang.reflect.Field field = BadgeService.class.getDeclaredField("holders");
            field.setAccessible(true);
            return (BadgeHolders) field.get(service);
        }
        catch (ReflectiveOperationException error)
        {
            throw new AssertionError(error);
        }
    }

    private static Player player(String name, int plane, WorldView view)
    {
        Player player = mock(Player.class);
        when(player.getWorldView()).thenReturn(view);
        when(player.getName()).thenReturn(name);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, plane));
        return player;
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static <T> IndexedObjectSet<T> set(T... items)
    {
        IndexedObjectSet<T> set = mock(IndexedObjectSet.class);
        when(set.iterator()).thenAnswer(invocation -> Arrays.asList(items).iterator());
        return set;
    }
}
