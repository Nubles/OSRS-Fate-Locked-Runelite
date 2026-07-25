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
