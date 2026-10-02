package com.fatelocked;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B9: the world map's pins are the areas the tracker says are locked, on
 * every golden bundle. They follow the decision service, so they appear when
 * the rules' character logs in and are gone on another character.
 */
public class LockedAreaPinsTest
{
    private static final Gson GSON = new Gson();

    private final FateLockedPlugin plugin = new FateLockedPlugin();
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final Client client = mock(Client.class);
    private final WorldMapPointManager map = mock(WorldMapPointManager.class);

    @Before
    public void setUp() throws Exception
    {
        when(config.worldMapMarkers()).thenReturn(true);
        set("config", config);
        set("client", client);
        set("worldMapPointManager", map);
    }

    @Test
    public void thePinsAreTheAreasTheTrackerSaysAreLocked() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz"));
            FateLockedBundle rules = FateLockedBundle.loadFromJson(GSON, json);
            JsonElement account = GSON.fromJson(json, JsonObject.class).getAsJsonObject("rules").get("account");
            Set<String> want = new TreeSet<>();
            for (Map.Entry<String, JsonElement> area
                : GoldenBundleContractTest.json(id + ".expect.json").getAsJsonObject("areas").entrySet())
            {
                if (!area.getValue().getAsBoolean() && rules.getSubAreaChunks().containsKey(area.getKey()))
                {
                    want.add(area.getKey());
                }
            }

            String player = account == null || account.isJsonNull() ? "Anyone" : account.getAsString();
            assertEquals(id, want, pinsFor(rules, player));
            assertTrue(id + " pins something", !want.isEmpty());
        }
    }

    @Test
    public void anotherCharacterOrNobodyGetsNoPinsAndALoginBringsThem() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));

        assertEquals(Set.of(), pinsFor(mid, "Someone Else"));
        assertEquals(Set.of(), pinsFor(mid, null));
        assertEquals("logging in places them", 164, pinsFor(mid, "Iron Example").size());

        when(config.worldMapMarkers()).thenReturn(false);
        assertEquals("the setting off", Set.of(), pinsFor(mid, "Iron Example"));
    }

    /** A pin's tooltip names its area and says Locked, as the sidebar does (accuracy review, P-41). */
    @Test
    public void aPinsTooltipSaysItsAreaIsLocked() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        pinsFor(mid, "Iron Example");
        ArgumentCaptor<WorldMapPoint> added = ArgumentCaptor.forClass(WorldMapPoint.class);
        verify(map, atLeast(1)).add(added.capture());
        for (WorldMapPoint pin : added.getAllValues())
        {
            assertEquals(pin.getName() + ": Locked", pin.getTooltip());
        }
    }

    /** E1: the pins are drawn in the palette's locked colour, and a colour change redraws them. */
    @Test
    public void thePinsTakeThePalettesLockedColour() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        pinsFor(mid, "Iron Example");
        assertEquals(com.fatelocked.ui.Palette.defaults().lockedEdge(), pinColour());

        when(config.colourPreset()).thenReturn(FateLockedConfig.ColourPreset.CUSTOM);
        when(config.unlockedColor()).thenReturn(new java.awt.Color(0, 128, 255, 110));
        when(config.frontierColor()).thenReturn(new java.awt.Color(255, 255, 0, 100));
        when(config.lockedColor()).thenReturn(new java.awt.Color(128, 0, 128, 110));
        clearInvocations(map);
        GearDecisionTest.configChanged(plugin, "lockedColor");

        assertEquals(new java.awt.Color(128, 0, 128), pinColour());
    }

    /** The colour inside the last pin placed, above its padlock. */
    private java.awt.Color pinColour()
    {
        ArgumentCaptor<WorldMapPoint> added = ArgumentCaptor.forClass(WorldMapPoint.class);
        verify(map, atLeast(1)).add(added.capture());
        java.awt.image.BufferedImage image = added.getValue().getImage();
        return new java.awt.Color(image.getRGB(7, 2), true);
    }

    /** The pins placed when these rules are in force for this character (null: nobody logged in). */
    private Set<String> pinsFor(FateLockedBundle rules, String name) throws Exception
    {
        set("active", new ActiveRules(rules, FateLockedPlugin.RulesSource.RELAY));
        Player player = name == null ? null : mock(Player.class);
        if (player != null) when(player.getName()).thenReturn(name);
        when(client.getLocalPlayer()).thenReturn(player);
        clearInvocations(map);

        Method refresh = FateLockedPlugin.class.getDeclaredMethod("refreshDecisions");
        refresh.setAccessible(true);
        refresh.invoke(plugin);

        ArgumentCaptor<WorldMapPoint> added = ArgumentCaptor.forClass(WorldMapPoint.class);
        verify(map, atLeast(0)).add(added.capture());
        Set<String> names = new TreeSet<>();
        for (WorldMapPoint point : added.getAllValues()) names.add(point.getName());
        return names;
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
