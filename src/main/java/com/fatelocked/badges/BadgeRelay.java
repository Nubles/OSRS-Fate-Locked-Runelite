package com.fatelocked.badges;

import com.google.gson.Gson;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * The badge requests, and the only ones besides the rules check: GET /badges for the public
 * list, and POST and DELETE /r/<code>/badge for the player's own, all to the fixed Fate Locked
 * relay with RuneLite's OkHttp client, asynchronously, with short limits. BadgeService decides
 * when; this only sends and reads.
 */
public final class BadgeRelay implements BadgeService.Relay
{
    /** A limit on the whole call: badges are never worth a long wait. */
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** 5000 badges are about 150 KiB. */
    static final long MAX_REPLY_BYTES = 512 * 1024;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String CODE_PATTERN = "[0-9a-f]{32}";
    private static final Runnable NO_CANCEL = () -> { };

    private final OkHttpClient http;
    private final Gson gson;
    private final String relayBase;

    /** @param relayBase the relay the rules come from, without a trailing slash */
    public BadgeRelay(OkHttpClient http, Gson gson, String relayBase)
    {
        this.http = http.newBuilder()
            .callTimeout(CALL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .connectTimeout(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .build();
        this.gson = gson;
        this.relayBase = relayBase;
    }

    /** The JSON a POST sends: the name and tier, nothing else. */
    static final class Badge
    {
        final String name;
        final int tier;

        Badge(String name, int tier)
        {
            this.name = name;
            this.tier = tier;
        }
    }

    @Override
    public Runnable fetch(String etag, BadgeService.Reply reply)
    {
        Request.Builder request = request("/badges").get();
        if (etag != null)
        {
            request.header("If-None-Match", etag);
        }
        return send(request, true, reply);
    }

    @Override
    public Runnable publish(String code, String name, int tier, BadgeService.Reply reply)
    {
        RequestBody body = RequestBody.create(JSON, gson.toJson(new Badge(name, tier)));
        return send(request(badgePath(code)).post(body), false, reply);
    }

    @Override
    public Runnable withdraw(String code, BadgeService.Reply reply)
    {
        return send(request(badgePath(code)).delete(), false, reply);
    }

    private static String badgePath(String code)
    {
        if (code == null || !code.matches(CODE_PATTERN))
        {
            throw new IllegalArgumentException("Invalid pairing code");
        }
        return "/r/" + code + "/badge";
    }

    private Request.Builder request(String path)
    {
        return new Request.Builder().url(relayBase + path);
    }

    /** Send it; the reply comes on OkHttp's thread. Returns what cancels it. */
    private Runnable send(Request.Builder request, boolean readBody, BadgeService.Reply reply)
    {
        final Call call;
        try
        {
            call = http.newCall(request.build());
        }
        catch (RuntimeException error)
        {
            reply.failed();
            return NO_CANCEL;
        }
        try
        {
            call.enqueue(new Callback()
            {
                @Override
                public void onFailure(Call call, IOException error)
                {
                    reply.failed();
                }

                @Override
                public void onResponse(Call call, Response response)
                {
                    read(response, readBody, reply);
                }
            });
        }
        catch (RuntimeException error)
        {
            reply.failed();
        }
        return call::cancel;
    }

    private static void read(Response response, boolean readBody, BadgeService.Reply reply)
    {
        int status;
        String etag;
        String retryAfter;
        String body = null;
        try (Response current = response)
        {
            status = current.code();
            etag = current.header("ETag");
            retryAfter = current.header("Retry-After");
            ResponseBody content = current.body();
            if (readBody && status == 200 && content != null)
            {
                BufferedSource source = content.source();
                // Buffers at most one byte past the cap, however long the reply.
                if (source.request(MAX_REPLY_BYTES + 1))
                {
                    reply.failed();
                    return;
                }
                body = content.string();
            }
        }
        catch (IOException | RuntimeException error)
        {
            reply.failed();
            return;
        }
        reply.replied(status, etag, retryAfter, body);
    }
}
