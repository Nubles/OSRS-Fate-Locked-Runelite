package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.Teleports;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.client.util.Text;

public final class TravelActionResolver
{
    /** Options that name no place themselves, so the target alone says where. */
    private static final Set<String> ACTIVATIONS = new HashSet<>(Arrays.asList(
        "cast", "break", "teleport", "rub", "read", "travel", "charter", "pay-fare", "minigame teleport"));

    public TravelAction resolve(MenuEntry entry, Client client, CanonicalChunk origin)
    {
        if (entry == null) return unknown("", origin);

        String option = clean(entry.getOption());
        String target = clean(entry.getTarget());
        String label = displayLabel(entry.getOption(), entry.getTarget());
        if (entry.getType() == MenuAction.WALK)
        {
            // "Walk here" carries viewport pixel coordinates, not a scene
            // tile, and walking is never blocked, so it is never travel.
            return unknown(label, origin);
        }

        CanonicalChunk destination = Teleports.checkedTravelDestinationChunk(
            option, target, true);
        if (destination != null)
        {
            Transport transport = transport(option + " " + target);
            return exact(transport.family, transport.methodId, label,
                origin, destination, transport.requiredUnlock);
        }

        // Doors, stairs, ladders and other objects are not travel either: an
        // object's own tile is not where it leads. Menu tags cover them.
        return unknown(label, origin);
    }

    /**
     * The trip as the menu names it, in its own case, without colour tags or
     * the "(LOCKED)" tag (B15): "Varrock Teleport" for Cast on the spell, and
     * "Amulet of glory(4) to Edgeville" for an option that names the place.
     */
    static String displayLabel(String rawOption, String rawTarget)
    {
        String option = display(rawOption);
        String target = display(rawTarget);
        if (target.isEmpty()) return option;
        if (option.isEmpty() || ACTIVATIONS.contains(option.toLowerCase(Locale.ROOT))) return target;
        return target + " to " + option;
    }

    private static String display(String value)
    {
        return Text.removeTags(value == null ? "" : value).replace("(LOCKED)", "")
            .replaceAll("\\s+", " ").trim();
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
        String label, CanonicalChunk origin, CanonicalChunk destination,
        String requiredUnlock)
    {
        return new TravelAction(family, methodId, label, origin,
            destination, requiredUnlock, TravelAction.Confidence.EXACT);
    }

    private static TravelAction unknown(String label, CanonicalChunk origin)
    {
        return new TravelAction(
            TravelAction.Family.UNKNOWN,
            "unknown",
            label,
            origin,
            null,
            null,
            TravelAction.Confidence.UNKNOWN);
    }

    private static String clean(String value)
    {
        return Text.removeTags(value == null ? "" : value)
            .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
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
