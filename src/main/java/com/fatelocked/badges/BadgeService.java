package com.fatelocked.badges;

import com.fatelocked.ChunkLocator;
import com.fatelocked.rules.Progress;
import com.google.gson.Gson;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;

/**
 * Fate Locked badges: other players' keys in chat and over their heads, from the relay's
 * public list, and the player's own key on that list when they choose to share it.
 *
 * <p>Nothing here touches the network unless online sync is on. The list is fetched at login
 * and every 30 minutes while logged in, only while a badge setting shows it, with its ETag;
 * failures are quiet and keep the list held. The player's own badge follows
 * {@link BadgePolicy}. One start's service is registered on the event bus while the plugin
 * runs; all of its state lives on the client thread, and relay replies are handed back there.
 */
@Slf4j
public final class BadgeService
{
    /** How often the list is fetched again while logged in. */
    static final long FETCH_EVERY_MILLIS = TimeUnit.MINUTES.toMillis(30);
    /** The wait after a failed request, unless the relay asks for another. */
    static final long RETRY_MILLIS = TimeUnit.MINUTES.toMillis(5);
    /** How many game ticks pass between looks at the player's own badge (about 6 seconds). */
    static final int DECIDE_EVERY_TICKS = 10;
    /** The code the player's badge went up under, so it can come down after a re-pairing. */
    static final String SHARED_CODE_KEY = "badgeSharedCode";
    /** The badge as last accepted: "tier|millis|name". */
    static final String SHARED_KEY = "badgeShared";
    private static final String GROUP = "fatelocked";
    private static final String CHAT_BUILDING = "chatMessageBuilding";

    /** The requests, sent asynchronously; each returns what cancels it. */
    public interface Relay
    {
        Runnable fetch(String etag, Reply reply);

        Runnable publish(String code, String name, int tier, Reply reply);

        Runnable withdraw(String code, Reply reply);
    }

    /** A request's end, on whichever thread it came. */
    public interface Reply
    {
        /** The relay answered; body only for a GET /badges 200. */
        void replied(int status, String etag, String retryAfter, String body);

        /** It never answered: no connection, a timeout, a reply too long or broken off. */
        void failed();
    }

    /** The settings this reads, and the pairing in use. */
    public interface Settings
    {
        boolean sync();

        boolean showInChat();

        boolean showAboveHeads();

        boolean share();

        /** The saved pairing code, or "". */
        String pairingCode();
    }

    /** Where what was shared is remembered between starts: the plugin's own settings group. */
    public interface Store
    {
        String get(String key);

        void set(String key, String value);

        void unset(String key);
    }

    /** The run, read on the client thread. */
    public interface Run
    {
        /** The logged-in character's name when the rules in force are bound to them; else null. */
        String boundName();

        /** The run's progress for this character; null when none. */
        Progress progress();
    }

    private final Relay relay;
    private final Gson gson;
    private final Settings settings;
    private final Store store;
    private final Run run;
    private final Client client;
    private final BadgeIcons icons;
    private final LongSupplier clock;
    private final Consumer<Runnable> onClient;
    private final BadgeHolders holders = new BadgeHolders();
    private final ChunkLocator locator;
    private final BadgeOverlay overlay;

    private boolean sync;
    private boolean showInChat;
    private boolean showAboveHeads;
    private boolean share;
    private boolean loggedIn;
    private volatile boolean stopped;

    private volatile BadgeList list = BadgeList.NONE;
    private String etag;
    private long nextFetchAt;
    private InFlight fetching;

    private BadgePolicy.Shared shared = BadgePolicy.Shared.NONE;
    private long nextSendAt;
    private InFlight sending;
    private int ticksToDecide;

    public BadgeService(Relay relay, Gson gson, Settings settings, Store store, Run run, Client client,
        BadgeIcons icons, LongSupplier clock, Consumer<Runnable> onClient)
    {
        this.relay = relay;
        this.gson = gson;
        this.settings = settings;
        this.store = store;
        this.run = run;
        this.client = client;
        this.icons = icons;
        this.clock = clock;
        this.onClient = onClient;
        this.overlay = new BadgeOverlay(holders, icons);
        this.locator = new ChunkLocator(client);
    }

    /** The overlay that draws keys over heads. */
    public BadgeOverlay overlay()
    {
        return overlay;
    }

    /** The list held now. */
    public BadgeList list()
    {
        return list;
    }

    /** On the client thread, once the plugin is up. */
    public void start()
    {
        refreshSettings();
        shared = loadShared();
        loggedIn = client.getGameState() == GameState.LOGGED_IN;
        nextFetchAt = 0;
        ticksToDecide = DECIDE_EVERY_TICKS;
        decide();
    }

    /** The plugin is stopping: cancel what's in flight; replies that still come change nothing. */
    public void stop()
    {
        stopped = true;
        cancel(fetching);
        cancel(sending);
        fetching = null;
        sending = null;
        holders.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        String key = event.getKey();
        if (GROUP.equals(event.getGroup()) && !SHARED_CODE_KEY.equals(key) && !SHARED_KEY.equals(key))
        {
            onClient.accept(this::settingsChanged);
        }
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged event)
    {
        onClient.accept(() -> {
            shared = loadShared();
            settingsChanged();
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN)
        {
            loggedIn = false;
            holders.clear();
        }
        else if (state == GameState.LOGGED_IN && !loggedIn)
        {
            // A login, not a loading screen: the list is fetched at once.
            loggedIn = true;
            nextFetchAt = 0;
            ticksToDecide = 1;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (stopped)
        {
            return;
        }
        maybeFetch();
        if (--ticksToDecide <= 0)
        {
            ticksToDecide = DECIDE_EVERY_TICKS;
            decide();
        }
        scanHeads();
    }

    /** As the chatbox builds a line: a badge before the name of a holder who sent or got it. */
    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if (!CHAT_BUILDING.equals(event.getEventName()) || !sync || !showInChat)
        {
            return;
        }
        BadgeList badges = list;
        if (badges.isEmpty())
        {
            return;
        }
        int[] ints = client.getIntStack();
        int intCount = client.getIntStackSize();
        Object[] objects = client.getObjectStack();
        int objectCount = client.getObjectStackSize();
        if (ints == null || objects == null || intCount < 1 || objectCount < 3)
        {
            return;
        }
        MessageNode message = client.getMessages().get(ints[intCount - 1]);
        if (message == null || !BadgeChat.badged(message.getType()) || !(objects[objectCount - 3] instanceof String))
        {
            return;
        }
        String name = (String) objects[objectCount - 3];
        String tag = BadgeChat.tag(icons.chatIndex(badges.tierOf(name)));
        if (tag != null)
        {
            objects[objectCount - 3] = BadgeChat.withBadge(name, tag);
        }
    }

    void settingsChanged()
    {
        if (stopped)
        {
            return;
        }
        boolean wasShowing = showing();
        refreshSettings();
        if (!wasShowing && showing())
        {
            nextFetchAt = 0;
        }
        if (!sync || !showAboveHeads)
        {
            holders.clear();
        }
        decide();
    }

    private void refreshSettings()
    {
        sync = settings.sync();
        showInChat = settings.showInChat();
        showAboveHeads = settings.showAboveHeads();
        share = settings.share();
    }

    private boolean showing()
    {
        return sync && (showInChat || showAboveHeads);
    }

    // ── The list ─────────────────────────────────────────────────────────────

    private void maybeFetch()
    {
        long now = clock.getAsLong();
        if (!loggedIn || !showing() || fetching != null || now < nextFetchAt)
        {
            return;
        }
        InFlight request = new InFlight();
        // Marked in flight before the send: a reply can come before fetch returns.
        fetching = request;
        request.cancel = relay.fetch(etag, new Reply()
        {
            @Override
            public void replied(int status, String tag, String retryAfter, String body)
            {
                onClient.accept(() -> fetched(request, status, tag, retryAfter, body));
            }

            @Override
            public void failed()
            {
                onClient.accept(() -> fetchFailed(request));
            }
        });
    }

    private void fetched(InFlight request, int status, String tag, String retryAfter, String body)
    {
        if (stopped || request != fetching)
        {
            return;
        }
        fetching = null;
        long now = clock.getAsLong();
        BadgeReplies.Fetched reply = BadgeReplies.fetched(gson, status, tag, body);
        switch (reply.outcome)
        {
            case LIST:
                list = reply.list;
                etag = reply.etag;
                nextFetchAt = now + FETCH_EVERY_MILLIS;
                break;
            case NONE:
                list = BadgeList.NONE;
                etag = null;
                nextFetchAt = now + FETCH_EVERY_MILLIS;
                break;
            case UNCHANGED:
                nextFetchAt = now + FETCH_EVERY_MILLIS;
                break;
            case KEEP:
            default:
                nextFetchAt = now + BadgeReplies.waitMillis(status == 429 ? retryAfter : null, RETRY_MILLIS);
                log.debug("Badge list not refreshed: HTTP {}", status);
        }
    }

    private void fetchFailed(InFlight request)
    {
        if (stopped || request != fetching)
        {
            return;
        }
        fetching = null;
        nextFetchAt = clock.getAsLong() + RETRY_MILLIS;
    }

    // ── The player's own badge ───────────────────────────────────────────────

    /** Send what the policy says, if anything, unless a send is in flight or waiting out a failure. */
    void decide()
    {
        if (stopped || sending != null)
        {
            return;
        }
        long now = clock.getAsLong();
        if (now < nextSendAt)
        {
            return;
        }
        String boundName = loggedIn && share ? run.boundName() : null;
        int tier = boundName == null ? BadgeTier.NONE : BadgeTier.of(run.progress());
        BadgePolicy.Facts facts = new BadgePolicy.Facts(share, sync, settings.pairingCode(), boundName, tier);
        BadgePolicy.Action action = BadgePolicy.decide(facts, shared, now);
        switch (action.getKind())
        {
            case POST:
                post(action);
                break;
            case DELETE:
                delete(action);
                break;
            case NOTHING:
            default:
                break;
        }
    }

    private void post(BadgePolicy.Action action)
    {
        if (!shared.getCode().equals(action.getCode()))
        {
            // Remembered before the send, so a badge whose reply is lost still comes down.
            shared = new BadgePolicy.Shared(action.getCode(), null, BadgeTier.NONE, 0);
            saveShared();
        }
        send(action, reply -> relay.publish(action.getCode(), action.getName(), action.getTier(), reply));
    }

    private void delete(BadgePolicy.Action action)
    {
        send(action, reply -> relay.withdraw(action.getCode(), reply));
    }

    private interface Request
    {
        Runnable send(Reply reply);
    }

    private void send(BadgePolicy.Action action, Request request)
    {
        InFlight inFlight = new InFlight();
        sending = inFlight;
        inFlight.cancel = request.send(new Reply()
        {
            @Override
            public void replied(int status, String tag, String retryAfter, String body)
            {
                onClient.accept(() -> sent(inFlight, action, status, retryAfter));
            }

            @Override
            public void failed()
            {
                onClient.accept(() -> sent(inFlight, action, -1, null));
            }
        });
    }

    /** A send ended: status -1 when it never got an answer. */
    private void sent(InFlight request, BadgePolicy.Action action, int status, String retryAfter)
    {
        if (stopped || request != sending)
        {
            return;
        }
        sending = null;
        long now = clock.getAsLong();
        boolean post = action.getKind() == BadgePolicy.Kind.POST;
        BadgeReplies.Sent outcome = status < 0 ? BadgeReplies.Sent.RETRY
            : post ? BadgeReplies.posted(status) : BadgeReplies.deleted(status);
        switch (outcome)
        {
            case DONE:
            case NO_PROFILE:
                // A refused post waits for a change or a day too, rather than asking every few seconds.
                nextSendAt = 0;
                if (post)
                {
                    shared = new BadgePolicy.Shared(action.getCode(), action.getName(), action.getTier(), now);
                    saveShared();
                }
                else if (shared.getCode().equals(action.getCode()))
                {
                    shared = BadgePolicy.Shared.NONE;
                    saveShared();
                }
                decide();
                break;
            case RETRY:
            default:
                nextSendAt = now + BadgeReplies.waitMillis(status == 429 ? retryAfter : null, RETRY_MILLIS);
                log.debug("Badge {} not sent: HTTP {}", action.getKind(), status);
        }
    }

    BadgePolicy.Shared shared()
    {
        return shared;
    }

    private BadgePolicy.Shared loadShared()
    {
        String code = store.get(SHARED_CODE_KEY);
        if (code == null || !code.trim().matches("[0-9a-f]{32}"))
        {
            return BadgePolicy.Shared.NONE;
        }
        String last = store.get(SHARED_KEY);
        if (last != null)
        {
            String[] parts = last.split("\\|", 3);
            try
            {
                if (parts.length == 3 && !parts[2].isEmpty())
                {
                    return new BadgePolicy.Shared(code.trim(), parts[2], Integer.parseInt(parts[0]),
                        Long.parseLong(parts[1]));
                }
            }
            catch (NumberFormatException ignored)
            {
                // Sent again, as if never accepted.
            }
        }
        return new BadgePolicy.Shared(code.trim(), null, BadgeTier.NONE, 0);
    }

    private void saveShared()
    {
        try
        {
            if (!shared.live())
            {
                store.unset(SHARED_CODE_KEY);
                store.unset(SHARED_KEY);
                return;
            }
            store.set(SHARED_CODE_KEY, shared.getCode());
            if (shared.getName() == null)
            {
                store.unset(SHARED_KEY);
            }
            else
            {
                store.set(SHARED_KEY, shared.getTier() + "|" + shared.getAt() + "|" + shared.getName());
            }
        }
        catch (RuntimeException error)
        {
            log.warn("Could not remember the shared badge: {}", error.getMessage());
        }
    }

    // ── Over heads ───────────────────────────────────────────────────────────

    private void scanHeads()
    {
        if (!sync || !showAboveHeads || !loggedIn)
        {
            holders.clear();
            return;
        }
        // In the Tombs of Amascut the game can hide the other players; their keys go too.
        boolean othersHidden = client.getVarbitValue(VarbitID.TOA_CLIENT_HIDEPLAYERS) == 1;
        Player self = client.getLocalPlayer();
        holders.scan(client.getTopLevelWorldView(), list,
            player -> (!othersHidden || player == self) && locator.onShownFloor(player));
    }

    /** A request in flight; its replies count only while it is the one held. */
    private static final class InFlight
    {
        volatile Runnable cancel;
    }

    private static void cancel(InFlight request)
    {
        if (request != null && request.cancel != null)
        {
            try
            {
                request.cancel.run();
            }
            catch (RuntimeException ignored)
            {
                // Cancelling is best effort; its reply is ignored either way.
            }
        }
    }
}
