package com.fatelocked.badges;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Player;
import net.runelite.api.WorldView;

/**
 * The badge holders in the scene, found once a game tick, so the overlay's frames only draw:
 * each player whose name is on the list and who shows (on the floor shown, not hidden by the
 * game). Names are looked up once while the list stays the same. Client thread.
 */
final class BadgeHolders
{
    /** Names looked up already, raw as the game gives them, while the list is the same. */
    private static final int MAX_REMEMBERED = 2048;

    /** Whether a player shows: on the floor shown, and not hidden by the game. */
    interface Visibility
    {
        boolean shown(Player player);
    }

    private Player[] players = new Player[8];
    private int[] tiers = new int[8];
    private int count;
    private final Map<String, Integer> remembered = new HashMap<>();
    private BadgeList rememberedFor = BadgeList.NONE;

    int count()
    {
        return count;
    }

    Player player(int index)
    {
        return players[index];
    }

    int tier(int index)
    {
        return tiers[index];
    }

    void clear()
    {
        Arrays.fill(players, 0, count, null);
        count = 0;
    }

    /** Find them again in the top-level view and the views inside it (boats). */
    void scan(WorldView top, BadgeList list, Visibility visibility)
    {
        clear();
        if (top == null || list == null || list.isEmpty())
        {
            return;
        }
        if (list != rememberedFor || remembered.size() > MAX_REMEMBERED)
        {
            remembered.clear();
            rememberedFor = list;
        }
        scanView(top, list, visibility);
        for (WorldView inner : top.worldViews())
        {
            scanView(inner, list, visibility);
        }
    }

    private void scanView(WorldView view, BadgeList list, Visibility visibility)
    {
        if (view == null)
        {
            return;
        }
        for (Player player : view.players())
        {
            if (player == null)
            {
                continue;
            }
            int tier = tierOf(player.getName(), list);
            if (tier == BadgeTier.NONE)
            {
                continue;
            }
            if (!visibility.shown(player))
            {
                continue;
            }
            add(player, tier);
        }
    }

    private int tierOf(String name, BadgeList list)
    {
        if (name == null)
        {
            return BadgeTier.NONE;
        }
        Integer known = remembered.get(name);
        if (known == null)
        {
            known = list.tierOf(name);
            remembered.put(name, known);
        }
        return known;
    }

    private void add(Player player, int tier)
    {
        if (count == players.length)
        {
            players = Arrays.copyOf(players, count * 2);
            tiers = Arrays.copyOf(tiers, count * 2);
        }
        players[count] = player;
        tiers[count] = tier;
        count++;
    }
}
