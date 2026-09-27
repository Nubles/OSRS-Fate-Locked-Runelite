package com.fatelocked;

import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Menu clicks as RuneLite gives them (see MenuFactsTest), for tests that go
 * through the plugin: the spellbook, inventory and worn items are interface
 * components, and a spell's book comes from the client's SPELLBOOK varbit,
 * which a mocked client reads as 0, the standard book.
 */
public final class TravelClicks
{
    private TravelClicks()
    {
    }

    /** Cast on a spell in the spellbook. */
    public static MenuOptionClicked cast(String spell)
    {
        return spell("Cast", spell);
    }

    /** An option on a spell in the spellbook. */
    public static MenuOptionClicked spell(String option, String spell)
    {
        return widget(option, "<col=00ff00>" + spell + "</col>", InterfaceID.MAGIC_SPELLBOOK, -1);
    }

    /** An option on an item in the inventory. */
    public static MenuOptionClicked item(int itemId, String option, String name)
    {
        return widget(option, "<col=ff9040>" + name + "</col>", InterfaceID.INVENTORY, itemId);
    }

    public static MenuOptionClicked npc(int npcId, String option, String name)
    {
        MenuOptionClicked click = click(MenuAction.NPC_FIRST_OPTION, option, name);
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenReturn(npcId);
        when(click.getMenuEntry().getNpc()).thenReturn(npc);
        return click;
    }

    public static MenuOptionClicked object(int objectId, String option, String name)
    {
        MenuOptionClicked click = click(MenuAction.GAME_OBJECT_FIRST_OPTION, option, name);
        when(click.getMenuEntry().getIdentifier()).thenReturn(objectId);
        return click;
    }

    /** A click whose entry keeps the target set on it, as a menu tag sets it. */
    public static MenuOptionClicked click(MenuAction type, String option, String target)
    {
        MenuEntry entry = mock(MenuEntry.class);
        String[] current = { target };
        when(entry.getOption()).thenReturn(option);
        when(entry.getTarget()).thenAnswer(call -> current[0]);
        doAnswer(call -> {
            current[0] = call.getArgument(0);
            return entry;
        }).when(entry).setTarget(anyString());
        when(entry.getType()).thenReturn(type);
        when(entry.getItemId()).thenReturn(-1);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);
        return click;
    }

    private static MenuOptionClicked widget(String option, String target, int group, int itemId)
    {
        MenuOptionClicked click = click(MenuAction.CC_OP, option, target);
        when(click.getMenuEntry().getParam1()).thenReturn(WidgetUtil.packComponentId(group, 1));
        when(click.getMenuEntry().getItemId()).thenReturn(itemId);
        return click;
    }
}
