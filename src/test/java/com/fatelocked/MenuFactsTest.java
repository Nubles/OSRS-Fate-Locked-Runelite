package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A9: what Strict Mode's travel matching will know about a click. The ids
 * are the ones RuneLite 1.12.39 gives: the spellbook is interface 218, the
 * inventory 149 and the worn items 387, whose slots draw their item in the
 * second child (RuneLite's MenuEntrySwapperPlugin reads worn items that way).
 */
public class MenuFactsTest
{
    private static final int VARROCK_TELEPORT_TABLET = 8007;
    private static final int AMULET_OF_GLORY_4 = 1712;
    private static final int FAIRY_RING = 29495;
    private static final int ARCEUUS = 3;

    private final Client client = mock(Client.class);
    private final MenuFactsReader reader = new MenuFactsReader(client);

    @Test
    public void textLosesColourTagsAndTheLockTagButKeepsItsCase()
    {
        assertEquals("Cast", MenuFactsReader.text("<col=ff9040>Cast</col>"));
        assertEquals("Varrock Teleport", MenuFactsReader.text(
            "<col=00ff00>Varrock Teleport</col> <col=f87171>(Locked)</col>"));
        assertEquals("Amulet of glory(4)", MenuFactsReader.text("<col=ff9040>Amulet of glory(4)</col>"));
        assertEquals("Last-destination (CKS)", MenuFactsReader.text("Last-destination (CKS)"));
        assertEquals("Necklace of passage(5)", MenuFactsReader.text(" Necklace of  passage(5) "));
        assertEquals("", MenuFactsReader.text(null));
    }

    @Test
    public void aSpellCarriesTheActiveSpellbook()
    {
        when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(ARCEUUS);
        MenuEntry cast = widgetOption("Cast", "<col=00ff00>Ape Atoll Teleport</col>",
            InterfaceID.MAGIC_SPELLBOOK, -1);

        MenuFacts facts = reader.read(cast);

        assertEquals(MenuFacts.Kind.WIDGET, facts.getKind());
        assertEquals("Cast", facts.getOption());
        assertEquals("Ape Atoll Teleport", facts.getTarget());
        assertEquals(InterfaceID.MAGIC_SPELLBOOK, facts.getInterfaceGroup());
        assertEquals(ARCEUUS, facts.getSpellbook());
        assertEquals(MenuFacts.NONE, facts.getItemId());
        assertEquals(MenuFacts.NONE, facts.getNpcId());
        assertEquals(MenuFacts.NONE, facts.getObjectId());
    }

    @Test
    public void anInventoryItemCarriesItsIdAndNoSpellbook()
    {
        MenuEntry tablet = widgetOption("Break", "Varrock teleport", InterfaceID.INVENTORY,
            VARROCK_TELEPORT_TABLET);

        MenuFacts facts = reader.read(tablet);

        assertEquals(VARROCK_TELEPORT_TABLET, facts.getItemId());
        assertEquals(InterfaceID.INVENTORY, facts.getInterfaceGroup());
        assertEquals(MenuFacts.NONE, facts.getSpellbook());
        verify(client, never()).getVarbitValue(anyInt());
        verify(tablet, never()).getWidget();
    }

    @Test
    public void aWornItemFallsBackToTheItemInItsSlot()
    {
        MenuEntry rub = widgetOption("Edgeville", "Amulet of glory(4)", InterfaceID.WORNITEMS, -1);
        wornSlot(rub, AMULET_OF_GLORY_4);
        assertEquals(AMULET_OF_GLORY_4, reader.read(rub).getItemId());

        MenuEntry named = widgetOption("Edgeville", "Amulet of glory(4)", InterfaceID.WORNITEMS,
            AMULET_OF_GLORY_4);
        assertEquals(AMULET_OF_GLORY_4, reader.read(named).getItemId());
        verify(named, never()).getWidget();

        MenuEntry empty = widgetOption("Remove", "", InterfaceID.WORNITEMS, -1);
        wornSlot(empty, -1);
        assertEquals(MenuFacts.NONE, reader.read(empty).getItemId());
        MenuEntry noSlot = widgetOption("Remove", "", InterfaceID.WORNITEMS, -1);
        assertEquals(MenuFacts.NONE, reader.read(noSlot).getItemId());
        MenuEntry noChild = widgetOption("Remove", "", InterfaceID.WORNITEMS, -1);
        when(noChild.getWidget()).thenReturn(mock(Widget.class));
        assertEquals(MenuFacts.NONE, reader.read(noChild).getItemId());
    }

    @Test
    public void anItemElsewhereHasNoFallback()
    {
        MenuEntry elsewhere = widgetOption("Withdraw-1", "Varrock teleport", InterfaceID.BANKSIDE, -1);

        assertEquals(MenuFacts.NONE, reader.read(elsewhere).getItemId());
        verify(elsewhere, never()).getWidget();
    }

    @Test
    public void npcAndObjectOptionsCarryTheirIdsAndWorldView()
    {
        NPC captain = mock(NPC.class);
        when(captain.getId()).thenReturn(3648);
        MenuEntry travel = entry(MenuAction.NPC_THIRD_OPTION, "Travel", "Captain Tobias");
        when(travel.getNpc()).thenReturn(captain);
        MenuFacts boat = reader.read(travel);
        assertEquals(MenuFacts.Kind.NPC, boat.getKind());
        assertEquals(3648, boat.getNpcId());
        assertEquals(MenuFacts.NONE, boat.getObjectId());

        MenuEntry gone = entry(MenuAction.NPC_FIRST_OPTION, "Talk-to", "Captain Tobias");
        assertEquals(MenuFacts.NONE, reader.read(gone).getNpcId());

        MenuEntry ring = entry(MenuAction.GAME_OBJECT_SECOND_OPTION, "Zanaris", "Fairy ring");
        when(ring.getIdentifier()).thenReturn(FAIRY_RING);
        when(ring.getWorldViewId()).thenReturn(5);
        MenuFacts fairyRing = reader.read(ring);
        assertEquals(MenuFacts.Kind.OBJECT, fairyRing.getKind());
        assertEquals(FAIRY_RING, fairyRing.getObjectId());
        assertEquals(5, fairyRing.getWorldViewId());
        assertEquals(MenuFacts.NONE, fairyRing.getInterfaceGroup());
    }

    @Test
    public void everythingElseIsOtherWithNoIds()
    {
        for (MenuAction type : new MenuAction[] {
            MenuAction.WALK, MenuAction.EXAMINE_OBJECT, MenuAction.GROUND_ITEM_THIRD_OPTION,
            MenuAction.PLAYER_FIRST_OPTION, MenuAction.WORLD_ENTITY_FIRST_OPTION,
            MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, MenuAction.WIDGET_CONTINUE, MenuAction.RUNELITE, null })
        {
            MenuEntry entry = entry(type, "Walk here", "");
            when(entry.getIdentifier()).thenReturn(FAIRY_RING);
            when(entry.getItemId()).thenReturn(VARROCK_TELEPORT_TABLET);
            when(entry.getParam1()).thenReturn(WidgetUtil.packComponentId(InterfaceID.MAGIC_SPELLBOOK, 1));

            MenuFacts facts = reader.read(entry);

            assertEquals(String.valueOf(type), MenuFacts.Kind.OTHER, facts.getKind());
            assertEquals(String.valueOf(type), MenuFacts.builder()
                .option("Walk here").worldViewId(facts.getWorldViewId()).build(), facts);
        }
        assertSame(MenuFacts.EMPTY, reader.read(null));
        assertEquals(MenuFacts.Kind.OTHER, MenuFacts.EMPTY.getKind());
        verify(client, never()).getVarbitValue(anyInt());
    }

    private static MenuEntry widgetOption(String option, String target, int group, int itemId)
    {
        MenuEntry entry = entry(MenuAction.CC_OP, option, target);
        when(entry.getParam1()).thenReturn(WidgetUtil.packComponentId(group, 3));
        when(entry.getItemId()).thenReturn(itemId);
        return entry;
    }

    private static void wornSlot(MenuEntry entry, int itemId)
    {
        Widget slot = mock(Widget.class);
        Widget item = mock(Widget.class);
        when(item.getItemId()).thenReturn(itemId);
        when(slot.getChild(1)).thenReturn(item);
        when(entry.getWidget()).thenReturn(slot);
    }

    private static MenuEntry entry(MenuAction type, String option, String target)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(type);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        return entry;
    }
}
