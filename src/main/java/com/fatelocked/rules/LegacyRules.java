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

    /**
     * An item's tier from an older export's item tiers, against the tier the
     * slot it is worn in is unlocked to; null when the export doesn't rate it.
     */
    ItemTier itemTier(int itemId, String wornSlot)
    {
        Integer tier = bundle.getItemTiers().get(String.valueOf(itemId));
        FateLockedBundle.RunState state = bundle.getState();
        if (tier == null || wornSlot == null || state == null || state.getEquipment() == null) return null;
        return new ItemTier(wornSlot, tier, state.getEquipment().getOrDefault(wornSlot, 0));
    }

    /** An older export's nearest usable bank, from its points of interest. */
    FateLockedBundle.Nearest nearestBank(CanonicalChunk from)
    {
        return bundle.nearestUsableBank(from);
    }

    /** An older export's nearest shop in an unlocked area. */
    FateLockedBundle.Nearest nearestShop(CanonicalChunk from)
    {
        return bundle.nearestUsableShop(from);
    }

    boolean hasNearestData()
    {
        return bundle.hasNearestData();
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
