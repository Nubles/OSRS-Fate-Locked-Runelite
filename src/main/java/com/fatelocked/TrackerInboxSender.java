package com.fatelocked;

import com.fatelocked.events.FateEvent;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Send to tracker: the plugin's one write to the relay, POST /r/&lt;code&gt;/inbox, only ever when the
 * player presses the Roll inbox card's button. The events go in requests of at most 100, one after
 * another, each with Copy for tracker's body; a failure ends the send, and nothing is retried. What
 * the replies mean is RelayInbox's.
 */
@Slf4j
final class TrackerInboxSender
{
    /** What a send tells the plugin, on OkHttp's threads. */
    interface Listener
    {
        /** The send is under way, before any request: called on the thread that asked for it. */
        default void started()
        {
        }

        /** The relay stored these events: mark them Sent. */
        void sent(List<String> eventIds);

        /** The send is over, and the card says this. Not called for a send stopped with the plugin. */
        void finished(String notice);
    }

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    /** A limit on each whole request, however slowly the reply comes. */
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);
    /** The relay's reply is a few bytes of JSON; nothing longer is read. */
    static final long MAX_REPLY_BYTES = 16 * 1024;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final Gson gson;
    private final TrackerConnectionSettings settings;
    private final Executor executor;
    private final LongSupplier clock;
    private final Object lock = new Object();
    /** The send in progress, or null. Guarded by lock. */
    private Send active;
    private boolean stopped;

    TrackerInboxSender(OkHttpClient http, Gson gson, TrackerConnectionSettings settings, Executor executor,
        LongSupplier clock)
    {
        this(http, CALL_TIMEOUT, gson, settings, executor, clock);
    }

    TrackerInboxSender(OkHttpClient http, Duration callTimeout, Gson gson, TrackerConnectionSettings settings,
        Executor executor, LongSupplier clock)
    {
        // Short limits, no silent retry of a dropped connection, and no redirect: a send happens
        // once, to the relay, when the player asks.
        this.http = http.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .callTimeout(callTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
        this.gson = gson;
        this.settings = settings;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * Send these events to the relay for this pairing code. False, and nothing sent, while another
     * send is under way (a second click), after stop, or without online sync.
     *
     * @param copy what Copy for tracker puts on the clipboard for a list of events
     */
    boolean send(String code, List<FateEvent> events, Function<List<FateEvent>, String> copy, Listener listener)
    {
        if (code == null || code.isEmpty() || events.isEmpty() || !settings.networkAccessAllowed())
        {
            return false;
        }
        Send send = new Send(code, events.size(), listener);
        synchronized (lock)
        {
            if (stopped || active != null)
            {
                return false;
            }
            active = send;
        }
        listener.started();
        List<FateEvent> copied = new ArrayList<>(events);
        try
        {
            // The bodies are made off the Swing thread the click came on.
            executor.execute(() -> start(send, copied, copy));
        }
        catch (RuntimeException error)
        {
            finish(send, RelayInbox.Outcome.UNREACHABLE);
        }
        return true;
    }

    /** Whether a send is under way. */
    boolean sending()
    {
        synchronized (lock)
        {
            return active != null;
        }
    }

    /** The plugin is stopping: cancel the request in flight, and send nothing more. */
    void stop()
    {
        synchronized (lock)
        {
            stopped = true;
            if (active != null)
            {
                if (active.call != null)
                {
                    active.call.cancel();
                }
                active = null;
            }
        }
    }

    private void start(Send send, List<FateEvent> events, Function<List<FateEvent>, String> copy)
    {
        try
        {
            send.batches = RelayInbox.batches(events, copy, clock.getAsLong());
        }
        catch (RuntimeException error)
        {
            log.warn("Could not prepare the events for the relay", error);
            finish(send, RelayInbox.Outcome.NOTHING_SENDABLE);
            return;
        }
        if (send.batches.isEmpty())
        {
            finish(send, RelayInbox.Outcome.NOTHING_SENDABLE);
            return;
        }
        sendBatch(send, 0);
    }

    private void sendBatch(Send send, int index)
    {
        // Consent or the pairing can change between two requests: stop there.
        if (!settings.networkAccessAllowed() || !send.code.equals(settings.pairingCode()))
        {
            finish(send, RelayInbox.Outcome.STOPPED);
            return;
        }
        RelayInbox.Batch batch = send.batches.get(index);
        Request request = new Request.Builder()
            .url(TrackerConnectionSettings.RELAY_BASE_URL + RelayInbox.path(send.code))
            .post(RequestBody.create(JSON, batch.body))
            .build();
        Call call;
        try
        {
            call = http.newCall(request);
        }
        catch (RuntimeException error)
        {
            finish(send, RelayInbox.Outcome.UNREACHABLE);
            return;
        }
        synchronized (lock)
        {
            if (active != send)
            {
                return;
            }
            send.call = call;
        }
        try
        {
            call.enqueue(new Callback()
            {
                @Override
                public void onFailure(Call failed, IOException error)
                {
                    finish(send, RelayInbox.Outcome.UNREACHABLE);
                }

                @Override
                public void onResponse(Call answered, Response response)
                {
                    replied(send, index, batch, response);
                }
            });
        }
        catch (RuntimeException error)
        {
            finish(send, RelayInbox.Outcome.UNREACHABLE);
        }
    }

    private void replied(Send send, int index, RelayInbox.Batch batch, Response response)
    {
        RelayInbox.Outcome outcome;
        try (Response current = response)
        {
            outcome = RelayInbox.classify(gson, current.code(), body(current));
        }
        catch (IOException | RuntimeException error)
        {
            // The reply broke off before it was read: whether the relay stored them isn't known.
            outcome = RelayInbox.Outcome.UNREACHABLE;
        }
        if (outcome != RelayInbox.Outcome.SENT)
        {
            finish(send, outcome);
            return;
        }
        synchronized (lock)
        {
            if (active != send)
            {
                return;
            }
            send.sent += batch.ids.size();
        }
        send.listener.sent(batch.ids);
        if (index + 1 < send.batches.size())
        {
            sendBatch(send, index + 1);
        }
        else
        {
            finish(send, RelayInbox.Outcome.SENT);
        }
    }

    /** The reply's body, or null when there is none or it is longer than any real reply. */
    private static String body(Response response) throws IOException
    {
        ResponseBody content = response.body();
        if (content == null)
        {
            return null;
        }
        BufferedSource source = content.source();
        // Buffers at most one byte past the cap, however long the reply.
        return source.request(MAX_REPLY_BYTES + 1) ? null : content.string();
    }

    /** End a current send: it stops being under way, then the card hears how it went. */
    private void finish(Send send, RelayInbox.Outcome outcome)
    {
        int sent;
        synchronized (lock)
        {
            if (active != send)
            {
                return;
            }
            active = null;
            sent = send.sent;
        }
        send.listener.finished(RelayInbox.notice(sent, send.total, outcome));
    }

    private static final class Send
    {
        private final String code;
        private final int total;
        private final Listener listener;
        /** Set once, before the first request, on the executor's thread. */
        private volatile List<RelayInbox.Batch> batches;
        /** Guarded by lock. */
        private Call call;
        /** Events the relay has taken so far. Guarded by lock. */
        private int sent;

        private Send(String code, int total, Listener listener)
        {
            this.code = code;
            this.total = total;
            this.listener = listener;
        }
    }
}
