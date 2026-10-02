package com.fatelocked;

import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.guardian.StrictModeAuditLog;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The local files one OSRS account owns, in accounts/&lt;account hash&gt;/:
 * its detected events, its Strict Mode audit log, and the quests and diary
 * tiers it has finished. A main account and an ironman played in one
 * RuneLite no longer share them. A file that fails to open leaves its
 * feature off for that account, and never stops the plugin.
 *
 * <p>An account's folder starts from the shared audit log older versions
 * kept, which is only ever read: since it names no account, only for the
 * character the rules are bound to. The detected events start empty: the
 * event-history.json files of earlier versions are left as they are, and
 * never read (Stage 4, plan decision 8). So are their Slayer task files:
 * the game's own variables say what the task is.
 */
@Slf4j
final class AccountFiles
{
    static final String AUDIT_LOG = "strict-mode-events.json";

    final long accountHash;
    /** Null when it couldn't be opened. */
    final DetectedEventStore detected;
    /** Null when it couldn't be opened. */
    final StrictModeAuditLog auditLog;
    /** The quests the account has finished; null when they couldn't be read. */
    final FinishedMemory quests;
    /** The diary tiers the account has finished; null when they couldn't be read. */
    final FinishedMemory diaryTiers;

    private AccountFiles(long accountHash, DetectedEventStore detected,
        StrictModeAuditLog auditLog, FinishedMemory quests, FinishedMemory diaryTiers)
    {
        this.accountHash = accountHash;
        this.detected = detected;
        this.auditLog = auditLog;
        this.quests = quests;
        this.diaryTiers = diaryTiers;
    }

    static Path folder(Path dataDirectory, long accountHash)
    {
        return dataDirectory.resolve("accounts").resolve(Long.toUnsignedString(accountHash));
    }

    /** @param boundCharacter whether the rules are bound to this character */
    static AccountFiles open(Gson gson, Path dataDirectory, long accountHash, boolean boundCharacter)
    {
        Path folder = folder(dataDirectory, accountHash);
        DetectedEventStore detected = null;
        try
        {
            detected = new DetectedEventStore(gson, folder.resolve(DetectedEventStore.FILE));
        }
        catch (IOException | RuntimeException error)
        {
            log.warn("Could not open the detected events", error);
        }
        StrictModeAuditLog auditLog = null;
        try
        {
            startFromShared(dataDirectory, folder, AUDIT_LOG, boundCharacter);
            auditLog = new StrictModeAuditLog(gson, folder.resolve(AUDIT_LOG));
        }
        catch (IOException | RuntimeException error)
        {
            log.warn("Could not open the Strict Mode audit log", error);
        }
        return new AccountFiles(accountHash, detected, auditLog,
            memory(gson, folder.resolve(FinishedMemory.QUESTS), "quests"),
            memory(gson, folder.resolve(FinishedMemory.DIARY_TIERS), "diary tiers"));
    }

    private static FinishedMemory memory(Gson gson, Path path, String what)
    {
        try
        {
            return FinishedMemory.open(gson, path);
        }
        catch (IOException | RuntimeException error)
        {
            log.warn("Could not read the finished " + what, error);
            return null;
        }
    }

    /** Copy a shared file into a new folder, for the bound character only; the shared one stays. */
    private static void startFromShared(Path dataDirectory, Path folder, String name,
        boolean boundCharacter) throws IOException
    {
        Path target = folder.resolve(name);
        Path shared = dataDirectory.resolve(name);
        if (boundCharacter && !Files.exists(target) && Files.isRegularFile(shared))
        {
            Files.createDirectories(folder);
            Files.copy(shared, target);
        }
    }
}
