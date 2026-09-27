package com.fatelocked;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The rules core compiles without RuneLite, so its decisions can be tested
 * against the golden bundles without a client and later shared with the web
 * app's CI. RuneLite types stay in the adapter classes around it.
 */
public class RulesCoreBoundaryTest
{
    private static final Path MAIN = Paths.get("src", "main", "java", "com", "fatelocked");

    @Test
    public void theRulesCoreImportsNothingFromRuneLite() throws IOException
    {
        List<Path> core = coreSources();
        assertTrue("the rules package is missing", core.size() > 3);

        List<String> offenders = new ArrayList<>();
        for (Path source : core)
        {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            if (text.contains("net.runelite."))
            {
                offenders.add(MAIN.relativize(source).toString());
            }
        }
        assertEquals("RuneLite types in the rules core", new ArrayList<String>(), offenders);
    }

    private static List<Path> coreSources() throws IOException
    {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> rules = Files.walk(MAIN.resolve("rules")))
        {
            sources.addAll(rules.filter(path -> path.toString().endsWith(".java")).sorted()
                .collect(Collectors.toList()));
        }
        sources.add(MAIN.resolve("FateLockedBundle.java"));
        sources.add(MAIN.resolve("CanonicalChunk.java"));
        // Travel matching reads these facts, not the menu entry (A9).
        sources.add(MAIN.resolve("MenuFacts.java"));
        return sources;
    }
}
