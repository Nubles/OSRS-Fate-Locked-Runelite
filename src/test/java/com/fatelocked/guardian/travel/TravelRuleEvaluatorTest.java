package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.MenuFacts;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.fatelocked.guardian.travel.TravelFixtures.AMULET_OF_GLORY_4;
import static com.fatelocked.guardian.travel.TravelFixtures.ANCIENT;
import static com.fatelocked.guardian.travel.TravelFixtures.DIGSITE_PENDANT_5;
import static com.fatelocked.guardian.travel.TravelFixtures.FAIRY_RING;
import static com.fatelocked.guardian.travel.TravelFixtures.FALADOR_TABLET;
import static com.fatelocked.guardian.travel.TravelFixtures.LUMBRIDGE_TABLET;
import static com.fatelocked.guardian.travel.TravelFixtures.cast;
import static com.fatelocked.guardian.travel.TravelFixtures.item;
import static com.fatelocked.guardian.travel.TravelFixtures.nubles;
import static com.fatelocked.guardian.travel.TravelFixtures.object;
import static com.fatelocked.guardian.travel.TravelFixtures.other;
import static com.fatelocked.guardian.travel.TravelFixtures.spell;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * F3 (G6): a trip's decision is the tracker's own for the option clicked,
 * which already counts the unlocks the method needs and where it lands. An
 * option that can go to several places is Unknown, even when the tracker
 * locks it: the place is picked after the click.
 */
public class TravelRuleEvaluatorTest
{
    private static final CanonicalChunk ORIGIN = new CanonicalChunk(50, 51);

    private final TravelRuleEvaluator evaluator = new TravelRuleEvaluator();
    private final IntentClassifier classifier = new IntentClassifier();
    private final DecisionService rules = nubles();

    @Test
    public void aTripIsTheTrackersDecisionForItsOption()
    {
        assertEquals(new TravelDecision(PermissionStatus.LOCKED, "Falador Teleport", "Unlock Falador"),
            evaluate(cast("Falador Teleport"), rules));
        assertEquals(new TravelDecision(PermissionStatus.ALLOWED, "Lumbridge Teleport", null),
            evaluate(cast("Lumbridge Teleport"), rules));
        assertEquals(new TravelDecision(PermissionStatus.NOT_READY, "Camelot Teleport", "No route from Lumbridge"),
            evaluate(cast("Camelot Teleport"), rules));
        assertEquals(new TravelDecision(PermissionStatus.LOCKED, "Falador teleport", "Unlock Falador"),
            evaluate(item(FALADOR_TABLET, "Break", "Falador teleport"), rules));
        assertEquals(PermissionStatus.ALLOWED, evaluate(item(LUMBRIDGE_TABLET, "Break", "Lumbridge teleport"), rules).getStatus());
        assertEquals(new TravelDecision(PermissionStatus.LOCKED, "Amulet of glory(4) to Al Kharid", "Unlock Al Kharid"),
            evaluate(item(AMULET_OF_GLORY_4, "Al Kharid", "Amulet of glory(4)"), rules));
    }

    /** G6: the method's own unlock, as the web names it: a spellbook, the Digsite pendant. */
    @Test
    public void eachMethodNeedsItsOwnUnlock()
    {
        assertEquals(new TravelDecision(PermissionStatus.LOCKED, "Senntisten Teleport", "Needs Ancient Magicks"),
            evaluate(spell(ANCIENT, "Cast", "Senntisten Teleport"), rules));
        assertEquals(new TravelDecision(PermissionStatus.LOCKED, "Digsite pendant (5) to Digsite", "Needs Digsite Pendant"),
            evaluate(item(DIGSITE_PENDANT_5, "Digsite", "Digsite pendant (5)"), rules));
    }

    /** G7: several places, or none, are Unknown whatever the tracker says. */
    @Test
    public void severalPlacesAreUnknown()
    {
        assertEquals(PermissionStatus.UNKNOWN, evaluate(item(DIGSITE_PENDANT_5, "Rub", "Digsite pendant (5)"), rules).getStatus());
        assertEquals(PermissionStatus.UNKNOWN, evaluate(cast("Varrock Teleport"), rules).getStatus());
        assertEquals(PermissionStatus.UNKNOWN, evaluate(item(AMULET_OF_GLORY_4, "Rub", "Amulet of glory(4)"), rules).getStatus());
    }

    /** Advisory travel still has the tracker's decision, for tags; the guard never blocks it. */
    @Test
    public void advisoryTravelKeepsItsDecision()
    {
        assertEquals(new TravelDecision(PermissionStatus.LOCKED, "Fairy ring to Zanaris", "Needs Fairy Rings"),
            evaluate(object(FAIRY_RING, "Zanaris", "Fairy ring"), rules));
    }

    @Test
    public void anotherCharacterOrOlderRulesDecideNothing() throws Exception
    {
        TravelDecision other = evaluate(cast("Falador Teleport"), TravelFixtures.playing("zezima"));
        assertEquals(PermissionStatus.UNKNOWN, other.getStatus());

        DecisionService legacy = DecisionService.create(RulesSnapshot.of(fixture("bundles/v3-standard.json")), "nubles", "nubles");
        assertEquals(PermissionStatus.UNKNOWN, evaluate(cast("Falador Teleport"), legacy).getStatus());
    }

    @Test
    public void anythingElseIsUnknown()
    {
        assertEquals(new TravelDecision(PermissionStatus.UNKNOWN, "Continue", null), evaluate(other("Continue", ""), rules));
        assertEquals(new TravelDecision(PermissionStatus.UNKNOWN, "Unknown travel", null), evaluator.evaluate(null, null, rules));
        TravelMatch match = classifier.classify(cast("Falador Teleport"), rules.travelTable());
        TravelAction action = TravelAction.of(match, cast("Falador Teleport"), ORIGIN);
        assertEquals(PermissionStatus.UNKNOWN, evaluator.evaluate(match, action, null).getStatus());
        assertEquals(PermissionStatus.UNKNOWN, evaluator.evaluate(null, action, rules).getStatus());
        assertNull(evaluator.evaluate(null, action, rules).getReason());
    }

    private TravelDecision evaluate(MenuFacts facts, DecisionService decisions)
    {
        TravelMatch match = classifier.classify(facts, decisions.travelTable());
        TravelAction action = match == null ? TravelAction.notTravel(facts, ORIGIN) : TravelAction.of(match, facts, ORIGIN);
        return evaluator.evaluate(match, action, decisions);
    }

    private FateLockedBundle fixture(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            return FateLockedBundle.loadFromJson(new Gson(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
