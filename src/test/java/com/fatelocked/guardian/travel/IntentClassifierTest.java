package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;
import com.fatelocked.MenuFacts;
import com.fatelocked.rules.TravelTable;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * F2 (G7, G13): a click is travel only when the tracker's table matches its
 * own ids and its exact option text. The ids are the ones RuneLite gives
 * (see MenuFactsTest); the rows are vanilla-interiors' table.
 */
public class IntentClassifierTest
{
    private static final Gson GSON = new Gson();
    private static final int STANDARD = 0;
    private static final int ANCIENT = 1;
    private static final int LUNAR = 2;
    private static final int ARCEUUS = 3;
    private static final int AMULET_OF_GLORY_4 = 1712;
    private static final int DIGSITE_PENDANT_5 = 11194;
    private static final int VARROCK_TELEPORT_TABLET = 8007;
    private static final int FAIRY_RING = 29495;
    private static final int PORT_SARIM_CREW = 14978;

    private final IntentClassifier classifier = new IntentClassifier();
    private final TravelTable table = golden("vanilla-interiors");

    /** Two spells share a name; the active spellbook tells them apart (G7). */
    @Test
    public void aSpellIsItsSpellbookAndName()
    {
        assertEquals("spell:arceuus:ape-atoll-teleport", id(spell(ARCEUUS, "Cast", "Ape Atoll Teleport")));
        assertEquals(new CanonicalChunk(43, 142), destination(spell(ARCEUUS, "Cast", "Ape Atoll Teleport")));
        assertEquals("spell:standard:ape-atoll-teleport", id(spell(STANDARD, "Cast", "Ape Atoll Teleport")));
        assertNull("the Lunar book has no such spell", classifier.classify(spell(LUNAR, "Cast", "Ape Atoll Teleport"), table));
        assertNull("a spellbook it doesn't know", classifier.classify(spell(4, "Cast", "Ape Atoll Teleport"), table));
        assertNull(classifier.classify(spell(MenuFacts.NONE, "Cast", "Ape Atoll Teleport"), table));

        // Senntisten lands in 51,52 (web 3580d64); Carrallanger is spelled as the game spells it.
        assertEquals(new CanonicalChunk(51, 52), destination(spell(ANCIENT, "Cast", "Senntisten Teleport")));
        assertEquals("spell:ancient:carrallanger-teleport", id(spell(ANCIENT, "Cast", "Carrallanger Teleport")));
        assertNull("an option the spell doesn't have", classifier.classify(spell(STANDARD, "Examine", "Varrock Teleport"), table));

        MenuFacts notTheSpellbook = MenuFacts.builder().kind(MenuFacts.Kind.WIDGET).interfaceGroup(InterfaceID.INVENTORY)
            .spellbook(STANDARD).option("Cast").target("Varrock Teleport").build();
        assertNull(classifier.classify(notTheSpellbook, table));
    }

    /** An item is its id in the inventory or the worn items, and nowhere else. */
    @Test
    public void anItemIsItsIdInTheInventoryOrWornItems()
    {
        TravelMatch rub = classifier.classify(item(InterfaceID.INVENTORY, AMULET_OF_GLORY_4, "Rub"), table);
        assertEquals("item:amulet-of-glory", rub.getMethod().getId());
        assertNull("Rub picks the place after the click", rub.getOption().destination());
        assertEquals(new CanonicalChunk(48, 54), destination(item(InterfaceID.WORNITEMS, AMULET_OF_GLORY_4, "Edgeville")));
        assertEquals("tablet:varrock-teleport", id(item(InterfaceID.INVENTORY, VARROCK_TELEPORT_TABLET, "Break")));

        // The Digsite pendant's Rub goes to one of three places (G7).
        TravelMatch digsite = classifier.classify(item(InterfaceID.INVENTORY, DIGSITE_PENDANT_5, "Rub"), table);
        assertEquals("item:digsite-pendant", digsite.getMethod().getId());
        assertNull(digsite.getOption().destination());

        assertNull("never travel", classifier.classify(item(InterfaceID.INVENTORY, AMULET_OF_GLORY_4, "Wear"), table));
        assertNull("in the bank", classifier.classify(item(InterfaceID.BANKSIDE, AMULET_OF_GLORY_4, "Edgeville"), table));
        assertNull("no item", classifier.classify(item(InterfaceID.INVENTORY, MenuFacts.NONE, "Edgeville"), table));
    }

    /** An object or NPC is its own id; a fairy ring's Last-destination is its code. */
    @Test
    public void anObjectOrNpcIsItsId()
    {
        assertEquals("network:fairy-ring", id(object(FAIRY_RING, "Zanaris")));
        TravelMatch last = classifier.classify(object(FAIRY_RING, "Last-destination (CKS)"), table);
        assertEquals("code:CKS", last.getOption().getText());
        assertEquals(new CanonicalChunk(53, 54), last.getOption().destination());
        assertNull("the dial opens; the code isn't known yet", classifier.classify(object(FAIRY_RING, "Configure"), table));
        assertEquals("boat:port-sarim-ship", id(npc(PORT_SARIM_CREW, "Travel")));

        assertNull("an item's id isn't an object's", classifier.classify(object(AMULET_OF_GLORY_4, "Edgeville"), table));
        assertNull("an object's id isn't an NPC's", classifier.classify(npc(FAIRY_RING, "Zanaris"), table));
        assertNull(classifier.classify(object(MenuFacts.NONE, "Zanaris"), table));
        assertNull(classifier.classify(npc(MenuFacts.NONE, "Travel"), table));
    }

    /** Walking, examining and the rest are never travel; nor is anything without a table. */
    @Test
    public void everythingElseIsNotTravel()
    {
        MenuFacts walk = MenuFacts.builder().option("Walk here").itemId(AMULET_OF_GLORY_4).objectId(FAIRY_RING).build();
        assertNull(classifier.classify(walk, table));
        assertNull(classifier.classify(MenuFacts.EMPTY, table));
        assertNull(classifier.classify(null, table));
        assertNull(classifier.classify(item(InterfaceID.INVENTORY, AMULET_OF_GLORY_4, "Edgeville"), null));
    }

    /** A click two methods match is neither's; one matched by its option alone is that one's. */
    @Test
    public void aClickTwoMethodsMatchIsNeither()
    {
        TravelTable shared = TravelTable.parse(GSON.fromJson("{"
            + "\"item:a\": {\"label\": \"A\", \"match\": {\"items\": [1]}, \"options\": {"
            + "  \"Teleport\": {\"to\": [\"50,50\"], \"status\": \"LOCKED\"}, \"Home\": {\"to\": [\"50,51\"], \"status\": \"LOCKED\"}}},"
            + "\"item:b\": {\"label\": \"B\", \"match\": {\"items\": [1, 2]}, \"options\": {"
            + "  \"Teleport\": {\"to\": [\"46,52\"], \"status\": \"LOCKED\"}, \"Away\": {\"to\": [\"46,53\"], \"status\": \"LOCKED\"}}}"
            + "}", JsonObject.class));

        assertNull(classifier.classify(item(InterfaceID.INVENTORY, 1, "Teleport"), shared));
        assertEquals("item:a", id(item(InterfaceID.INVENTORY, 1, "Home"), shared));
        assertEquals("item:b", id(item(InterfaceID.INVENTORY, 1, "Away"), shared));
        assertEquals("item:b", id(item(InterfaceID.INVENTORY, 2, "Teleport"), shared));
    }

    private String id(MenuFacts facts)
    {
        return id(facts, table);
    }

    private String id(MenuFacts facts, TravelTable in)
    {
        TravelMatch match = classifier.classify(facts, in);
        return match == null ? null : match.getMethod().getId();
    }

    private CanonicalChunk destination(MenuFacts facts)
    {
        return classifier.classify(facts, table).getOption().destination();
    }

    static MenuFacts spell(int spellbook, String option, String name)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.WIDGET).interfaceGroup(InterfaceID.MAGIC_SPELLBOOK)
            .spellbook(spellbook).option(option).target(name).build();
    }

    static MenuFacts item(int group, int itemId, String option)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.WIDGET).interfaceGroup(group).itemId(itemId)
            .option(option).target("An item").build();
    }

    static MenuFacts object(int objectId, String option)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.OBJECT).objectId(objectId).option(option).target("An object").build();
    }

    static MenuFacts npc(int npcId, String option)
    {
        return MenuFacts.builder().kind(MenuFacts.Kind.NPC).npcId(npcId).option(option).target("Someone").build();
    }

    static TravelTable golden(String id)
    {
        try (InputStream in = IntentClassifierTest.class.getClassLoader()
            .getResourceAsStream("contracts/golden-bundles/" + id + ".bundle.json.gz");
             GZIPInputStream gzip = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            gzip.transferTo(out);
            return FateLockedBundle.loadFromJson(GSON, out.toString(StandardCharsets.UTF_8)).getRules().getTravel();
        }
        catch (java.io.IOException ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
