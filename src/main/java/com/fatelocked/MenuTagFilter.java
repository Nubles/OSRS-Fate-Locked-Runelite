package com.fatelocked;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.MenuAction;

/**
 * The menu options a " (Locked)" tag could ever go on, by their type alone (F1, A9). The tag
 * reads two things: travel the tracker's table matches by id, on an item or a spell, an object
 * or an NPC; and the chunk something stands in, an NPC, an object or an item on the ground,
 * whether its own option or an item used, or a spell cast, on it.
 * Walk here, Cancel, Examine, other players and RuneLite's own options have neither, so the menu
 * passes them over before any text is read, and they cost nothing.
 */
final class MenuTagFilter
{
    private static final Set<MenuAction> TAGGABLE = EnumSet.of(
        MenuAction.CC_OP,
        MenuAction.CC_OP_LOW_PRIORITY,
        MenuAction.GAME_OBJECT_FIRST_OPTION,
        MenuAction.GAME_OBJECT_SECOND_OPTION,
        MenuAction.GAME_OBJECT_THIRD_OPTION,
        MenuAction.GAME_OBJECT_FOURTH_OPTION,
        MenuAction.GAME_OBJECT_FIFTH_OPTION,
        MenuAction.GROUND_ITEM_FIRST_OPTION,
        MenuAction.GROUND_ITEM_SECOND_OPTION,
        MenuAction.GROUND_ITEM_THIRD_OPTION,
        MenuAction.GROUND_ITEM_FOURTH_OPTION,
        MenuAction.GROUND_ITEM_FIFTH_OPTION,
        MenuAction.NPC_FIRST_OPTION,
        MenuAction.NPC_SECOND_OPTION,
        MenuAction.NPC_THIRD_OPTION,
        MenuAction.NPC_FOURTH_OPTION,
        MenuAction.NPC_FIFTH_OPTION,
        // An item used, or a spell cast, on an NPC: tagged by where the NPC stands.
        MenuAction.WIDGET_TARGET_ON_NPC,
        // An item used on an object, or a spell cast on an item on the ground: tagged by the
        // tile, as the object's and the ground item's own options are.
        MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
        MenuAction.WIDGET_TARGET_ON_GROUND_ITEM);

    private MenuTagFilter()
    {
    }

    /** Whether an option of this type could be tagged; one with no type never is. */
    static boolean mayTag(MenuAction type)
    {
        return TAGGABLE.contains(type);
    }
}
