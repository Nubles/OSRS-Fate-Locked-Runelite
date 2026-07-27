package com.fatelocked;

import com.google.gson.Gson;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

final class TrackerConnectionController
{
    interface RelayBundleImporter
    {
        boolean importBundle(String payload);
    }

    private static final MediaType JSON =
        MediaType.parse("application/json");

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
    private String currentIdentityCode;
    private String legacyClearedCode;
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
        String code = PairingSupport.newCode();
        synchronized (pollLock)
        {
            settings.replacePairingCode(code);
            generation++;
            activePoll = null;
            acceptedVersion = null;
            lastSync = null;
            currentIdentityCode = code;
            stopped = false;
            snapshot = TrackerConnectionSnapshot.waiting();
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
        String code = settings.pairingCode();
        String version;
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
                currentIdentityCode = code;
                snapshot = code.isEmpty()
                    ? TrackerConnectionSnapshot.disconnected()
                    : TrackerConnectionSnapshot.waiting();
                listener.accept(snapshot);
            }
            if (code.isEmpty())
            {
                if (!identityChanged)
                {
                    snapshot = TrackerConnectionSnapshot.disconnected();
                    listener.accept(snapshot);
                }
                return;
            }
            version = acceptedVersion;
        }
        RelayPollToken token = beginPoll(
            TrackerConnectionSettings.RELAY_BASE_URL, code, version);
        if (token == null)
        {
            return;
        }

        Request.Builder builder = new Request.Builder()
            .url(token.baseUrl + "/r/" + token.code);
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
            clearPoll(token);
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

    private RelayPollToken beginPoll(
        String baseUrl, String code, String version)
    {
        synchronized (pollLock)
        {
            if (stopped || activePoll != null
                || !baseUrl.equals(TrackerConnectionSettings.RELAY_BASE_URL)
                || !code.equals(settings.pairingCode())
                || !equal(version, acceptedVersion))
            {
                return null;
            }
            RelayPollToken token = new RelayPollToken(
                generation, baseUrl, code, canonicalVersion(version));
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
                publishIfCurrent(token,
                    TrackerConnectionState.EXPIRED,
                    "Pairing request expired");
                clearPoll(token);
                return;
            }
            if (!current.isSuccessful() || current.body() == null)
            {
                publishIfCurrent(token,
                    TrackerConnectionState.OFFLINE,
                    "Tracker is unavailable");
                clearPoll(token);
                return;
            }

            RelayEnvelope envelope = gson.fromJson(
                current.body().string(), RelayEnvelope.class);
            if (envelope == null || envelope.payload == null)
            {
                clearPoll(token);
                return;
            }
            Integer responseVersion = acceptableVersion(
                token, current.header("ETag"), envelope.version);
            if (responseVersion == null)
            {
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
                    token, envelope.payload, canonical, envelope.version);
            }
        }
        catch (Exception error)
        {
            clearPoll(token);
        }
    }

    private void handleNotModified(
        RelayPollToken token, String responseEtag)
    {
        String responseVersion = canonicalVersion(responseEtag);
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
        String version,
        int acknowledgementVersion)
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
                        return;
                    }

                    Instant acceptedAt = clock.instant();
                    boolean clearLegacy;
                    synchronized (pollLock)
                    {
                        if (!isPollCurrentLocked(token)
                            || !acceptedStateUnchangedLocked(token))
                        {
                            return;
                        }
                        acceptedVersion = version;
                        lastSync = acceptedAt;
                        clearLegacy = !token.code.equals(legacyClearedCode);
                        if (clearLegacy)
                        {
                            legacyClearedCode = token.code;
                        }
                        snapshot = TrackerConnectionSnapshot.connected(
                            acceptedAt, version);
                        listener.accept(snapshot);
                    }
                    if (clearLegacy)
                    {
                        settings.clearLegacySettings();
                    }
                    postStateAcknowledgement(
                        token, acknowledgementVersion);
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
            clearPoll(token);
        }
    }

    private void postStateAcknowledgement(
        RelayPollToken token, int version)
    {
        if (!isActiveSession(token)) return;
        Map<String, Object> acknowledgement = new HashMap<>();
        acknowledgement.put("ts", clock.millis());
        acknowledgement.put("version", version);

        Map<String, Object> envelope = new HashMap<>();
        String storedToken = settings.token("stateToken", token.code);
        if (storedToken != null)
        {
            envelope.put("token", storedToken);
        }
        envelope.put("payload", gson.toJson(acknowledgement));
        Request request = new Request.Builder()
            .url(token.baseUrl + "/r/" + token.code + "/state")
            .post(RequestBody.create(JSON, gson.toJson(envelope)))
            .build();
        if (!isActiveSession(token)) return;
        http.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException error)
            {
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response current = response)
                {
                    if (!isSessionCurrent(token)
                        || !current.isSuccessful()
                        || current.body() == null)
                    {
                        return;
                    }
                    TokenResponse tokenResponse = gson.fromJson(
                        current.body().string(), TokenResponse.class);
                    if (tokenResponse != null
                        && tokenResponse.token != null
                        && isSessionCurrent(token))
                    {
                        settings.saveToken(
                            "stateToken", token.code,
                            tokenResponse.token);
                    }
                }
                catch (Exception ignored)
                {
                }
            }
        });
    }

    private Integer acceptableVersion(
        RelayPollToken token, String responseEtag, int bodyVersion)
    {
        Integer responseVersion = parseVersion(responseEtag);
        if (responseVersion == null
            || bodyVersion <= 0
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
            && token != null
            && activePoll == token
            && token.generation == generation
            && token.baseUrl.equals(
                TrackerConnectionSettings.RELAY_BASE_URL)
            && token.code.equals(settings.pairingCode());
    }

    private boolean isActiveSession(RelayPollToken token)
    {
        synchronized (pollLock)
        {
            return isPollCurrentLocked(token);
        }
    }

    private boolean isSessionCurrent(RelayPollToken token)
    {
        synchronized (pollLock)
        {
            return !stopped
                && token != null
                && token.generation == generation
                && token.baseUrl.equals(
                    TrackerConnectionSettings.RELAY_BASE_URL)
                && token.code.equals(settings.pairingCode());
        }
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
            return TrackerConnectionSnapshot.waiting();
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
        private final String baseUrl;
        private final String code;
        private final String acceptedVersion;

        private RelayPollToken(
            long generation,
            String baseUrl,
            String code,
            String acceptedVersion)
        {
            this.generation = generation;
            this.baseUrl = baseUrl;
            this.code = code;
            this.acceptedVersion = acceptedVersion;
        }
    }

    private static final class RelayEnvelope
    {
        private int version;
        private String payload;
    }

    private static final class TokenResponse
    {
        private String token;
    }
}
