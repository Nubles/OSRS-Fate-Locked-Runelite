package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.guardian.GuardResult;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.rules.DecisionService;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;

import java.util.Optional;

/**
 * Owns the complete recognised-travel flow. One verdict from the Strict Mode
 * gate routes the click; the click handler is invoked only for a proven
 * block, and only after every presentation decision has been staged.
 */
public final class TravelGuardianCoordinator
{
    private final TravelActionResolver resolver;
    private final TravelRuleEvaluator evaluator;
    private final TravelAlternativeFinder alternativeFinder;
    private final TravelBlockNoticeStore noticeStore;
    private final StrictModeClickHandler clickHandler;

    public TravelGuardianCoordinator(
        TravelActionResolver resolver,
        TravelRuleEvaluator evaluator,
        TravelAlternativeFinder alternativeFinder,
        TravelBlockNoticeStore noticeStore,
        StrictModeClickHandler clickHandler)
    {
        this.resolver = resolver;
        this.evaluator = evaluator;
        this.alternativeFinder = alternativeFinder;
        this.noticeStore = noticeStore;
        this.clickHandler = clickHandler;
    }

    public TravelGuardianResult handle(
        MenuOptionClicked event,
        MenuEntry entry,
        Client client,
        CanonicalChunk origin,
        StrictModeReadiness readiness,
        DecisionService rules,
        TravelAvailability availability)
    {
        TravelAction action = resolver.resolve(entry, client, origin);
        TravelDecision decision = withDisplayLabel(
            evaluator.evaluate(action, rules));
        GuardResult verdict = clickHandler.decide(action, decision, readiness);

        if (verdict.getOutcome() == GuardResult.Outcome.ALLOW_PAUSED)
        {
            // Only locked travel is let through by the pause; travel the
            // rules allow anyway is not worth recording. A repeated trip is
            // recorded once per chat window.
            boolean recordPaused =
                noticeStore.shouldWriteChat("paused:" + fingerprint(action));
            return new TravelGuardianResult(
                action, decision, null, verdict,
                false, false, recordPaused);
        }

        if (verdict.getOutcome() != GuardResult.Outcome.BLOCK)
        {
            // Not proven locked: the click is not Strict Mode's to touch.
            return new TravelGuardianResult(
                action, decision, null, verdict,
                false, false, false);
        }

        TravelAlternative alternative = findAlternative(action, rules, availability);
        String fingerprint = fingerprint(action);
        noticeStore.show(
            fingerprint,
            "Travel blocked \u2014 " + decision.getLabel(),
            reason(decision),
            alternative == null ? null : alternative.getLabel());
        boolean writeChat = noticeStore.shouldWriteChat(fingerprint);

        // Final enforcement operation: all fallible coordinator work is above.
        GuardResult guardResult =
            clickHandler.handleTravel(event, action, decision, readiness);
        // Every click on a blocked trip is consumed, but a repeat inside the
        // chat window is neither announced nor recorded again.
        return new TravelGuardianResult(
            action, decision, alternative, guardResult,
            writeChat, writeChat, false);
    }

    private TravelAlternative findAlternative(
        TravelAction action,
        DecisionService rules,
        TravelAvailability availability)
    {
        try
        {
            Optional<TravelAlternative> alternative =
                alternativeFinder.find(action, rules, availability);
            return alternative == null ? null : alternative.orElse(null);
        }
        catch (RuntimeException ex)
        {
            return null;
        }
    }

    private static TravelDecision withDisplayLabel(TravelDecision decision)
    {
        if (decision == null
            || decision.getLabel() == null
            || decision.getLabel().isEmpty()
            || Character.isUpperCase(decision.getLabel().charAt(0)))
        {
            return decision;
        }
        String label = Character.toUpperCase(decision.getLabel().charAt(0))
            + decision.getLabel().substring(1);
        return new TravelDecision(
            decision.getStatus(), label, decision.getReason());
    }

    private static String fingerprint(TravelAction action)
    {
        CanonicalChunk destination = action.getDestination();
        String method = action.getMethodId() == null
            ? action.getFamily().name().toLowerCase()
            : action.getMethodId();
        return method + ":" + destination.getCx() + "," + destination.getCy();
    }

    private static String reason(TravelDecision decision)
    {
        String reason = decision.getReason();
        return reason == null || reason.trim().isEmpty()
            ? "Travel is locked" : reason;
    }
}
