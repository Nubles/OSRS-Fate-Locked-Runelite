package com.fatelocked;

import com.fatelocked.rules.TravelTable;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F4 (G14): the " (Locked)" tag on a travel option is the tracker's exact
 * decision, the one Strict Mode reads. On every golden, every option of
 * every method in the travel table, clicked as the game gives it, is tagged
 * exactly when the tracker locks it and it goes to one place: networks and
 * boats too, which Strict Mode never blocks. So every trip Strict Mode
 * blocks was tagged first. Another character sees no tags.
 */
@RunWith(Parameterized.class)
public class TagConsistencyTest
{
    private static final Gson GSON = new Gson();
    private static final String TAG = " <col=f87171>(Locked)</col>";

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> scenarios() throws IOException
    {
        return GoldenBundleContractTest.scenarios();
    }

    private final String id;
    private final FateLockedBundle bundle;
    private final JsonObject travel;
    private final FateLockedPlugin plugin = new FateLockedPlugin();
    private final Client client = mock(Client.class);

    public TagConsistencyTest(String id) throws Exception
    {
        this.id = id;
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
        bundle = FateLockedBundle.loadFromJson(GSON, json);
        travel = GSON.fromJson(json, JsonObject.class).getAsJsonObject("rules").getAsJsonObject("travel");
        FateLockedConfig config = mock(FateLockedConfig.class);
        when(config.tagLockedOptions()).thenReturn(true);
        set("config", config);
        set("client", client);
        set("active", new ActiveRules(bundle, FateLockedPlugin.RulesSource.RELAY));
    }

    @Test
    public void aTravelTagIsAnExactLockedDecision() throws Exception
    {
        playing("Iron Example");
        List<String> want = new ArrayList<>();
        for (Map.Entry<String, JsonElement> method : travel.entrySet())
        {
            JsonObject row = method.getValue().getAsJsonObject();
            addLocked(method.getKey(), row.getAsJsonObject("options"), false, want);
            if (row.has("codes")) addLocked(method.getKey(), row.getAsJsonObject("codes"), true, want);
        }
        assertTrue(id + " locks some trip", !want.isEmpty());
        assertEquals(id, want, tagged());
    }

    @Test
    public void anotherCharacterSeesNoTravelTags() throws Exception
    {
        if (bundle.getRules().getAccount() == null) return;
        playing("Someone Else");
        assertEquals(id, List.of(), tagged());
    }

    private static void addLocked(String method, JsonObject options, boolean codes, List<String> into)
    {
        for (Map.Entry<String, JsonElement> option : options.entrySet())
        {
            JsonObject decided = option.getValue().getAsJsonObject();
            if ("LOCKED".equals(decided.get("status").getAsString()) && decided.getAsJsonArray("to").size() == 1)
            {
                into.add(method + "|" + (codes ? "Last-destination (" + option.getKey() + ")" : option.getKey()));
            }
        }
    }

    /** Every option of every method, clicked on its first id; those the plugin tagged. */
    private List<String> tagged()
    {
        List<String> tagged = new ArrayList<>();
        for (TravelTable.Method method : bundle.getRules().getTravel().methods())
        {
            for (String option : GoldenBundleContractTest.texts(method))
            {
                MenuEntry entry = entry(method, option);
                String before = entry.getTarget();
                plugin.onMenuEntryAdded(new MenuEntryAdded(entry));
                if (entry.getTarget().equals(before + TAG)) tagged.add(method.getId() + "|" + option);
            }
        }
        return tagged;
    }

    /** The option as the game gives it: a spell with its book active, or the method's first id. */
    private MenuEntry entry(TravelTable.Method method, String option)
    {
        int first = method.getIds().isEmpty() ? -1 : method.getIds().iterator().next();
        switch (method.getMatch())
        {
            case SPELL:
                when(client.getVarbitValue(VarbitID.SPELLBOOK))
                    .thenReturn(TravelTable.SPELLBOOKS.indexOf(method.getSpellbook()));
                return TravelClicks.spell(option, method.getSpell()).getMenuEntry();
            case ITEMS:
                return TravelClicks.item(first, option, method.getLabel()).getMenuEntry();
            case OBJECTS:
                return TravelClicks.object(first, option, method.getLabel()).getMenuEntry();
            default:
                return TravelClicks.npc(first, option, method.getLabel()).getMenuEntry();
        }
    }

    private void playing(String name) throws Exception
    {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(client.getLocalPlayer()).thenReturn(player);
        Method refresh = FateLockedPlugin.class.getDeclaredMethod("refreshDecisions");
        refresh.setAccessible(true);
        refresh.invoke(plugin);
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
