package com.fatelocked.guardian.travel;

import com.fatelocked.rules.PermissionStatus;
import lombok.Value;

@Value
public class TravelDecision
{
    PermissionStatus status;
    String label;
    String reason;
}