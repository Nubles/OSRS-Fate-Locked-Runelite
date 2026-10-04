package com.fatelocked.events;

import com.fatelocked.storage.LocalFileMerge;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import lombok.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * What RuneLite noticed for one character (Stage 4), each event New, Copied or Dismissed, in
 * accounts/&lt;account hash&gt;/detected-events.json. Another RuneLite on this computer may share the
 * file, so every write merges with what is on disk (LocalFileMerge), and a state never goes back:
 * Dismissed wins over Copied, and Copied over New. Events older than 30 days go, as the tracker
 * refuses them, and the newest 250 stay.
 *
 * <p>Events recorded before Stage 4 live in event-history.json, which this never reads: they
 * have no state, and some carry old labels (plan decision 8).
 */
public final class DetectedEventStore
{
    public static final String FILE = "detected-events.json";
    /** What the tracker's paste reads the copy as. */
    public static final String FORMAT = "fate-locked-runelite-events";
    static final int MAX_EVENTS = 250;
    static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;

    /** In the order a merge prefers: a later state wins. */
    public enum Status
    {
        NEW, COPIED, DISMISSED
    }

    @Value
    public static class Entry
    {
        FateEvent event;
        Status status;
    }

    interface Persistence
    {
        void write(Path target, byte[] bytes) throws IOException;
    }

    private final Gson gson;
    private final Path path;
    private final Persistence persistence;
    private final LongSupplier clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public DetectedEventStore(Gson gson, Path path) throws IOException
    {
        this(gson, path, LocalFileMerge::replace, System::currentTimeMillis);
    }

    DetectedEventStore(Gson gson, Path path, Persistence persistence, LongSupplier clock) throws IOException
    {
        if (gson == null || path == null || persistence == null || clock == null)
        {
            throw new IllegalArgumentException("Store dependencies are required");
        }
        // A missing label is written as null, which the tracker's parser needs.
        this.gson = gson.newBuilder().serializeNulls().create();
        this.path = path;
        this.persistence = persistence;
        this.clock = clock;
        if (Files.exists(path))
        {
            List<Entry> loaded = parse(Files.readAllBytes(path));
            if (loaded == null)
            {
                LocalFileMerge.moveAside(path);
            }
            else
            {
                replaceWith(fresh(loaded));
            }
        }
    }

    /** Record a new event; false when one with its id is already here, and nothing is written. */
    public synchronized boolean record(FateEvent event) throws IOException
    {
        if (!valid(event) || entries.containsKey(event.getEventId()))
        {
            return false;
        }
        persist(Collections.singletonList(new Entry(event, Status.NEW)));
        return true;
    }

    /** Move these events on to a state; one already past it stays where it is. */
    public synchronized void mark(Collection<String> eventIds, Status status) throws IOException
    {
        List<Entry> changed = new ArrayList<>();
        for (String id : eventIds)
        {
            Entry entry = entries.get(id);
            if (entry != null && entry.getStatus().compareTo(status) < 0)
            {
                changed.add(new Entry(entry.getEvent(), status));
            }
        }
        if (!changed.isEmpty())
        {
            persist(changed);
        }
    }

    /** Every event kept, oldest first. */
    public synchronized List<Entry> entries()
    {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    /**
     * What the Roll inbox offers: this run's events that are New or Copied and young enough for
     * the tracker, newest first. A pet is offered too: the tracker asks which pet it was, and each
     * pet gives its Omni-Key once.
     */
    public synchronized List<Entry> offered(String runId)
    {
        long oldest = clock.getAsLong() - MAX_AGE_MS;
        List<Entry> offered = new ArrayList<>();
        for (Entry entry : entries.values())
        {
            FateEvent event = entry.getEvent();
            if (entry.getStatus() != Status.DISMISSED && runId != null && runId.equals(event.getRunId())
                && event.getOccurredAt() >= oldest)
            {
                offered.add(entry);
            }
        }
        offered.sort(Comparator.comparingLong((Entry entry) -> entry.getEvent().getOccurredAt()).reversed());
        return offered;
    }

    /** The copy for the tracker's Paste from RuneLite: the events, nulls kept, in the form it reads. */
    public String copyOf(List<FateEvent> events)
    {
        Batch batch = new Batch();
        batch.format = FORMAT;
        batch.events = new ArrayList<>(events);
        return gson.toJson(batch);
    }

    private void persist(List<Entry> ours) throws IOException
    {
        List<Entry> written = new ArrayList<>();
        LocalFileMerge.update(path, current -> {
            List<Entry> onDisk = current == null ? Collections.emptyList() : parse(current);
            if (onDisk == null)
            {
                // Damaged: kept aside, and not written over.
                LocalFileMerge.moveAside(path);
                onDisk = Collections.emptyList();
            }
            Map<String, Entry> merged = new LinkedHashMap<>();
            for (List<Entry> source : Arrays.asList(onDisk, new ArrayList<>(entries.values()), ours))
            {
                for (Entry entry : source)
                {
                    Entry kept = merged.get(entry.getEvent().getEventId());
                    if (kept == null || kept.getStatus().compareTo(entry.getStatus()) < 0)
                    {
                        merged.put(entry.getEvent().getEventId(), entry);
                    }
                }
            }
            written.clear();
            written.addAll(fresh(new ArrayList<>(merged.values())));
            State state = new State();
            state.version = 1;
            state.entries = new ArrayList<>(written);
            return gson.toJson(state).getBytes(StandardCharsets.UTF_8);
        }, persistence::write);
        // Only once written: a failed write leaves memory as it was.
        replaceWith(written);
    }

    /** Young enough for the tracker, the newest 250, oldest first. */
    private List<Entry> fresh(List<Entry> all)
    {
        long oldest = clock.getAsLong() - MAX_AGE_MS;
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : all)
        {
            if (entry.getEvent().getOccurredAt() >= oldest)
            {
                kept.add(entry);
            }
        }
        kept.sort(Comparator.comparingLong(entry -> entry.getEvent().getOccurredAt()));
        return kept.size() > MAX_EVENTS ? new ArrayList<>(kept.subList(kept.size() - MAX_EVENTS, kept.size())) : kept;
    }

    private void replaceWith(List<Entry> kept)
    {
        entries.clear();
        for (Entry entry : kept)
        {
            entries.put(entry.getEvent().getEventId(), entry);
        }
    }

    /** The entries in a file's contents, bad ones left out; null when the file is damaged. */
    private List<Entry> parse(byte[] bytes)
    {
        State state;
        try
        {
            state = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), State.class);
        }
        catch (JsonParseException | IllegalStateException error)
        {
            return null;
        }
        if (state == null || state.entries == null)
        {
            return null;
        }
        List<Entry> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry entry : state.entries)
        {
            if (entry != null && entry.getStatus() != null && valid(entry.getEvent())
                && seen.add(entry.getEvent().getEventId()))
            {
                valid.add(new Entry(Evidence.withWholeNumbers(entry.getEvent()), entry.getStatus()));
            }
        }
        return valid;
    }

    private static boolean valid(FateEvent event)
    {
        return event != null && event.getEventId() != null && !event.getEventId().trim().isEmpty()
            && event.getEventType() != null && event.getConfidence() != null;
    }

    private static final class State
    {
        private int version;
        private List<Entry> entries;
    }

    private static final class Batch
    {
        private String format;
        private List<FateEvent> events;
    }
}
