package com.fatelocked.events;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** An event's evidence as it was recorded, after a trip through a file. */
final class Evidence
{
    /** Integers beyond this aren't exact as doubles. */
    private static final double EXACT = 9_007_199_254_740_992d;

    private Evidence()
    {
    }

    /**
     * Gson reads every number back as a double, so a level of 71 would return as 71.0: give back
     * whole numbers as whole numbers, as they were recorded.
     */
    static FateEvent withWholeNumbers(FateEvent event)
    {
        Map<String, Object> evidence = event.getEvidence();
        if (evidence == null)
        {
            return event.toBuilder().evidence(Collections.<String, Object>emptyMap()).build();
        }
        Map<String, Object> fixed = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<String, Object> entry : evidence.entrySet())
        {
            Object value = entry.getValue();
            if (value instanceof Double && isWhole((Double) value))
            {
                fixed.put(entry.getKey(), ((Double) value).longValue());
                changed = true;
            }
            else
            {
                fixed.put(entry.getKey(), value);
            }
        }
        return changed ? event.toBuilder().evidence(Collections.unmodifiableMap(fixed)).build() : event;
    }

    private static boolean isWhole(double value)
    {
        return value == Math.rint(value) && Math.abs(value) <= EXACT;
    }
}
