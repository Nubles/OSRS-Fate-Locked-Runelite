package com.fatelocked;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.Text;

import java.util.regex.Pattern;

/**
 * Reads a menu entry into {@link MenuFacts} (A9), on the client thread: it
 * reads the spellbook varbit and, for a worn item, the clicked widget. A
 * reader for clicks, given the {@link FairyRingDial}, also reads a fairy
 * ring's dials for a click on their confirm button, and notes any other
 * click on the dials or their travel log, which may turn them.
 */
final class MenuFactsReader
{
    private static final Pattern WHITESPACE =
        Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    /** A worn slot draws its item in its second child. */
    private static final int WORN_ITEM_CHILD = 1;
    /** A component's first op, as a CC_OP entry's identifier gives it: the confirm button's left click. */
    private static final int CONFIRM_OP = 1;

    private final Client client;
    /** The dials' turns, for clicks; null for a reader of menu entries as they are added. */
    private final FairyRingDial dial;

    MenuFactsReader(Client client)
    {
        this(client, null);
    }

    MenuFactsReader(Client client, FairyRingDial dial)
    {
        this.client = client;
        this.dial = dial;
    }

    MenuFacts read(MenuEntry entry)
    {
        if (entry == null) return MenuFacts.EMPTY;
        MenuFacts.MenuFactsBuilder facts = MenuFacts.builder()
            .option(text(entry.getOption()))
            .target(text(entry.getTarget()))
            .worldViewId(entry.getWorldViewId());
        MenuAction type = entry.getType();
        if (type == null) return facts.build();
        switch (type)
        {
            case CC_OP:
            case CC_OP_LOW_PRIORITY:
            {
                int component = entry.getParam1();
                int group = WidgetUtil.componentToInterface(component);
                facts.kind(MenuFacts.Kind.WIDGET).interfaceGroup(group).componentId(component)
                    .itemId(itemId(entry, group));
                if (group == InterfaceID.MAGIC_SPELLBOOK)
                {
                    facts.spellbook(client.getVarbitValue(VarbitID.SPELLBOOK));
                }
                // The confirm button's first op is the trip; any other op on it is left alone.
                if (FairyRingDial.isConfirm(component) && entry.getIdentifier() == CONFIRM_OP)
                {
                    facts.fairyRingCode(confirmedCode());
                }
                else
                {
                    noteTurn(component);
                }
                break;
            }
            case WIDGET_TYPE_1:
            case WIDGET_TYPE_4:
            case WIDGET_TYPE_5:
                // Older button kinds: never a trip here, but one on the dials may turn them.
                noteTurn(entry.getParam1());
                break;
            case GAME_OBJECT_FIRST_OPTION:
            case GAME_OBJECT_SECOND_OPTION:
            case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION:
            case GAME_OBJECT_FIFTH_OPTION:
                facts.kind(MenuFacts.Kind.OBJECT).objectId(entry.getIdentifier());
                break;
            case NPC_FIRST_OPTION:
            case NPC_SECOND_OPTION:
            case NPC_THIRD_OPTION:
            case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION:
            {
                NPC npc = entry.getNpc();
                facts.kind(MenuFacts.Kind.NPC).npcId(npc == null ? MenuFacts.NONE : npc.getId());
                break;
            }
            default:
                break;
        }
        return facts.build();
    }

    /** The code a confirm goes to, when this reader follows the dials; null otherwise or while unsure. */
    private String confirmedCode()
    {
        if (dial == null) return null;
        return dial.settled(client.getTickCount(), FairyRingDial.read(client));
    }

    /** A click on the dials or their travel log, other than the confirm, may turn them. */
    private void noteTurn(int component)
    {
        if (dial != null && FairyRingDial.mayTurn(component))
        {
            dial.turned(client.getTickCount(), FairyRingDial.read(client));
        }
    }

    /**
     * The item behind an item option. A worn slot may not say, so its item
     * is read from the slot's widget, as RuneLite's own menu swapper does.
     */
    private static int itemId(MenuEntry entry, int group)
    {
        int id = entry.getItemId();
        if (id > MenuFacts.NONE || group != InterfaceID.WORNITEMS) return Math.max(id, MenuFacts.NONE);
        Widget slot = entry.getWidget();
        Widget item = slot == null ? null : slot.getChild(WORN_ITEM_CHILD);
        return item == null ? MenuFacts.NONE : Math.max(item.getItemId(), MenuFacts.NONE);
    }

    /** Menu text without colour tags or our lock tag, in its own case. */
    static String text(String value)
    {
        if (value == null) return "";
        return WHITESPACE.matcher(Text.removeTags(value).replace(MenuFacts.LOCKED_MARK, ""))
            .replaceAll(" ").trim();
    }
}
