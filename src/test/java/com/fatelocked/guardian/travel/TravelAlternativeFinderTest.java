package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.FateRuleEngine;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RuleDecision;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TravelAlternativeFinderTest
{
    private static final CanonicalChunk VARROCK = new CanonicalChunk(50, 53);
    private static final CanonicalChunk FALADOR = new CanonicalChunk(46, 52);

    private final FateRuleEngine rules = mock(FateRuleEngine.class);
    private final TravelAvailability availability = mock(TravelAvailability.class);

    @Before
    public void allowTabletMobilityByDefault()
    {
        when(rules.mobility("Teleport Tablets"))
            .thenReturn(decision(PermissionStatus.ALLOWED));
    }

    @Test
    public void prefersCarriedAllowedAlternativeInTheIntendedArea()
    {
        when(rules.entry(VARROCK)).thenReturn(allowed("Varrock"));
        when(rules.entry(FALADOR)).thenReturn(allowed("Falador"));
        when(rules.areaLabel(new CanonicalChunk(49, 53))).thenReturn("  VARROCK ");
        when(rules.areaLabel(VARROCK)).thenReturn("varrock");
        when(rules.areaLabel(FALADOR)).thenReturn("Falador");
        when(availability.hasAnyItem(setOf(8007))).thenReturn(true);
        when(availability.hasAnyItem(setOf(8009))).thenReturn(true);

        Optional<TravelAlternative> result = new TravelAlternativeFinder(
            Arrays.asList(
                tablet("falador", "Falador", FALADOR, 8009),
                tablet("varrock", "Varrock", VARROCK, 8007)))
            .find(exactAction(new CanonicalChunk(49, 53)), rules, availability);

        assertTrue(result.isPresent());
        assertEquals("Varrock", result.get().getLabel());
        assertEquals(VARROCK, result.get().getDestination());
    }

    @Test
    public void rejectsLockedUnknownNotReadyAndUnavailableAlternatives()
    {
        CanonicalChunk locked = new CanonicalChunk(50, 53);
        CanonicalChunk unknown = new CanonicalChunk(50, 52);
        CanonicalChunk notReady = new CanonicalChunk(50, 51);
        CanonicalChunk absent = new CanonicalChunk(50, 50);
        when(rules.entry(locked)).thenReturn(decision(PermissionStatus.LOCKED));
        when(rules.entry(unknown)).thenReturn(decision(PermissionStatus.UNKNOWN));
        when(rules.entry(notReady)).thenReturn(decision(PermissionStatus.NOT_READY));
        when(rules.entry(absent)).thenReturn(allowed("Lumbridge"));
        when(availability.hasAnyItem(any())).thenReturn(false);

        List<TravelAlternative> catalog = Arrays.asList(
            tablet("locked", "Locked", locked, 8007),
            tablet("unknown", "Unknown", unknown, 8008),
            tablet("not-ready", "Not ready", notReady, 8009),
            tablet("absent", "Absent", absent, 8010));

        assertFalse(new TravelAlternativeFinder(catalog)
            .find(exactAction(new CanonicalChunk(51, 53)), rules, availability)
            .isPresent());
    }

    @Test
    public void tabletMobilityMustBeExplicitlyAllowed()
    {
        TravelAlternative candidate =
            TravelAlternativeCatalog.alternatives().get(0);
        for (TravelAlternative tablet :
            TravelAlternativeCatalog.alternatives())
        {
            assertEquals("Teleport Tablets", tablet.getRequiredUnlock());
        }
        when(rules.entry(candidate.getDestination()))
            .thenReturn(allowed("Varrock"));
        when(availability.hasAnyItem(candidate.getRequiredItemIds()))
            .thenReturn(true);

        when(rules.mobility("Teleport Tablets")).thenReturn(null);
        assertFalse(new TravelAlternativeFinder(
            Collections.singletonList(candidate))
            .find(exactAction(VARROCK), rules, availability).isPresent());

        for (PermissionStatus status : Arrays.asList(
            PermissionStatus.UNKNOWN,
            PermissionStatus.NOT_READY,
            PermissionStatus.LOCKED))
        {
            when(rules.mobility("Teleport Tablets"))
                .thenReturn(decision(status));
            assertFalse(new TravelAlternativeFinder(
                Collections.singletonList(candidate))
                .find(exactAction(VARROCK), rules, availability).isPresent());
        }

        when(rules.mobility("Teleport Tablets"))
            .thenReturn(decision(PermissionStatus.ALLOWED));
        assertEquals(candidate, new TravelAlternativeFinder(
            Collections.singletonList(candidate))
            .find(exactAction(VARROCK), rules, availability).get());
    }

    @Test
    public void requiresEveryDeclaredLocalRequirement()
    {
        TravelAlternative alternative = new TravelAlternative(
            "verified", "Verified", VARROCK, "Teleport Tablets", setOf(8007),
            Skill.MAGIC, 45, 0);
        TravelAlternativeFinder finder = new TravelAlternativeFinder(
            Collections.singletonList(alternative));
        when(rules.entry(VARROCK)).thenReturn(allowed("Varrock"));
        when(availability.hasAnyItem(setOf(8007))).thenReturn(true);

        when(availability.realLevel(Skill.MAGIC)).thenReturn(44);
        when(availability.spellbook()).thenReturn(0);
        assertFalse(finder.find(exactAction(VARROCK), rules, availability).isPresent());

        when(availability.realLevel(Skill.MAGIC)).thenReturn(45);
        when(availability.spellbook()).thenReturn(1);
        assertFalse(finder.find(exactAction(VARROCK), rules, availability).isPresent());

        when(availability.spellbook()).thenReturn(0);
        assertEquals(alternative,
            finder.find(exactAction(VARROCK), rules, availability).get());
    }

    @Test
    public void adjacentRankOutranksOtherBeforeDistance()
    {
        CanonicalChunk destination = new CanonicalChunk(50, 50);
        CanonicalChunk adjacent = new CanonicalChunk(50, 51);
        TravelAlternative zeroDistanceOther = tablet(
            "a-zero-distance", "Zero distance other", destination, 8008);
        TravelAlternative adjacentCandidate = tablet(
            "z-adjacent", "Adjacent", adjacent, 8007);
        when(rules.entry(destination)).thenReturn(allowed("Destination"));
        when(rules.entry(adjacent)).thenReturn(allowed("Adjacent"));
        when(availability.hasAnyItem(any())).thenReturn(true);

        Optional<TravelAlternative> result = new TravelAlternativeFinder(
            Arrays.asList(zeroDistanceOther, adjacentCandidate))
            .find(exactAction(destination), rules, availability);

        assertEquals(adjacentCandidate, result.get());
    }

    @Test
    public void normalizedStableIdBreaksEqualRankAndDistanceTies()
    {
        CanonicalChunk destination = new CanonicalChunk(50, 50);
        CanonicalChunk east = new CanonicalChunk(51, 50);
        CanonicalChunk north = new CanonicalChunk(50, 51);
        TravelAlternative catalogFirst = tablet(
            " Z-last-ID ", "Catalog first", east, 8008);
        TravelAlternative lexicographicFirst = tablet(
            "a-first-id", "Lexicographic first", north, 8007);
        when(rules.entry(east)).thenReturn(allowed("East"));
        when(rules.entry(north)).thenReturn(allowed("North"));
        when(availability.hasAnyItem(any())).thenReturn(true);

        Optional<TravelAlternative> result = new TravelAlternativeFinder(
            Arrays.asList(catalogFirst, lexicographicFirst))
            .find(exactAction(destination), rules, availability);

        assertEquals(lexicographicFirst, result.get());
    }

    @Test
    public void catalogAndAlternativesAreImmutableCheckedData()
    {
        List<TravelAlternative> alternatives = TravelAlternativeCatalog.alternatives();
        assertEquals(6, alternatives.size());
        assertEquals("varrock-tablet", alternatives.get(0).getId());
        assertEquals("watchtower-tablet", alternatives.get(5).getId());
        assertThrows(UnsupportedOperationException.class,
            () -> alternatives.add(alternatives.get(0)));

        Set<Integer> mutableIds = new LinkedHashSet<>(setOf(8007));
        TravelAlternative copied = new TravelAlternative(
            "copy", "Copy", VARROCK, "Teleport Tablets", mutableIds,
            null, 0, null);
        mutableIds.clear();
        assertEquals(setOf(8007), copied.getRequiredItemIds());
        assertThrows(UnsupportedOperationException.class,
            () -> copied.getRequiredItemIds().add(8008));
    }

    @Test
    public void unresolvedInputsAndEmptyItemRequirementsNeverProduceAGuess()
    {
        when(rules.entry(VARROCK)).thenReturn(allowed("Varrock"));
        when(availability.hasAnyItem(any())).thenReturn(true);
        TravelAlternativeFinder finder = new TravelAlternativeFinder(
            Collections.singletonList(new TravelAlternative(
                "none", "None", VARROCK, "Teleport Tablets",
                Collections.emptySet(), null, 0, null)));

        assertFalse(finder.find(null, rules, availability).isPresent());
        assertFalse(finder.find(unknownAction(), rules, availability).isPresent());
        assertFalse(finder.find(exactAction(VARROCK), null, availability).isPresent());
        assertFalse(finder.find(exactAction(VARROCK), rules, null).isPresent());
        assertFalse(finder.find(exactAction(VARROCK), rules, availability).isPresent());
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

    private static TravelAlternative tablet(
        String id, String label, CanonicalChunk destination, int itemId)
    {
        return new TravelAlternative(
            id, label, destination, "Teleport Tablets", setOf(itemId),
            null, 0, null);
    }

    private static TravelAction exactAction(CanonicalChunk destination)
    {
        return new TravelAction(
            TravelAction.Family.WALK, "walk", "Walk here",
            new CanonicalChunk(49, 50), destination, null,
            TravelAction.Confidence.EXACT);
    }

    private static TravelAction unknownAction()
    {
        return new TravelAction(
            TravelAction.Family.UNKNOWN, "unknown", "Unknown",
            new CanonicalChunk(49, 50), null, null,
            TravelAction.Confidence.UNKNOWN);
    }

    private static Set<Integer> setOf(Integer... values)
    {
        return Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(values)));
    }

    private static RuleDecision allowed(String label)
    {
        return new RuleDecision(PermissionStatus.ALLOWED, label, null);
    }

    private static RuleDecision decision(PermissionStatus status)
    {
        return new RuleDecision(status, status.name(), null);
    }
}
