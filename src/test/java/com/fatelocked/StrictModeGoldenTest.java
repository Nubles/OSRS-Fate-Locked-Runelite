package com.fatelocked;

import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.guardian.travel.TravelAction;
import com.fatelocked.guardian.travel.TravelActionResolver;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelGuardianResult;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.Trust;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B4: Strict Mode on the decision service, against the golden bundles. Each
 * teleport the plugin matches today ("Cast" on every place it knows) is
 * blocked exactly when the tracker locks where it lands, with fresh rules
 * bound to the character playing; on another character nothing is blocked,
 * even with the gate forced open.
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
    private final String account;
    private final Client client = mock(Client.class);
    private final TravelAvailability availability = mock(TravelAvailability.class);

    public StrictModeGoldenTest(String id) throws IOException
    {
        this.id = id;
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json));
        JsonElement bound = GSON.fromJson(json, JsonObject.class).getAsJsonObject("rules").get("account");
        account = bound == null || bound.isJsonNull() ? null : AccountBinding.normalize(bound.getAsString());
    }

    @Test
    public void everyLockedDestinationBlocksWhenFreshAndBound()
    {
        DecisionService playing = DecisionService.create(rules, account, account);
        StrictModeReadiness readiness = readiness(playing, account);
        if (account == null)
        {
            // An unbound profile: Strict Mode can't act, so nothing is blocked.
            assertEquals(StrictModeReadiness.State.INACTIVE, readiness.getState());
            assertEquals(List.of(), blockedPlaces(playing, readiness));
            return;
        }
        assertEquals(StrictModeReadiness.State.ACTIVE, readiness.getState());

        List<String> want = new ArrayList<>();
        for (String place : Teleports.destinations().keySet())
        {
            Decision where = playing.chunk(Teleports.destinations().get(place));
            if (where.getStatus() == PermissionStatus.LOCKED && where.getSource() == Decision.Source.CHUNK)
            {
                want.add(place);
            }
        }
        assertTrue(id + " locks somewhere a teleport lands", !want.isEmpty());
        assertEquals(id, want, blockedPlaces(playing, readiness));
    }

    @Test
    public void nothingIsBlockedOnAnotherCharacter()
    {
        if (account == null) return;
        DecisionService other = DecisionService.create(rules, account, "someone else");
        assertEquals(Trust.WRONG_CHARACTER, other.trust());
        StrictModeReadiness readiness = readiness(other, "someone else");
        assertEquals(StrictModeReadiness.State.INACTIVE, readiness.getState());

        assertEquals(id, List.of(), blockedPlaces(other, readiness));
        StrictModeReadiness forcedOpen = new StrictModeReadiness(StrictModeReadiness.State.ACTIVE, null);
        assertEquals(id + " with the gate forced open", List.of(), blockedPlaces(other, forcedOpen));
    }

    /** The readiness the plugin works out for these rules and this character, fresh and unpaused. */
    private StrictModeReadiness readiness(DecisionService decisions, String player)
    {
        return StrictModeReadiness.evaluate(true, false, true, account, player,
            decisions.trust() == Trust.TRUSTED && decisions.isBound(), true);
    }

    /** Click "Cast" on every place the plugin's teleport table knows; the places whose click was consumed. */
    private List<String> blockedPlaces(DecisionService decisions, StrictModeReadiness readiness)
    {
        TravelAlternativeFinder finder = mock(TravelAlternativeFinder.class);
        when(finder.find(any(), any(), any())).thenReturn(Optional.empty());
        TravelGuardianCoordinator coordinator = new TravelGuardianCoordinator(
            new TravelActionResolver(), new TravelRuleEvaluator(), finder,
            new TravelBlockNoticeStore(Clock.systemUTC()),
            new StrictModeClickHandler(new StrictModeGuard()));
        List<String> blocked = new ArrayList<>();
        for (String place : Teleports.destinations().keySet())
        {
            MenuOptionClicked click = cast(place + " Teleport");
            TravelGuardianResult result = coordinator.handle(
                click, click.getMenuEntry(), client, ORIGIN, readiness, decisions, availability);
            assertEquals(place, TravelAction.Confidence.EXACT, result.getAction().getConfidence());
            assertEquals(place, Teleports.destinations().get(place), result.getAction().getDestination());
            if (Mockito.mockingDetails(click).getInvocations().stream()
                .anyMatch(call -> call.getMethod().getName().equals("consume")))
            {
                blocked.add(place);
            }
        }
        return blocked;
    }

    private static MenuOptionClicked cast(String spell)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn("Cast");
        when(entry.getTarget()).thenReturn(spell);
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);
        return click;
    }
}
