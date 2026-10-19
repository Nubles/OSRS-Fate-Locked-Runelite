package com.fatelocked.badges;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The badge requests on the wire (CONTRACT section 2), against a local stand-in for the relay. */
public class BadgeRelayTest
{
    private static final String CODE = "0123456789abcdef0123456789abcdef";

    private MockWebServer server;
    private BadgeRelay relay;

    /** A reply, or "failed". */
    private static final class Outcome implements BadgeService.Reply
    {
        final CompletableFuture<String> done = new CompletableFuture<>();
        volatile String body;
        volatile String etag;

        @Override
        public void replied(int status, String tag, String retryAfter, String content)
        {
            body = content;
            etag = tag;
            done.complete(status + (retryAfter == null ? "" : " retry " + retryAfter));
        }

        @Override
        public void failed()
        {
            done.complete("failed");
        }

        String await() throws Exception
        {
            return done.get(15, TimeUnit.SECONDS);
        }
    }

    @Before
    public void setUp() throws Exception
    {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString();
        relay = new BadgeRelay(new OkHttpClient(), new Gson(), base.substring(0, base.length() - 1));
    }

    @After
    public void tearDown() throws Exception
    {
        server.shutdown();
    }

    @Test
    public void theListIsAskedForWithItsETag() throws Exception
    {
        server.enqueue(new MockResponse().setBody("{\"badges\":[]}").setHeader("ETag", "\"v2\""));
        Outcome outcome = new Outcome();
        relay.fetch("\"v1\"", outcome);
        assertEquals("200", outcome.await());
        assertEquals("{\"badges\":[]}", outcome.body);
        assertEquals("\"v2\"", outcome.etag);

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/badges", request.getPath());
        assertEquals("\"v1\"", request.getHeader("If-None-Match"));
        assertEquals(0, request.getBodySize());
    }

    @Test
    public void theFirstAskHasNoETag() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));
        Outcome outcome = new Outcome();
        relay.fetch(null, outcome);
        assertEquals("404", outcome.await());
        assertNull(outcome.body);
        assertNull(server.takeRequest().getHeader("If-None-Match"));
    }

    @Test
    public void aBadgeIsPostedAsNameAndTierOnly() throws Exception
    {
        server.enqueue(new MockResponse().setBody("{\"ok\":true}"));
        Outcome outcome = new Outcome();
        relay.publish(CODE, "Iron Example", 3, outcome);
        assertEquals("200", outcome.await());
        assertNull(outcome.body);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/r/" + CODE + "/badge", request.getPath());
        assertTrue(request.getHeader("Content-Type").startsWith("application/json"));
        assertEquals("{\"name\":\"Iron Example\",\"tier\":3}", request.getBody().readUtf8());
    }

    @Test
    public void aBadgeIsDeletedWithNoBody() throws Exception
    {
        server.enqueue(new MockResponse().setResponseCode(429).setHeader("Retry-After", "120"));
        Outcome outcome = new Outcome();
        relay.withdraw(CODE, outcome);
        assertEquals("429 retry 120", outcome.await());

        RecordedRequest request = server.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertEquals("/r/" + CODE + "/badge", request.getPath());
        assertEquals(0, request.getBodySize());
    }

    @Test
    public void onlyAPairingCodeGoesInThePath()
    {
        assertThrows(IllegalArgumentException.class, () -> relay.withdraw("../badges", new Outcome()));
        assertThrows(IllegalArgumentException.class, () -> relay.publish("", "Iron Example", 1, new Outcome()));
    }

    @Test
    public void aListTooLongIsAFailure() throws Exception
    {
        StringBuilder body = new StringBuilder();
        while (body.length() <= BadgeRelay.MAX_REPLY_BYTES)
        {
            body.append("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        }
        server.enqueue(new MockResponse().setBody(body.toString()));
        Outcome outcome = new Outcome();
        relay.fetch(null, outcome);
        assertEquals("failed", outcome.await());
    }

    @Test
    public void noAnswerIsAFailure() throws Exception
    {
        server.shutdown();
        Outcome outcome = new Outcome();
        relay.fetch(null, outcome);
        assertEquals("failed", outcome.await());
    }
}
