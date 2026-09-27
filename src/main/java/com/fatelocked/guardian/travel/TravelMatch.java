package com.fatelocked.guardian.travel;

import com.fatelocked.rules.TravelTable;
import lombok.Value;

/** A click the travel table matches by id (F2): its method, and the option clicked. */
@Value
public class TravelMatch
{
    TravelTable.Method method;
    TravelTable.Option option;
}
