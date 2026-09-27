package com.fatelocked;

import lombok.Builder;
import lombok.Value;

/**
 * What a clicked menu option is, as plain values (A9). Strict Mode's travel
 * matching reads these instead of the menu entry, so its fixtures can be
 * written down and cited. {@link MenuFactsReader} makes them on the client
 * thread; nothing here depends on RuneLite.
 *
 * <p>Text keeps its case, with colour tags and the plugin's own
 * {@link #LOCKED_MARK} removed. An id that doesn't apply is {@link #NONE}.
 */
@Value
@Builder
public class MenuFacts
{
    /**
     * What the plugin adds to an option the rules lock ({@code Terms.LOCKED_TAG}). Every
     * reader of menu text removes it first, so a tag never changes what an option matches.
     */
    public static final String LOCKED_MARK = "(Locked)";

    /** What the option was on, which decides the ids a travel row may match. */
    public enum Kind
    {
        /** An interface component: a spell, or an inventory or worn item. */
        WIDGET,
        /** A scene object, such as a fairy ring or a portal. */
        OBJECT,
        NPC,
        /** Anything else, which is never travel: walking, players, ground items, examine, ships. */
        OTHER
    }

    public static final int NONE = -1;

    /** A click with nothing to read: no entry at all. */
    public static final MenuFacts EMPTY = MenuFacts.builder().build();

    @Builder.Default
    Kind kind = Kind.OTHER;
    @Builder.Default
    String option = "";
    @Builder.Default
    String target = "";
    /** The interface the option is on, for {@link Kind#WIDGET}. */
    @Builder.Default
    int interfaceGroup = NONE;
    /** The item behind an inventory or worn item option. */
    @Builder.Default
    int itemId = NONE;
    /** The active spellbook (the SPELLBOOK varbit) for an option on the spellbook. */
    @Builder.Default
    int spellbook = NONE;
    @Builder.Default
    int npcId = NONE;
    /** The object's base id, as the menu gives it. */
    @Builder.Default
    int objectId = NONE;
    /** The world view the option belongs to, as the menu gives it. */
    @Builder.Default
    int worldViewId = NONE;
}
