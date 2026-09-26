package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.ItemTier;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B12: over-tier gear from the tracker's itemRules and unlocks. Every rated
 * item of every golden bundle agrees with today's itemTiers answer, another
 * character gets no rating, an older export still rates by the slot an item
 * is worn in, and the plugin's warning follows the decision service.
 */
public class GearDecisionTest
{
    private static final Gson GSON = new Gson();

    @Test
    public void everyRatedItemAgreesWithTodaysTiers() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            JsonObject wire = GSON.fromJson(json, JsonObject.class);
            JsonObject itemTiers = wire.getAsJsonObject("itemTiers");
            JsonObject equipment = wire.getAsJsonObject("state").getAsJsonObject("equipment");
            DecisionService service = DecisionService.create(
                RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), null, null);
            List<String> mismatches = new ArrayList<>();
            int over = 0;
            for (Map.Entry<String, JsonElement> rule : wire.getAsJsonObject("rules").getAsJsonObject("itemRules").entrySet())
            {
                String slot = rule.getValue().getAsJsonObject().get("slot").getAsString();
                ItemTier got = service.itemTier(Integer.parseInt(rule.getKey()), slot);
                ItemTier today = new ItemTier(slot, itemTiers.get(rule.getKey()).getAsInt(),
                    equipment.has(slot) ? equipment.get(slot).getAsInt() : 0);
                if (!today.equals(got)) mismatches.add(rule.getKey() + " want " + today + " got " + got);
                if (today.isOver()) over++;
            }
            assertTrue(id + " rates items", itemTiers.size() > 2000);
            assertTrue(id + " has over-tier items", over > 0);
            assertEquals(id, List.of(), mismatches);
        }
    }

    @Test
    public void onlyAboveTheUnlockedTierIsOver()
    {
        assertTrue(new ItemTier("Weapon", 3, 2).isOver());
        assertFalse(new ItemTier("Weapon", 2, 2).isOver());
        assertFalse(new ItemTier("Weapon", 1, 2).isOver());
    }

    @Test
    public void anotherCharacterGetsNoRating() throws Exception
    {
        DecisionService other = DecisionService.create(RulesSnapshot.of(golden("vanilla-mid")), "iron example", "someone else");
        assertNull(other.itemTier(4151, "Weapon"));
    }

    @Test
    public void anOlderExportRatesByTheSlotItIsWornIn() throws Exception
    {
        JsonObject v3;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("bundles/v3-standard.json"))
        {
            v3 = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
        }
        v3.add("itemTiers", GSON.fromJson("{\"4151\": 3}", JsonObject.class));
        v3.getAsJsonObject("state").add("equipment", GSON.fromJson("{\"Weapon\": 1}", JsonObject.class));
        RulesSnapshot rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, v3.toString()));
        DecisionService nubles = DecisionService.create(rules, "nubles", "nubles");

        assertEquals(new ItemTier("Weapon", 3, 1), nubles.itemTier(4151, "Weapon"));
        assertEquals("a slot not listed is unlocked to T0", new ItemTier("Shield", 3, 0), nubles.itemTier(4151, "Shield"));
        assertNull(nubles.itemTier(1, "Weapon"));
        assertNull(DecisionService.create(rules, "nubles", "zezima").itemTier(4151, "Weapon"));
    }

    /** The plugin's warning: a vanilla-mid weapon above T2, worn by the rules' character or another. */
    @Test
    public void thePluginWarnsOnlyForTheRulesCharacter() throws Exception
    {
        FateLockedBundle mid = golden("vanilla-mid");
        int weapon = overTierWeapon(mid);
        FateLockedPlugin plugin = new FateLockedPlugin();
        FateLockedConfig config = mock(FateLockedConfig.class);
        when(config.warnOverTierGear()).thenReturn(true);
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        ItemContainer worn = mock(ItemContainer.class);
        when(worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(new Item(weapon, 1));
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
        ItemManager items = mock(ItemManager.class);
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getName()).thenReturn("Over-tier weapon");
        when(items.getItemComposition(anyInt())).thenReturn(composition);
        set(plugin, "config", config);
        set(plugin, "client", client);
        set(plugin, "itemManager", items);
        set(plugin, "chatMessageManager", mock(ChatMessageManager.class));
        set(plugin, "notifier", mock(Notifier.class));
        set(plugin, "active", new ActiveRules(mid, FateLockedPlugin.RulesSource.RELAY));

        when(player.getName()).thenReturn("Iron Example");
        call(plugin, "refreshDecisions");
        call(plugin, "recomputeOverTierGear");
        assertEquals("Weapon", plugin.getOverTierSummary());

        when(player.getName()).thenReturn("Someone Else");
        call(plugin, "refreshDecisions");
        call(plugin, "recomputeOverTierGear");
        assertNull(plugin.getOverTierSummary());
    }

    private static int overTierWeapon(FateLockedBundle mid)
    {
        for (Map.Entry<String, com.fatelocked.rules.RuneliteRulesManifest.ItemRule> rule
            : mid.getRules().getItemRules().entrySet())
        {
            if ("Weapon".equals(rule.getValue().getSlot()) && rule.getValue().getTier() > 2)
            {
                return Integer.parseInt(rule.getKey());
            }
        }
        throw new AssertionError("vanilla-mid rates no weapon above T2");
    }

    private static FateLockedBundle golden(String id) throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")));
    }

    private static void call(FateLockedPlugin plugin, String name) throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(plugin);
    }

    private static void set(FateLockedPlugin plugin, String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
