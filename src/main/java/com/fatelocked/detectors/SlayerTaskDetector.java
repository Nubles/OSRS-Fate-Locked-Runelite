package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import com.fatelocked.storage.LocalFileMerge;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SlayerTaskDetector
{
    private final Gson gson;
    private final Path path;
    private State state;

    public SlayerTaskDetector(Gson gson, Path path) throws IOException
    {
        this.gson = gson;
        this.path = path;
        state = load();
    }

    /**
     * Read the saved assignment. A damaged or unreadable file (truncated,
     * hand-edited, or written by a different version) is moved aside as
     * {@code .corrupt-<millis>} and the detector starts with no assignment,
     * the same recovery the event history uses, so it can never stop the
     * plugin from starting.
     */
    private State load() throws IOException
    {
        if (!Files.exists(path)) return new State();
        try
        {
            State loaded = gson.fromJson(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8),
                State.class);
            return loaded == null ? new State() : loaded;
        }
        catch (RuntimeException error)
        {
            Files.move(path,
                path.resolveSibling(path.getFileName() + ".corrupt-"
                    + System.currentTimeMillis()),
                StandardCopyOption.REPLACE_EXISTING);
            return new State();
        }
    }

    public synchronized void assignment(
        String name, String master, int count, boolean joinedMidAssignment)
        throws IOException
    {
        State next = new State();
        next.name = name;
        next.master = master;
        next.startCount = count;
        next.joinedMidAssignment = joinedMidAssignment;
        LocalFileMerge.update(path, current -> bytes(next));
        state = next;
    }

    /**
     * The task complete, once. The saved state is re-read under the file's
     * lock, so another RuneLite on the same account can't complete the same
     * task a second time, and a task it saved is the one completed here.
     */
    public synchronized Optional<DetectedEvent> completion(String signature)
        throws IOException
    {
        State[] completed = {null};
        LocalFileMerge.update(path, current -> {
            State saved = current == null ? state : parse(current);
            state = saved;
            if (saved.name == null || saved.completed) return null;
            State done = copy(saved);
            done.completed = true;
            completed[0] = done;
            return bytes(done);
        });
        if (completed[0] == null) return Optional.empty();
        state = completed[0];
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("assignment", state.name);
        if (state.master != null) evidence.put("master", state.master);
        evidence.put("startCount", state.startCount);
        evidence.put("completionSignature", signature == null ? "" : signature);
        evidence.put("joinedMidAssignment", state.joinedMidAssignment);
        return Optional.of(DetectedEvent.builder()
            .type(FateEventType.SLAYER_TASK)
            .canonicalLabel(state.name)
            .confidence(EventConfidence.UNCERTAIN)
            .detectorId("slayer-task-v1")
            .detectorVersion(1)
            .evidence(evidence)
            .build());
    }

    private byte[] bytes(State value)
    {
        return gson.toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    /** The saved state; a damaged file is moved aside, and counts as no assignment. */
    private State parse(byte[] current) throws IOException
    {
        try
        {
            State loaded = gson.fromJson(new String(current, StandardCharsets.UTF_8), State.class);
            return loaded == null ? new State() : loaded;
        }
        catch (RuntimeException error)
        {
            LocalFileMerge.moveAside(path);
            return new State();
        }
    }

    private static State copy(State source)
    {
        State copy = new State();
        copy.name = source.name;
        copy.master = source.master;
        copy.startCount = source.startCount;
        copy.joinedMidAssignment = source.joinedMidAssignment;
        copy.completed = source.completed;
        return copy;
    }

    private static final class State
    {
        String name;
        String master;
        int startCount;
        boolean joinedMidAssignment;
        boolean completed;
    }
}
