package com.fatelocked.guardian.travel;

import com.fatelocked.MenuFacts;
import com.fatelocked.rules.TravelTable;
import net.runelite.api.gameval.InterfaceID;

import java.util.Collections;
import java.util.List;

/**
 * Travel by id (F2, G7, G13). A click is a trip only when the tracker's
 * travel table has one method its own ids match and one of that method's
 * options is the click's exact text. A spell is matched by the active
 * spellbook and the spell's name on the spellbook, an item by its id in the
 * inventory or worn items, and an object or NPC by its id. Each kind of
 * method matches only its own kind of click, so an object's id is never
 * read as an item's. Anything else, including a click two methods match,
 * isn't travel: Strict Mode leaves it alone.
 */
public final class IntentClassifier
{
    /** The spellbook varbit's values, in order: standard, ancient, lunar and arceuus. */
    private static final List<String> SPELLBOOKS = TravelTable.SPELLBOOKS;

    /** The method and option a click matches; null when it matches none, or more than one. */
    public TravelMatch classify(MenuFacts facts, TravelTable table)
    {
        if (facts == null || table == null) return null;
        TravelMatch match = null;
        for (TravelTable.Method method : candidates(facts, table))
        {
            TravelTable.Option option = method.option(facts.getOption());
            if (option == null) continue;
            if (match != null) return null;
            match = new TravelMatch(method, option);
        }
        return match;
    }

    private static List<TravelTable.Method> candidates(MenuFacts facts, TravelTable table)
    {
        switch (facts.getKind())
        {
            case WIDGET:
                if (facts.getInterfaceGroup() == InterfaceID.MAGIC_SPELLBOOK)
                {
                    return table.spell(spellbook(facts.getSpellbook()), facts.getTarget());
                }
                if ((facts.getInterfaceGroup() == InterfaceID.INVENTORY
                    || facts.getInterfaceGroup() == InterfaceID.WORNITEMS) && facts.getItemId() != MenuFacts.NONE)
                {
                    return table.byId(TravelTable.Match.ITEMS, facts.getItemId());
                }
                return Collections.emptyList();
            case OBJECT:
                return facts.getObjectId() == MenuFacts.NONE ? Collections.emptyList()
                    : table.byId(TravelTable.Match.OBJECTS, facts.getObjectId());
            case NPC:
                return facts.getNpcId() == MenuFacts.NONE ? Collections.emptyList()
                    : table.byId(TravelTable.Match.NPCS, facts.getNpcId());
            default:
                return Collections.emptyList();
        }
    }

    /** The table's name for the spellbook varbit's value; null for one it doesn't know. */
    static String spellbook(int varbit)
    {
        return varbit >= 0 && varbit < SPELLBOOKS.size() ? SPELLBOOKS.get(varbit) : null;
    }
}
