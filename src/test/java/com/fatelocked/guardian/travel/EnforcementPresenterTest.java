package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.PermissionStatus;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * B15 (G10): what Strict Mode says when it blocks a trip. The banner names
 * Strict Mode and the trip in the game's own case, with no "(locked)"; the
 * chat line adds another way there and how to pause.
 */
public class EnforcementPresenterTest
{
    private static final String PAUSE =
        "To go anyway, pause Strict Mode for 60 seconds from the banner or the sidebar.";

    private final EnforcementPresenter presenter = new EnforcementPresenter();
    private final TravelActionResolver resolver = new TravelActionResolver();

    @Test
    public void theBannerAndChatNameStrictModeAndTheTrip()
    {
        TravelDecision decision = new TravelDecision(PermissionStatus.LOCKED,
            TravelActionResolver.displayLabel("Cast", "<col=00ff00>Varrock Teleport</col> <col=ef4444>(LOCKED)</col>"),
            "Varrock is locked");
        TravelAlternative tablet = new TravelAlternative("falador-tablet", "Falador teleport tablet",
            new CanonicalChunk(46, 52), "Teleport Tablets", Collections.singleton(8009), null, 0, null);

        BlockNotice notice = presenter.present(action(), decision, tablet);

        assertEquals("Strict Mode blocked Varrock Teleport", notice.getHeadline());
        assertEquals("Varrock is locked", notice.getReason());
        assertEquals("Falador teleport tablet", notice.getAlternative());
        assertEquals("Strict Mode blocked Varrock Teleport: Varrock is locked. "
            + "Try Falador teleport tablet instead. " + PAUSE, notice.getChatLine());
        assertFalse(notice.getChatLine().toLowerCase().contains("(locked)"));
    }

    @Test
    public void withNoReasonOrAlternativeItStillSaysWhy()
    {
        BlockNotice notice = presenter.present(action(),
            new TravelDecision(PermissionStatus.LOCKED, "Ectophial", "  "), null);

        assertEquals("Travel is locked", notice.getReason());
        assertNull(notice.getAlternative());
        assertEquals("Strict Mode blocked Ectophial: Travel is locked. " + PAUSE, notice.getChatLine());
    }

    @Test
    public void labelsKeepTheGamesCaseAndNameThePlace()
    {
        assertEquals("Varrock Teleport", TravelActionResolver.displayLabel("Cast", "Varrock Teleport (LOCKED)"));
        assertEquals("Varrock teleport", TravelActionResolver.displayLabel("Break", "<col=ff9040>Varrock teleport</col>"));
        assertEquals("Amulet of glory(4) to Edgeville",
            TravelActionResolver.displayLabel("Edgeville", "<col=ff9040>Amulet of glory(4)</col>"));
        assertEquals("Walk here", TravelActionResolver.displayLabel("Walk here", ""));
        assertEquals("Varrock Teleport", resolver.resolve(entry("Cast",
            "<col=00ff00>Varrock Teleport</col> <col=ef4444>(LOCKED)</col>"), null, null).getLabel());
    }

    private static TravelAction action()
    {
        return new TravelAction(TravelAction.Family.SPELL_OR_ITEM, "named-teleport", "Varrock Teleport",
            null, new CanonicalChunk(50, 53), null, TravelAction.Confidence.EXACT);
    }

    private static net.runelite.api.MenuEntry entry(String option, String target)
    {
        net.runelite.api.MenuEntry entry = org.mockito.Mockito.mock(net.runelite.api.MenuEntry.class);
        org.mockito.Mockito.when(entry.getOption()).thenReturn(option);
        org.mockito.Mockito.when(entry.getTarget()).thenReturn(target);
        org.mockito.Mockito.when(entry.getType()).thenReturn(net.runelite.api.MenuAction.CC_OP);
        return entry;
    }
}
