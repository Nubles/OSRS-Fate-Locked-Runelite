package com.fatelocked;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Stage 4 (C2): the plugin writes the clipboard in one place, the panel's copy for Copy for tracker,
 * and only the Roll inbox's events go there, when the player clicks.
 */
public class ClipboardBoundaryTest
{
    @Test
    public void theOnlyClipboardWriteIsCopyForTracker() throws Exception
    {
        Map<Path, String> sources = productionSources();
        int writes = 0;
        for (Map.Entry<Path, String> source : sources.entrySet())
        {
            int here = occurrences(source.getValue(), "setContents(");
            if (here > 0)
            {
                assertEquals(source.getKey().toString(), "FateLockedPanel.java", source.getKey().getFileName().toString());
            }
            writes += here;
        }
        assertEquals(1, writes);

        String all = String.join("\n", sources.values());
        assertEquals(0, occurrences(all, "getSystemSelection"));
        assertEquals(1, occurrences(all, "boolean copyToClipboard(String text)"));
        // The one caller copies the Roll inbox's events, from its button.
        assertEquals(1, occurrences(all, ".copyToClipboard("));
        assertTrue(sourceNamed(sources, "FateLockedPlugin.java").contains("panel.copyToClipboard(store.copyOf(events))"));
        assertTrue(sourceNamed(sources, "FateLockedPlugin.java").contains("panel.onCopyForTracker(this::copyForTracker)"));
    }

    private static Map<Path, String> productionSources() throws IOException
    {
        Map<Path, String> sources = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(Paths.get("src", "main", "java")))
        {
            paths.filter(path -> path.toString().endsWith(".java")).sorted().forEach(path -> sources.put(path, read(path)));
        }
        return sources;
    }

    private static String sourceNamed(Map<Path, String> sources, String fileName)
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
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        catch (IOException error)
        {
            throw new UncheckedIOException(error);
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
