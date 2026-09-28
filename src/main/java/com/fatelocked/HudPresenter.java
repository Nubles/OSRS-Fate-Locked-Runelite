package com.fatelocked;

import com.fatelocked.guardian.StrictModeStatusView;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.Trust;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Terms;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * What the HUD says (U8, E6), from the rules' decisions for where the player stands.
 *
 * <ul>
 * <li>Compact: the place, its status and why, Strict Mode, the nearest bank and shop, and any
 *     rule warnings.</li>
 * <li>Detailed adds progress, the run's Keys and Fate Points, and what the place holds, as the
 *     sidebar's Here card lists it. It replaces the separate content box.</li>
 * </ul>
 *
 * <p>Another character sees only where they are and that the run isn't theirs, in either mode.
 * Words, never marks: the overlay fonts have no ✓ or ⚠.
 */
final class HudPresenter
{
    /** Everything the HUD reads, gathered on the client thread. */
    @Value
    @Builder
    static class Facts
    {
        FateLockedConfig.HudMode mode;
        DecisionService decisions;
        /** The player's rules chunk; null when it can't be known. */
        CanonicalChunk chunk;
        /** Strict Mode's status; null before it is first worked out. */
        StrictModeStatusView strict;
        /** The nearest bank and shop the rules allow; null for none. */
        FateLockedBundle.Nearest bank;
        FateLockedBundle.Nearest shop;
        /** The locked Slayer task, or null. */
        String slayerWarning;
        /** The worn slots above their unlocked tier, or null. */
        String overTier;
        /** The run's Keys and Fate Points; null when the rules carry none. */
        FateLockedBundle.RunState run;
        /** What the place holds, for Detailed; null for nothing. */
        HereModel here;
    }

    private HudPresenter()
    {
    }

    static HudModel present(Facts facts)
    {
        DecisionService decisions = facts.getDecisions();
        Trust trust = decisions.trust();
        if (facts.getMode() == FateLockedConfig.HudMode.OFF
            || trust == Trust.NO_RULES || trust == Trust.LOGGED_OUT)
        {
            return HudModel.NONE;
        }
        boolean detailed = facts.getMode() == FateLockedConfig.HudMode.DETAILED;
        List<HudModel.Line> lines = new ArrayList<>();
        CanonicalChunk chunk = facts.getChunk();
        if (chunk != null)
        {
            String area = decisions.areaName(chunk);
            lines.add(new HudModel.Line("Here",
                shorten(area != null ? area : "Chunk (" + chunk.getCx() + ", " + chunk.getCy() + ")", 22), null));
        }
        if (trust != Trust.TRUSTED)
        {
            // Another character's rules say nothing about this one: two short lines, in either mode.
            lines.add(new HudModel.Line("Status", Terms.DIFFERENT_CHARACTER, Palette.Tone.BAD));
            return new HudModel(lines, false);
        }
        if (chunk != null)
        {
            Decision decision = decisions.chunk(chunk);
            Palette.Tone tone = Palette.tone(decision.getStatus());
            lines.add(new HudModel.Line("Status", Terms.place(decision.getStatus()), tone));
            boolean why = decision.getStatus() == PermissionStatus.LOCKED
                || decision.getStatus() == PermissionStatus.NOT_READY;
            if (why && decision.getReason() != null)
            {
                lines.add(new HudModel.Line("Why", shorten(decision.getReason(), 22), tone));
            }
        }
        StrictModeStatusView strict = facts.getStrict();
        if (strict != null && strict.isShownOnHud())
        {
            lines.add(new HudModel.Line(Terms.STRICT_MODE, strict.getText(),
                strict.getTone() == StrictModeStatusView.Tone.ACTIVE ? Palette.Tone.GOOD : Palette.Tone.PENDING));
        }
        if (chunk != null && decisions.hasNearestData())
        {
            lines.add(nearest("Bank", facts.getBank(), chunk, decisions));
            lines.add(nearest("Shop", facts.getShop(), chunk, decisions));
        }
        if (facts.getSlayerWarning() != null)
        {
            lines.add(new HudModel.Line("Slayer", shorten(facts.getSlayerWarning(), 18), Palette.Tone.BAD));
        }
        if (facts.getOverTier() != null)
        {
            lines.add(new HudModel.Line("Over-tier", shorten(facts.getOverTier(), 20), Palette.Tone.BAD));
        }
        if (detailed)
        {
            detail(lines, facts, decisions);
        }
        // Nothing to say, where the player's chunk can't be found: no HUD.
        return lines.isEmpty() ? HudModel.NONE : new HudModel(lines, detailed);
    }

    /** Detailed's lines: progress, the run, and what the place holds. */
    private static void detail(List<HudModel.Line> lines, Facts facts, DecisionService decisions)
    {
        String progress = ProgressText.hudLine(decisions.progress());
        if (progress != null)
        {
            lines.add(new HudModel.Line(Terms.UNLOCKED, progress, null));
        }
        FateLockedBundle.RunState run = facts.getRun();
        if (run != null)
        {
            lines.add(new HudModel.Line(Terms.KEYS, String.valueOf(run.getKeys()), null));
            if (run.getSpecialKeys() > 0)
            {
                lines.add(new HudModel.Line(Terms.OMNI_KEYS, String.valueOf(run.getSpecialKeys()), null));
            }
            if (run.getChaosKeys() > 0)
            {
                lines.add(new HudModel.Line(Terms.CHAOS_KEYS, String.valueOf(run.getChaosKeys()), null));
            }
            lines.add(new HudModel.Line(Terms.FATE_POINTS, String.valueOf(run.getFatePoints()), null));
            String ritual = Terms.ritual(run.getActiveBuff());
            if (ritual != null)
            {
                lines.add(new HudModel.Line("Ritual", shorten(ritual, 20), Palette.Tone.GOOD));
            }
            if (run.getPinnedGoals() != null && !run.getPinnedGoals().isEmpty())
            {
                lines.add(new HudModel.Line("Goal", shorten(run.getPinnedGoals().get(0), 20), null));
            }
        }
        HereModel here = facts.getHere();
        if (here != null)
        {
            for (HereModel.Group group : here.getGroups())
            {
                lines.add(HudModel.Line.heading(group.getTitle()));
                for (HereModel.Row row : group.shown(false))
                {
                    lines.add(new HudModel.Line(shorten(row.getName(), 18), row.getWord(), row.getTone()));
                }
                if (group.hidden() > 0)
                {
                    lines.add(new HudModel.Line("+" + group.hidden() + " more", null, Palette.Tone.NEUTRAL));
                }
            }
        }
    }

    /** One "Bank" or "Shop" line: here, the nearest one's area, distance and way, or none. */
    private static HudModel.Line nearest(String label, FateLockedBundle.Nearest near, CanonicalChunk from,
        DecisionService decisions)
    {
        if (near == null)
        {
            return new HudModel.Line(label, "None unlocked", Palette.Tone.BAD);
        }
        if (near.getDistanceChunks() == 0)
        {
            return new HudModel.Line(label, "Here", Palette.Tone.GOOD);
        }
        String area = decisions.areaName(near.getChunk());
        String name = area == null
            ? "(" + near.getChunk().getCx() + ", " + near.getChunk().getCy() + ")"
            : area.split(" · ")[0];
        // From inside an interior, the way is from its entrance; none when the bank is right there.
        CanonicalChunk origin = decisions.surfaceOf(from);
        int dx = near.getChunk().getCx() - origin.getCx();
        int dy = near.getChunk().getCy() - origin.getCy();
        String way = dx == 0 && dy == 0 ? "" : " " + compass(dx, dy);
        return new HudModel.Line(label, shorten(name, 13) + " · " + near.getDistanceChunks() + way, null);
    }

    /**
     * The 8-way compass point for a chunk step; a 2:1 dominant axis counts as its cardinal
     * (5 east and 1 north is "E"; 5 east and 4 north is "NE"). World y grows north.
     */
    static String compass(int dx, int dy)
    {
        String ns = dy > 0 ? "N" : "S";
        String ew = dx > 0 ? "E" : "W";
        if (dy == 0 || Math.abs(dx) >= 2 * Math.abs(dy)) return ew;
        if (dx == 0 || Math.abs(dy) >= 2 * Math.abs(dx)) return ns;
        return ns + ew;
    }

    /** Text cut to fit one HUD line, ending in an ellipsis when cut. */
    static String shorten(String text, int max)
    {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
