package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import lombok.Value;

@Value
public class TravelAction
{
    public enum Family
    {
        WALK, BOUNDARY_OBJECT, SPELL_OR_ITEM, FAIRY_RING, SPIRIT_TREE,
        GNOME_GLIDER, CHARTER_SHIP, MINE_CART, MAGIC_CARPET, BALLOON,
        EAGLE, MINIGAME_TELEPORT, QUETZAL, OTHER_TRANSPORT, UNKNOWN
    }

    public enum Confidence { EXACT, UNKNOWN }

    Family family;
    String methodId;
    String label;
    CanonicalChunk origin;
    CanonicalChunk destination;
    String requiredUnlock;
    Confidence confidence;
}
