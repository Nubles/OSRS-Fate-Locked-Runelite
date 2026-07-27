package com.fatelocked.guardian.travel;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuneLiteTravelAvailabilityTest
{
    private final Client client = mock(Client.class);
    private final ItemContainer inventory = mock(ItemContainer.class);
    private final ItemContainer equipment = mock(ItemContainer.class);
    private final RuneLiteTravelAvailability availability =
        new RuneLiteTravelAvailability(client);

    @Test
    public void readsOnlyCarriedAndEquippedItems()
    {
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        when(inventory.getItems()).thenReturn(new Item[]{new Item(8007, 1)});
        when(equipment.getItems()).thenReturn(new Item[]{new Item(8009, 1)});

        assertTrue(availability.hasAnyItem(setOf(8007)));
        assertTrue(availability.hasAnyItem(setOf(8009)));
        assertFalse(availability.hasAnyItem(setOf(8010)));
        verify(client, never()).getItemContainer(InventoryID.BANK);
    }

    @Test
    public void missingOrUnreadableContainersFailClosed()
    {
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(null);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        when(equipment.getItems()).thenReturn(null);

        assertFalse(availability.hasAnyItem(setOf(8007)));
        assertFalse(availability.hasAnyItem(new LinkedHashSet<>()));
        assertFalse(availability.hasAnyItem(null));
    }

    @Test
    public void readsRealLevelsAndCurrentSpellbookDirectly()
    {
        when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(67);
        when(client.getVarbitValue(Varbits.SPELLBOOK)).thenReturn(2);

        assertEquals(67, availability.realLevel(Skill.MAGIC));
        assertEquals(2, availability.spellbook());
    }

    private static Set<Integer> setOf(Integer... values)
    {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
