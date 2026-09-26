package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.FateLockedBundle;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The one reader of the tracker's rules. Every surface (sidebar, HUD,
 * overlays, alerts, tags, warnings and Strict Mode) asks it, so they can't
 * disagree. It only looks answers up: missing data stays Unknown and a lock
 * is never inferred from a gap.
 *
 * <p>Immutable, so it can be published to overlays through a volatile
 * field. A new one is made when the rules or the logged-in character change.
 */
public final class DecisionService
{
    private static final String WRONG_ACCOUNT = "Wrong account";

    private final RulesSnapshot rules;
    private final Trust trust;
    private final boolean bound;

    private DecisionService(RulesSnapshot rules, Trust trust, boolean bound)
    {
        this.rules = rules;
        this.trust = trust;
        this.bound = bound;
    }

    /**
     * @param boundAccount    the account the rules are for, normalised the
     *                        tracker's way; null or empty when bound to no one
     * @param loggedInAccount the logged-in character, normalised the same
     *                        way; null or empty when nobody is logged in
     */
    public static DecisionService create(RulesSnapshot rules, String boundAccount, String loggedInAccount)
    {
        RulesSnapshot snapshot = rules == null ? RulesSnapshot.empty() : rules;
        boolean bound = boundAccount != null && !boundAccount.isEmpty();
        Trust trust;
        if (snapshot.isEmpty()) trust = Trust.NO_RULES;
        else if (!bound) trust = Trust.TRUSTED;
        else if (loggedInAccount == null || loggedInAccount.isEmpty()) trust = Trust.LOGGED_OUT;
        else trust = boundAccount.equals(loggedInAccount) ? Trust.TRUSTED : Trust.WRONG_CHARACTER;
        return new DecisionService(snapshot, trust, bound);
    }

    public Trust trust()
    {
        return trust;
    }

    /** Whether the rules name an account. Strict Mode acts only on bound rules. */
    public boolean isBound()
    {
        return bound;
    }

    /** Why the rules don't apply right now; null when they do. */
    public String trustReason()
    {
        switch (trust)
        {
            case NO_RULES: return "No tracker rules are loaded";
            case LOGGED_OUT: return "Not logged in";
            case WRONG_CHARACTER: return WRONG_ACCOUNT;
            default: return null;
        }
    }

    public RulesSnapshot rules()
    {
        return rules;
    }

    /** Whether a chunk may be entered. */
    public Decision chunk(CanonicalChunk chunk)
    {
        Decision gate = gate();
        if (gate != null) return gate;
        if (chunk == null) return unmapped(null);
        if (rules.isLegacy()) return rules.legacy().chunk(chunk);
        Optional<ChunkPermissionSnapshot> snapshot = rules.bundle().permissionsAt(chunk);
        if (!snapshot.isPresent()) return unmapped(null);
        ChunkPermissionSnapshot value = snapshot.get();
        return new Decision(value.getEntry(),
            value.getName() == null ? value.getChunkKey() : value.getName(),
            null, Decision.Source.CHUNK);
    }

    /** The tracker's rows for a chunk, for the sidebar; empty unless the rules apply. */
    public Optional<ChunkPermissionSnapshot> details(CanonicalChunk chunk)
    {
        if (trust != Trust.TRUSTED || rules.isLegacy() || chunk == null) return Optional.empty();
        return rules.bundle().permissionsAt(chunk);
    }

    /**
     * A row inside a chunk's rules, matched by kind and name (either may be
     * empty to match any). A locked chunk locks everything in it.
     */
    public Decision target(CanonicalChunk chunk, String targetKind, String targetName)
    {
        Decision gate = gate();
        if (gate != null) return gate;
        if (rules.isLegacy() || chunk == null) return unmapped(targetName);
        Optional<ChunkPermissionSnapshot> optional = rules.bundle().permissionsAt(chunk);
        if (!optional.isPresent()) return unmapped(targetName);
        ChunkPermissionSnapshot snapshot = optional.get();
        String kind = normalizeTarget(targetKind);
        String name = normalizeTarget(targetName);
        for (Map.Entry<String, List<ChunkPermissionRow>> category : snapshot.getCategories().entrySet())
        {
            for (ChunkPermissionRow row : category.getValue())
            {
                boolean kindMatches = kind.isEmpty() || kind.equals(normalizeTarget(row.getTargetKind()));
                boolean nameMatches = name.isEmpty() || name.equals(normalizeTarget(row.getName()));
                if (!kindMatches || !nameMatches) continue;
                PermissionStatus status = snapshot.getEntry() == PermissionStatus.LOCKED
                    ? PermissionStatus.LOCKED : row.getStatus();
                return new Decision(status, row.getName(), row.getDetail(), Decision.Source.ROW);
            }
        }
        return unmapped(targetName);
    }

    /**
     * What a chunk is called, on any character: names aren't decisions. The
     * tracker's name for it, else the area name older exports also carry
     * ("Falador · Asgarnia"); null when the rules name neither.
     */
    public String chunkName(CanonicalChunk chunk)
    {
        if (chunk == null) return null;
        String name = snapshotAt(chunk).map(ChunkPermissionSnapshot::getName).orElse(null);
        return isBlank(name) ? rules.areaLabel(chunk) : name;
    }

    /** The region (continent) a chunk is in, on any character; null when the rules don't say. */
    public String regionName(CanonicalChunk chunk)
    {
        if (chunk == null) return null;
        String region = snapshotAt(chunk).map(ChunkPermissionSnapshot::getRegion).orElse(null);
        return isBlank(region) ? rules.regionAt(chunk) : region;
    }

    /**
     * An older export's content lists for a chunk, for the sidebar; empty
     * unless those rules apply to this character.
     */
    public Map<String, List<String>> legacyContent(CanonicalChunk chunk)
    {
        if (trust != Trust.TRUSTED || !rules.isLegacy() || chunk == null) return Collections.emptyMap();
        return rules.legacyContent(chunk);
    }

    /** The bank at a chunk. */
    public Decision bankAt(CanonicalChunk chunk)
    {
        Decision gate = gate();
        if (gate != null) return gate;
        if (rules.isLegacy()) return chunk == null ? unmapped(null) : rules.legacy().bankAt(chunk);
        return target(chunk, "BANK", "");
    }

    /** A mobility unlock, such as Fairy Rings, by its tracker name. */
    public Decision mobility(String unlockId)
    {
        Decision gate = gate();
        if (gate != null) return gate;
        if (rules.isLegacy() || unlockId == null) return unmapped(unlockId);
        RuneliteRulesManifest manifest = rules.bundle().getRules();
        if (!containsName(manifest.getKnownMobility(), unlockId)) return unmapped(unlockId);
        boolean unlocked = containsName(manifest.getUnlocks().getMobility(), unlockId);
        return unlocked
            ? new Decision(PermissionStatus.ALLOWED, unlockId, null, Decision.Source.MOBILITY)
            : new Decision(PermissionStatus.LOCKED, unlockId, unlockId + " is not unlocked",
                Decision.Source.MOBILITY);
    }

    /** Whether an item's equipment tier is unlocked for its slot. */
    public Decision item(int itemId)
    {
        Decision gate = gate();
        if (gate != null) return gate;
        String label = "Item " + itemId;
        if (rules.isLegacy()) return unmapped(label);
        RuneliteRulesManifest manifest = rules.bundle().getRules();
        RuneliteRulesManifest.ItemRule item = manifest.getItemRules().get(String.valueOf(itemId));
        if (item == null || item.getSlot() == null || item.getSlot().trim().isEmpty()) return unmapped(label);
        Integer unlocked = manifest.getUnlocks().getEquipment().get(item.getSlot());
        if (unlocked == null) return unmapped(label);
        PermissionStatus status = item.getTier() > unlocked ? PermissionStatus.LOCKED : PermissionStatus.ALLOWED;
        String reason = "T" + item.getTier() + "; " + item.getSlot() + " is unlocked to T" + unlocked;
        return new Decision(status, label, reason, Decision.Source.ITEM);
    }

    private Optional<ChunkPermissionSnapshot> snapshotAt(CanonicalChunk chunk)
    {
        return rules.isEmpty() || rules.isLegacy() ? Optional.empty() : rules.bundle().permissionsAt(chunk);
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    /** Null when the rules apply; otherwise the Unknown answer every question gets. */
    private Decision gate()
    {
        switch (trust)
        {
            case TRUSTED:
                return null;
            case NO_RULES:
                return new Decision(PermissionStatus.UNKNOWN, null, trustReason(), Decision.Source.TRUST);
            default:
                return new Decision(PermissionStatus.UNKNOWN, WRONG_ACCOUNT, WRONG_ACCOUNT, Decision.Source.TRUST);
        }
    }

    private static Decision unmapped(String label)
    {
        return new Decision(PermissionStatus.UNKNOWN,
            label == null || label.trim().isEmpty() ? null : label, null, Decision.Source.UNMAPPED);
    }

    private static boolean containsName(List<String> values, String name)
    {
        String expected = normalizeName(name);
        if (expected.isEmpty()) return false;
        for (String value : values)
        {
            if (expected.equals(normalizeName(value))) return true;
        }
        return false;
    }

    private static String normalizeName(String value)
    {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Menu text as rows name it: no colour tags, no "(LOCKED)" tag, any case. */
    private static String normalizeTarget(String value)
    {
        if (value == null) return "";
        return value.replaceAll("<[^>]+>", "").replace("(LOCKED)", "").trim().toLowerCase(Locale.ROOT);
    }

    /** The bundle behind these rules, for the few adapters that still render from it. */
    FateLockedBundle bundle()
    {
        return rules.bundle();
    }
}
