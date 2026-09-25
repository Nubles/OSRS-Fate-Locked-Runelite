package com.fatelocked;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Runs the tracker connection: the one relay request, its threads and its
 * locking. What each reply means is RelayContract's; the state and timing
 * that follow are SyncMachine's, always changed under pollLock.
 */
@Slf4j
final class TrackerConnectionController
{
    /**
     * Turns a relay payload into active rules in two steps. prepare runs on
     * the thread that read the reply, so a large bundle is parsed off the
     * game thread; commit runs on the client thread.
     */
    interface RelayBundleImporter<T>
    {
        /** Parse and check the payload; null rejects it. */
        T prepare(String payload);

        /** Switch to the prepared rules, which the relay calls version; false rejects them. */
        boolean commit(T prepared, String version);
    }

    /**
     * A limit on the whole request. OkHttp limits each connect, read and
     * write, but not the whole call unless asked, so a reply that trickled
     * in slowly held the only check for as long as it liked.
     */
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(20);
    /** No real reply comes near this: the relay refuses to store a bundle over 256 KiB. */
    static final long MAX_REPLY_BYTES = 1024 * 1024;

    private final OkHttpClient http;
    private final Gson gson;
    private final TrackerConnectionSettings settings;
    private final Clock clock;
    private final Consumer<Runnable> clientDispatcher;
    private final RelayBundleImporter<?> importer;
    private final Consumer<TrackerConnectionSnapshot> listener;
    private final Object pollLock = new Object();

    private final SyncMachine machine = new SyncMachine();
    /** Logs each kind of unreadable reply, and a repeat at most every 15 minutes. */
    private final RepeatedValueLimiter unreadableLimiter =
        new RepeatedValueLimiter(Duration.ofMinutes(15).toMillis());
    private long generation;
    private RelayPollToken activePoll;
    private String currentIdentityCode;
    private boolean stopped;
    private volatile TrackerConnectionSnapshot snapshot =
        TrackerConnectionSnapshot.disconnected();

    TrackerConnectionController(
        OkHttpClient http,
        Gson gson,
        TrackerConnectionSettings settings,
        Clock clock,
        Consumer<Runnable> clientDispatcher,
        RelayBundleImporter<?> importer,
        Consumer<TrackerConnectionSnapshot> listener)
    {
        this(http, CALL_TIMEOUT, gson, settings, clock, clientDispatcher, importer, listener);
    }

    TrackerConnectionController(
        OkHttpClient http,
        Duration callTimeout,
        Gson gson,
        TrackerConnectionSettings settings,
        Clock clock,
        Consumer<Runnable> clientDispatcher,
        RelayBundleImporter<?> importer,
        Consumer<TrackerConnectionSnapshot> listener)
    {
        this.http = http.newBuilder()
            .callTimeout(callTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .build();
        this.gson = gson;
        this.settings = settings;
        this.clock = clock;
        this.clientDispatcher = clientDispatcher;
        this.importer = importer;
        this.listener = listener;
        this.currentIdentityCode = settings.pairingCode();
        listener.accept(snapshot);
    }

    String beginPairing()
    {
        if (!settings.networkAccessAllowed())
        {
            throw new IllegalStateException("Online sync requires consent");
        }
        String code = PairingSupport.newCode();
        synchronized (pollLock)
        {
            // A stopped controller belongs to a plugin that was turned off;
            // a Connect queued before that must not bring it back.
            if (stopped)
            {
                throw new IllegalStateException("The tracker connection has stopped");
            }
            settings.replacePairingCode(code);
            settings.clearLegacySettings();
            abandonCheckLocked();
            currentIdentityCode = code;
            showLocked(machine.pairingStarted(clock.instant()));
        }
        return PairingSupport.trackerPairingUrl(code);
    }


    void poll()
    {
        if (!settings.networkAccessAllowed())
        {
            networkAccessOff();
            return;
        }
        String code = settings.pairingCode();
        String version;
        boolean clearLegacy = false;
        synchronized (pollLock)
        {
            if (stopped) return;
            if (!equal(code, currentIdentityCode))
            {
                abandonCheckLocked();
                currentIdentityCode = code;
                clearLegacy = !code.isEmpty();
                showLocked(machine.pairingReplaced(!code.isEmpty()));
            }
            if (code.isEmpty())
            {
                showLocked(machine.unpaired(clock.instant()));
                return;
            }
            version = machine.acceptedVersion();
        }
        if (clearLegacy)
        {
            settings.clearLegacySettings();
        }
        RelayPollToken token = beginPoll(code, version);
        if (token == null)
        {
            return;
        }

        Request.Builder builder = new Request.Builder()
            .url(TrackerConnectionSettings.RELAY_BASE_URL
                + "/r/" + token.code)
            .get();
        String validator = token.rejectedVersion != null
            ? token.rejectedVersion : token.acceptedVersion;
        if (validator != null)
        {
            builder.header("If-None-Match", validator);
        }
        Request request = builder.build();
        try
        {
            Call call = http.newCall(request);
            synchronized (pollLock)
            {
                // Abandoned already, by a stop, a consent change or a new pairing.
                if (activePoll != token)
                {
                    return;
                }
                token.call = call;
            }
            call.enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException error)
                {
                    publishIfCurrent(token,
                        TrackerConnectionState.OFFLINE,
                        SyncMachine.UNREACHABLE_MESSAGE);
                    scheduleFailure(token, SyncMachine.FAILURE_BACKOFF_SECONDS);
                    clearPoll(token);
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    handleResponse(token, response);
                }
            });
        }
        catch (RuntimeException error)
        {
            publishIfCurrent(token,
                TrackerConnectionState.OFFLINE,
                SyncMachine.UNREACHABLE_MESSAGE);
            scheduleFailure(token, SyncMachine.FAILURE_BACKOFF_SECONDS);
            clearPoll(token);
        }
    }

    void pollIfDue()
    {
        if (!settings.networkAccessAllowed())
        {
            networkAccessOff();
            return;
        }
        synchronized (pollLock)
        {
            if (stopped || !machine.checkDue(clock.instant()))
            {
                return;
            }
        }
        poll();
    }

    /**
     * A check found consent off. Reset once; the ticks that follow find
     * nothing held and nothing in flight, and change nothing.
     */
    private void networkAccessOff()
    {
        synchronized (pollLock)
        {
            if (activePoll == null
                && machine.acceptedVersion() == null
                && snapshot.getState() == TrackerConnectionState.DISCONNECTED)
            {
                return;
            }
        }
        networkAccessChanged();
    }

    void networkAccessChanged()
    {
        synchronized (pollLock)
        {
            abandonCheckLocked();
            showLocked(machine.networkAccessChanged());
        }
    }

    /**
     * A file or clipboard import has replaced the rules this controller
     * accepted. Forget the accepted version, so the next check fetches the
     * relay copy in full instead of hearing "unchanged", and make that check
     * due now: while paired, the tracker's rules win over a local import.
     */
    void localRulesReplacedTrackerRules()
    {
        synchronized (pollLock)
        {
            if (machine.localRulesReplaced())
            {
                abandonCheckLocked();
            }
        }
    }

    /**
     * Rules this pairing's relay sent were restored from the last start.
     * Remember their version, so the first check asks only whether they are
     * still current (If-None-Match) and a 304 confirms them; newer rules
     * still arrive in full. Ignored once this start has accepted anything.
     */
    void seedAcceptedVersion(String version)
    {
        if (version == null || version.trim().isEmpty())
        {
            return;
        }
        synchronized (pollLock)
        {
            if (!stopped)
            {
                machine.seed(version.trim());
            }
        }
    }

    void stop()
    {
        synchronized (pollLock)
        {
            stopped = true;
            abandonCheckLocked();
            showLocked(TrackerConnectionSnapshot.disconnected());
        }
    }

    /**
     * Drop the check in flight, if any: cancel its request, and make its
     * reply, if one still comes, change nothing.
     */
    private void abandonCheckLocked()
    {
        generation++;
        if (activePoll != null && activePoll.call != null)
        {
            activePoll.call.cancel();
        }
        activePoll = null;
    }

    TrackerConnectionSnapshot snapshot()
    {
        return snapshot;
    }

    /** Whether a relay request is in flight; its handling ends by clearing this. */
    boolean pollInFlight()
    {
        synchronized (pollLock)
        {
            return activePoll != null;
        }
    }

    private RelayPollToken beginPoll(String code, String version)
    {
        synchronized (pollLock)
        {
            if (stopped || !settings.networkAccessAllowed() || activePoll != null
                || !code.equals(settings.pairingCode())
                || !equal(version, machine.acceptedVersion()))
            {
                return null;
            }
            RelayPollToken token = new RelayPollToken(generation, code,
                RelayContract.canonicalVersion(version), machine.rejectedVersion());
            activePoll = token;
            return token;
        }
    }

    private void handleResponse(
        RelayPollToken token, Response response)
    {
        try (Response current = response)
        {
            if (!isPollCurrent(token))
            {
                clearPoll(token);
                return;
            }
            RelayContract.Reply reply = classify(token, current);
            switch (reply.outcome)
            {
                case UNCHANGED:
                    confirmUnchanged(token);
                    return;
                case UNCONFIRMED:
                    // Nothing to show, but the next check waits its turn.
                    showIfCurrent(token, now -> machine.unconfirmed(now));
                    clearPoll(token);
                    return;
                case MISSING:
                    handleNotFound(token);
                    return;
                case BUSY:
                    publishIfCurrent(token, TrackerConnectionState.OFFLINE,
                        SyncMachine.BUSY_MESSAGE);
                    scheduleFailure(token, Math.max(
                        SyncMachine.FAILURE_BACKOFF_SECONDS, reply.retryAfterSeconds));
                    clearPoll(token);
                    return;
                case UNAVAILABLE:
                    publishIfCurrent(token, TrackerConnectionState.OFFLINE,
                        SyncMachine.UNAVAILABLE_MESSAGE);
                    scheduleFailure(token, SyncMachine.FAILURE_BACKOFF_SECONDS);
                    clearPoll(token);
                    return;
                case RULES:
                    if (!isPollCurrent(token))
                    {
                        clearPoll(token);
                        return;
                    }
                    if (publishIfCurrent(token,
                        TrackerConnectionState.IMPORTING,
                        "Importing tracker data"))
                    {
                        prepareImport(importer, token, reply.payload,
                            String.valueOf(reply.version));
                    }
                    return;
                case UNREADABLE:
                    // A captive portal's page or a broken reply: say so,
                    // instead of backing off with the old status showing.
                    if (unreadableLimiter.shouldReport(reply.detail, clock.millis()))
                    {
                        log.warn("Tracker relay sent an unreadable reply: {}", reply.detail);
                    }
                    publishIfCurrent(token, TrackerConnectionState.OFFLINE,
                        SyncMachine.UNREADABLE_MESSAGE);
                    scheduleFailure(token, SyncMachine.FAILURE_BACKOFF_SECONDS);
                    clearPoll(token);
                    return;
                case STILL_REJECTED:
                    showIfCurrent(token, now -> machine.stillRejected(now));
                    clearPoll(token);
                    return;
                case STALE:
                default:
                    scheduleFailure(token, SyncMachine.FAILURE_BACKOFF_SECONDS);
                    clearPoll(token);
            }
        }
        catch (Exception error)
        {
            // The reply broke off, or took too long, before it was read in full.
            publishIfCurrent(token, TrackerConnectionState.OFFLINE,
                SyncMachine.UNREACHABLE_MESSAGE);
            scheduleFailure(token, SyncMachine.FAILURE_BACKOFF_SECONDS);
            clearPoll(token);
        }
    }

    /** Read the reply, a 2xx body only up to MAX_REPLY_BYTES, and say what it means. */
    private RelayContract.Reply classify(RelayPollToken token, Response response)
        throws IOException
    {
        int status = response.code();
        ResponseBody content = response.body();
        String body = null;
        if (status >= 200 && status < 300 && content != null)
        {
            BufferedSource source = content.source();
            // Buffers at most one byte past the cap, however long the reply.
            if (source.request(MAX_REPLY_BYTES + 1))
            {
                return RelayContract.oversized();
            }
            body = content.string();
        }
        return RelayContract.classify(gson, token.acceptedVersion, token.rejectedVersion,
            status, response.header("ETag"), response.header("Retry-After"), body);
    }

    /** The relay says the rules the plugin holds are still current. */
    private void confirmUnchanged(RelayPollToken token)
    {
        try
        {
            clientDispatcher.accept(() -> {
                try
                {
                    if (!isPollCurrent(token)
                        || !acceptedStateUnchanged(token))
                    {
                        return;
                    }
                    Instant refreshedAt = clock.instant();
                    synchronized (pollLock)
                    {
                        if (!isPollCurrentLocked(token)
                            || !acceptedStateUnchangedLocked(token))
                        {
                            return;
                        }
                        showLocked(machine.confirmed(refreshedAt));
                    }
                }
                finally
                {
                    clearPoll(token);
                }
            });
        }
        catch (RuntimeException error)
        {
            clearPoll(token);
        }
    }

    /** Parse the payload here, on the reply's thread, and commit it on the client thread. */
    private <T> void prepareImport(
        RelayBundleImporter<T> importer,
        RelayPollToken token,
        String payload,
        String version)
    {
        T prepared;
        try
        {
            prepared = importer.prepare(payload);
        }
        catch (RuntimeException error)
        {
            prepared = null;
        }
        if (prepared == null)
        {
            showIfCurrent(token, now -> machine.rejected(version, now));
            clearPoll(token);
            return;
        }
        T rules = prepared;
        dispatchImport(token, () -> importer.commit(rules, version), version);
    }

    private void dispatchImport(
        RelayPollToken token,
        BooleanSupplier commit,
        String version)
    {
        try
        {
            clientDispatcher.accept(() -> {
                try
                {
                    if (!isPollCurrent(token)
                        || !acceptedStateUnchanged(token))
                    {
                        return;
                    }
                    boolean imported;
                    try
                    {
                        imported = commit.getAsBoolean();
                    }
                    catch (RuntimeException error)
                    {
                        imported = false;
                    }
                    if (!imported)
                    {
                        showIfCurrent(token, now -> machine.rejected(version, now));
                        return;
                    }

                    Instant acceptedAt = clock.instant();
                    synchronized (pollLock)
                    {
                        if (!isPollCurrentLocked(token)
                            || !acceptedStateUnchangedLocked(token))
                        {
                            return;
                        }
                        showLocked(machine.accepted(version, acceptedAt));
                    }
                }
                finally
                {
                    clearPoll(token);
                }
            });
        }
        catch (RuntimeException error)
        {
            publishIfCurrent(token,
                TrackerConnectionState.IMPORT_FAILED,
                "Could not import tracker data");
            scheduleFailure(token, SyncMachine.FAILURE_BACKOFF_SECONDS);
            clearPoll(token);
        }
    }

    /** The relay has no profile for this code; SyncMachine.notFound says what that means. */
    private void handleNotFound(RelayPollToken token)
    {
        showIfCurrent(token, now -> machine.notFound(token.acceptedVersion != null, now));
        clearPoll(token);
    }

    /**
     * For a current check: apply a machine transition, and show its snapshot
     * unless rules were accepted or forgotten since the check began.
     */
    private void showIfCurrent(
        RelayPollToken token, Function<Instant, TrackerConnectionSnapshot> transition)
    {
        synchronized (pollLock)
        {
            if (!isPollCurrentLocked(token))
            {
                return;
            }
            boolean unchanged = acceptedStateUnchangedLocked(token);
            TrackerConnectionSnapshot next = transition.apply(clock.instant());
            if (unchanged && next != null)
            {
                showLocked(next);
            }
        }
    }

    /**
     * Show a snapshot, under pollLock, unless it says exactly what the last
     * one did. Being under the lock, the listener hears the snapshots in the
     * order they were made, whichever threads made them.
     */
    private void showLocked(TrackerConnectionSnapshot next)
    {
        if (next.equals(snapshot))
        {
            return;
        }
        snapshot = next;
        listener.accept(next);
    }

    private void scheduleFailure(RelayPollToken token, long minimumSeconds)
    {
        synchronized (pollLock)
        {
            if (isPollCurrentLocked(token))
            {
                machine.failed(clock.instant(), minimumSeconds);
            }
        }
    }

    private boolean isPollCurrent(RelayPollToken token)
    {
        synchronized (pollLock)
        {
            return isPollCurrentLocked(token)
                && acceptedStateUnchangedLocked(token);
        }
    }

    private boolean isPollCurrentLocked(RelayPollToken token)
    {
        return !stopped
            && settings.networkAccessAllowed()
            && token != null
            && activePoll == token
            && token.generation == generation
            && token.code.equals(settings.pairingCode());
    }

    private boolean acceptedStateUnchanged(RelayPollToken token)
    {
        synchronized (pollLock)
        {
            return acceptedStateUnchangedLocked(token);
        }
    }

    private boolean acceptedStateUnchangedLocked(RelayPollToken token)
    {
        return equal(token.acceptedVersion,
            RelayContract.canonicalVersion(machine.acceptedVersion()));
    }

    private void clearPoll(RelayPollToken token)
    {
        synchronized (pollLock)
        {
            if (activePoll == token)
            {
                activePoll = null;
            }
        }
    }

    private boolean publishIfCurrent(
        RelayPollToken token,
        TrackerConnectionState state,
        String explicitMessage)
    {
        synchronized (pollLock)
        {
            if (!isPollCurrentLocked(token)
                || !acceptedStateUnchangedLocked(token))
            {
                return false;
            }
            showLocked(machine.show(state, explicitMessage));
            return true;
        }
    }

    private static boolean equal(String left, String right)
    {
        return left == null ? right == null : left.equals(right);
    }

    private static final class RelayPollToken
    {
        private final long generation;
        private final String code;
        private final String acceptedVersion;
        /** The version the plugin could not import, sent as the validator instead. */
        private final String rejectedVersion;
        /** The request, once sent; cancelled if the check is abandoned. Guarded by pollLock. */
        private Call call;

        private RelayPollToken(
            long generation,
            String code,
            String acceptedVersion,
            String rejectedVersion)
        {
            this.generation = generation;
            this.code = code;
            this.acceptedVersion = acceptedVersion;
            this.rejectedVersion = rejectedVersion;
        }
    }
}
