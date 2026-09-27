package com.fatelocked;

import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelGuardianResult;
import com.fatelocked.rules.DecisionService;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;

import java.time.Clock;

/**
 * Thin plugin boundary for coordinator routing and post-enforcement side
 * effects. It reads the click into {@link MenuFacts} on the client thread
 * and never repeats travel recognition, evaluation, or presentation.
 * Clicks that are not exactly matched travel are left alone.
 */
final class TravelGuardianPluginShell
{
    enum Route
    {
        EXACT_TRAVEL,
        NOT_TRAVEL,
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
    interface DiagnosticSink
    {
        void record(String stage, Exception error);
    }

    private final TravelGuardianCoordinator coordinator;
    private final TravelAvailability availability;
    private final ChatSink chatSink;
    private final AuditSink auditSink;
    private final DiagnosticSink diagnosticSink;
    private final Clock clock;

    TravelGuardianPluginShell(
        TravelGuardianCoordinator coordinator,
        TravelAvailability availability,
        ChatSink chatSink,
        AuditSink auditSink,
        DiagnosticSink diagnosticSink,
        Clock clock)
    {
        this.coordinator = coordinator;
        this.availability = availability;
        this.chatSink = chatSink;
        this.auditSink = auditSink;
        this.diagnosticSink = diagnosticSink;
        this.clock = clock;
    }

    Route handle(
        MenuOptionClicked event,
        Client client,
        StrictModeReadiness readiness,
        DecisionService travelRules)
    {
        TravelGuardianResult result;
        try
        {
            MenuFacts facts = new MenuFactsReader(client).read(event.getMenuEntry());
            result = coordinator.handle(event, facts, readiness, travelRules, availability);
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
            return Route.NOT_TRAVEL;
        }

        if (result.isWriteChat() && result.getNotice() != null)
        {
            try
            {
                chatSink.write(result.getNotice().getChatLine());
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
