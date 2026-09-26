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
 * B17: every surface reads the rules through DecisionService. The bundle's
 * old lock-state answers are read only by LegacyRules (for v1-3 exports)
 * and RulesSnapshot (which hands them to the decision service), so no
 * surface can answer "is this locked?" a second way (U4, R2). The Slayer
 * reach the old engine had is gone altogether.
 */
public class DecisionBoundaryTest
{
    private static final String[] OLD_ANSWERS = {
        ".lockStateAt(", ".isUnlocked(", ".isFrontierChunk(", ".nearestUsableBank(", ".nearestUsableShop(",
        ".isBankUnlocked(", ".getItemTiers(", ".labelAt(",
        ".getUnlockedAreas(", ".getTotalAreas(", ".getUnlockedChunks(", ".getTotalChunks(",
    };
    /** The bundle itself, and the two readers allowed to ask it. */
    private static final List<String> READERS = List.of("FateLockedBundle.java", "LegacyRules.java", "RulesSnapshot.java");

    @Test
    public void onlyTheRulesSnapshotAndLegacyRulesReadTheOldAnswers() throws IOException
    {
        List<String> offenders = new ArrayList<>();
        List<Path> sources = sources();
        for (Path source : sources)
        {
            String name = source.getFileName().toString();
            if (READERS.contains(name)) continue;
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            for (String call : OLD_ANSWERS)
            {
                if (text.contains(call)) offenders.add(name + ": " + call);
            }
        }
        assertTrue("the production sources are found", sources.size() > 50);
        assertEquals(new ArrayList<String>(), offenders);
    }

    @Test
    public void theOldSlayerReachIsGone() throws IOException
    {
        for (Path source : sources())
        {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            assertTrue(source.toString(), !text.contains("monsterReach("));
        }
    }

    private static List<Path> sources() throws IOException
    {
        try (Stream<Path> paths = Files.walk(Paths.get("src", "main", "java")))
        {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }
}
