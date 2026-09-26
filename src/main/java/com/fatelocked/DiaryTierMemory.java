package com.fatelocked;

import com.fatelocked.storage.LocalFileMerge;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The diary tiers an account has finished, kept in its own folder. A tier
 * counts as newly finished once: when it flips during play, or at the next
 * login when it was finished while RuneLite was closed. Finished tiers are
 * never forgotten, so a login's first readings, taken before every tier
 * has come from the server, can't make one look new.
 */
final class DiaryTierMemory
{
    static final String FILE = "diary-tiers.json";

    private final Gson gson;
    private final Path path;
    /** Null until this account's first full reading. */
    private Set<String> finished;

    DiaryTierMemory(Gson gson, Path path)
    {
        this.gson = gson;
        this.path = path;
    }

    /**
     * A full reading of the tiers finished now, at the start of a session:
     * returns those finished since the account was last seen here. The
     * account's first reading returns none, and only remembers them.
     */
    synchronized List<String> reading(Collection<String> finishedNow) throws IOException
    {
        return remember(finishedNow, true);
    }

    /** A tier finished during play: true the first time. */
    synchronized boolean finishedNow(String tier) throws IOException
    {
        return !remember(List.of(tier), false).isEmpty();
    }

    /** Remember these tiers, merged with the file, and return those that are new. */
    private List<String> remember(Collection<String> tiers, boolean reading) throws IOException
    {
        List<String> fresh = new ArrayList<>();
        Set<String> all = new TreeSet<>();
        LocalFileMerge.update(path, current -> {
            Set<String> known = onDisk(current);
            if (known == null && finished != null)
            {
                known = finished;
            }
            // The account's first reading: nothing counts as new.
            boolean first = known == null && reading;
            all.clear();
            if (known != null) all.addAll(known);
            if (finished != null) all.addAll(finished);
            fresh.clear();
            for (String tier : tiers)
            {
                if (all.add(tier) && !first) fresh.add(tier);
            }
            State state = new State();
            state.finished = new ArrayList<>(all);
            return gson.toJson(state).getBytes(StandardCharsets.UTF_8);
        });
        finished = all;
        return fresh;
    }

    /** The tiers in the file; null when there is no file, or it is damaged (kept aside). */
    private Set<String> onDisk(byte[] current) throws IOException
    {
        if (current == null) return null;
        try
        {
            State state = gson.fromJson(new String(current, StandardCharsets.UTF_8), State.class);
            if (state != null && state.finished != null)
            {
                Set<String> tiers = new TreeSet<>();
                for (String tier : state.finished)
                {
                    if (tier != null) tiers.add(tier);
                }
                return tiers;
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
