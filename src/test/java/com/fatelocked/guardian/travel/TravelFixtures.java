package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.MenuFacts;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import net.runelite.api.gameval.InterfaceID;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The v4 test bundle with a small travel table (bundles/v4-travel.json),
 * bound to Nubles, and clicks on it as MenuFacts. In the table Falador is
 * locked, Lumbridge allowed, Camelot not ready, Varrock two places, and
 * Senntisten needs Ancient Magicks; the glory's Al Kharid and Karamja are
 * locked and its Edgeville allowed; the ring of dueling's Emir's Arena is
 * allowed; the Digsite pendant needs its own unlock; and the
 * fairy ring and the Port Sarim ship are advisory.
 */
public final class TravelFixtures
{
    public static final CanonicalChunk FALADOR = new CanonicalChunk(46, 52);
    public static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    public static final CanonicalChunk AL_KHARID = new CanonicalChunk(51, 49);
    public static final CanonicalChunk EDGEVILLE = new CanonicalChunk(48, 54);
    public static final CanonicalChunk DIGSITE = new CanonicalChunk(52, 53);
    public static final CanonicalChunk EMIRS_ARENA = new CanonicalChunk(51, 50);
    public static final int STANDARD = 0;
    public static final int ANCIENT = 1;
    public static final int LUMBRIDGE_TABLET = 8008;
    public static final int FALADOR_TABLET = 8009;
    public static final int AMULET_OF_GLORY_4 = 1712;
    public static final int RING_OF_DUELING_8 = 2552;
    public static final int DIGSITE_PENDANT_5 = 11194;
    public static final int FAIRY_RING = 29495;
    public static final int PORT_SARIM_CREW = 14978;

    private TravelFixtures()
    {
    }

    public static String json()
    {
        try (InputStream in = TravelFixtures.class.getClassLoader().getResourceAsStream("bundles/v4-travel.json"))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new IllegalStateException(ex);
        }
    }

    public static FateLockedBundle bundle()
    {
        return FateLockedBundle.loadFromJson(new Gson(), json());
    }

    /** The table's rules, bound to Nubles, with this character playing. */
    public static DecisionService playing(String player)
    {
        return DecisionService.create(RulesSnapshot.of(bundle()), "nubles", player);
    }

    public static DecisionService nubles()
    {
        return playing("nubles");
    }

    /** Cast on a standard spell. */
    public static MenuFacts cast(String spell)
    {
        return spell(STANDARD, "Cast", spell);
    }

    public static MenuFacts spell(int spellbook, String option, String name)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.WIDGET).interfaceGroup(InterfaceID.MAGIC_SPELLBOOK)
            .spellbook(spellbook).option(option).target(name).build();
    }

    /** An option on an item in the inventory. */
    public static MenuFacts item(int itemId, String option, String name)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.WIDGET).interfaceGroup(InterfaceID.INVENTORY)
            .itemId(itemId).option(option).target(name).build();
    }

    public static MenuFacts object(int objectId, String option, String name)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.OBJECT).objectId(objectId).option(option).target(name).build();
    }

    public static MenuFacts npc(int npcId, String option, String name)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.NPC).npcId(npcId).option(option).target(name).build();
    }

    /** Anything that is never travel: walking, dialogue, a player. */
    public static MenuFacts other(String option, String target)
    {
        return MenuFacts.builder().option(option).target(target).build();
    }
}
