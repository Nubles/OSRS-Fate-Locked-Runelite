package com.fatelocked.guardian;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public final class StrictModeAuditLog
{
    private static final int MAX_ENTRIES = 100;
    private static final Set<String> LEGACY_FIELDS = new HashSet<>(Arrays.asList(
        "timestamp", "actionKind", "target", "chunk", "reason"));
    private static final Set<String> CURRENT_FIELDS = new HashSet<>(Arrays.asList(
        "timestamp", "actionKind", "target", "chunk", "reason",
        "outcome", "paused", "alternativeAvailable"));
    private final Gson gson;
    private final Path path;
    private final List<StrictModeAuditEntry> entries = new ArrayList<>();

    public StrictModeAuditLog(Gson gson, Path path) throws IOException
    {
        this.gson = gson;
        this.path = path;
        load();
    }

    public synchronized void append(StrictModeAuditEntry entry) throws IOException
    {
        if (entry == null) return;
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) entries.remove(0);
        persist();
    }

    public synchronized List<StrictModeAuditEntry> recent(int limit)
    {
        int count = Math.max(0, Math.min(limit, entries.size()));
        List<StrictModeAuditEntry> result = new ArrayList<>();
        for (int i = entries.size() - 1; i >= entries.size() - count; i--)
        {
            result.add(entries.get(i));
        }
        return Collections.unmodifiableList(result);
    }

    private void load() throws IOException
    {
        if (!Files.exists(path)) return;
        try
        {
            JsonObject state = gson.fromJson(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8),
                JsonObject.class);
            if (state == null || !state.has("entries")
                || !state.get("entries").isJsonArray())
            {
                return;
            }
            JsonArray loaded = state.getAsJsonArray("entries");
            for (JsonElement element : loaded)
            {
                if (!element.isJsonObject()) continue;
                StrictModeAuditEntry entry = loadEntry(element.getAsJsonObject());
                if (entry != null) entries.add(entry);
            }
            while (entries.size() > MAX_ENTRIES) entries.remove(0);
        }
        catch (JsonParseException | IllegalStateException ex)
        {
            entries.clear();
        }
    }

    private StrictModeAuditEntry loadEntry(JsonObject object)
    {
        try
        {
            StrictModeAuditEntry entry = gson.fromJson(
                object, StrictModeAuditEntry.class);
            if (!hasSafeCommonFields(object, entry)) return null;

            if (!object.has("outcome") || object.get("outcome").isJsonNull())
            {
                if (!hasOnlyFields(object, LEGACY_FIELDS)) return null;
                return new StrictModeAuditEntry(
                    entry.getTimestamp(), entry.getActionKind(), entry.getTarget(),
                    entry.getChunk(), entry.getReason(),
                    "BLOCKED", false, false);
            }

            if (!hasOnlyFields(object, CURRENT_FIELDS)
                || !isString(object, "outcome")
                || !isBoolean(object, "paused")
                || !isBoolean(object, "alternativeAvailable"))
            {
                return null;
            }
            if ("BLOCKED".equals(entry.getOutcome()))
            {
                return entry.isPaused() ? null : entry;
            }
            if ("ALLOWED_PAUSED".equals(entry.getOutcome()))
            {
                return entry.isPaused() && !entry.isAlternativeAvailable()
                    ? entry : null;
            }
            return null;
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }

    private static boolean hasSafeCommonFields(
        JsonObject object, StrictModeAuditEntry entry)
    {
        return entry != null
            && object.has("timestamp")
            && object.get("timestamp").isJsonPrimitive()
            && object.get("timestamp").getAsJsonPrimitive().isNumber()
            && isString(object, "actionKind")
            && entry.getActionKind() != null
            && isString(object, "target")
            && entry.getTarget() != null
            && isOptionalString(object, "chunk")
            && isOptionalString(object, "reason");
    }

    private static boolean isString(JsonObject object, String field)
    {
        return object.has(field)
            && object.get(field).isJsonPrimitive()
            && object.get(field).getAsJsonPrimitive().isString();
    }

    private static boolean isBoolean(JsonObject object, String field)
    {
        return object.has(field)
            && object.get(field).isJsonPrimitive()
            && object.get(field).getAsJsonPrimitive().isBoolean();
    }

    private static boolean isOptionalString(
        JsonObject object, String field)
    {
        return !object.has(field)
            || object.get(field).isJsonNull()
            || isString(object, field);
    }

    private static boolean hasOnlyFields(
        JsonObject object, Set<String> allowed)
    {
        for (String field : object.keySet())
        {
            if (!allowed.contains(field)) return false;
        }
        return true;
    }

    private void persist() throws IOException
    {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        State state = new State();
        state.entries = new ArrayList<>(entries);
        Files.write(temporary,
            gson.toJson(state).getBytes(StandardCharsets.UTF_8));
        try
        {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException ex)
        {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class State
    {
        List<StrictModeAuditEntry> entries;
    }
}
