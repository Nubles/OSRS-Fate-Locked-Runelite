package com.fatelocked.guardian;

import com.fatelocked.ChunkLocator;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GuardedActionFactoryTest
{
    private final GuardedActionFactory factory = new GuardedActionFactory();
    private final Client client = mock(Client.class);
    private final ChunkLocator locator = new ChunkLocator(client);

    @Test
    public void normalizesNpcAndBankActors()
    {
        NPC npc = mock(NPC.class);
        when(npc.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        MenuEntry attack = entry("Attack", "<col=ffff00>Goblin</col>");
        when(attack.getNpc()).thenReturn(npc);
        assertEquals(GuardedAction.Kind.NPC,
            factory.from(attack, locator).getKind());
        assertEquals("goblin", factory.from(attack, locator).getTarget());

        MenuEntry bank = entry("Bank", "Banker");
        when(bank.getNpc()).thenReturn(npc);
        assertEquals(GuardedAction.Kind.BANK,
            factory.from(bank, locator).getKind());
    }

    /** Travel is the tracker's table's to match, by id (F4): menu text alone is never a teleport here. */
    @Test
    public void walkingAndTeleportTextHaveNoChunkWhileEquipmentIsRecognized()
    {
        // "Walk here" carries viewport pixel coordinates, not a scene tile,
        // so it is never given a chunk (and never tagged).
        MenuEntry walk = entry("Walk here", "");
        when(walk.getType()).thenReturn(MenuAction.WALK);
        when(walk.getParam0()).thenReturn(10);
        when(walk.getParam1()).thenReturn(20);
        assertEquals(GuardedAction.Kind.UNKNOWN,
            factory.from(walk, locator).getKind());
        assertNull(factory.from(walk, locator).getChunk());

        MenuEntry teleport = entry("Teleport", "Falador");
        assertEquals(GuardedAction.Kind.UNKNOWN,
            factory.from(teleport, locator).getKind());
        assertNull(factory.from(teleport, locator).getChunk());

        MenuEntry wield = entry("Wield", "Abyssal whip");
        when(wield.getItemId()).thenReturn(4151);
        assertEquals(GuardedAction.Kind.EQUIPMENT,
            factory.from(wield, locator).getKind());
        assertEquals(Integer.valueOf(4151),
            factory.from(wield, locator).getItemId());
    }

    @Test
    public void examineAndUnrelatedWidgetsStayUnknown()
    {
        assertEquals(GuardedAction.Kind.UNKNOWN,
            factory.from(entry("Examine", "Goblin"), locator).getKind());
        assertEquals(GuardedAction.Kind.UNKNOWN,
            factory.from(entry("Continue", ""), locator).getKind());
    }

    @Test
    public void transportTextIsNeverAChunk()
    {
        String[] transports = {
            "mine cart", "magic carpet", "balloon", "eagle", "minigame teleport"
        };
        for (String transport : transports)
        {
            assertEquals(GuardedAction.Kind.UNKNOWN,
                factory.from(entry("Travel via " + transport, "Falador"), locator).getKind());
        }
    }

    private static MenuEntry entry(String option, String target)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenReturn(target);
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);
        when(entry.getItemId()).thenReturn(-1);
        return entry;
    }
}
