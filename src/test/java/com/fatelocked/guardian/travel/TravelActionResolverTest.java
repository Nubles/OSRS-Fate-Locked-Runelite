package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class TravelActionResolverTest
{
    private final TravelActionResolver resolver = new TravelActionResolver();
    private final Client client = mock(Client.class);
    private final CanonicalChunk origin = new CanonicalChunk(50, 50);

    @Test
    public void resolvesNamedTeleportWalkAndCrossChunkObject()
    {
        assertTravel(entry("Teleport", "Falador", MenuAction.UNKNOWN),
            TravelAction.Family.SPELL_OR_ITEM, new CanonicalChunk(46, 52), true);

        MenuEntry walk = entry("Walk here", "", MenuAction.WALK);
        when(walk.getParam0()).thenReturn(10);
        when(walk.getParam1()).thenReturn(20);
        when(client.getPlane()).thenReturn(0);
        try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
        {
            points.when(() -> WorldPoint.fromScene(client, 10, 20, 0))
                .thenReturn(new WorldPoint(3264, 3264, 0));
            assertTravel(walk, TravelAction.Family.WALK,
                new CanonicalChunk(51, 51), true);
        }

        MenuEntry door = entry("Open", "Gate", MenuAction.GAME_OBJECT_FIRST_OPTION);
        when(door.getParam0()).thenReturn(10);
        when(door.getParam1()).thenReturn(20);
        try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
        {
            points.when(() -> WorldPoint.fromScene(client, 10, 20, 0))
                .thenReturn(new WorldPoint(3264, 3264, 0));
            assertTravel(door, TravelAction.Family.BOUNDARY_OBJECT,
                new CanonicalChunk(51, 51), true);
        }
    }

    @Test
    public void walkAndBoundaryMethodsWithoutResolvedCrossChunkTargetsStayUnknown()
    {
        assertUnknown(entry("Walk here", "", MenuAction.UNKNOWN));
        TravelAction unresolvedWalk = resolver.resolve(
            entry("Walk here", "", MenuAction.WALK), null, origin);
        assertEquals(TravelAction.Confidence.UNKNOWN, unresolvedWalk.getConfidence());
        assertNull(unresolvedWalk.getDestination());

        assertUnknown(entry("Open", "Gate", MenuAction.UNKNOWN));
        TravelAction unresolvedBoundary = resolver.resolve(
            entry("Open", "Gate", MenuAction.GAME_OBJECT_FIRST_OPTION), null, origin);
        assertEquals(TravelAction.Confidence.UNKNOWN, unresolvedBoundary.getConfidence());
        assertNull(unresolvedBoundary.getDestination());
    }
    @Test
    public void sameChunkAndOriginUnknownWalksStayUnresolved()
    {
        MenuEntry sameChunk = entry("Walk here", "", MenuAction.WALK);
        when(sameChunk.getParam0()).thenReturn(10);
        when(sameChunk.getParam1()).thenReturn(20);
        when(client.getPlane()).thenReturn(0);
        try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
        {
            points.when(() -> WorldPoint.fromScene(client, 10, 20, 0))
                .thenReturn(new WorldPoint(3201, 3201, 0));
            assertUnknown(sameChunk);
        }

        MenuEntry unknownOrigin = entry("Walk here", "", MenuAction.WALK);
        when(unknownOrigin.getParam0()).thenReturn(11);
        when(unknownOrigin.getParam1()).thenReturn(21);
        try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
        {
            points.when(() -> WorldPoint.fromScene(client, 11, 21, 0))
                .thenReturn(new WorldPoint(3264, 3264, 0));
            TravelAction unresolved = resolver.resolve(
                unknownOrigin, client, null);
            assertEquals(TravelAction.Confidence.UNKNOWN,
                unresolved.getConfidence());
            assertNull(unresolved.getDestination());
        }
    }

    @Test
    public void resolvesKnownTransportKeywordsWithTheirUnlocks()
    {
        assertTransport("fairy ring", TravelAction.Family.FAIRY_RING,
            "Fairy Rings", "fairy-rings");
        assertTransport("spirit tree", TravelAction.Family.SPIRIT_TREE,
            "Spirit Trees", "spirit-trees");
        assertTransport("gnome glider", TravelAction.Family.GNOME_GLIDER,
            "Gnome Gliders", "gnome-gliders");
        assertTransport("charter", TravelAction.Family.CHARTER_SHIP,
            "Charter Ships", "charter-ships");
        assertTransport("mine cart", TravelAction.Family.MINE_CART,
            "Mine Carts", "mine-carts");
        assertTransport("magic carpet", TravelAction.Family.MAGIC_CARPET,
            "Magic Carpets", "magic-carpets");
        assertTransport("balloon", TravelAction.Family.BALLOON,
            "Balloon Transport", "balloon-transport");
        assertTransport("eagle", TravelAction.Family.EAGLE,
            "Eagle Transport", "eagle-transport");
        assertTransport("minigame teleport", TravelAction.Family.MINIGAME_TELEPORT,
            "Minigame Teleports", "minigame-teleports");
        assertTransport("quetzal", TravelAction.Family.QUETZAL,
            "Quetzal Network", "quetzal-network");
    }

    @Test
    public void resolvesOnlyCheckedDestinationsForEveryNamedTransportFamily()
    {
        assertNamedTransport("Teleport", "Lunar Isle",
            TravelAction.Family.SPELL_OR_ITEM, new CanonicalChunk(33, 61),
            "Teleport", "Teleport configuration",
            "Teleport", "New destination");
        assertNamedTransport("Travel", "Fairy ring — Zanaris",
            TravelAction.Family.FAIRY_RING, new CanonicalChunk(37, 69),
            "Check", "Fairy ring health",
            "Travel", "Fairy ring — New destination");
        assertNamedTransport("Travel", "Spirit tree — Prifddinas",
            TravelAction.Family.SPIRIT_TREE, new CanonicalChunk(51, 95),
            "Check", "Spirit tree health",
            "Travel", "Spirit tree — New destination");
        assertNamedTransport("Travel", "Gnome glider — Lemanto Andra",
            TravelAction.Family.GNOME_GLIDER, new CanonicalChunk(51, 53),
            "Check", "Gnome glider condition",
            "Travel", "Gnome glider — New destination");
        assertNamedTransport("Charter", "Port Khazard",
            TravelAction.Family.CHARTER_SHIP, new CanonicalChunk(41, 49),
            "Examine", "Charter schedule",
            "Charter", "Unknown port");
        assertNamedTransport("Travel", "Mine cart — Lovakengj",
            TravelAction.Family.MINE_CART, new CanonicalChunk(23, 58),
            "Check", "Mine cart wheels",
            "Travel", "Mine cart — New destination");
        assertNamedTransport("Pay-fare", "Magic carpet to Nardah",
            TravelAction.Family.MAGIC_CARPET, new CanonicalChunk(53, 47),
            "Talk-to", "Rug merchant",
            "Pay-fare", "Magic carpet to Unknown");
        assertNamedTransport("Travel", "Balloon — Taverley",
            TravelAction.Family.BALLOON, new CanonicalChunk(45, 54),
            "Check", "Balloon fabric",
            "Travel", "Balloon — New destination");
        assertNamedTransport("Travel", "Eagle — Eagles' Peak",
            TravelAction.Family.EAGLE, new CanonicalChunk(36, 54),
            "Check", "Eagle nest",
            "Travel", "Eagle — New destination");
        assertNamedTransport("Minigame teleport", "Nightmare Zone",
            TravelAction.Family.MINIGAME_TELEPORT, new CanonicalChunk(41, 54),
            "Check", "Minigame teleport settings",
            "Minigame teleport", "Unknown minigame");
        assertNamedTransport("Travel", "Quetzal — Hunter Guild",
            TravelAction.Family.QUETZAL, new CanonicalChunk(24, 47),
            "Check", "Quetzal feathers",
            "Travel", "Quetzal — New destination");
    }

    @Test
    public void similarlyWordedAndUnresolvedTransportActionsStayUnknown()
    {
        assertUnknown(entry("Teleport", "Teleport configuration", MenuAction.UNKNOWN));
        assertUnknown(entry("Teleport", "New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Check", "Fairy ring health", MenuAction.UNKNOWN));
        assertUnknown(entry("Travel", "Fairy ring — New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Check", "Spirit tree health", MenuAction.UNKNOWN));
        assertUnknown(entry("Travel", "Spirit tree — New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Check", "Gnome glider condition", MenuAction.UNKNOWN));
        assertUnknown(entry("Travel", "Gnome glider — New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Examine", "Charter schedule", MenuAction.UNKNOWN));
        assertUnknown(entry("Charter", "Unknown port", MenuAction.UNKNOWN));
        assertUnknown(entry("Check", "Mine cart wheels", MenuAction.UNKNOWN));
        assertUnknown(entry("Travel", "Mine cart — New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Talk-to", "Rug merchant", MenuAction.UNKNOWN));
        assertUnknown(entry("Pay-fare", "Magic carpet to Unknown", MenuAction.UNKNOWN));
        assertUnknown(entry("Check", "Balloon fabric", MenuAction.UNKNOWN));
        assertUnknown(entry("Travel", "Balloon — New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Check", "Eagle nest", MenuAction.UNKNOWN));
        assertUnknown(entry("Travel", "Eagle — New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Check", "Minigame teleport settings", MenuAction.UNKNOWN));
        assertUnknown(entry("Minigame teleport", "Unknown minigame", MenuAction.UNKNOWN));
        assertUnknown(entry("Check", "Quetzal feathers", MenuAction.UNKNOWN));
        assertUnknown(entry("Travel", "Quetzal — New destination", MenuAction.UNKNOWN));
    }
    @Test
    public void genericTravelWordsWithoutAResolvedDestinationStayUnknown()
    {
        assertUnknown(entry("Travel", "New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Enter", "New destination", MenuAction.UNKNOWN));
        assertUnknown(entry("Teleport", "New destination", MenuAction.UNKNOWN));
    }
    @Test
    public void sameChunkObjectAndUnknownWidgetStayUnresolved()
    {
        MenuEntry object = entry("Open", "Gate", MenuAction.GAME_OBJECT_FIRST_OPTION);
        when(object.getParam0()).thenReturn(10);
        when(object.getParam1()).thenReturn(20);
        when(client.getPlane()).thenReturn(0);
        try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
        {
            points.when(() -> WorldPoint.fromScene(client, 10, 20, 0))
                .thenReturn(new WorldPoint(3200, 3200, 0));
            assertUnknown(object);
        }

        assertUnknown(entry("Continue", "", MenuAction.UNKNOWN));
    }

    private void assertTransport(String keyword, TravelAction.Family family,
        String requiredUnlock, String methodId)
    {
        TravelAction action = resolver.resolve(
            entry("Travel via " + keyword, "Falador", MenuAction.UNKNOWN), client, origin);

        assertTravel(action, family, new CanonicalChunk(46, 52), true);
        assertEquals(requiredUnlock, action.getRequiredUnlock());
        assertEquals(methodId, action.getMethodId());
    }

    private void assertNamedTransport(
        String option, String target, TravelAction.Family family,
        CanonicalChunk destination,
        String unrelatedOption, String unrelatedTarget,
        String unresolvedOption, String unresolvedTarget)
    {
        assertTravel(entry(option, target, MenuAction.UNKNOWN), family, destination, true);
        assertUnknown(entry(unrelatedOption, unrelatedTarget, MenuAction.UNKNOWN));
        assertUnknown(entry(unresolvedOption, unresolvedTarget, MenuAction.UNKNOWN));
    }
    private void assertTravel(MenuEntry entry, TravelAction.Family family,
        CanonicalChunk destination, boolean exact)
    {
        assertTravel(resolver.resolve(entry, client, origin), family, destination, exact);
    }

    private static void assertTravel(TravelAction action, TravelAction.Family family,
        CanonicalChunk destination, boolean exact)
    {
        assertEquals(family, action.getFamily());
        assertEquals(destination, action.getDestination());
        assertEquals(exact ? TravelAction.Confidence.EXACT : TravelAction.Confidence.UNKNOWN,
            action.getConfidence());
    }

    private void assertUnknown(MenuEntry entry)
    {
        TravelAction unknown = resolver.resolve(entry, client, origin);
        assertEquals(TravelAction.Confidence.UNKNOWN, unknown.getConfidence());
        assertNull(unknown.getDestination());
    }

    private static MenuEntry entry(String option, String target, MenuAction action)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        when(entry.getType()).thenReturn(action);
        return entry;
    }
}
