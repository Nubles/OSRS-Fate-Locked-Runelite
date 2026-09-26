package com.fatelocked.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LocalFileMergeTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void eachUpdateMergesIntoTheFileAsItIsNow() throws Exception
    {
        Path file = folder.getRoot().toPath().resolve("state.json");

        LocalFileMerge.update(file, current -> {
            assertNull(current);
            return bytes("a");
        });
        LocalFileMerge.update(file, current -> bytes(text(current) + "b"));

        assertEquals("ab", text(Files.readAllBytes(file)));
    }

    @Test
    public void aMergeWithNothingToWriteLeavesTheFile() throws Exception
    {
        Path file = folder.getRoot().toPath().resolve("state.json");
        LocalFileMerge.update(file, current -> bytes("kept"));

        LocalFileMerge.update(file, current -> null);

        assertEquals("kept", text(Files.readAllBytes(file)));
    }

    @Test
    public void aFailedWriteLeavesTheFileAsItWasAndNoTempFileBehind() throws Exception
    {
        Path dir = folder.getRoot().toPath();
        Path file = dir.resolve("state.json");
        LocalFileMerge.update(file, current -> bytes("kept"));

        try
        {
            LocalFileMerge.update(file, current -> bytes("new"),
                (target, contents) -> { throw new IOException("disk full"); });
            fail("the write should fail");
        }
        catch (IOException expected)
        {
            // The update reports it.
        }

        assertEquals("kept", text(Files.readAllBytes(file)));
        assertEquals(Set.of("state.json", "state.json.lock"), names(dir));
    }

    @Test
    public void aMoveThatFailsLeavesNoTempFileBehind() throws Exception
    {
        Path dir = folder.getRoot().toPath();
        // A directory with something in it can't be replaced by a file.
        Path file = Files.createDirectory(dir.resolve("state.json"));
        Files.write(file.resolve("inside"), bytes("x"));

        try
        {
            LocalFileMerge.replace(file, bytes("new"));
            fail("the move should fail");
        }
        catch (IOException expected)
        {
            // The write reports it.
        }

        assertEquals(Set.of("state.json"), names(dir));
    }

    @Test
    public void aDamagedFileIsKeptAside() throws Exception
    {
        Path dir = folder.getRoot().toPath();
        Path file = dir.resolve("state.json");
        Files.write(file, bytes("{damaged"));

        LocalFileMerge.moveAside(file);

        Set<String> names = names(dir);
        assertEquals(1, names.size());
        String kept = names.iterator().next();
        assertTrue(kept, kept.startsWith("state.json.corrupt-"));
        assertEquals("{damaged", text(Files.readAllBytes(dir.resolve(kept))));
    }

    private static Set<String> names(Path dir) throws IOException
    {
        try (Stream<Path> files = Files.list(dir))
        {
            return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private static byte[] bytes(String text)
    {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] bytes)
    {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
