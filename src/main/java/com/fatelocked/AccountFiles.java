package com.fatelocked;

import com.fatelocked.detectors.SlayerTaskDetector;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventHistory;
import com.fatelocked.guardian.StrictModeAuditLog;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The local files one OSRS account owns, in accounts/&lt;account hash&gt;/:
 * its detected-event history, its Strict Mode audit log and its Slayer
 * task. A main account and an ironman played in one RuneLite no longer
 * share them. A file that fails to open leaves its feature off for that
 * account, and never stops the plugin.
 *
 * <p>An account's folder starts from the shared files older versions kept,
 * which are only ever read: the history's events for that character, and,
 * since they name no account, the audit log and Slayer task only for the
 * character the rules are bound to.
 */
@Slf4j
final class AccountFiles
{
    static final String HISTORY = "event-history.json";
    static final String LEGACY_OUTBOX = "event-outbox.json";
    static final String AUDIT_LOG = "strict-mode-events.json";
    static final String SLAYER = "slayer-assignment.json";

    final long accountHash;
    /** Null when it couldn't be opened. */
    final FateEventHistory history;
    /** Null when it couldn't be opened. */
    final StrictModeAuditLog auditLog;
    /** Null when it couldn't be opened. */
    final SlayerTaskDetector slayer;

    private AccountFiles(long accountHash, FateEventHistory history,
        StrictModeAuditLog auditLog, SlayerTaskDetector slayer)
    {
        this.accountHash = accountHash;
        this.history = history;
        this.auditLog = auditLog;
        this.slayer = slayer;
    }

    static Path folder(Path dataDirectory, long accountHash)
    {
        return dataDirectory.resolve("accounts").resolve(Long.toUnsignedString(accountHash));
    }

    /**
     * @param accountName the character's name, whose events the shared history gives
     * @param boundCharacter whether the rules are bound to this character
     */
    static AccountFiles open(Gson gson, Path dataDirectory, long accountHash,
        String accountName, boolean boundCharacter)
    {
        Path folder = folder(dataDirectory, accountHash);
        FateEventHistory history = openHistory(gson, dataDirectory, folder, accountName);
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
        return new AccountFiles(accountHash, history, auditLog, slayer);
    }

    private static FateEventHistory openHistory(
        Gson gson, Path dataDirectory, Path folder, String accountName)
    {
        Path target = folder.resolve(HISTORY);
        boolean fresh = !Files.exists(target);
        try
        {
            FateEventHistory history = new FateEventHistory(gson, target, null);
            if (fresh)
            {
                List<FateEvent> ours = new ArrayList<>();
                for (FateEvent event : FateEventHistory.readOnly(gson,
                    dataDirectory.resolve(HISTORY), dataDirectory.resolve(LEGACY_OUTBOX)))
                {
                    if (AccountBinding.sameAccount(event.getAccount(), accountName))
                    {
                        ours.add(event);
                    }
                }
                if (!ours.isEmpty())
                {
                    history.adopt(ours);
                }
            }
            return history;
        }
        catch (IOException | RuntimeException error)
        {
            log.warn("Could not open the local Fate event history", error);
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
