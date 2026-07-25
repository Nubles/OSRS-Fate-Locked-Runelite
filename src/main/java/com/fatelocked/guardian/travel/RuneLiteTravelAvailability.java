package com.fatelocked.guardian.travel;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;

import java.util.Set;

public class RuneLiteTravelAvailability implements TravelAvailability
{
    private final Client client;

    public RuneLiteTravelAvailability(Client client)
    {
        this.client = client;
    }

    @Override
    public boolean hasAnyItem(Set<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return false;
        }
        return containsAny(
            client.getItemContainer(InventoryID.INVENTORY), itemIds)
            || containsAny(
                client.getItemContainer(InventoryID.EQUIPMENT), itemIds);
    }

    @Override
    public int realLevel(Skill skill)
    {
        return client.getRealSkillLevel(skill);
    }

    @Override
    public int spellbook()
    {
        return client.getVarbitValue(Varbits.SPELLBOOK);
    }

    private static boolean containsAny(
        ItemContainer container, Set<Integer> itemIds)
    {
        if (container == null || container.getItems() == null)
        {
            return false;
        }
        for (Item item : container.getItems())
        {
            if (item != null && item.getId() > 0
                && itemIds.contains(item.getId()))
            {
                return true;
            }
        }
        return false;
    }
}
