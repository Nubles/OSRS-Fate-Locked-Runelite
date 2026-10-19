package com.fatelocked;

import com.fatelocked.guardian.travel.NetworkFixtures;
import com.fatelocked.guardian.travel.TravelFixtures;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.ui.Palette;
import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The fairy ring dial status: the code dialled and the tracker's decision for it, in the
 * palette's open, not-ready and locked tones, as advice with Strict Mode on or off.
 */
public class FairyRingDialLineTest
{
    private final DecisionService rules = NetworkFixtures.rules();

    @Test
    public void eachDecisionReadsAsItShould()
    {
        assertLine("CKS: Locked: Karamja", Palette.Tone.BAD, "CKS");
        assertLine("AIQ: Unlocked", Palette.Tone.GOOD, "AIQ");
        assertLine("CIP: Not ready: Needs Fairytale II", Palette.Tone.PENDING, "CIP");
        assertLine("DKR: Needs checking", Palette.Tone.NEUTRAL, "DKR");
        assertLine("the tracker's decision, even with nowhere to land", "BLQ: Locked: Needs Fairy Rings",
            Palette.Tone.BAD, "BLQ");
        assertLine("AIP: not in the rules", Palette.Tone.NEUTRAL, "AIP");
    }

    @Test
    public void rulesWithoutCodesSayTheCodeIsNotInTheRules() throws Exception
    {
        assertEquals("AIQ: not in the rules", FairyRingDialLine.present("AIQ", TravelFixtures.nubles()).getText());
        DecisionService noTravel = DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(
            new com.google.gson.Gson(), fixture("bundles/v4-rules.json"))), null, null);
        assertEquals("CKS: not in the rules", FairyRingDialLine.present("CKS", noTravel).getText());
    }

    @Test
    public void noLineWithoutACodeOrForAnotherCharacter()
    {
        assertNull(FairyRingDialLine.present(null, rules));
        assertNull(FairyRingDialLine.present("CKS", null));
        assertNull("another character's rules say nothing about this one",
            FairyRingDialLine.present("CKS", DecisionService.create(rules.rules(), NetworkFixtures.PLAYER, "someone else")));
        assertNull("nobody logged in", FairyRingDialLine.present("CKS",
            DecisionService.create(rules.rules(), NetworkFixtures.PLAYER, null)));
        assertNull("no rules", FairyRingDialLine.present("CKS", DecisionService.create(RulesSnapshot.empty(), null, null)));
    }

    @Test
    public void aLongReasonIsCutShort()
    {
        String reason = "Needs every one of these unlocks and a quest that is long";
        String text = FairyRingDialLine.present("CKS", withReason(reason)).getText();
        assertTrue(text, text.startsWith("CKS: Locked: Needs every one"));
        assertEquals("CKS: Locked: ".length() + FairyRingDialLine.MAX_REASON, text.length());
    }

    /** Above the dials where the view leaves room; otherwise inside their top edge, never over them. */
    @Test
    public void theLineSitsAboveTheDialsOrJustInsideTheirTop()
    {
        int ascent = 12;
        int descent = 3;
        assertEquals("resizable: room above", 100 - FateLockedFairyRingOverlay.GAP - descent,
            FateLockedFairyRingOverlay.baseline(new Rectangle(200, 100, 512, 334), ascent, descent));
        assertEquals("fixed: the dials fill the view", 4 + FateLockedFairyRingOverlay.GAP + ascent,
            FateLockedFairyRingOverlay.baseline(new Rectangle(4, 4, 512, 334), ascent, descent));
        assertEquals(FateLockedFairyRingOverlay.GAP + ascent,
            FateLockedFairyRingOverlay.baseline(new Rectangle(0, 0, 512, 334), ascent, descent));
    }

    private void assertLine(String text, Palette.Tone tone, String code)
    {
        assertLine(code, text, tone, code);
    }

    private void assertLine(String message, String text, Palette.Tone tone, String code)
    {
        FairyRingDialLine.Line line = FairyRingDialLine.present(code, rules);
        assertEquals(message, text, line.getText());
        assertEquals(message, tone, line.getTone());
    }

    private static String fixture(String name) throws Exception
    {
        try (java.io.InputStream in = FairyRingDialLineTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static DecisionService withReason(String reason)
    {
        String json = NetworkFixtures.json().replace("\"reason\":\"Karamja\"", "\"reason\":\"" + reason + "\"");
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(new com.google.gson.Gson(), json)),
            NetworkFixtures.PLAYER, NetworkFixtures.PLAYER);
    }
}
