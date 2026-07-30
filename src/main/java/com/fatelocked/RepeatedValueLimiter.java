package com.fatelocked;

import java.util.Objects;

final class RepeatedValueLimiter
{
    private final long windowMillis;
    private String lastValue;
    private long lastReportedAt;
    private boolean hasReported;

    RepeatedValueLimiter(long windowMillis)
    {
        this.windowMillis = windowMillis;
    }

    synchronized boolean shouldReport(String value, long nowMillis)
    {
        if (!hasReported
            || !Objects.equals(lastValue, value)
            || nowMillis - lastReportedAt >= windowMillis)
        {
            hasReported = true;
            lastValue = value;
            lastReportedAt = nowMillis;
            return true;
        }
        return false;
    }
}
