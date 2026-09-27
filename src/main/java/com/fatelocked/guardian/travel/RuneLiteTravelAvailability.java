package com.fatelocked.guardian.travel;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

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
            client.getItemContainer(InventoryID.INV), itemIds)
            || containsAny(
                client.getItemContainer(InventoryID.WORN), itemIds);
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
