package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.google.gson.Gson;
import org.junit.Test;

import java.nio.file.Files;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExpandedDetectorsTest
{
    @Test
    public void slayerPersistsAndDeduplicatesCompletion() throws Exception
    {
        java.nio.file.Path path = Files.createTempFile("slayer", ".json");
        Files.delete(path);
        SlayerTaskDetector detector = new SlayerTaskDetector(new Gson(), path);
        detector.assignment("Abyssal demons", "Duradel", 150, false);
        assertTrue(detector.completion("return to a Slayer master").isPresent());
        assertFalse(detector.completion("duplicate").isPresent());
        assertFalse(new SlayerTaskDetector(new Gson(), path)
            .completion("restart duplicate").isPresent());
    }

    @Test
    public void damagedSlayerFileIsMovedAsideInsteadOfFailing() throws Exception
    {
        for (String damaged : new String[] {
            "{\"name\":\"Abyssal demons\",",   // truncated mid-write
            "{\"startCount\":\"many\"}",        // field type from another version
            "[]",                                   // not an object at all
        })
        {
            java.nio.file.Path dir = Files.createTempDirectory("slayer-damaged");
            java.nio.file.Path path = dir.resolve("slayer-assignment.json");
            Files.write(path, damaged.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            SlayerTaskDetector detector = new SlayerTaskDetector(new Gson(), path);

            assertFalse(damaged, detector.completion("task complete").isPresent());
            assertFalse(damaged, Files.exists(path));
            try (java.util.stream.Stream<java.nio.file.Path> kept = Files.list(dir))
            {
                assertEquals(damaged, 1, kept.filter(p -> p.getFileName().toString()
                    .startsWith("slayer-assignment.json.corrupt-")).count());
            }
            detector.assignment("Kurask", null, 0, false);
            assertTrue(damaged, detector.completion("task complete").isPresent());
        }
    }

    @Test
    public void diaryEmitsOnlyZeroToOne()
    {
        DiaryTierReviewDetector detector = new DiaryTierReviewDetector();
        assertTrue(detector.onVarbit("Ardougne Easy", 0, 1).isPresent());
        assertFalse(detector.onVarbit("Ardougne Easy", 1, 1).isPresent());
    }

    @Test
    public void aNewPetIsRecordedWithoutGuessingWhichItIs()
    {
        // The game's lines, from RuneLite's screenshot plugin.
        PetDropDetector detector = new PetDropDetector();
        Optional<DetectedEvent> followed = detector.detect(
            "You have a funny feeling like you're being followed.", 10_000);
        assertTrue(followed.isPresent());
        assertNull(followed.get().getCanonicalLabel());
        assertEquals(EventConfidence.UNCERTAIN, followed.get().getConfidence());
        assertTrue(detector.detect(
            "You feel something weird sneaking into your backpack.", 20_000).isPresent());
    }

    @Test
    public void aPetAlreadyOwnedIsNotANewPet()
    {
        PetDropDetector detector = new PetDropDetector();
        assertFalse(detector.detect(
            "You have a funny feeling like you would have been followed...", 10_000).isPresent());
        assertFalse(detector.detect("Your pet is insured.", 20_000).isPresent());
    }

    @Test
    public void pestControlRequiresBothSignals()
    {
        MinigameCompletionDetector detector = new MinigameCompletionDetector();
        assertFalse(detector.onMessage("You have won the game!", 1000).isPresent());
        detector.onPestControlWidget(2000);
        assertTrue(detector.onMessage("You have won the game!", 3000).isPresent());
        assertFalse(detector.onMessage("You have completed a farming contract.", 3001).isPresent());
    }

    @Test
    public void bossV2UsesNamedMappingNotCombatLevel()
    {
        BossKillDetectorV2 detector = new BossKillDetectorV2();
        assertTrue(detector.detect("NPC", "Vorkath", 1).isPresent());
        assertFalse(detector.detect("NPC", "Abyssal demon", 2).isPresent());
        assertFalse(detector.detect("NPC", "Vorkath", 1).isPresent());
    }
}
