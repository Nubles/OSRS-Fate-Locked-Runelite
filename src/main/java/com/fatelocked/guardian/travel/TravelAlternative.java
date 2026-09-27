package com.fatelocked.guardian.travel;

import com.fatelocked.CanonicalChunk;
import lombok.Value;

/** Another way to go, which the notice suggests: display only (F7). */
@Value
public class TravelAlternative
{
    /** The table's method and option: "tablet:lumbridge-teleport|Break". */
    String id;
    /** "Lumbridge teleport", or "Amulet of glory to Edgeville". */
    String label;
    CanonicalChunk destination;
}
