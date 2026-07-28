package com.fatelocked.events;

import com.fatelocked.FateLockedConfig;
import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FateEventRelayClient
{
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MAX_FLUSH = 20;
    private static final long[] RETRY_DELAYS_MS = { 5_000, 10_000, 20_000, 40_000, 60_000 };

    interface TokenStore
    {
        String get(String key);
        void put(String key, String value);
    }

    private static final class ConfigTokenStore implements TokenStore
    {
        private final ConfigManager configManager;

        private ConfigTokenStore(ConfigManager configManager)
        {
            this.configManager = configManager;
        }

        @Override
        public String get(String key)
        {
            return configManager.getConfiguration(FateLockedConfig.GROUP, key);
        }

        @Override
        public void put(String key, String value)
        {
            configManager.setConfiguration(FateLockedConfig.GROUP, key, value);
        }
    }

    private final OkHttpClient client;
    private final Gson gson;
    private final BooleanSupplier enabled;
    private final Supplier<String> pairingCode;
    private final Consumer<Runnable> pairingDispatcher;
    private final TokenStore tokens;
    private final Set<String> flushInFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> acknowledgementInFlight =
        ConcurrentHashMap.newKeySet();
    private volatile String retryPairingCode;
    private volatile int failureCount;
    private volatile long retryAfter;

    public FateEventRelayClient(
        OkHttpClient client,
        Gson gson,
        ConfigManager configManager,
        BooleanSupplier enabled,
        Supplier<String> pairingCode,
        Consumer<Runnable> pairingDispatcher)
    {
        this(client, gson, enabled, pairingCode, pairingDispatcher,
            new ConfigTokenStore(configManager));
    }

    FateEventRelayClient(
        OkHttpClient client,
        Gson gson,
        BooleanSupplier enabled,
        Supplier<String> pairingCode,
        Consumer<Runnable> pairingDispatcher,
        TokenStore tokens)
    {
        this.client = client;
        this.gson = gson;
        this.enabled = enabled;
        this.pairingCode = pairingCode;
        this.pairingDispatcher = pairingDispatcher;
        this.tokens = tokens;
    }

    public void flush(String relayBase, String code, FateEventOutbox outbox)
    {
        if (!canContact(relayBase, code)) return;
        String identity = code.trim();
        if (!retryReady(identity) || !flushInFlight.add(identity)) return;

        List<FateEvent> pending = outbox.pending();
        if (pending.isEmpty())
        {
            flushInFlight.remove(identity);
            return;
        }
        List<FateEvent> batch = new ArrayList<>(
            pending.subList(0, Math.min(MAX_FLUSH, pending.size())));
        Map<String, Object> body = new HashMap<>();
        String token = tokens.get(tokenKey(identity));
        if (token != null && !token.trim().isEmpty()) body.put("token", token);
        body.put("events", batch);

        Request request;
        try
        {
            request = new Request.Builder()
                .url(resourceUrl(relayBase, identity, "events"))
                .post(RequestBody.create(JSON, gson.toJson(body)))
                .build();
        }
        catch (IllegalArgumentException ex)
        {
            completeFlushFailure(identity);
            return;
        }

        client.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException exception)
            {
                completeFlushFailure(identity);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                String responseToken = null;
                boolean successful = false;
                try (Response closed = response)
                {
                    if (closed.isSuccessful() && closed.body() != null)
                    {
                        AppendResponse parsed = gson.fromJson(
                            closed.body().string(), AppendResponse.class);
                        responseToken = parsed == null ? null : parsed.token;
                        successful = true;
                    }
                }
                catch (Exception ignored)
                {
                    // A malformed response is treated as a retryable failure.
                }
                if (successful)
                {
                    completeFlushSuccess(identity, responseToken);
                }
                else
                {
                    completeFlushFailure(identity);
                }
            }
        });
    }

    public void pollAcknowledgements(
        String relayBase, String code, FateEventOutbox outbox)
    {
        if (!canContact(relayBase, code)) return;
        String identity = code.trim();
        if (!acknowledgementInFlight.add(identity)) return;

        Request request;
        try
        {
            request = new Request.Builder()
                .url(resourceUrl(relayBase, identity, "acks"))
                .build();
        }
        catch (IllegalArgumentException ex)
        {
            acknowledgementInFlight.remove(identity);
            return;
        }

        client.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException exception)
            {
                acknowledgementInFlight.remove(identity);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                Set<String> terminal = null;
                try (Response closed = response)
                {
                    if (closed.code() == 404 || !closed.isSuccessful()
                        || closed.body() == null)
                    {
                        acknowledgementInFlight.remove(identity);
                        return;
                    }
                    AcknowledgementBatch parsed = gson.fromJson(
                        closed.body().string(), AcknowledgementBatch.class);
                    if (parsed == null || parsed.acknowledgements == null)
                    {
                        acknowledgementInFlight.remove(identity);
                        return;
                    }
                    terminal = terminalAcknowledgements(parsed);
                }
                catch (Exception ignored)
                {
                    acknowledgementInFlight.remove(identity);
                    return;
                }
                completeAcknowledgements(identity, terminal, outbox);
            }
        });
    }

    private Set<String> terminalAcknowledgements(AcknowledgementBatch batch)
    {
        Set<String> terminal = new HashSet<>();
        for (Acknowledgement acknowledgement : batch.acknowledgements)
        {
            if (acknowledgement != null && acknowledgement.eventId != null
                && ("COMPLETED".equals(acknowledgement.state)
                || "DISMISSED".equals(acknowledgement.state)
                || "DUPLICATE".equals(acknowledgement.state)))
            {
                terminal.add(acknowledgement.eventId);
            }
        }
        return terminal;
    }

    private void completeFlushFailure(String code)
    {
        dispatchPairingMutation(code, () -> {
            if (isCurrentPairing(code)) noteFailure(code);
        }, () -> flushInFlight.remove(code));
    }

    private void completeFlushSuccess(String code, String responseToken)
    {
        dispatchPairingMutation(code, () -> {
            if (responseToken != null && isCurrentPairing(code))
            {
                tokens.put(tokenKey(code), responseToken);
            }
            if (isCurrentPairing(code)) noteSuccess(code);
        }, () -> flushInFlight.remove(code));
    }

    private void completeAcknowledgements(
        String code, Set<String> terminal, FateEventOutbox outbox)
    {
        dispatchPairingMutation(code, () -> {
            if (!terminal.isEmpty() && isCurrentPairing(code))
            {
                try
                {
                    outbox.acknowledge(terminal);
                }
                catch (IOException ignored)
                {
                    // Persistence failure leaves the event pending.
                }
            }
        }, () -> acknowledgementInFlight.remove(code));
    }

    private void dispatchPairingMutation(
        String code, Runnable mutation, Runnable cleanup)
    {
        try
        {
            pairingDispatcher.accept(() -> {
                try
                {
                    if (isCurrentPairing(code)) mutation.run();
                }
                catch (RuntimeException ignored)
                {
                    // Local config/persistence failure remains retryable.
                }
                finally
                {
                    cleanup.run();
                }
            });
        }
        catch (RuntimeException ignored)
        {
            cleanup.run();
        }
    }

    private boolean canContact(String relayBase, String code)
    {
        return isCurrentPairing(code)
            && relayBase != null && !relayBase.trim().isEmpty()
            && code != null && !code.trim().isEmpty();
    }

    private boolean isCurrentPairing(String code)
    {
        if (!enabled.getAsBoolean() || code == null) return false;
        String current = pairingCode.get();
        return current != null
            && code.trim().equals(current.trim());
    }

    private synchronized boolean retryReady(String code)
    {
        return !code.equals(retryPairingCode)
            || System.currentTimeMillis() >= retryAfter;
    }

    private synchronized void noteFailure(String code)
    {
        if (!isCurrentPairing(code)) return;
        if (!code.equals(retryPairingCode))
        {
            retryPairingCode = code;
            failureCount = 0;
            retryAfter = 0;
        }
        failureCount = Math.min(failureCount + 1, RETRY_DELAYS_MS.length);
        retryAfter = System.currentTimeMillis()
            + RETRY_DELAYS_MS[failureCount - 1];
    }

    private synchronized void noteSuccess(String code)
    {
        if (!isCurrentPairing(code)) return;
        retryPairingCode = code;
        failureCount = 0;
        retryAfter = 0;
    }

    private String resourceUrl(
        String relayBase, String code, String resource)
    {
        return relayBase.trim().replaceAll("/+$", "")
            + "/r/" + code + "/" + resource;
    }

    private String tokenKey(String code)
    {
        return "eventToken." + code;
    }

    private static final class AppendResponse
    {
        String token;
    }

    private static final class AcknowledgementBatch
    {
        List<Acknowledgement> acknowledgements;
    }

    private static final class Acknowledgement
    {
        String eventId;
        String state;
    }
}
