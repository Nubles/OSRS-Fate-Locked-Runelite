package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Value;

/**
 * What a sync opened since the rules before it, for the in-game banner, the chat line and the
 * world map's glow: areas, banks, skill tiers and the tracker's other unlocks, by name, and
 * the chunks that are no longer locked. Only between two rule sets for the same run and the
 * same character, both of which apply to the character logged in, so starting up, another
 * run, a re-pairing or another character never announces anything.
 */
@Value
public class UnlockNews
{
    public static final UnlockNews NONE = new UnlockNews(Collections.emptyList(), Collections.emptySet());

    /** What opened, by the tracker's names, areas first. */
    List<String> names;
    /** The chunks that were locked and aren't now. */
    Set<CanonicalChunk> chunks;

    public boolean isEmpty()
    {
        return names.isEmpty() && chunks.isEmpty();
    }

    /** What opened between two decision services; {@link #NONE} unless both are the same run's, for its character. */
    public static UnlockNews between(DecisionService before, DecisionService after)
    {
        if (before == null || after == null
            || before.trust() != Trust.TRUSTED || after.trust() != Trust.TRUSTED
            || before.rules().isLegacy() || after.rules().isLegacy())
        {
            return NONE;
        }
        RuneliteRulesManifest was = before.rules().bundle().getRules();
        RuneliteRulesManifest now = after.rules().bundle().getRules();
        if (was == null || now == null || was.getRunId() == null || !was.getRunId().equals(now.getRunId())
            || !Objects.equals(was.getAccount(), now.getAccount()))
        {
            return NONE;
        }

        Set<String> names = new LinkedHashSet<>();
        for (String area : after.areas().keySet())
        {
            if (before.area(area).isLocked() && !after.area(area).isLocked())
            {
                names.add(area);
            }
        }
        if (was.getBanks() != null && now.getBanks() != null)
        {
            for (Map.Entry<String, RuneliteRulesManifest.Bank> bank : now.getBanks().entrySet())
            {
                RuneliteRulesManifest.Bank old = was.getBanks().get(bank.getKey());
                if (old != null && old.getStatus() == PermissionStatus.LOCKED
                    && bank.getValue().getStatus() != PermissionStatus.LOCKED && bank.getValue().getName() != null)
                {
                    names.add(bank.getValue().getName() + " bank");
                }
            }
        }
        RuneliteRulesManifest.Unlocks old = was.getUnlocks();
        RuneliteRulesManifest.Unlocks unlocks = now.getUnlocks();
        for (Map.Entry<String, Integer> skill : unlocks.getSkills().entrySet())
        {
            int tier = skill.getValue() == null ? 0 : skill.getValue();
            Integer oldTier = old.getSkills().get(skill.getKey());
            if (tier > (oldTier == null ? 0 : oldTier))
            {
                names.add(skill.getKey() + " tier " + tier);
            }
        }
        added(names, old.getMerchants(), unlocks.getMerchants());
        added(names, old.getBosses(), unlocks.getBosses());
        added(names, old.getMinigames(), unlocks.getMinigames());
        added(names, old.getMobility(), unlocks.getMobility());
        added(names, old.getArcana(), unlocks.getArcana());
        added(names, old.getGuilds(), unlocks.getGuilds());
        added(names, old.getFarming(), unlocks.getFarming());
        added(names, old.getSlayer(), unlocks.getSlayer());

        Set<CanonicalChunk> chunks = new LinkedHashSet<>();
        for (CanonicalChunk chunk : after.mappedChunks())
        {
            if (before.chunk(chunk).isLocked() && !after.chunk(chunk).isLocked())
            {
                chunks.add(chunk);
            }
        }
        return new UnlockNews(Collections.unmodifiableList(new ArrayList<>(names)),
            Collections.unmodifiableSet(chunks));
    }

    private static void added(Set<String> names, List<String> before, List<String> after)
    {
        for (String name : after)
        {
            if (name != null && !name.trim().isEmpty() && !before.contains(name))
            {
                names.add(name.trim());
            }
        }
    }

    /**
     * The banner's headline: the first thing that opened, and how many more. With only chunks,
     * as a Chunked roll gives, how many chunks.
     */
    public String headline()
    {
        if (names.isEmpty())
        {
            return chunks.size() == 1 ? "A new chunk" : chunks.size() + " new chunks";
        }
        return names.size() == 1 ? names.get(0) : names.get(0) + " and " + (names.size() - 1) + " more";
    }

    /** The chat line: everything that opened, by name. */
    public String line()
    {
        if (names.isEmpty())
        {
            return "Unlocked " + headline().toLowerCase(java.util.Locale.ROOT) + ".";
        }
        return "Unlocked " + String.join(", ", names) + ".";
    }
}
