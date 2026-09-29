package com.fatelocked;

import com.fatelocked.detectors.SlayerTaskDetector;
import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.guardian.StrictModeAuditLog;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The local files one OSRS account owns, in accounts/&lt;account hash&gt;/:
 * its detected events, its Strict Mode audit log, its Slayer task
 * and its finished diary tiers. A main account and an ironman played in one RuneLite no longer
 * share them. A file that fails to open leaves its feature off for that
 * account, and never stops the plugin.
 *
 * <p>An account's folder starts from the shared files older versions kept,
 * which are only ever read: since they name no account, the audit log and
 * Slayer task only for the character the rules are bound to. The detected
 * events start empty: the event-history.json files of earlier versions are
 * left as they are, and never read (Stage 4, plan decision 8).
 */
@Slf4j
final class AccountFiles
{
    static final String AUDIT_LOG = "strict-mode-events.json";
    static final String SLAYER = "slayer-assignment.json";

    final long accountHash;
    /** Null when it couldn't be opened. */
    final DetectedEventStore detected;
    /** Null when it couldn't be opened. */
    final StrictModeAuditLog auditLog;
    /** Null when it couldn't be opened. */
    final SlayerTaskDetector slayer;
    /** The diary tiers the account has finished. */
    final DiaryTierMemory diaryTiers;

    private AccountFiles(long accountHash, DetectedEventStore detected,
        StrictModeAuditLog auditLog, SlayerTaskDetector slayer, DiaryTierMemory diaryTiers)
    {
        this.accountHash = accountHash;
        this.detected = detected;
        this.auditLog = auditLog;
        this.slayer = slayer;
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
        SlayerTaskDetector slayer = null;
        try
        {
            startFromShared(dataDirectory, folder, SLAYER, boundCharacter);
            slayer = new SlayerTaskDetector(gson, folder.resolve(SLAYER));
        }
        catch (IOException | RuntimeException error)
        {
            log.warn("Could not open the Slayer task state", error);
        }
        return new AccountFiles(accountHash, detected, auditLog, slayer,
            new DiaryTierMemory(gson, folder.resolve(DiaryTierMemory.FILE)));
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
