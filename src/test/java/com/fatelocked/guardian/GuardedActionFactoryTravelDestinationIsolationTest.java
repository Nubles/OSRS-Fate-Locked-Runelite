package com.fatelocked.guardian;

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
public class GuardedActionFactoryTravelDestinationIsolationTest
{
    private final GuardedActionFactory factory = new GuardedActionFactory();
    private final Client client = mock(Client.class);
    private final String option;
    private final String target;
    private final GuardedAction.Kind kind;
    private final CanonicalChunk destination;

    public GuardedActionFactoryTravelDestinationIsolationTest(String name,
        String option, String target, GuardedAction.Kind kind,
        CanonicalChunk destination)
    {
        this.option = option;
        this.target = target;
        this.kind = kind;
        this.destination = destination;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases()
    {
        return Arrays.asList(new Object[][] {
            unknown("lunar-isle", "Teleport", "Lunar Isle"),
            unknown("zanaris", "Travel", "Fairy ring — Zanaris"),
            unknown("prifddinas", "Travel", "Spirit tree — Prifddinas"),
            unknown("lovakengj", "Travel", "Mine cart — Lovakengj"),
            unknown("nardah", "Pay-fare", "Magic carpet to Nardah"),
            unknown("taverley", "Travel", "Balloon — Taverley"),
            unknown("eagles-peak", "Travel", "Eagle — Eagles' Peak"),
            unknown("nightmare-zone", "Minigame teleport", "Nightmare Zone"),
            unknown("hunter-guild", "Travel", "Quetzal — Hunter Guild"),
            known("port-khazard-existing-legacy-match", "Charter", "Port Khazard", 41, 49)
        });
    }

    @Test
    public void preservesThePreTaskSevenLegacyClassification()
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);
        when(entry.getItemId()).thenReturn(-1);

        GuardedAction action = factory.from(entry, client);

        assertEquals(kind, action.getKind());
        if (destination == null)
        {
            assertNull(action.getChunk());
        }
        else
        {
            assertEquals(destination, action.getChunk());
        }
    }

    private static Object[] unknown(String name, String option, String target)
    {
        return new Object[] { name, option, target, GuardedAction.Kind.UNKNOWN, null };
    }

    private static Object[] known(String name, String option, String target, int cx, int cy)
    {
        return new Object[] { name, option, target, GuardedAction.Kind.TELEPORT,
            new CanonicalChunk(cx, cy) };
    }
}