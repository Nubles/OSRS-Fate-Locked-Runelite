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

    @Test
    public void theBannerAndChatNameStrictModeAndTheTrip()
    {
        TravelDecision decision = new TravelDecision(PermissionStatus.LOCKED,
            TravelAction.label(TravelFixtures.cast("Varrock Teleport")),
            "Varrock is locked");
        TravelAlternative tablet = new TravelAlternative("tablet:falador-teleport|Break", "Falador teleport tablet",
            new CanonicalChunk(46, 52));

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

    /** The menu's own words, which MenuFacts has without colour tags or the lock tag (see MenuFactsTest). */
    @Test
    public void labelsKeepTheGamesCaseAndNameThePlace()
    {
        assertEquals("Varrock Teleport", TravelAction.label(TravelFixtures.cast("Varrock Teleport")));
        assertEquals("Varrock teleport", TravelAction.label(TravelFixtures.item(8007, "Break", "Varrock teleport")));
        assertEquals("Amulet of glory(4) to Edgeville",
            TravelAction.label(TravelFixtures.item(1712, "Edgeville", "Amulet of glory(4)")));
        assertEquals("Walk here", TravelAction.label(TravelFixtures.other("Walk here", "")));
        assertEquals("Teleport", TravelAction.label(TravelFixtures.other("Teleport", "")));
        assertEquals("", TravelAction.label(null));
    }

    private static TravelAction action()
    {
        return new TravelAction("spell:standard:varrock-teleport", "Cast", "Varrock Teleport",
            null, Collections.singletonList(new CanonicalChunk(50, 53)), false, TravelAction.Confidence.EXACT);
    }
}
