package com.fatelocked;

import com.fatelocked.storage.LocalFileMerge;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a character has finished, kept in its own folder: its quests, or its diary tiers. The
 * detectors start each session from it, so something finished while RuneLite was closed counts at
 * the session's first reading, and what they find is written back. Nothing is ever forgotten, so
 * a login's first readings, taken before everything has come from the server, can't make one look
 * new. A character with no file yet has its first reading only remembered.
 */
final class FinishedMemory
{
    static final String QUESTS = "quests.json";
    static final String DIARY_TIERS = "diary-tiers.json";

    private final Gson gson;
    private final Path path;
    /** Null while nothing was ever remembered. */
    private Set<String> finished;

    private FinishedMemory(Gson gson, Path path, Set<String> finished)
    {
        this.gson = gson;
        this.path = path;
        this.finished = finished;
    }

    /** Reads what the file remembers; a damaged file is kept aside and remembers nothing. */
    static FinishedMemory open(Gson gson, Path path) throws IOException
    {
        byte[] current = Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
        return new FinishedMemory(gson, path, read(gson, path, current));
    }

    /** What is remembered; null while nothing ever was. */
    synchronized Set<String> finished()
    {
        return finished == null ? null : Collections.unmodifiableSet(new TreeSet<>(finished));
    }

    /** Remember these too, with what the file has from another client. */
    synchronized void remember(Collection<String> names) throws IOException
    {
        Set<String> all = new TreeSet<>();
        LocalFileMerge.update(path, current -> {
            all.clear();
            Set<String> known = read(gson, path, current);
            if (known != null) all.addAll(known);
            if (finished != null) all.addAll(finished);
            for (String name : names)
            {
                if (name != null) all.add(name);
            }
            State state = new State();
            state.finished = new ArrayList<>(all);
            return gson.toJson(state).getBytes(StandardCharsets.UTF_8);
        });
        finished = all;
    }

    /** The names in the file's bytes; null when there are none or the file is damaged (kept aside). */
    private static Set<String> read(Gson gson, Path path, byte[] current) throws IOException
    {
        if (current == null) return null;
        try
        {
            State state = gson.fromJson(new String(current, StandardCharsets.UTF_8), State.class);
            if (state != null && state.finished != null)
            {
                Set<String> names = new TreeSet<>();
                for (String name : state.finished)
                {
                    if (name != null) names.add(name);
                }
                return names;
            }
        }
        catch (RuntimeException error)
        {
            // Damaged: kept aside below.
        }
        LocalFileMerge.moveAside(path);
        return null;
    }

    private static final class State
    {
        List<String> finished;
    }
}
