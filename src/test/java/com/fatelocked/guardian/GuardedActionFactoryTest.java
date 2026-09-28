package com.fatelocked.guardian;

import com.fatelocked.ChunkLocator;
import com.fatelocked.MenuFacts;
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
        MenuEntry tagged = entry("Attack", "<col=ffff00>Goblin</col> <col=f87171>(Locked)</col>");
        when(tagged.getNpc()).thenReturn(npc);
        assertEquals("the plugin's own tag is removed", "goblin", factory.from(tagged, locator).getTarget());

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

    /** F1: from the option's facts, read once, the factory says what it says from the entry. */
    @Test
    public void theFactsSayWhatTheEntrySays()
    {
        NPC npc = mock(NPC.class);
        when(npc.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        MenuEntry attack = entry("Attack", "<col=ffff00>Goblin</col>");
        when(attack.getNpc()).thenReturn(npc);
        MenuEntry wield = entry("Wield", "Abyssal whip");
        when(wield.getItemId()).thenReturn(4151);
        MenuEntry examine = entry("Examine", "Goblin");
        for (MenuEntry entry : new MenuEntry[] {attack, wield, examine})
        {
            MenuFacts facts = MenuFacts.builder()
                .option(entry.getOption().replaceAll("<[^>]*>", ""))
                .target(entry.getTarget().replaceAll("<[^>]*>", ""))
                .build();
            assertEquals(entry.getOption(), factory.from(entry, locator), factory.from(facts, entry, locator));
        }
        GuardedAction bank = factory.from(MenuFacts.builder().option("Bank").target("Banker").build(),
            entry("Bank", "Banker"), locator);
        assertEquals("the facts keep their case; the factory's words don't", "bank", bank.getOption());
        assertEquals("banker", bank.getTarget());
    }

    /** A non-breaking space in a name is a space, as in the facts. */
    @Test
    public void anyWhitespaceIsOneSpace()
    {
        assertEquals("goblin (level-2)", factory.from(entry("Attack", "Goblin\u00a0 (level-2)"), locator).getTarget());
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
