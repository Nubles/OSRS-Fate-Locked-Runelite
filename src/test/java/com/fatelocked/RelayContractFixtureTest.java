package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Every reply in the web app's relay fixture (contracts/relay/relay-get.json)
 * reads as the outcome the fixture gives it: the relay's own replies, and
 * what a proxy, a captive portal or the network can hand the plugin instead.
 * The transport cases need a real connection; RelayTransportFixtureTest
 * runs those.
 */
@RunWith(Parameterized.class)
public class RelayContractFixtureTest
{
    static final String FIXTURE = "contracts/relay/relay-get.json";
    private static final Gson GSON = new Gson();
    /**
     * Replies the plugin does not read as the fixture says yet: what it reads
     * each as today, and the task that fixes it.
     */
    private static final Map<String, Divergence> KNOWN = Map.of(
        "the owner marked the code gone",
        new Divergence(RelayContract.Outcome.MISSING, "review finding S11: Stage 1 task C15"));

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> replies() throws IOException
    {
        List<Object[]> cases = new ArrayList<>();
        for (JsonElement element : fixture().getAsJsonArray("cases"))
        {
            JsonObject relayCase = element.getAsJsonObject();
            if (!relayCase.get("from").getAsString().equals("transport"))
            {
                cases.add(new Object[] {relayCase.get("name").getAsString(), relayCase});
            }
        }
        return cases;
    }

    private final String name;
    private final JsonObject relayCase;

    public RelayContractFixtureTest(String name, JsonObject relayCase)
    {
        this.name = name;
        this.relayCase = relayCase;
    }

    @Test
    public void readsTheReplyAsTheFixtureSays()
    {
        JsonObject response = relayCase.getAsJsonObject("response");
        JsonObject headers = response.getAsJsonObject("headers");
        RelayContract.Reply reply = RelayContract.classify(GSON,
            text(relayCase, "held"),
            null,
            response.get("status").getAsInt(),
            text(headers, "ETag"),
            text(headers, "Retry-After"),
            text(response, "body"));

        Divergence known = KNOWN.get(name);
        if (known != null)
        {
            // Today's answer, until the divergence is fixed on purpose.
            assertEquals(name + " until " + known.fixedBy, known.today, reply.outcome);
            return;
        }
        assertEquals(name, expectedOutcome(relayCase), reply.outcome);
        if (relayCase.has("retryAfterSeconds"))
        {
            assertEquals(name, relayCase.get("retryAfterSeconds").getAsLong(),
                reply.retryAfterSeconds);
        }
        if (reply.outcome == RelayContract.Outcome.RULES)
        {
            JsonObject body = GSON.fromJson(text(response, "body"), JsonObject.class);
            assertEquals(name, body.get("version").getAsInt(), reply.version);
            assertEquals(name, body.get("payload").getAsString(), reply.payload);
        }
    }

    static RelayContract.Outcome expectedOutcome(JsonObject relayCase)
    {
        return RelayContract.Outcome.valueOf(
            relayCase.get("outcome").getAsString().toUpperCase(Locale.ROOT));
    }

    static JsonObject fixture() throws IOException
    {
        try (InputStream in = RelayContractFixtureTest.class.getClassLoader()
            .getResourceAsStream(FIXTURE))
        {
            if (in == null) throw new IOException("missing " + FIXTURE);
            return GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                JsonObject.class);
        }
    }

    private static String text(JsonObject object, String field)
    {
        return object.has(field) && !object.get(field).isJsonNull()
            ? object.get(field).getAsString() : null;
    }

    private static final class Divergence
    {
        private final RelayContract.Outcome today;
        private final String fixedBy;

        private Divergence(RelayContract.Outcome today, String fixedBy)
        {
            this.today = today;
            this.fixedBy = fixedBy;
        }
    }
}
