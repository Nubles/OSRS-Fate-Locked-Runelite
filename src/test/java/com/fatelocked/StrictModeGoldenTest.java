package com.fatelocked;

import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.guardian.travel.IntentClassifier;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelGuardianResult;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.TravelTable;
import com.fatelocked.rules.Trust;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F3 against the golden bundles. Every option of every method in the
 * tracker's travel table, clicked as the game gives it, is blocked exactly
 * when the tracker locks it, it goes to one place and it isn't advisory,
 * with fresh rules bound to the character playing. On another character
 * nothing is blocked, even with the gate forced open.
 */
@RunWith(Parameterized.class)
public class StrictModeGoldenTest
{
    private static final Gson GSON = new Gson();
    private static final CanonicalChunk ORIGIN = new CanonicalChunk(50, 50);

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> scenarios() throws IOException
    {
        return GoldenBundleContractTest.scenarios();
    }

    private final String id;
    private final RulesSnapshot rules;
    private final JsonObject travel;
    private final String account;
    private final TravelAvailability availability = mock(TravelAvailability.class);

    public StrictModeGoldenTest(String id) throws IOException
    {
        this.id = id;
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json));
        JsonObject wire = GSON.fromJson(json, JsonObject.class).getAsJsonObject("rules");
        travel = wire.getAsJsonObject("travel");
        JsonElement bound = wire.get("account");
        account = bound == null || bound.isJsonNull() ? null : AccountBinding.normalize(bound.getAsString());
    }

    @Test
    public void everyLockedTripToOnePlaceBlocksWhenFreshAndBound()
    {
        DecisionService playing = DecisionService.create(rules, account, account);
        StrictModeReadiness readiness = readiness(playing, account);
        if (account == null)
        {
            // An unbound profile: Strict Mode can't act, so nothing is blocked.
            assertEquals(StrictModeReadiness.State.INACTIVE, readiness.getState());
            assertEquals(List.of(), blocked(playing, readiness));
            return;
        }
        assertEquals(StrictModeReadiness.State.ACTIVE, readiness.getState());

        List<String> want = new ArrayList<>();
        for (Map.Entry<String, JsonElement> method : travel.entrySet())
        {
            JsonObject row = method.getValue().getAsJsonObject();
            if (row.has("advisory") && row.get("advisory").getAsBoolean()) continue;
            for (Map.Entry<String, JsonElement> option : row.getAsJsonObject("options").entrySet())
            {
                JsonObject decided = option.getValue().getAsJsonObject();
                if ("LOCKED".equals(decided.get("status").getAsString()) && decided.getAsJsonArray("to").size() == 1)
                {
                    want.add(method.getKey() + "|" + option.getKey());
                }
            }
        }
        assertTrue(id + " locks some trip", !want.isEmpty());
        List<String> got = blocked(playing, readiness);
        assertEquals(id, want, got);

        if (id.equals("vanilla-interiors"))
        {
            // G6 and G7, as vanilla-interiors has them: each item's own unlock, and one place.
            assertTrue(got.contains("item:digsite-pendant|Fossil Island"));
            assertFalse("the Rub goes to one of several places", got.contains("item:digsite-pendant|Rub"));
            assertTrue(got.contains("item:xerics-talisman|Xeric's Lookout"));
            assertTrue(got.contains("item:drakans-medallion|Ver Sinhaza"));
            assertTrue(got.contains("item:necklace-of-passage|Eagles' Eyrie"));
            assertTrue(got.contains("item:ring-of-dueling|Castle Wars"));
            assertFalse("Ancient Magicks is unlocked", got.contains("spell:ancient:senntisten-teleport|Cast"));
            assertFalse("advisory", got.contains("network:fairy-ring|Zanaris"));
        }
    }

    @Test
    public void nothingIsBlockedOnAnotherCharacter()
    {
        if (account == null) return;
        DecisionService other = DecisionService.create(rules, account, "someone else");
        assertEquals(Trust.WRONG_CHARACTER, other.trust());
        StrictModeReadiness readiness = readiness(other, "someone else");
        assertEquals(StrictModeReadiness.State.INACTIVE, readiness.getState());

        assertEquals(id, List.of(), blocked(other, readiness));
        StrictModeReadiness forcedOpen = new StrictModeReadiness(StrictModeReadiness.State.ACTIVE, null);
        assertEquals(id + " with the gate forced open", List.of(), blocked(other, forcedOpen));
    }

    /** The readiness the plugin works out for these rules and this character, fresh and unpaused. */
    private StrictModeReadiness readiness(DecisionService decisions, String player)
    {
        return StrictModeReadiness.evaluate(true, false, true, account, player,
            decisions.trust() == Trust.TRUSTED && decisions.isBound(), true);
    }

    /** Click every option of every method, on its first id; the trips whose click was consumed. */
    private List<String> blocked(DecisionService decisions, StrictModeReadiness readiness)
    {
        TravelAlternativeFinder finder = mock(TravelAlternativeFinder.class);
        when(finder.find(any(), any(), any())).thenReturn(Optional.empty());
        TravelGuardianCoordinator coordinator = new TravelGuardianCoordinator(
            new IntentClassifier(), new TravelRuleEvaluator(), finder,
            new TravelBlockNoticeStore(Clock.systemUTC()),
            new StrictModeClickHandler(new StrictModeGuard()));
        TravelTable table = decisions.travelTable();
        List<String> blocked = new ArrayList<>();
        for (TravelTable.Method method : table.methods())
        {
            for (String option : GoldenBundleContractTest.texts(method))
            {
                MenuOptionClicked click = mock(MenuOptionClicked.class);
                TravelGuardianResult result = coordinator.handle(click,
                    GoldenBundleContractTest.clicks(method, option).get(0), ORIGIN, readiness, decisions, availability);
                assertEquals(method.getId() + "|" + option, TravelAction.Confidence.EXACT, result.getAction().getConfidence());
                if (Mockito.mockingDetails(click).getInvocations().stream()
                    .anyMatch(call -> call.getMethod().getName().equals("consume")))
                {
                    blocked.add(method.getId() + "|" + option);
                }
            }
        }
        return blocked;
    }
}
