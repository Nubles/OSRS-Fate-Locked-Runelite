package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.ChunkLocator;
import com.fatelocked.MenuFacts;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.client.util.Text;

import java.util.Locale;

/**
 * What a menu option is on, for the chunk tags. Travel isn't read here: the
 * tracker's travel table matches it by id (F4).
 */
public final class GuardedActionFactory
{
    /** What a menu option is on, placed by the chunk locator (instances and boats, B14). */
    public GuardedAction from(MenuEntry entry, ChunkLocator locator)
    {
        if (entry == null) return unknown("", "");
        String option = normalize(entry.getOption());
        String target = normalize(entry.getTarget());
        MenuAction type = entry.getType();
        if (option.startsWith("examine")) return unknown(option, target);
        if (type == MenuAction.WALK)
        {
            // "Walk here" carries viewport pixel coordinates, not a scene
            // tile: it has no knowable destination, so it is never tagged.
            return unknown(option, target);
        }

        if (option.equals("wear") || option.equals("wield") || option.equals("equip"))
        {
            int id = entry.getItemId();
            return new GuardedAction(
                GuardedAction.Kind.EQUIPMENT, option, target, null,
                id < 0 ? null : id);
        }

        NPC npc = entry.getNpc();
        if (npc != null)
        {
            GuardedAction.Kind kind = isBankOption(option)
                ? GuardedAction.Kind.BANK : GuardedAction.Kind.NPC;
            return new GuardedAction(
                kind, option, target, locator == null ? null : locator.actor(npc), null);
        }

        CanonicalChunk tile = locator == null ? null : locator.menuTarget(entry);
        if (tile != null)
        {
            GuardedAction.Kind kind = isBankOption(option)
                ? GuardedAction.Kind.BANK : GuardedAction.Kind.OBJECT;
            return new GuardedAction(kind, option, target, tile, null);
        }
        return unknown(option, target);
    }

    private static boolean isBankOption(String option)
    {
        return option.equals("bank") || option.equals("collect")
            || option.equals("deposit") || option.equals("use-bank");
    }

    private static GuardedAction unknown(String option, String target)
    {
        return new GuardedAction(
            GuardedAction.Kind.UNKNOWN, option, target, null, null);
    }

    private static String normalize(String value)
    {
        if (value == null) return "";
        return Text.removeTags(value)
            .replace(MenuFacts.LOCKED_MARK, "")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
