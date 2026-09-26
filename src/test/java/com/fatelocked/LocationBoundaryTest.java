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
 * B14: ChunkLocator is the one reader of where the player and menu targets
 * are, so instances and boats are placed the same way everywhere. No other
 * production class reads a raw world location or the client's deprecated
 * scene calls, which ignore world views.
 */
public class LocationBoundaryTest
{
    private static final String[] FORBIDDEN = {
        "getWorldLocation(",
        "WorldChunks.of(",
        "WorldPoint.fromScene(client",
        "LocalPoint.fromWorld(client",
        "client.getPlane(",
        "client.getBaseX(",
        "client.getBaseY(",
    };

    @Test
    public void onlyTheChunkLocatorReadsWhereThingsAre() throws IOException
    {
        List<String> offenders = new ArrayList<>();
        List<Path> sources = sources();
        for (Path source : sources)
        {
            String name = source.getFileName().toString();
            if (name.equals("ChunkLocator.java") || name.equals("WorldChunks.java")) continue;
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            for (String call : FORBIDDEN)
            {
                if (text.contains(call)) offenders.add(name + ": " + call);
            }
        }
        assertTrue("the production sources are found", sources.size() > 50);
        assertEquals(new ArrayList<String>(), offenders);
    }

    private static List<Path> sources() throws IOException
    {
        try (Stream<Path> paths = Files.walk(Paths.get("src", "main", "java")))
        {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }
}
