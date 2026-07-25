package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.Teleports;
import java.util.Locale;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;

public final class TravelActionResolver
{
    public TravelAction resolve(MenuEntry entry, Client client, CanonicalChunk origin)
    {
        if (entry == null) return unknown("", "", origin);

        String option = clean(entry.getOption());
        String target = clean(entry.getTarget());
        MenuAction action = entry.getType();
        if (action == MenuAction.WALK)
        {
            CanonicalChunk destination = tileChunk(entry, client);
            return origin == null || destination == null
                || origin.equals(destination)
                ? unknown(option, target, origin)
                : exact(TravelAction.Family.WALK, "walk", option, target,
                    origin, destination, null);
        }

        CanonicalChunk destination = Teleports.checkedTravelDestinationChunk(
            option, target, true);
        if (destination != null)
        {
            Transport transport = transport(option + " " + target);
            return exact(transport.family, transport.methodId, option, target,
                origin, destination, transport.requiredUnlock);
        }

        if (isBoundaryObject(action) && isBoundaryOption(option))
        {
            CanonicalChunk tile = tileChunk(entry, client);
            if (tile != null && origin != null && !origin.equals(tile))
            {
                return exact(TravelAction.Family.BOUNDARY_OBJECT,
                    "boundary-object", option, target, origin, tile, null);
            }
        }
        return unknown(option, target, origin);
    }

    private static Transport transport(String text)
    {
        if (text.contains("fairy ring"))
            return new Transport(TravelAction.Family.FAIRY_RING, "fairy-rings", "Fairy Rings");
        if (text.contains("spirit tree"))
            return new Transport(TravelAction.Family.SPIRIT_TREE, "spirit-trees", "Spirit Trees");
        if (text.contains("gnome glider"))
            return new Transport(TravelAction.Family.GNOME_GLIDER, "gnome-gliders", "Gnome Gliders");
        if (text.contains("charter"))
            return new Transport(TravelAction.Family.CHARTER_SHIP, "charter-ships", "Charter Ships");
        if (text.contains("mine cart"))
            return new Transport(TravelAction.Family.MINE_CART, "mine-carts", "Mine Carts");
        if (text.contains("magic carpet"))
            return new Transport(TravelAction.Family.MAGIC_CARPET, "magic-carpets", "Magic Carpets");
        if (text.contains("balloon"))
            return new Transport(TravelAction.Family.BALLOON, "balloon-transport", "Balloon Transport");
        if (text.contains("eagle"))
            return new Transport(TravelAction.Family.EAGLE, "eagle-transport", "Eagle Transport");
        if (text.contains("minigame teleport"))
            return new Transport(TravelAction.Family.MINIGAME_TELEPORT,
                "minigame-teleports", "Minigame Teleports");
        if (text.contains("quetzal"))
            return new Transport(TravelAction.Family.QUETZAL, "quetzal-network", "Quetzal Network");
        if (text.contains("tablet"))
            return new Transport(TravelAction.Family.SPELL_OR_ITEM,
                "teleport-tablets", "Teleport Tablets");
        if (isJewelleryTeleport(text))
            return new Transport(TravelAction.Family.SPELL_OR_ITEM,
                "jewelry-teleports", "Jewelry Teleports");
        return new Transport(TravelAction.Family.SPELL_OR_ITEM, "named-teleport", null);
    }

    private static boolean isJewelleryTeleport(String text)
    {
        return text.contains("amulet of glory") || text.contains("ring of dueling")
            || text.contains("ring of duelling") || text.contains("games necklace")
            || text.contains("combat bracelet") || text.contains("skills necklace")
            || text.contains("ring of wealth") || text.contains("necklace of passage")
            || text.contains("burning amulet") || text.contains("digsite pendant")
            || text.contains("slayer ring") || text.contains("drakan's medallion")
            || text.contains("xeric's talisman") || text.contains("ring of the elements");
    }

    private static TravelAction exact(TravelAction.Family family, String methodId,
        String option, String target, CanonicalChunk origin, CanonicalChunk destination,
        String requiredUnlock)
    {
        return new TravelAction(family, methodId, cleanLabel(option, target), origin,
            destination, requiredUnlock, TravelAction.Confidence.EXACT);
    }

    private static TravelAction unknown(String option, String target, CanonicalChunk origin)
    {
        return new TravelAction(
            TravelAction.Family.UNKNOWN,
            "unknown",
            cleanLabel(option, target),
            origin,
            null,
            null,
            TravelAction.Confidence.UNKNOWN);
    }

    private static CanonicalChunk tileChunk(MenuEntry entry, Client client)
    {
        if (client == null) return null;
        int x = entry.getParam0();
        int y = entry.getParam1();
        if (x < 0 || x >= Constants.SCENE_SIZE || y < 0 || y >= Constants.SCENE_SIZE)
            return null;
        WorldPoint point = WorldPoint.fromScene(client, x, y, client.getPlane());
        return point == null ? null : CanonicalChunk.of(point);
    }

    private static boolean isBoundaryObject(MenuAction action)
    {
        if (action == null) return false;
        switch (action)
        {
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
                return true;
            default:
                return false;
        }
    }

    private static boolean isBoundaryOption(String option)
    {
        return option.equals("open") || option.equals("enter") || option.equals("climb")
            || option.equals("climb-up") || option.equals("climb-down")
            || option.equals("board") || option.equals("travel") || option.equals("pay-fare")
            || option.equals("squeeze-through") || option.equals("cross");
    }

    private static String clean(String value)
    {
        return Text.removeTags(value == null ? "" : value)
            .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String cleanLabel(String option, String target)
    {
        String label = (option + " " + target).trim();
        return label.replaceAll("\\s+", " ");
    }

    private static final class Transport
    {
        private final TravelAction.Family family;
        private final String methodId;
        private final String requiredUnlock;

        private Transport(TravelAction.Family family, String methodId, String requiredUnlock)
        {
            this.family = family;
            this.methodId = methodId;
            this.requiredUnlock = requiredUnlock;
        }
    }
}
