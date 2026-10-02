package com.fatelocked;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class FinishedMemoryTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();
    private Path file;

    @Before
    public void setUp()
    {
        file = folder.getRoot().toPath().resolve(FinishedMemory.QUESTS);
    }

    @Test
    public void aCharacterWithNoFileRemembersNothingYet() throws Exception
    {
        assertNull(FinishedMemory.open(gson, file).finished());
        assertFalse(Files.exists(file));
    }

    @Test
    public void whatItRemembersIsThereTheNextTime() throws Exception
    {
        FinishedMemory memory = FinishedMemory.open(gson, file);
        memory.remember(List.of("Cook's Assistant", "Rune Mysteries"));

        assertEquals(Set.of("Cook's Assistant", "Rune Mysteries"), memory.finished());
        assertEquals(Set.of("Cook's Assistant", "Rune Mysteries"), FinishedMemory.open(gson, file).finished());
    }

    @Test
    public void nothingIsForgotten() throws Exception
    {
        FinishedMemory memory = FinishedMemory.open(gson, file);
        memory.remember(List.of("Cook's Assistant"));
        memory.remember(List.of());

        assertEquals(Set.of("Cook's Assistant"), FinishedMemory.open(gson, file).finished());
    }

    @Test
    public void anotherClientsNamesAreKept() throws Exception
    {
        // Both RuneLites open the file before either writes.
        FinishedMemory main = FinishedMemory.open(gson, file);
        FinishedMemory second = FinishedMemory.open(gson, file);

        main.remember(List.of("Cook's Assistant"));
        second.remember(List.of("Rune Mysteries"));

        assertEquals(Set.of("Cook's Assistant", "Rune Mysteries"), second.finished());
        assertEquals(Set.of("Cook's Assistant", "Rune Mysteries"), FinishedMemory.open(gson, file).finished());
    }

    @Test
    public void aFileRemovedMeanwhileGetsBackWhatThisClientRemembers() throws Exception
    {
        FinishedMemory memory = FinishedMemory.open(gson, file);
        memory.remember(List.of("Cook's Assistant"));
        Files.delete(file);

        memory.remember(List.of("Rune Mysteries"));

        assertEquals(Set.of("Cook's Assistant", "Rune Mysteries"), FinishedMemory.open(gson, file).finished());
    }

    @Test
    public void readsTheDiaryTiersEarlierVersionsRemembered() throws Exception
    {
        Path tiers = folder.getRoot().toPath().resolve(FinishedMemory.DIARY_TIERS);
        Files.write(tiers, "{\"finished\":[\"Ardougne Easy\",null,\"Lumbridge Easy\"]}".getBytes(StandardCharsets.UTF_8));

        FinishedMemory memory = FinishedMemory.open(gson, tiers);
        assertEquals(Set.of("Ardougne Easy", "Lumbridge Easy"), memory.finished());

        memory.remember(Arrays.asList("Varrock Easy", null));
        assertEquals(Set.of("Ardougne Easy", "Lumbridge Easy", "Varrock Easy"), FinishedMemory.open(gson, tiers).finished());
    }

    @Test
    public void aDamagedFileIsKeptAsideAndRemembersNothing() throws Exception
    {
        for (String damaged : new String[]{"{damaged", "[]", "{\"finished\":\"Cook's Assistant\"}", "{}"})
        {
            Path own = folder.newFolder().toPath().resolve(FinishedMemory.QUESTS);
            Files.write(own, damaged.getBytes(StandardCharsets.UTF_8));

            FinishedMemory memory = FinishedMemory.open(gson, own);

            assertNull(damaged, memory.finished());
            assertFalse(damaged, Files.exists(own));
            try (Stream<Path> files = Files.list(own.getParent()))
            {
                assertEquals(damaged, 1, files.filter(path -> path.getFileName().toString()
                    .startsWith(FinishedMemory.QUESTS + ".corrupt-")).count());
            }
            memory.remember(List.of("Cook's Assistant"));
            assertEquals(damaged, Set.of("Cook's Assistant"), FinishedMemory.open(gson, own).finished());
        }
    }
}
