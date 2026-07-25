package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import java.util.Arrays;
import java.util.Collection;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class TravelActionResolverMatrixTest
{
    private final TravelActionResolver resolver = new TravelActionResolver();
    private final Client client = mock(Client.class);
    private final String option;
    private final String target;
    private final TravelAction.Family family;
    private final CanonicalChunk destination;
    private final TravelAction.Confidence confidence;

    public TravelActionResolverMatrixTest(String name, String option, String target,
        TravelAction.Family family, CanonicalChunk destination,
        TravelAction.Confidence confidence)
    {
        this.option = option;
        this.target = target;
        this.family = family;
        this.destination = destination;
        this.confidence = confidence;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases()
    {
        return Arrays.asList(new Object[][] {
            exact("lunar-isle", "Teleport", "Lunar Isle", TravelAction.Family.SPELL_OR_ITEM, 33, 61),
            exact("zanaris", "Travel", "Fairy ring — Zanaris", TravelAction.Family.FAIRY_RING, 37, 69),
            exact("prifddinas", "Travel", "Spirit tree — Prifddinas", TravelAction.Family.SPIRIT_TREE, 51, 95),
            exact("lemanto-andra", "Travel", "Gnome glider — Lemanto Andra", TravelAction.Family.GNOME_GLIDER, 51, 53),
            exact("port-khazard", "Charter", "Port Khazard", TravelAction.Family.CHARTER_SHIP, 41, 49),
            exact("lovakengj", "Travel", "Mine cart — Lovakengj", TravelAction.Family.MINE_CART, 23, 58),
            exact("nardah", "Pay-fare", "Magic carpet to Nardah", TravelAction.Family.MAGIC_CARPET, 53, 45),
            exact("taverley", "Travel", "Balloon — Taverley", TravelAction.Family.BALLOON, 45, 53),
            exact("eagles-peak", "Travel", "Eagle — Eagles' Peak", TravelAction.Family.EAGLE, 36, 54),
            exact("nightmare-zone", "Minigame teleport", "Nightmare Zone", TravelAction.Family.MINIGAME_TELEPORT, 40, 48),
            exact("hunter-guild", "Travel", "Quetzal — Hunter Guild", TravelAction.Family.QUETZAL, 24, 47),

            unknown("lunar-isle-near-miss", "Teleport", "Teleport configuration"),
            unknown("zanaris-near-miss", "Check", "Fairy ring health"),
            unknown("prifddinas-near-miss", "Check", "Spirit tree health"),
            unknown("lemanto-andra-near-miss", "Check", "Gnome glider condition"),
            unknown("port-khazard-near-miss", "Examine", "Charter schedule"),
            unknown("lovakengj-near-miss", "Check", "Mine cart wheels"),
            unknown("nardah-near-miss", "Talk-to", "Rug merchant"),
            unknown("taverley-near-miss", "Check", "Balloon fabric"),
            unknown("eagles-peak-near-miss", "Check", "Eagle nest"),
            unknown("nightmare-zone-near-miss", "Check", "Minigame teleport settings"),
            unknown("hunter-guild-near-miss", "Check", "Quetzal feathers"),

            unknown("lunar-isle-unresolved", "Teleport", "New destination"),
            unknown("zanaris-unresolved", "Travel", "Fairy ring — New destination"),
            unknown("prifddinas-unresolved", "Travel", "Spirit tree — New destination"),
            unknown("lemanto-andra-unresolved", "Travel", "Gnome glider — New destination"),
            unknown("port-khazard-unresolved", "Charter", "Unknown port"),
            unknown("lovakengj-unresolved", "Travel", "Mine cart — New destination"),
            unknown("nardah-unresolved", "Pay-fare", "Magic carpet to Unknown"),
            unknown("taverley-unresolved", "Travel", "Balloon — New destination"),
            unknown("eagles-peak-unresolved", "Travel", "Eagle — New destination"),
            unknown("nightmare-zone-unresolved", "Minigame teleport", "Unknown minigame"),
            unknown("hunter-guild-unresolved", "Travel", "Quetzal — New destination")
        });
    }

    @Test
    public void resolvesOnlyTheIndependentlyCheckedFixture()
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);

        TravelAction action = resolver.resolve(entry, client, new CanonicalChunk(50, 50));

        assertEquals(family, action.getFamily());
        assertEquals(confidence, action.getConfidence());
        if (destination == null)
        {
            assertNull(action.getDestination());
        }
        else
        {
            assertEquals(destination, action.getDestination());
        }
    }

    private static Object[] exact(String name, String option, String target,
        TravelAction.Family family, int cx, int cy)
    {
        return new Object[] { name, option, target, family,
            new CanonicalChunk(cx, cy), TravelAction.Confidence.EXACT };
    }

    private static Object[] unknown(String name, String option, String target)
    {
        return new Object[] { name, option, target, TravelAction.Family.UNKNOWN,
            null, TravelAction.Confidence.UNKNOWN };
    }
}