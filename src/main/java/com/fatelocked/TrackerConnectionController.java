package com.fatelocked;

import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

final class TrackerConnectionController
{
    static final long CONNECTED_POLL_SECONDS = 60;
    static final long WAITING_POLL_SECONDS = 5;
    static final long FAILURE_BACKOFF_SECONDS = 30;
    static final long MAX_FAILURE_BACKOFF_SECONDS = 15 * 60;
    /** How long a pairing started here waits for the browser to publish. */
    static final long PAIRING_CONFIRM_SECONDS = 10 * 60;
    /** Check every WAITING_POLL_SECONDS this long, then every PAIRING_SLOW_POLL_SECONDS. */
    static final long PAIRING_FAST_POLL_WINDOW_SECONDS = 2 * 60;
    static final long PAIRING_SLOW_POLL_SECONDS = 15;
    /** The relay has nothing yet because the player is still confirming. */
    static final String CONFIRM_MESSAGE = "Confirm in browser";
    /** A pairing started here got no profile within PAIRING_CONFIRM_SECONDS. */
    static final String NO_PROFILE_MESSAGE = "No profile received";
    /** The relay's copy lapsed: it keeps a profile 24 hours after the last publish. */
    static final String NO_RECENT_UPDATE_MESSAGE = "No recent update";

    interface RelayBundleImporter
    {
        boolean importBundle(String payload);
    }

    private final OkHttpClient http;
    private final Gson gson;
    private final TrackerConnectionSettings settings;
    private final Clock clock;
    private final Consumer<Runnable> clientDispatcher;
    private final RelayBundleImporter importer;
    private final Consumer<TrackerConnectionSnapshot> listener;
    private final Object pollLock = new Object();

    private long generation;
    private RelayPollToken activePoll;
    private String acceptedVersion;
    private Instant lastSync;
    private Instant nextAutomaticPoll = Instant.EPOCH;
    private int consecutiveFailures;
    private String currentIdentityCode;
    /** When this session began the current pairing; null once it imports. */
    private Instant pairingStartedAt;
    private boolean stopped;
    private volatile TrackerConnectionSnapshot snapshot =
        TrackerConnectionSnapshot.disconnected();

    TrackerConnectionController(
        OkHttpClient http,
        Gson gson,
        TrackerConnectionSettings settings,
        Clock clock,
        Consumer<Runnable> clientDispatcher,
        RelayBundleImporter importer,
        Consumer<TrackerConnectionSnapshot> listener)
    {
        this.http = http;
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
            generation++;
            activePoll = null;
            acceptedVersion = null;
            lastSync = null;
            resetAutomaticPollingLocked();
            currentIdentityCode = code;
            pairingStartedAt = clock.instant();
            snapshot = TrackerConnectionSnapshot.of(
                TrackerConnectionState.WAITING, null, null, CONFIRM_MESSAGE);
            listener.accept(snapshot);
        }
        return PairingSupport.trackerPairingUrl(code);
    }

    void reportBrowserLaunchFailure()
    {
        publish(TrackerConnectionState.OFFLINE,
            "Could not open the web tracker");
    }

    void poll()
    {
        if (!settings.networkAccessAllowed())
        {
            networkAccessChanged();
            return;
        }
        String code = settings.pairingCode();
        String version;
        boolean clearLegacy = false;
        synchronized (pollLock)
        {
            if (stopped) return;
            boolean identityChanged =
                !equal(code, currentIdentityCode);
            if (identityChanged)
            {
                generation++;
                activePoll = null;
                acceptedVersion = null;
                lastSync = null;
                resetAutomaticPollingLocked();
                currentIdentityCode = code;
                pairingStartedAt = null;
                snapshot = code.isEmpty()
                    ? TrackerConnectionSnapshot.disconnected()
                    : TrackerConnectionSnapshot.waiting();
                clearLegacy = !code.isEmpty();
                listener.accept(snapshot);
            }
            if (code.isEmpty())
            {
                nextAutomaticPoll = clock.instant()
                    .plusSeconds(CONNECTED_POLL_SECONDS);
                if (!identityChanged)
                {
                    snapshot = TrackerConnectionSnapshot.disconnected();
                    listener.accept(snapshot);
                }
                return;
            }
            version = acceptedVersion;
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
        if (token.acceptedVersion != null)
        {
            builder.header("If-None-Match", token.acceptedVersion);
        }
        Request request = builder.build();
        try
        {
            http.newCall(request).enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException error)
                {
                    publishIfCurrent(token,
                        TrackerConnectionState.OFFLINE,
                        "Could not reach tracker");
                    scheduleFailure(token, FAILURE_BACKOFF_SECONDS,
                        MAX_FAILURE_BACKOFF_SECONDS);
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
                "Could not reach tracker");
            scheduleFailure(token, FAILURE_BACKOFF_SECONDS,
                MAX_FAILURE_BACKOFF_SECONDS);
            clearPoll(token);
        }
    }

    void pollIfDue()
    {
        if (!settings.networkAccessAllowed())
        {
            networkAccessChanged();
            return;
        }
        synchronized (pollLock)
        {
            Instant now = clock.instant();
            if (stopped || now.isBefore(nextAutomaticPoll))
            {
                return;
            }
            // Reserve the next scheduler tick while this asynchronous request
            // is in flight. Its response will replace this with the healthy or
            // failure cadence.
            nextAutomaticPoll = now.plusSeconds(WAITING_POLL_SECONDS);
        }
        poll();
    }

    void networkAccessChanged()
    {
        synchronized (pollLock)
        {
            generation++;
            activePoll = null;
            acceptedVersion = null;
            lastSync = null;
            pairingStartedAt = null;
            resetAutomaticPollingLocked();
            snapshot = TrackerConnectionSnapshot.disconnected();
            listener.accept(snapshot);
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
            if (acceptedVersion == null)
            {
                return;
            }
            generation++;
            activePoll = null;
            acceptedVersion = null;
            resetAutomaticPollingLocked();
        }
    }

    void stop()
    {
        synchronized (pollLock)
        {
            stopped = true;
            generation++;
            activePoll = null;
            snapshot = TrackerConnectionSnapshot.disconnected();
            listener.accept(snapshot);
        }
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
                || !equal(version, acceptedVersion))
            {
                return null;
            }
            RelayPollToken token = new RelayPollToken(
                generation, code, canonicalVersion(version));
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
            if (current.code() == 304)
            {
                handleNotModified(token, current.header("ETag"));
                return;
            }
            if (current.code() == 404)
            {
                handleNotFound(token);
                return;
            }
            if (!current.isSuccessful() || current.body() == null)
            {
                boolean rateLimited = current.code() == 429;
                publishIfCurrent(token,
                    TrackerConnectionState.OFFLINE,
                    rateLimited
                        ? "Tracker relay is busy; retrying later"
                        : "Tracker is unavailable");
                long retryAfter = rateLimited
                    ? retryAfterSeconds(current.header("Retry-After")) : 0;
                scheduleFailure(token,
                    Math.max(FAILURE_BACKOFF_SECONDS, retryAfter),
                    MAX_FAILURE_BACKOFF_SECONDS);
                clearPoll(token);
                return;
            }

            RelayEnvelope envelope = gson.fromJson(
                current.body().string(), RelayEnvelope.class);
            if (envelope == null || envelope.payload == null)
            {
                scheduleFailure(token, FAILURE_BACKOFF_SECONDS,
                    MAX_FAILURE_BACKOFF_SECONDS);
                clearPoll(token);
                return;
            }
            Integer responseVersion = acceptableVersion(
                token, current.header("ETag"), envelope.version);
            if (responseVersion == null)
            {
                scheduleFailure(token, FAILURE_BACKOFF_SECONDS,
                    MAX_FAILURE_BACKOFF_SECONDS);
                clearPoll(token);
                return;
            }
            if (!isPollCurrent(token))
            {
                clearPoll(token);
                return;
            }
            String canonical = String.valueOf(responseVersion);
            if (publishIfCurrent(token,
                TrackerConnectionState.IMPORTING,
                "Importing tracker data"))
            {
                dispatchImport(
                    token, envelope.payload, canonical);
            }
        }
        catch (Exception error)
        {
            scheduleFailure(token, FAILURE_BACKOFF_SECONDS,
                MAX_FAILURE_BACKOFF_SECONDS);
            clearPoll(token);
        }
    }

    private void handleNotModified(
        RelayPollToken token, String responseEtag)
    {
        String responseVersion = responseEtag == null
            ? token.acceptedVersion
            : canonicalVersion(responseEtag);
        if (token.acceptedVersion == null
            || !token.acceptedVersion.equals(responseVersion))
        {
            clearPoll(token);
            return;
        }
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
                        lastSync = refreshedAt;
                        scheduleHealthyPollLocked();
                        snapshot = TrackerConnectionSnapshot.connected(
                            refreshedAt, acceptedVersion);
                        listener.accept(snapshot);
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

    private void dispatchImport(
        RelayPollToken token,
        String payload,
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
                        imported = importer.importBundle(payload);
                    }
                    catch (RuntimeException error)
                    {
                        imported = false;
                    }
                    if (!imported)
                    {
                        publishIfCurrent(token,
                            TrackerConnectionState.IMPORT_FAILED,
                            "Could not import tracker data");
                        scheduleFailure(token, FAILURE_BACKOFF_SECONDS,
                            MAX_FAILURE_BACKOFF_SECONDS);
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
                        acceptedVersion = version;
                        lastSync = acceptedAt;
                        pairingStartedAt = null;
                        scheduleHealthyPollLocked();
                        snapshot = TrackerConnectionSnapshot.connected(
                            acceptedAt, version);
                        listener.accept(snapshot);
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
            scheduleFailure(token, FAILURE_BACKOFF_SECONDS,
                MAX_FAILURE_BACKOFF_SECONDS);
            clearPoll(token);
        }
    }

    /**
     * The relay has no profile for this code. While a pairing this session
     * started is waiting for the browser, that is expected: say "Confirm in
     * browser" and keep checking, quickly at first. After 10 minutes, say no
     * profile arrived. Otherwise the relay's copy has lapsed (it keeps one
     * for 24 hours after the web app last published): the pairing still
     * works, and opening the web tracker sends the rules again.
     */
    private void handleNotFound(RelayPollToken token)
    {
        Instant now = clock.instant();
        Instant startedAt;
        synchronized (pollLock)
        {
            startedAt = pairingStartedAt;
        }
        if (token.acceptedVersion == null && startedAt != null)
        {
            long waited = Duration.between(startedAt, now).getSeconds();
            if (waited < PAIRING_CONFIRM_SECONDS)
            {
                publishIfCurrent(token,
                    TrackerConnectionState.WAITING, CONFIRM_MESSAGE);
                scheduleAfter(token, waited < PAIRING_FAST_POLL_WINDOW_SECONDS
                    ? WAITING_POLL_SECONDS : PAIRING_SLOW_POLL_SECONDS);
            }
            else
            {
                publishIfCurrent(token,
                    TrackerConnectionState.EXPIRED, NO_PROFILE_MESSAGE);
                scheduleHealthyPoll(token);
            }
        }
        else
        {
            publishIfCurrent(token,
                TrackerConnectionState.WAITING, NO_RECENT_UPDATE_MESSAGE);
            scheduleHealthyPoll(token);
        }
        clearPoll(token);
    }

    private void scheduleAfter(RelayPollToken token, long seconds)
    {
        synchronized (pollLock)
        {
            if (isPollCurrentLocked(token))
            {
                consecutiveFailures = 0;
                nextAutomaticPoll = clock.instant().plusSeconds(seconds);
            }
        }
    }

    private void resetAutomaticPollingLocked()
    {
        consecutiveFailures = 0;
        nextAutomaticPoll = Instant.EPOCH;
    }

    private void scheduleHealthyPoll(RelayPollToken token)
    {
        synchronized (pollLock)
        {
            if (isPollCurrentLocked(token))
            {
                scheduleHealthyPollLocked();
            }
        }
    }

    private void scheduleHealthyPollLocked()
    {
        consecutiveFailures = 0;
        nextAutomaticPoll = clock.instant()
            .plusSeconds(CONNECTED_POLL_SECONDS);
    }

    private void scheduleFailure(
        RelayPollToken token, long minimumSeconds, long maximumSeconds)
    {
        synchronized (pollLock)
        {
            if (!isPollCurrentLocked(token))
            {
                return;
            }
            int shift = Math.min(consecutiveFailures, 5);
            long delay = minimumSeconds * (1L << shift);
            consecutiveFailures++;
            nextAutomaticPoll = clock.instant().plusSeconds(
                Math.min(delay, maximumSeconds));
        }
    }

    private static long retryAfterSeconds(String raw)
    {
        if (raw == null || !raw.trim().matches("[0-9]+"))
        {
            return 0;
        }
        try
        {
            return Long.parseLong(raw.trim());
        }
        catch (NumberFormatException error)
        {
            return 0;
        }
    }

    private Integer acceptableVersion(
        RelayPollToken token, String responseEtag, int bodyVersion)
    {
        Integer responseVersion = responseEtag == null
            ? Integer.valueOf(bodyVersion)
            : parseVersion(responseEtag);
        if (bodyVersion <= 0
            || responseVersion == null
            || responseVersion != bodyVersion)
        {
            return null;
        }
        if (token.acceptedVersion == null)
        {
            return responseVersion;
        }
        Integer previous = parseVersion(token.acceptedVersion);
        return previous != null && responseVersion > previous
            ? responseVersion : null;
    }

    private static Integer parseVersion(String raw)
    {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.startsWith("W/"))
        {
            value = value.substring(2);
        }
        if (value.startsWith("\""))
        {
            if (value.length() < 2 || !value.endsWith("\""))
            {
                return null;
            }
            value = value.substring(1, value.length() - 1);
        }
        else if (value.contains("\""))
        {
            return null;
        }
        if (!value.matches("[1-9][0-9]*")) return null;
        try
        {
            return Integer.valueOf(value);
        }
        catch (NumberFormatException error)
        {
            return null;
        }
    }

    private static String canonicalVersion(String raw)
    {
        Integer parsed = parseVersion(raw);
        return parsed == null ? raw : String.valueOf(parsed);
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
            canonicalVersion(acceptedVersion));
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
            snapshot = snapshotForLocked(state, explicitMessage);
            listener.accept(snapshot);
            return true;
        }
    }

    private void publish(
        TrackerConnectionState state, String explicitMessage)
    {
        synchronized (pollLock)
        {
            snapshot = snapshotForLocked(state, explicitMessage);
            listener.accept(snapshot);
        }
    }

    private TrackerConnectionSnapshot snapshotForLocked(
        TrackerConnectionState state, String explicitMessage)
    {
        if (state == TrackerConnectionState.DISCONNECTED)
        {
            return TrackerConnectionSnapshot.disconnected();
        }
        else if (state == TrackerConnectionState.WAITING)
        {
            return explicitMessage == null
                ? TrackerConnectionSnapshot.waiting()
                : TrackerConnectionSnapshot.of(
                    state, lastSync, acceptedVersion, explicitMessage);
        }
        else if (state == TrackerConnectionState.CONNECTED)
        {
            return TrackerConnectionSnapshot.connected(
                lastSync, acceptedVersion);
        }
        else
        {
            return TrackerConnectionSnapshot.of(
                state, lastSync, acceptedVersion,
                explicitMessage == null
                    ? defaultMessage(state) : explicitMessage);
        }
    }

    private static String defaultMessage(TrackerConnectionState state)
    {
        switch (state)
        {
            case PREPARING:
                return "Preparing connection";
            case IMPORTING:
                return "Importing tracker data";
            case EXPIRED:
                return "Pairing request expired";
            case OFFLINE:
                return "Tracker is offline";
            case IMPORT_FAILED:
                return "Could not import tracker data";
            default:
                return "";
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

        private RelayPollToken(
            long generation,
            String code,
            String acceptedVersion)
        {
            this.generation = generation;
            this.code = code;
            this.acceptedVersion = acceptedVersion;
        }
    }

    private static final class RelayEnvelope
    {
        private int version;
        private String payload;
    }
}
