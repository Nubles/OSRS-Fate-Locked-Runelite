package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;

/**
 * The v1–3 root-field rules, kept only for file and clipboard imports from
 * older tracker versions. v4 bundles never reach this class: their answers
 * come from the tracker's own decisions in {@code rules}.
 */
final class LegacyRules
{
    private final FateLockedBundle bundle;

    LegacyRules(FateLockedBundle bundle)
    {
        this.bundle = bundle;
    }

    Decision chunk(CanonicalChunk chunk)
    {
        switch (bundle.lockStateAt(chunk))
        {
            case UNLOCKED:
                return new Decision(PermissionStatus.ALLOWED, bundle.labelAt(chunk), null,
                    Decision.Source.LEGACY);
            case LOCKED:
                return new Decision(PermissionStatus.LOCKED, bundle.labelAt(chunk), null,
                    Decision.Source.LEGACY);
            default:
                return new Decision(PermissionStatus.UNKNOWN, null, null, Decision.Source.UNMAPPED);
        }
    }

    /** The bank at a chunk: only bank-locked runs lock one, and only until it is rolled. */
    Decision bankAt(CanonicalChunk chunk)
    {
        String label = bundle.labelAt(chunk);
        String name = label == null ? "This bank" : label + " bank";
        return bundle.isBankUnlocked(chunk)
            ? new Decision(PermissionStatus.ALLOWED, name, null, Decision.Source.LEGACY)
            : new Decision(PermissionStatus.LOCKED, name, null, Decision.Source.LEGACY);
    }
}
