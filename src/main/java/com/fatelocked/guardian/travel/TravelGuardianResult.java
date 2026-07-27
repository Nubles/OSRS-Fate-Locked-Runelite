package com.fatelocked.guardian.travel;

import com.fatelocked.guardian.GuardResult;
import lombok.Value;

@Value
public class TravelGuardianResult
{
    TravelAction action;
    TravelDecision decision;
    TravelAlternative alternative;
    GuardResult guardResult;
    boolean writeChat;
    boolean writeBlockedAudit;
    boolean writePausedAudit;
}
