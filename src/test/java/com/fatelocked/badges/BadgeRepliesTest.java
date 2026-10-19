package com.fatelocked.badges;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.google.gson.Gson;
import org.junit.Test;

/** What each relay reply means; every failure keeps the list held (CONTRACT section 2). */
public class BadgeRepliesTest
{
    private final Gson gson = new Gson();

    @Test
    public void aListIsReadWithItsETag()
    {
        BadgeReplies.Fetched reply = BadgeReplies.fetched(gson, 200, "\"abc\"",
            "{\"badges\":[{\"n\":\"Iron Example\",\"t\":2},{\"n\":\"Zezima Test\",\"t\":4}]}");
        assertEquals(BadgeReplies.Fetch.LIST, reply.outcome);
        assertEquals("\"abc\"", reply.etag);
        assertEquals(2, reply.list.size());
        assertEquals(BadgeTier.PATHFINDER, reply.list.tierOf("iron_example"));
        assertEquals(BadgeTier.FATE_UNBOUND, reply.list.tierOf("<img=2>Zezima Test"));
        assertEquals(BadgeTier.NONE, reply.list.tierOf("Someone Else"));
    }

    @Test
    public void entriesThatArentANameAndATierAreLeftOut()
    {
        BadgeReplies.Fetched reply = BadgeReplies.fetched(gson, 200, null,
            "{\"badges\":[{\"n\":\"Good One\",\"t\":1},{\"n\":\"Too High\",\"t\":5},{\"n\":\"Half\",\"t\":1.5},"
                + "{\"n\":7,\"t\":1},{\"t\":1},{\"n\":\"No Tier\"},{\"n\":\"\",\"t\":1},\"text\",null,"
                + "{\"n\":\"Negative\",\"t\":-1}],\"extra\":true}");
        assertEquals(BadgeReplies.Fetch.LIST, reply.outcome);
        assertEquals(1, reply.list.size());
        assertEquals(BadgeTier.WAYFARER, reply.list.tierOf("good one"));
        assertNull(reply.etag);
    }

    @Test
    public void anEmptyListIsNoBadges()
    {
        BadgeReplies.Fetched reply = BadgeReplies.fetched(gson, 200, "\"e\"", "{\"badges\":[]}");
        assertEquals(BadgeReplies.Fetch.LIST, reply.outcome);
        assertSame(BadgeList.NONE, reply.list);
    }

    @Test
    public void atMostFiveThousandAreRead()
    {
        StringBuilder body = new StringBuilder("{\"badges\":[");
        for (int i = 0; i < BadgeReplies.MAX_BADGES + 10; i++)
        {
            body.append(i == 0 ? "" : ",").append("{\"n\":\"p").append(i).append("\",\"t\":0}");
        }
        body.append("]}");
        BadgeReplies.Fetched reply = BadgeReplies.fetched(gson, 200, null, body.toString());
        assertEquals(BadgeReplies.MAX_BADGES, reply.list.size());
    }

    @Test
    public void notModifiedKeepsTheList()
    {
        assertEquals(BadgeReplies.Fetch.UNCHANGED, BadgeReplies.fetched(gson, 304, "\"abc\"", null).outcome);
    }

    /** Until the relay is redeployed, /badges answers 404 "not found" in plain text. */
    @Test
    public void anOldRelaysPlainTextNotFoundMeansNoBadges()
    {
        BadgeReplies.Fetched reply = BadgeReplies.fetched(gson, 404, null, "not found");
        assertEquals(BadgeReplies.Fetch.NONE, reply.outcome);
        assertSame(BadgeList.NONE, reply.list);
        assertEquals(BadgeReplies.Fetch.NONE, BadgeReplies.fetched(gson, 404, null, null).outcome);
    }

    @Test
    public void anythingElseKeepsTheListQuietly()
    {
        for (int status : new int[] {500, 502, 429, 403, 201, 0})
        {
            assertEquals("HTTP " + status, BadgeReplies.Fetch.KEEP,
                BadgeReplies.fetched(gson, status, null, "{\"badges\":[]}").outcome);
        }
        for (String body : new String[] {null, "", "not json", "<html>portal</html>", "[]", "{}",
            "{\"badges\":{}}", "{\"badges\":\"x\"}", "null"})
        {
            BadgeReplies.Fetched reply = BadgeReplies.fetched(gson, 200, "\"x\"", body);
            assertEquals(String.valueOf(body), BadgeReplies.Fetch.KEEP, reply.outcome);
            assertNull(reply.list);
        }
    }

    @Test
    public void ownBadgeReplies()
    {
        assertEquals(BadgeReplies.Sent.DONE, BadgeReplies.posted(200));
        assertEquals(BadgeReplies.Sent.NO_PROFILE, BadgeReplies.posted(404));
        assertEquals(BadgeReplies.Sent.RETRY, BadgeReplies.posted(429));
        assertEquals(BadgeReplies.Sent.RETRY, BadgeReplies.posted(400));
        assertEquals(BadgeReplies.Sent.RETRY, BadgeReplies.posted(503));
        assertEquals(BadgeReplies.Sent.DONE, BadgeReplies.deleted(200));
        // Only a relay too old for badges answers a DELETE 404, and it holds none.
        assertEquals(BadgeReplies.Sent.DONE, BadgeReplies.deleted(404));
        assertEquals(BadgeReplies.Sent.RETRY, BadgeReplies.deleted(429));
        assertEquals(BadgeReplies.Sent.RETRY, BadgeReplies.deleted(500));
    }

    @Test
    public void retryAfterIsHonouredWithinBounds()
    {
        assertEquals(300_000, BadgeReplies.waitMillis(null, 300_000));
        assertEquals(300_000, BadgeReplies.waitMillis("soon", 300_000));
        assertEquals(120_000, BadgeReplies.waitMillis("120", 300_000));
        assertEquals(60_000, BadgeReplies.waitMillis("1", 300_000));
        assertEquals(3_600_000, BadgeReplies.waitMillis("999999", 300_000));
    }
}
