package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEventType;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class DetectedEvent
{
    /** The count for something that happens once, such as a quest: its name alone is its id. */
    public static final String ONCE = "";

    FateEventType type;
    String canonicalLabel;
    EventConfidence confidence;
    String detectorId;
    int detectorVersion;
    Map<String, Object> evidence;
    /**
     * What tells repeats of this name apart, such as a kill count, and so makes its id
     * (EventIds): ONCE for something that happens once. Null gives every occurrence its own id.
     */
    String count;
}
