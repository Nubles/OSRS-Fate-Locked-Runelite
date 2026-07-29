package com.fatelocked.rules;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Display-only certificate summary exported by the Fate Locked app.
 * RuneLite intentionally does not evaluate or replay the underlying proof.
 */
@Getter
public final class RuneProofSummary
{
    public enum Status
    {
        OBTAINABLE,
        OBTAINABLE_RNG,
        BLOCKED,
        IMPOSSIBLE,
        UNKNOWN
    }

    private String goalId;
    private String goalLabel;
    private Status status;
    private String explanation;
    private List<String> routeLabels;
    private List<String> blockerLabels;
    private List<String> unavoidableBlockerLabels;
    private String proofHash;
    private String sourceVersion;
    private long runRevision;

    public RuneProofSummary normalized()
    {
        RuneProofSummary copy = new RuneProofSummary();
        copy.goalId = goalId;
        copy.goalLabel = goalLabel;
        copy.status = status == null ? Status.UNKNOWN : status;
        copy.explanation = explanation;
        copy.routeLabels = immutableSortedLabels(routeLabels);
        copy.blockerLabels = immutableSortedLabels(blockerLabels);
        copy.unavoidableBlockerLabels = immutableSortedLabels(unavoidableBlockerLabels);
        copy.proofHash = proofHash;
        copy.sourceVersion = sourceVersion;
        copy.runRevision = Math.max(0, runRevision);
        return copy;
    }

    public boolean isPositive()
    {
        return status == Status.OBTAINABLE || status == Status.OBTAINABLE_RNG;
    }

    public boolean isUnverified()
    {
        return isPositive() && (proofHash == null || proofHash.trim().isEmpty());
    }

    private static List<String> immutableSortedLabels(List<String> labels)
    {
        List<String> copy = new ArrayList<>();
        if (labels != null)
        {
            for (String label : labels)
            {
                if (label != null)
                {
                    copy.add(label);
                }
            }
        }
        Collections.sort(copy);
        return Collections.unmodifiableList(copy);
    }
}