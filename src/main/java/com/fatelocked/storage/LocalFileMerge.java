package com.fatelocked.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Updates a local file that another RuneLite on this computer may also be
 * writing. Under an exclusive lock on a "&lt;file&gt;.lock" sidecar, it reads
 * the file as it is now, merges this client's change into it, writes the
 * result to a temp file of its own and moves that into place. Two clients
 * sharing a data folder then keep each other's changes, where rewriting
 * the whole file from memory let the last writer's copy win.
 */
public final class LocalFileMerge
{
    /** The file's new contents, from its current ones: null when there is no file. */
    public interface Merge
    {
        /** Null writes nothing. */
        byte[] apply(byte[] current) throws IOException;
    }

    /** Puts the merged contents in place. */
    public interface Writer
    {
        void write(Path target, byte[] bytes) throws IOException;
    }

    private LocalFileMerge()
    {
    }

    public static void update(Path target, Merge merge) throws IOException
    {
        update(target, merge, LocalFileMerge::replace);
    }

    /**
     * One update at a time within this RuneLite, since a JVM cannot hold two
     * locks on one file, and one across RuneLites, through the file lock.
     */
    public static synchronized void update(Path target, Merge merge, Writer writer)
        throws IOException
    {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Path lockPath = target.resolveSibling(target.getFileName() + ".lock");
        try (FileChannel lockChannel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock())
        {
            byte[] current = Files.exists(target) ? Files.readAllBytes(target) : null;
            byte[] next = merge.apply(current);
            if (next != null)
            {
                writer.write(target, next);
            }
        }
    }

    /**
     * Write the bytes to a temp file of this write's own, flush them to the
     * disk, and move the file into place, so a reader sees the old contents
     * or the new, never part of them.
     */
    public static void replace(Path target, byte[] bytes) throws IOException
    {
        Path parent = target.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(parent, target.getFileName() + ".", ".tmp");
        try
        {
            try (FileChannel out = FileChannel.open(temporary,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING))
            {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining())
                {
                    out.write(buffer);
                }
                out.force(true);
            }
            try
            {
                Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException error)
            {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    /** Keep a damaged file as "&lt;file&gt;.corrupt-&lt;millis&gt;" rather than writing over it. */
    public static void moveAside(Path target) throws IOException
    {
        Files.move(target, target.resolveSibling(
                target.getFileName() + ".corrupt-" + System.currentTimeMillis()),
            StandardCopyOption.REPLACE_EXISTING);
    }
}
