package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.guardian.GuardContext;
import com.fatelocked.guardian.GuardResult;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;

import java.util.Optional;

/**
 * Owns the complete recognised-travel flow. The click handler is deliberately
 * invoked only after every presentation decision has been staged.
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
        GuardContext context,
        FateRuleEngine rules,
        TravelAvailability availability)
    {
        TravelAction action = resolver.resolve(entry, client, origin);
        TravelDecision decision = withDisplayLabel(
            evaluator.evaluate(action, rules));

        if (isTrustedExact(action, decision, context) && context.isPaused())
        {
            GuardResult guardResult =
                clickHandler.handleTravel(event, action, decision, context);
            return new TravelGuardianResult(
                action, decision, null, guardResult,
                false, false, true);
        }

        if (!isProvenBlock(action, decision, context))
        {
            GuardResult guardResult =
                clickHandler.handleTravel(event, action, decision, context);
            return new TravelGuardianResult(
                action, decision, null, guardResult,
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
            clickHandler.handleTravel(event, action, decision, context);
        return new TravelGuardianResult(
            action, decision, alternative, guardResult,
            writeChat, true, false);
    }

    private TravelAlternative findAlternative(
        TravelAction action,
        FateRuleEngine rules,
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

    private static boolean isProvenBlock(
        TravelAction action,
        TravelDecision decision,
        GuardContext context)
    {
        return isTrustedExact(action, decision, context)
            && !context.isPaused()
            && decision.getStatus() == PermissionStatus.LOCKED;
    }

    private static boolean isTrustedExact(
        TravelAction action,
        TravelDecision decision,
        GuardContext context)
    {
        if (action == null
            || decision == null
            || context == null
            || !context.isEnabled()
            || !context.isAccountMatches()
            || !context.isFreshRules()
            || context.getRules() == null
            || action.getConfidence() != TravelAction.Confidence.EXACT
            || action.getDestination() == null)
        {
            return false;
        }
        return decision.getStatus() == PermissionStatus.ALLOWED
            || decision.getStatus() == PermissionStatus.LOCKED;
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
