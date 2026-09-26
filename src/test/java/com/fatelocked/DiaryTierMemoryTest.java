package com.fatelocked;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DiaryTierMemoryTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    private Path file;

    @Before
    public void setUp()
    {
        file = folder.getRoot().toPath().resolve(DiaryTierMemory.FILE);
    }

    @Test
    public void anAccountsFirstReadingOnlyRemembers() throws Exception
    {
        DiaryTierMemory memory = new DiaryTierMemory(gson, file);

        assertEquals(List.of(), memory.reading(List.of("Ardougne Easy", "Lumbridge Easy")));
        assertFalse(memory.finishedNow("Lumbridge Easy"));
    }

    @Test
    public void tiersFinishedWhileAwayCountAtTheNextReading() throws Exception
    {
        new DiaryTierMemory(gson, file).reading(List.of("Ardougne Easy"));

        // A later session: RuneLite was closed when Lumbridge Easy was finished.
        DiaryTierMemory later = new DiaryTierMemory(gson, file);

        assertEquals(List.of("Lumbridge Easy"),
            later.reading(List.of("Ardougne Easy", "Lumbridge Easy")));
    }

    @Test
    public void aTierFinishedInPlayCountsOnce() throws Exception
    {
        DiaryTierMemory memory = new DiaryTierMemory(gson, file);
        memory.reading(List.of());

        assertTrue(memory.finishedNow("Varrock Hard"));
        assertFalse(memory.finishedNow("Varrock Hard"));
        assertFalse(new DiaryTierMemory(gson, file).finishedNow("Varrock Hard"));
    }

    @Test
    public void readingsTakenBeforeEveryTierArrivesForgetNothing() throws Exception
    {
        DiaryTierMemory memory = new DiaryTierMemory(gson, file);
        memory.reading(List.of("Ardougne Easy"));

        // A login's first readings: the tier's varbit has not arrived yet.
        DiaryTierMemory next = new DiaryTierMemory(gson, file);
        assertEquals(List.of(), next.reading(List.of()));

        assertFalse(next.finishedNow("Ardougne Easy"));
    }

    @Test
    public void twoClientsCountATierOnce() throws Exception
    {
        DiaryTierMemory main = new DiaryTierMemory(gson, file);
        DiaryTierMemory second = new DiaryTierMemory(gson, file);
        main.reading(List.of());
        second.reading(List.of());

        assertTrue(main.finishedNow("Falador Elite"));
        assertFalse(second.finishedNow("Falador Elite"));
    }

    @Test
    public void aDamagedFileIsKeptAsideAndCountsAsAFirstReading() throws Exception
    {
        Files.write(file, "{damaged".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of(), new DiaryTierMemory(gson, file).reading(List.of("Ardougne Easy")));
        try (Stream<Path> files = Files.list(file.getParent()))
        {
            assertEquals(1, files.filter(path -> path.getFileName().toString()
                .startsWith(DiaryTierMemory.FILE + ".corrupt-")).count());
        }
    }
}
