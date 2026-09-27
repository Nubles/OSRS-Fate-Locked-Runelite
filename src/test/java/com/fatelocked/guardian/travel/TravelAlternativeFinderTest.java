package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.fatelocked.guardian.travel.TravelFixtures.AMULET_OF_GLORY_4;
import static com.fatelocked.guardian.travel.TravelFixtures.DIGSITE_PENDANT_5;
import static com.fatelocked.guardian.travel.TravelFixtures.EDGEVILLE;
import static com.fatelocked.guardian.travel.TravelFixtures.EMIRS_ARENA;
import static com.fatelocked.guardian.travel.TravelFixtures.FALADOR;
import static com.fatelocked.guardian.travel.TravelFixtures.FALADOR_TABLET;
import static com.fatelocked.guardian.travel.TravelFixtures.LUMBRIDGE;
import static com.fatelocked.guardian.travel.TravelFixtures.LUMBRIDGE_TABLET;
import static com.fatelocked.guardian.travel.TravelFixtures.RING_OF_DUELING_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * F7: another way there, from the tracker's travel table: a trip on an item
 * the player carries or wears, to one place, that the tracker allows for
 * the run. In the fixture the Lumbridge tablet and the glory's Edgeville
 * are allowed; the Falador tablet, the glory's Karamja and Al Kharid, and
 * the Digsite pendant are locked; a Rub goes to one of several places.
 */
public class TravelAlternativeFinderTest
{
    private static final Gson GSON = new Gson();
    private static final String LUMBRIDGE_AREA = "Lumbridge · Misthalin";

    private final TravelAlternativeFinder finder = new TravelAlternativeFinder();
    private final TravelAvailability availability = mock(TravelAvailability.class);
    private final DecisionService rules = spy(TravelFixtures.nubles());
    private final Set<Integer> carried = new HashSet<>();

    public TravelAlternativeFinderTest()
    {
        when(availability.hasAnyItem(any())).thenAnswer(call -> {
            Set<Integer> ids = call.getArgument(0);
            return ids != null && !Collections.disjoint(ids, carried);
        });
    }

    @Test
    public void onlyACarriedTripToOnePlaceTheTrackerAllows()
    {
        assertFalse("carrying nothing", find(FALADOR).isPresent());

        carrying(FALADOR_TABLET, DIGSITE_PENDANT_5);
        assertFalse("every trip they have is locked, or goes to several places", find(FALADOR).isPresent());

        carrying(LUMBRIDGE_TABLET);
        assertEquals(new TravelAlternative("tablet:lumbridge-teleport|Break", "Lumbridge teleport", LUMBRIDGE),
            find(FALADOR).get());
    }

    @Test
    public void anOptionThatNamesAPlaceIsNamed()
    {
        carrying(AMULET_OF_GLORY_4);

        assertEquals(new TravelAlternative("item:amulet-of-glory|Edgeville", "Amulet of glory to Edgeville", EDGEVILLE),
            find(FALADOR).get());
    }

    /** The blocked trip's area first, then the nearest; then the lower id. */
    @Test
    public void theBlockedTripsAreaRanksFirst()
    {
        carrying(LUMBRIDGE_TABLET, AMULET_OF_GLORY_4);
        assertEquals(LUMBRIDGE_AREA, rules.areaName(LUMBRIDGE));

        assertEquals("neither in Falador's area: the nearer", "item:amulet-of-glory|Edgeville", id(FALADOR));

        doReturn(LUMBRIDGE_AREA).when(rules).areaName(FALADOR);
        assertEquals("in the blocked trip's area, though farther", "tablet:lumbridge-teleport|Break", id(FALADOR));

        CanonicalChunk northOfEdgeville = new CanonicalChunk(48, 55);
        assertEquals("the nearest", "item:amulet-of-glory|Edgeville", id(northOfEdgeville));

        CanonicalChunk between = new CanonicalChunk(49, 52);
        assertEquals("as near as each other: the lower id", "item:amulet-of-glory|Edgeville", id(between));
        doReturn(LUMBRIDGE_AREA).when(rules).areaName(between);
        assertEquals("tablet:lumbridge-teleport|Break", id(between));

        carrying(LUMBRIDGE_TABLET, RING_OF_DUELING_8);
        CanonicalChunk unnamed = new CanonicalChunk(50, 51);
        assertNull(rules.areaName(unnamed));
        assertNull(rules.areaName(EMIRS_ARENA));
        assertEquals("an area the lists don't name matches nothing", "tablet:lumbridge-teleport|Break", id(unnamed));
    }

    /** Where the area lists name nothing, the nearest wins: the same place first. */
    @Test
    public void withoutAreasTheNearestWins()
    {
        carrying(LUMBRIDGE_TABLET, RING_OF_DUELING_8);
        doReturn(null).when(rules).areaName(any());

        assertEquals("the same place, over the one next to it", "tablet:lumbridge-teleport|Break", id(LUMBRIDGE));
        assertEquals("item:ring-of-dueling|Emir's Arena", id(EMIRS_ARENA));
    }

    /** Spells aren't carried: only item trips are suggested, even with every id read as carried. */
    @Test
    public void onlyItemsAreSuggested()
    {
        when(availability.hasAnyItem(any())).thenReturn(true);

        assertEquals("tablet:lumbridge-teleport|Break", id(LUMBRIDGE));
    }

    /** One place, allowed: not an allowed trip to several places, nor one not ready. */
    @Test
    public void onlyOnePlaceTheTrackerAllows()
    {
        JsonObject root = GSON.fromJson(TravelFixtures.json(), JsonObject.class);
        root.getAsJsonObject("rules").getAsJsonObject("travel").add("item:odd", GSON.fromJson("{"
            + "\"label\": \"Odd item\", \"match\": {\"items\": [1]}, \"options\": {"
            + "\"Everywhere\": {\"to\": [\"50,50\", \"50,51\"], \"status\": \"ALLOWED\"},"
            + "\"Home\": {\"to\": [\"50,50\"], \"status\": \"NOT_READY\", \"reason\": \"No route\"}}}", JsonObject.class));
        DecisionService odd = DecisionService.create(
            RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, root.toString())), "nubles", "nubles");
        carrying(1);

        assertFalse(finder.find(blocked(LUMBRIDGE), odd, availability).isPresent());
    }

    @Test
    public void anotherCharacterOrOlderRulesSuggestNothing() throws Exception
    {
        carrying(LUMBRIDGE_TABLET, AMULET_OF_GLORY_4);

        assertFalse(finder.find(blocked(FALADOR), TravelFixtures.playing("zezima"), availability).isPresent());
        DecisionService legacy = DecisionService.create(
            RulesSnapshot.of(fixture("bundles/v3-standard.json")), "nubles", "nubles");
        assertFalse(finder.find(blocked(FALADOR), legacy, availability).isPresent());
    }

    @Test
    public void unresolvedInputsNeverProduceAGuess()
    {
        carrying(LUMBRIDGE_TABLET);
        TravelAction notTravel = new TravelAction(null, null, "Unknown",
            Collections.emptyList(), false, TravelAction.Confidence.UNKNOWN);
        TravelAction severalPlaces = new TravelAction("item:amulet-of-glory", "Rub", "Amulet of glory(4)",
            Arrays.asList(EDGEVILLE, FALADOR), false, TravelAction.Confidence.EXACT);

        assertFalse(finder.find(null, rules, availability).isPresent());
        assertFalse(finder.find(notTravel, rules, availability).isPresent());
        assertFalse(finder.find(severalPlaces, rules, availability).isPresent());
        assertFalse(finder.find(blocked(FALADOR), null, availability).isPresent());
        assertFalse(finder.find(blocked(FALADOR), rules, null).isPresent());
    }

    @Test
    public void finderExposesDataSelectionOnly()
    {
        for (Method method : TravelAlternativeFinder.class.getDeclaredMethods())
        {
            assertFalse(method.getName().matches(
                "(?i).*(click|move|invoke|interact|menu|activate).*"));
            assertFalse(Client.class.isAssignableFrom(method.getReturnType()));
            for (Class<?> parameterType : method.getParameterTypes())
            {
                assertFalse(Client.class.isAssignableFrom(parameterType));
            }
        }
        assertNull(TravelAlternative.class.getSuperclass().getSuperclass());
    }

    private Optional<TravelAlternative> find(CanonicalChunk destination)
    {
        return finder.find(blocked(destination), rules, availability);
    }

    private String id(CanonicalChunk destination)
    {
        return find(destination).get().getId();
    }

    private void carrying(Integer... items)
    {
        carried.clear();
        carried.addAll(Arrays.asList(items));
    }

    /** A blocked trip to one place. */
    private static TravelAction blocked(CanonicalChunk destination)
    {
        return new TravelAction("spell:standard:falador-teleport", "Cast", "Falador Teleport",
            Collections.singletonList(destination), false, TravelAction.Confidence.EXACT);
    }

    private FateLockedBundle fixture(String name) throws Exception
    {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name))
        {
            return FateLockedBundle.loadFromJson(new Gson(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
