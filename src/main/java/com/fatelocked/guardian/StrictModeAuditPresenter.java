package com.fatelocked.guardian;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts privacy-safe audit records into the panel's recent-blocked lines. */
public final class StrictModeAuditPresenter
{
    private StrictModeAuditPresenter()
    {
    }

    public static List<String> recentPrevented(
        List<StrictModeAuditEntry> entries)
    {
        if (entries == null || entries.isEmpty())
        {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        for (StrictModeAuditEntry entry : entries)
        {
            if (entry != null && "BLOCKED".equals(entry.getOutcome()))
            {
                lines.add(entry.getTarget() + " \u2014 " + entry.getReason());
            }
        }
        return Collections.unmodifiableList(lines);
    }
}
