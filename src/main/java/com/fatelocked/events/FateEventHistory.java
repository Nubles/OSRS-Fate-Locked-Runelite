package com.fatelocked.events;

import com.fatelocked.storage.LocalFileMerge;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The newest 250 detected events, kept in a local file that another
 * RuneLite on this computer may share: each write merges into what is on
 * disk rather than replacing it (LocalFileMerge).
 */
public final class FateEventHistory
{
    static final int MAX_EVENTS = 250;

    interface Persistence
    {
        void write(Path target, byte[] bytes) throws IOException;
    }

    private final Gson gson;
    private final Path historyPath;
    private final Persistence persistence;
    private final List<FateEvent> events = new ArrayList<>();

    public FateEventHistory(
        Gson gson, Path historyPath, Path legacyOutboxPath)
        throws IOException
    {
        this(gson, historyPath, legacyOutboxPath, LocalFileMerge::replace);
    }

    FateEventHistory(
        Gson gson,
        Path historyPath,
        Path legacyOutboxPath,
        Persistence persistence)
        throws IOException
    {
        if (gson == null || historyPath == null || persistence == null)
        {
            throw new IllegalArgumentException(
                "History dependencies are required");
        }
        this.gson = gson;
        this.historyPath = historyPath;
        this.persistence = persistence;
        load(legacyOutboxPath);
    }

    public synchronized boolean record(FateEvent event) throws IOException
    {
        if (event == null || event.getEventId() == null
            || event.getEventId().trim().isEmpty()
            || contains(event.getEventId()))
        {
            return false;
        }

        List<FateEvent> candidate = new ArrayList<>(events);
        candidate.add(event);
        List<FateEvent> merged = persist(candidate);
        // Only once written: a failed write leaves memory as it was.
        events.clear();
        events.addAll(merged);
        return true;
    }

    public synchronized List<FateEvent> events()
    {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    private boolean contains(String eventId)
    {
        for (FateEvent event : events)
        {
            if (eventId.equals(event.getEventId()))
            {
                return true;
            }
        }
        return false;
    }

    private void load(Path legacyOutboxPath) throws IOException
    {
        if (Files.exists(historyPath))
        {
            loadHistory();
            return;
        }
        if (legacyOutboxPath != null && Files.exists(legacyOutboxPath))
        {
            migrateLegacy(legacyOutboxPath);
        }
    }

    private void loadHistory() throws IOException
    {
        try
        {
            State state = gson.fromJson(
                new String(
                    Files.readAllBytes(historyPath),
                    StandardCharsets.UTF_8),
                State.class);
            if (state == null || state.events == null)
            {
                throw new JsonParseException("invalid event history");
            }
            events.addAll(boundedUnique(state.events));
        }
        catch (RuntimeException error)
        {
            Path corrupt = historyPath.resolveSibling(
                historyPath.getFileName() + ".corrupt-"
                    + System.currentTimeMillis());
            Files.move(historyPath, corrupt,
                StandardCopyOption.REPLACE_EXISTING);
            events.clear();
        }
    }

    private void migrateLegacy(Path legacyOutboxPath) throws IOException
    {
        LegacyState legacy;
        try
        {
            legacy = gson.fromJson(
                new String(
                    Files.readAllBytes(legacyOutboxPath),
                    StandardCharsets.UTF_8),
                LegacyState.class);
        }
        catch (RuntimeException error)
        {
            return;
        }
        if (legacy == null || legacy.pending == null)
        {
            return;
        }
        events.addAll(persist(boundedUnique(legacy.pending)));
    }

    private List<FateEvent> boundedUnique(List<FateEvent> source)
    {
        List<FateEvent> bounded = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = source.size() - 1; index >= 0; index--)
        {
            FateEvent event = source.get(index);
            String eventId = event == null ? null : event.getEventId();
            if (eventId == null || eventId.trim().isEmpty()
                || !seen.add(eventId))
            {
                continue;
            }
            bounded.add(event);
            if (bounded.size() == MAX_EVENTS)
            {
                break;
            }
        }
        Collections.reverse(bounded);
        return bounded;
    }

    /**
     * Write these events merged with those on disk now, which another
     * RuneLite may have added since this one loaded: the newest 250, oldest
     * first. A damaged file is moved aside first. Returns what was written.
     */
    private List<FateEvent> persist(List<FateEvent> ours) throws IOException
    {
        List<FateEvent> merged = new ArrayList<>();
        LocalFileMerge.update(historyPath, current -> {
            List<FateEvent> all = new ArrayList<>(onDisk(current));
            all.addAll(ours);
            all.sort(Comparator.comparingLong(FateEvent::getOccurredAt));
            merged.clear();
            merged.addAll(boundedUnique(all));
            State state = new State();
            state.events = new ArrayList<>(merged);
            return gson.toJson(state).getBytes(StandardCharsets.UTF_8);
        }, persistence::write);
        return merged;
    }

    /** The events in the file's current contents; none if there is no file, or it is damaged. */
    private List<FateEvent> onDisk(byte[] current) throws IOException
    {
        if (current == null)
        {
            return Collections.emptyList();
        }
        try
        {
            State state = gson.fromJson(new String(current, StandardCharsets.UTF_8), State.class);
            if (state != null && state.events != null)
            {
                List<FateEvent> valid = new ArrayList<>();
                for (FateEvent event : state.events)
                {
                    if (event != null) valid.add(event);
                }
                return valid;
            }
        }
        catch (RuntimeException error)
        {
            // Damaged: kept aside below, and not written over.
        }
        LocalFileMerge.moveAside(historyPath);
        return Collections.emptyList();
    }

    private static final class State
    {
        private List<FateEvent> events;
    }

    private static final class LegacyState
    {
        private List<FateEvent> pending;
    }
}
