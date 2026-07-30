package com.fatelocked.events;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        this(gson, historyPath, legacyOutboxPath,
            FateEventHistory::writeAtomically);
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
        while (candidate.size() >= MAX_EVENTS)
        {
            candidate.remove(0);
        }
        candidate.add(event);
        persist(candidate);
        events.clear();
        events.addAll(candidate);
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
        List<FateEvent> migrated = boundedUnique(legacy.pending);
        persist(migrated);
        events.addAll(migrated);
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

    private void persist(List<FateEvent> candidate) throws IOException
    {
        State state = new State();
        state.events = new ArrayList<>(candidate);
        persistence.write(
            historyPath,
            gson.toJson(state).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAtomically(Path target, byte[] bytes)
        throws IOException
    {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Path temporary = target.resolveSibling(
            target.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        try
        {
            Files.move(temporary, target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException error)
        {
            Files.move(temporary, target,
                StandardCopyOption.REPLACE_EXISTING);
        }
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
