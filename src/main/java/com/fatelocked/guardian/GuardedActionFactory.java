package com.fatelocked.guardian;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.ChunkLocator;
import com.fatelocked.MenuFacts;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.client.util.Text;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a menu option is on, for the chunk tags. Travel isn't read here: the
 * tracker's travel table matches it by id (F4).
 */
public final class GuardedActionFactory
{
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /** What a menu option is on, placed by the chunk locator (instances and boats, B14). */
    public GuardedAction from(MenuEntry entry, ChunkLocator locator)
    {
        if (entry == null) return unknown("", "");
        return from(entry, normalize(entry.getOption()), normalize(entry.getTarget()), locator);
    }

    /**
     * The same, from the option's facts, whose text is already read without tags (F1): the
     * menu tag reads each entry's text once.
     */
    public GuardedAction from(MenuFacts facts, MenuEntry entry, ChunkLocator locator)
    {
        return from(entry, facts.getOption().toLowerCase(Locale.ROOT), facts.getTarget().toLowerCase(Locale.ROOT),
            locator);
    }

    private GuardedAction from(MenuEntry entry, String option, String target, ChunkLocator locator)
    {
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

    /** Menu text as {@link MenuFacts} holds it, in lower case. */
    private static String normalize(String value)
    {
        if (value == null) return "";
        return WHITESPACE.matcher(Text.removeTags(value).replace(MenuFacts.LOCKED_MARK, ""))
            .replaceAll(" ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
