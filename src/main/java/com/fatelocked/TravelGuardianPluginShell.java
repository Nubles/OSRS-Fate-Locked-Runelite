package com.fatelocked;

import com.fatelocked.guardian.GuardContext;
import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelAlternative;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelGuardianResult;
import com.fatelocked.rules.FateRuleEngine;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;

import java.time.Clock;

/**
 * Thin plugin boundary for coordinator routing and post-enforcement side
 * effects. It never repeats travel recognition, evaluation, or presentation.
 */
final class TravelGuardianPluginShell
{
    enum Route
    {
        EXACT_TRAVEL,
        GENERIC,
        FAIL_OPEN
    }

    @FunctionalInterface
    interface ChatSink
    {
        void write(String message);
    }

    @FunctionalInterface
    interface AuditSink
    {
        void write(StrictModeAuditEntry entry) throws Exception;
    }

    @FunctionalInterface
    interface GenericHandler
    {
        void handle(MenuOptionClicked event, GuardContext context);
    }

    @FunctionalInterface
    interface DiagnosticSink
    {
        void record(String stage, Exception error);
    }

    private final TravelGuardianCoordinator coordinator;
    private final TravelAvailability availability;
    private final ChatSink chatSink;
    private final AuditSink auditSink;
    private final GenericHandler genericHandler;
    private final DiagnosticSink diagnosticSink;
    private final Clock clock;

    TravelGuardianPluginShell(
        TravelGuardianCoordinator coordinator,
        TravelAvailability availability,
        ChatSink chatSink,
        AuditSink auditSink,
        GenericHandler genericHandler,
        DiagnosticSink diagnosticSink,
        Clock clock)
    {
        this.coordinator = coordinator;
        this.availability = availability;
        this.chatSink = chatSink;
        this.auditSink = auditSink;
        this.genericHandler = genericHandler;
        this.diagnosticSink = diagnosticSink;
        this.clock = clock;
    }

    Route handle(
        MenuOptionClicked event,
        Client client,
        CanonicalChunk origin,
        GuardContext context,
        FateRuleEngine rules)
    {
        TravelGuardianResult result;
        try
        {
            result = coordinator.handle(
                event, event.getMenuEntry(), client, origin,
                context, rules, availability);
        }
        catch (RuntimeException ex)
        {
            diagnose("coordinator", ex);
            return Route.FAIL_OPEN;
        }

        if (result == null)
        {
            diagnose("coordinator",
                new IllegalStateException("coordinator returned no result"));
            return Route.FAIL_OPEN;
        }

        TravelAction action = result.getAction();
        if (action == null
            || action.getConfidence() != TravelAction.Confidence.EXACT)
        {
            genericHandler.handle(event, context);
            return Route.GENERIC;
        }

        if (result.isWriteChat())
        {
            try
            {
                chatSink.write(chatMessage(result));
            }
            catch (RuntimeException ex)
            {
                diagnose("chat", ex);
            }
        }
        if (result.isWriteBlockedAudit() || result.isWritePausedAudit())
        {
            try
            {
                auditSink.write(auditEntry(result));
            }
            catch (Exception ex)
            {
                diagnose("audit", ex);
            }
        }
        return Route.EXACT_TRAVEL;
    }

    private String chatMessage(TravelGuardianResult result)
    {
        String reason = result.getDecision().getReason();
        if (reason == null || reason.trim().isEmpty())
        {
            reason = "Travel is locked";
        }
        StringBuilder message = new StringBuilder()
            .append("[Fate Guardian] Blocked ")
            .append(result.getDecision().getLabel())
            .append(": ")
            .append(reason)
            .append('.');
        TravelAlternative alternative = result.getAlternative();
        if (alternative != null
            && alternative.getLabel() != null
            && !alternative.getLabel().trim().isEmpty())
        {
            message.append(" Suggested: ")
                .append(alternative.getLabel())
                .append('.');
        }
        return message.toString();
    }

    private StrictModeAuditEntry auditEntry(TravelGuardianResult result)
    {
        TravelAction action = result.getAction();
        CanonicalChunk destination = action.getDestination();
        String chunk = destination == null ? null
            : destination.getCx() + "," + destination.getCy();
        boolean paused = result.isWritePausedAudit();
        return new StrictModeAuditEntry(
            clock.millis(),
            "TRAVEL",
            result.getDecision().getLabel(),
            chunk,
            result.getDecision().getReason(),
            paused ? "ALLOWED_PAUSED" : "BLOCKED",
            paused,
            !paused && result.getAlternative() != null);
    }

    private void diagnose(String stage, Exception error)
    {
        try
        {
            diagnosticSink.record(stage, error);
        }
        catch (RuntimeException ignored)
        {
            // Diagnostics must never change fail-open or enforced outcomes.
        }
    }
}
