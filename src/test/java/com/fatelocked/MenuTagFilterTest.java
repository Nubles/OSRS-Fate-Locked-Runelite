package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fatelocked.guardian.GuardedActionFactory;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * F1: the filter lets through exactly the menu types the tag could ever tag. For every
 * {@link MenuAction}, an entry as RuneLite makes it, with an NPC where RuneLite gives one and a
 * tile in the scene, is tagged only if its facts are a kind travel is matched on, or it is placed
 * in a chunk.
 */
public class MenuTagFilterTest
{
    /** The types RuneLite's menu entries give an NPC for. */
    private static final Set<MenuAction> WITH_AN_NPC = EnumSet.of(
        MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION,
        MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION, MenuAction.WIDGET_TARGET_ON_NPC,
        MenuAction.EXAMINE_NPC);
    private static final Set<MenuAction> EXAMINE = EnumSet.of(
        MenuAction.EXAMINE_OBJECT, MenuAction.EXAMINE_NPC, MenuAction.EXAMINE_ITEM,
        MenuAction.EXAMINE_ITEM_GROUND);

    private final Client client = mock(Client.class);

    @Test
    public void theFilterLetsThroughExactlyWhatCouldBeTagged()
    {
        MenuFactsReader reader = new MenuFactsReader(client);
        GuardedActionFactory factory = new GuardedActionFactory();
        ChunkLocator locator = new ChunkLocator(client);
        int taggable = 0;
        for (MenuAction type : MenuAction.values())
        {
            MenuEntry entry = asRuneLiteMakesIt(type);
            boolean couldBeTagged = reader.read(entry).getKind() != MenuFacts.Kind.OTHER
                || factory.from(entry, locator).getChunk() != null;
            assertEquals(type.name(), couldBeTagged, MenuTagFilter.mayTag(type));
            if (couldBeTagged) taggable++;
        }
        assertEquals("items and spells, objects, ground items, NPCs, and a use on an NPC", 18, taggable);
    }

    @Test
    public void walkingCancelExamineAndPlayersAreNeverRead()
    {
        for (MenuAction type : EnumSet.of(MenuAction.WALK, MenuAction.CANCEL, MenuAction.EXAMINE_OBJECT,
            MenuAction.EXAMINE_NPC, MenuAction.EXAMINE_ITEM, MenuAction.EXAMINE_ITEM_GROUND,
            MenuAction.PLAYER_FIRST_OPTION, MenuAction.PLAYER_EIGHTH_OPTION, MenuAction.RUNELITE))
        {
            assertFalse(type.name(), MenuTagFilter.mayTag(type));
        }
        assertFalse("an entry with no type", MenuTagFilter.mayTag(null));
    }

    /** An entry of this type as the client makes it: its own words, an NPC where one is given, a tile in view. */
    private MenuEntry asRuneLiteMakesIt(MenuAction type)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(type);
        when(entry.getOption()).thenReturn(EXAMINE.contains(type) ? "Examine"
            : type == MenuAction.WALK ? "Walk here" : type == MenuAction.CANCEL ? "Cancel" : "Use");
        when(entry.getTarget()).thenReturn("Thing");
        when(entry.getWorldViewId()).thenReturn(WorldView.TOPLEVEL);
        when(entry.getParam0()).thenReturn(10);
        when(entry.getParam1()).thenReturn(10);
        when(entry.getItemId()).thenReturn(-1);
        TestWorld.topLevel(client);
        if (WITH_AN_NPC.contains(type))
        {
            NPC npc = mock(NPC.class);
            TestWorld.standAt(client, npc, new WorldPoint(3200, 3200, 0));
            when(entry.getNpc()).thenReturn(npc);
        }
        return entry;
    }
}
