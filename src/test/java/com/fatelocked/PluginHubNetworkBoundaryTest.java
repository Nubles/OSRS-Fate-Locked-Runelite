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

    @Test
    public void productionSourceHasOneFixedInboundRequestBoundary()
        throws Exception
    {
        Map<Path, String> sources = productionSources();
        String allSource = String.join("\n", sources.values());
        String controllerSource = sourceNamed(
            sources, "TrackerConnectionController.java");
        String compactController =
            controllerSource.replaceAll("\\s+", " ");

        assertEquals(1, occurrences(allSource, "new Request.Builder()"));
        assertTrue(compactController.contains(
            "TrackerConnectionSettings.RELAY_BASE_URL"
                + " + \"/r/\" + token.code"));
        assertTrue(controllerSource.contains(".get()"));
        assertFalse(controllerSource.contains(".post("));
        assertFalse(controllerSource.contains(".put("));
        assertFalse(controllerSource.contains(".patch("));
        assertFalse(controllerSource.contains(".delete("));

        assertAbsent(allSource,
            "/events",
            "/acks",
            "/suggest",
            "/state",
            "RequestBody",
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
