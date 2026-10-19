package com.fatelocked;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginHubNetworkBoundaryTest
{
    private static final String[] LEGACY_TOKEN_PREFIXES = {
        "eventToken.", "stateToken.", "suggestToken.", "ackToken."
    };

    /**
     * Three request builders, each to the fixed relay: the rules check, GET /r/&lt;code&gt;, in the
     * connection controller; Send to tracker, POST /r/&lt;code&gt;/inbox, in the inbox sender, which
     * the player starts with a click; and the badge requests in BadgeRelay. Nothing else in the
     * plugin builds a request or a request body.
     */
    @Test
    public void productionSourceHasTheFixedRelayRequestsAndNoOthers()
        throws Exception
    {
        Map<Path, String> sources = productionSources();
        String allSource = String.join("\n", sources.values());
        String controllerSource = sourceNamed(
            sources, "TrackerConnectionController.java");
        String compactController =
            controllerSource.replaceAll("\\s+", " ");
        String senderSource = sourceNamed(sources, "TrackerInboxSender.java");
        String compactSender = senderSource.replaceAll("\\s+", " ");

        // The rules check, Send to tracker, and the badge requests
        // (badgeRequestsGoToTheFixedRelayFromOneClass).
        assertEquals(3, occurrences(allSource, "new Request.Builder()"));

        assertEquals(1, occurrences(controllerSource, "new Request.Builder()"));
        assertTrue(compactController.contains(
            "TrackerConnectionSettings.RELAY_BASE_URL"
                + " + \"/r/\" + token.code"));
        assertTrue(controllerSource.contains(".get()"));
        assertFalse(controllerSource.contains(".post("));
        assertFalse(controllerSource.contains(".put("));
        assertFalse(controllerSource.contains(".patch("));
        assertFalse(controllerSource.contains(".delete("));
        assertFalse(controllerSource.contains("RequestBody"));

        assertEquals(1, occurrences(senderSource, "new Request.Builder()"));
        assertTrue(compactSender.contains(
            ".url(TrackerConnectionSettings.RELAY_BASE_URL + RelayInbox.path(send.code))"
                + " .post(RequestBody.create(JSON, batch.body))"));
        assertTrue(sourceNamed(sources, "RelayInbox.java")
            .contains("return \"/r/\" + code + \"/inbox\";"));
        assertEquals(1, occurrences(senderSource, ".post("));
        assertFalse(senderSource.contains(".get()"));
        assertFalse(senderSource.contains(".put("));
        assertFalse(senderSource.contains(".patch("));
        assertFalse(senderSource.contains(".delete("));
        // Sent once, to the relay: no silent retry, no redirect elsewhere.
        assertTrue(compactSender.contains(".retryOnConnectionFailure(false)"));
        assertTrue(compactSender.contains(".followRedirects(false)"));
        assertTrue(compactSender.contains(".followSslRedirects(false)"));

        for (Map.Entry<Path, String> source : sources.entrySet())
        {
            String file = source.getKey().getFileName().toString();
            if (!file.equals("TrackerInboxSender.java") && !file.equals("BadgeRelay.java"))
            {
                assertFalse("a request body outside the inbox sender and badge relay: " + source.getKey(),
                    source.getValue().contains("RequestBody"));
            }
        }

        assertAbsent(allSource,
            "/events",
            "/acks",
            "/suggest",
            "/state",
            "FateEventRelayClient",
            "FateEventOutbox",
            "localhost",
            "127.0.0.1",
            "ProcessBuilder",
            "Runtime.getRuntime().exec",
            "Class.forName",
            "java.lang.reflect",
            "ServerSocket",
            "HttpServer",
            "HttpsServer");

        assertEquals(1, occurrences(allSource, "@PluginDescriptor("));
        assertEquals(1, occurrences(allSource, "NavigationButton.builder()"));
    }

    /** Send to tracker is called from one place: the Roll inbox card's button, through the plugin. */
    @Test
    public void onlyTheCardsButtonSends()
        throws Exception
    {
        Map<Path, String> sources = productionSources();
        int sends = 0;
        for (Map.Entry<Path, String> source : sources.entrySet())
        {
            sends += occurrences(source.getValue(), "sender.send(");
        }
        assertEquals(1, sends);
        String plugin = sourceNamed(sources, "FateLockedPlugin.java");
        assertEquals(1, occurrences(plugin, "sender.send("));
        assertEquals(1, occurrences(plugin, "panel.onSendToTracker(this::sendToTracker)"));
        // Declared, and handed to the button: never called on a timer or an event.
        assertEquals(2, occurrences(plugin, "sendToTracker"));
    }

    /**
     * Badges (CONTRACT section 2) add three requests behind online sync's consent: GET /badges,
     * and, only with the default-off Share my badge, POST and DELETE /r/<code>/badge with the
     * name and tier as JSON, to the same fixed relay, from one class.
     */
    @Test
    public void badgeRequestsGoToTheFixedRelayFromOneClass()
        throws Exception
    {
        Map<Path, String> sources = productionSources();
        String relaySource = sourceNamed(sources, "BadgeRelay.java");
        String compactRelay = relaySource.replaceAll("\\s+", " ");
        String compactWiring = sourceNamed(sources, "BadgeWiring.java").replaceAll("\\s+", " ");

        assertEquals(1, occurrences(relaySource, "new Request.Builder()"));
        assertTrue(compactRelay.contains("new Request.Builder().url(relayBase + path)"));
        assertTrue(compactWiring.contains("new BadgeRelay(http, gson, TrackerConnectionSettings.RELAY_BASE_URL)"));
        assertEquals(1, occurrences(String.join("\n", sources.values()), "new BadgeRelay("));
        assertTrue(compactRelay.contains("request(\"/badges\").get()"));
        assertTrue(compactRelay.contains("return \"/r/\" + code + \"/badge\";"));
        assertEquals(1, occurrences(relaySource, ".post("));
        assertEquals(1, occurrences(relaySource, ".delete()"));
        assertEquals(1, occurrences(relaySource, "RequestBody.create(JSON, gson.toJson(new Badge(name, tier)))"));
        assertFalse(relaySource.contains(".put("));
        assertFalse(relaySource.contains(".patch("));
        assertAbsent(compactRelay, "http://", "https://", "localhost", "127.0.0.1");
    }

    @Test
    public void legacyTokenNamesExistOnlyInOneWayCleanup()
        throws Exception
    {
        Map<Path, String> sources = productionSources();
        String settingsSource = sourceNamed(
            sources, "TrackerConnectionSettings.java");
        StringBuilder otherSource = new StringBuilder();
        for (Map.Entry<Path, String> entry : sources.entrySet())
        {
            if (!entry.getKey().getFileName().toString()
                .equals("TrackerConnectionSettings.java"))
            {
                otherSource.append(entry.getValue()).append('\n');
            }
        }

        for (String prefix : LEGACY_TOKEN_PREFIXES)
        {
            assertTrue(settingsSource.contains("\"" + prefix + "\""));
            assertFalse(otherSource.toString().contains(prefix));
        }
        assertFalse(settingsSource.contains("String token("));
        assertFalse(settingsSource.contains("void saveToken("));
        assertAbsent(otherSource.toString(),
            "TokenResponse",
            "saveToken(",
            "loadRelayToken(",
            "tokenRequest",
            "requestToken");
    }

    private static Map<Path, String> productionSources()
        throws IOException
    {
        Path root = Paths.get("src", "main", "java");
        Map<Path, String> sources = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root))
        {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .forEach(path -> sources.put(
                    path, read(path)));
        }
        return sources;
    }

    private static String allSourceExcept(Map<Path, String> sources, String fileName)
    {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Path, String> entry : sources.entrySet())
        {
            if (!entry.getKey().getFileName().toString().equals(fileName))
            {
                text.append(entry.getValue()).append('\n');
            }
        }
        return text.toString();
    }

    private static String sourceNamed(
        Map<Path, String> sources, String fileName)
    {
        for (Map.Entry<Path, String> entry : sources.entrySet())
        {
            if (entry.getKey().getFileName().toString().equals(fileName))
            {
                return entry.getValue();
            }
        }
        throw new AssertionError("missing production source " + fileName);
    }

    private static String read(Path path)
    {
        try
        {
            return new String(
                Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new AssertionError("could not read " + path, ex);
        }
    }

    private static void assertAbsent(
        String source, String... forbidden)
    {
        for (String value : forbidden)
        {
            assertFalse(
                "forbidden production source value: " + value,
                source.contains(value));
        }
    }

    private static int occurrences(String text, String value)
    {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0)
        {
            count++;
            offset += value.length();
        }
        return count;
    }
}
