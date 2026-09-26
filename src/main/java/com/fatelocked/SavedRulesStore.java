package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * Keeps the last accepted rules in saved-rules.json at the root of the data
 * folder, so they survive a restart and an offline start. They belong to
 * the pairing, not the OSRS account, because they must load before login,
 * and the file names the pairing only by its tag, never the code. The
 * bundle is kept compressed ("FLGZ:"), the form the bundle loader reads.
 */
@Slf4j
final class SavedRulesStore
{
    static final String FILE_NAME = "saved-rules.json";
    private static final int FORMAT = 1;
    private static final String GZ_PREFIX = "FLGZ:";

    private final Gson gson;
    private final Path path;

    SavedRulesStore(Gson gson, Path path)
    {
        this.gson = gson;
        this.path = path;
    }

    /**
     * The saved rules, or null when there are none. A damaged file is moved
     * aside as {@code .corrupt-<millis>}; a file from a newer plugin is left
     * alone. Never throws.
     */
    SavedRules load()
    {
        try
        {
            if (!Files.exists(path))
            {
                return null;
            }
            State state = gson.fromJson(
                new String(Files.readAllBytes(path), StandardCharsets.UTF_8),
                State.class);
            if (state != null && state.format > FORMAT)
            {
                return null;
            }
            SavedRules rules = state == null ? null : state.toRules();
            if (rules == null)
            {
                throw new JsonParseException("incomplete saved rules");
            }
            return rules;
        }
        catch (IOException | RuntimeException ex)
        {
            log.warn("Saved rules could not be read: {}", ex.getMessage());
            moveAside();
            return null;
        }
    }

    void save(SavedRules rules) throws IOException
    {
        State state = new State();
        state.format = FORMAT;
        state.source = rules.getSource().name();
        state.savedAt = rules.getSavedAt().toString();
        state.relayVersion = rules.getRelayVersion();
        state.pairingTag = rules.getPairingTag();
        state.payload = compress(rules.getPayload());
        byte[] bytes = gson.toJson(state).getBytes(StandardCharsets.UTF_8);

        Path parent = path.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temporary, bytes);
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

    /** The bundle compressed as the web app compresses it, unless it already is. */
    static String compress(String bundle)
    {
        String trimmed = bundle.trim();
        if (trimmed.startsWith(GZ_PREFIX))
        {
            return trimmed;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes))
        {
            gzip.write(trimmed.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException ex)
        {
            // Writing to memory does not fail.
            throw new IllegalStateException(ex);
        }
        return GZ_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private void moveAside()
    {
        try
        {
            if (Files.exists(path))
            {
                Files.move(path, path.resolveSibling(path.getFileName()
                    + ".corrupt-" + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException ex)
        {
            log.warn("Could not move damaged saved rules aside: {}", ex.getMessage());
        }
    }

    private static final class State
    {
        int format;
        String source;
        String savedAt;
        String relayVersion;
        String pairingTag;
        String payload;

        SavedRules toRules()
        {
            if (format != FORMAT || payload == null || source == null || savedAt == null)
            {
                return null;
            }
            try
            {
                return new SavedRules(payload,
                    FateLockedPlugin.RulesSource.valueOf(source),
                    Instant.parse(savedAt), relayVersion, pairingTag);
            }
            catch (IllegalArgumentException | DateTimeParseException ex)
            {
                return null;
            }
        }
    }
}
