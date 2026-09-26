package com.fatelocked.rules;

import lombok.Value;

/** An item's equipment tier and the tier its slot is unlocked to (B12). */
@Value
public class ItemTier
{
    String slot;
    int tier;
    int unlocked;

    /** Above what its slot is unlocked to. */
    public boolean isOver()
    {
        return tier > unlocked;
    }
}
