package com.fatelocked;

import com.fatelocked.events.FateEventType;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.ConnectionModel;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.HerePresenter;
import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.sidebar.RollInboxModel.Row;
import com.fatelocked.sidebar.RunModel;
import com.fatelocked.sidebar.Sidebar;
import com.fatelocked.sidebar.StatusCardModel;
import com.fatelocked.sidebar.StrictModeModel;
import com.fatelocked.sidebar.StrictModeSectionPresenter;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Palette.Tone;
import com.fatelocked.ui.Section;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.JComponent;

/**
 * The new sidebar in each state of the UX report's status table, for design review
 * and the web guide. The run is the golden bundles' fictional "Iron Example".
 */
final class SidebarShots
{
    private static final Gson GSON = new Gson();
    static final String CHARACTER = "Iron Example";

    private SidebarShots()
    {
    }

    static Map<String, Callable<JComponent>> all(IconSource icons)
    {
        Map<String, Callable<JComponent>> shots = new LinkedHashMap<>();
        shots.put("new-01-first-run", () -> sidebar(icons,
            StatusCardModel.of(Tone.NEUTRAL, "Not connected",
                "Connect the tracker to load your run's rules.")
                .withActions(CardAction.CONNECT, CardAction.USE_BACKUP),
            HereModel.message("Log in to see the place you're standing in."),
            StrictModeModel.off(), null, disconnected()));
        shots.put("new-02-waiting", () -> sidebar(icons,
            StatusCardModel.of(Tone.PENDING, "Waiting for confirmation",
                "Confirm this profile in the browser tab RuneLite opened. RuneLite checks every few seconds.")
                .withActions(CardAction.OPEN_PAGE_AGAIN, CardAction.CANCEL_PAIRING),
            HereModel.message("Log in to see the place you're standing in."),
            StrictModeModel.off(), null, disconnected()));
        shots.put("new-03-lumbridge", () -> sidebar(icons, upToDate(),
            here("vanilla-mid", 50, 50, true), active(), run(), connected()));
        shots.put("new-04-falador-locked", () -> sidebar(icons, upToDate(),
            here("vanilla-fresh", 46, 52, true), active(), run(), connected()));
        shots.put("new-05-stale", () -> sidebar(icons,
            StatusCardModel.of(Tone.PENDING, "Rules may be out of date",
                "Last synced 32 min ago. RuneLite couldn't reach the tracker; next check at 12:35."
                    + " Strict Mode is inactive until the rules refresh.")
                .withActions(CardAction.CHECK_NOW, null),
            here("vanilla-mid", 50, 50, true),
            new StrictModeModel(true, "Inactive", Tone.PENDING,
                "Not blocking anything: the rules are more than 15 minutes old.", null,
                Collections.emptyList()), run(), connected()));
        shots.put("new-06-expired", () -> sidebar(icons,
            StatusCardModel.of(Tone.PENDING, "Tracker copy expired",
                "The tracker hasn't sent your rules in the last 24 hours. Open the web tracker to send them"
                    + " again.")
                .withActions(CardAction.OPEN_TRACKER, null),
            here("vanilla-mid", 50, 50, true), active(), run(), connected()));
        shots.put("new-07-backup", () -> sidebar(icons,
            StatusCardModel.of(Tone.NEUTRAL, "Using a backup",
                "Rules from the clipboard, exported at 11:58. Strict Mode works for 15 minutes after the"
                    + " tracker exports them.")
                .withActions(null, CardAction.CONNECT),
            here("vanilla-mid", 50, 50, true), StrictModeModel.off(), run(), disconnected()));
        shots.put("new-08-different-character", () -> sidebar(icons,
            StatusCardModel.of(Tone.BAD, "Different character",
                "This run belongs to " + CHARACTER + ", and you're logged in as Zezima. Warnings and Strict Mode"
                    + " are off."),
            here("vanilla-mid", 50, 50, false),
            new StrictModeModel(true, "Inactive", Tone.PENDING,
                "Not blocking anything: the rules are for " + CHARACTER + ".", null, Collections.emptyList()),
            run(), connected()));
        shots.put("new-09-paused", () -> sidebar(icons, upToDate(),
            here("vanilla-mid", 50, 50, true),
            new StrictModeModel(true, "Paused · 42s", Tone.PENDING, null, CardAction.RESUME_STRICT_MODE,
                Arrays.asList("Varrock Teleport, 12:02", "Ring of dueling: Emir's Arena, 11:40")),
            run(), connected()));
        shots.put("new-10-interior", () -> sidebar(icons, upToDate(),
            here("vanilla-interiors", 18, 143, true), active(), run(), connected()));
        shots.put("new-12-strict-paused", () -> {
            Sidebar sidebar = (Sidebar) sidebar(icons, upToDate(), here("vanilla-mid", 50, 50, true),
                new StrictModeModel(true, "Paused · 42s", Tone.PENDING, null, CardAction.RESUME_STRICT_MODE,
                    Arrays.asList("Varrock Teleport, 12:02", "Ring of dueling: Emir's Arena, 11:40")),
                run(), connected());
            sidebar.here().setExpanded(false);
            return sidebar;
        });
        shots.put("new-13-here-open", () -> {
            Sidebar sidebar = (Sidebar) sidebar(icons, upToDate(), here("vanilla-mid", 50, 50, true), active(),
                run(), connected());
            sidebar.here().setOpen(new java.util.TreeSet<>(Arrays.asList("SKILLING", "SKILLING/Woodcutting", "BANKS")));
            sidebar.strictMode().setExpanded(false);
            return sidebar;
        });
        shots.put("new-11-run-and-connection", () -> {
            Sidebar sidebar = (Sidebar) sidebar(icons, upToDate(), here("vanilla-interiors", 18, 143, true),
                active(), run(), connected());
            sidebar.here().setExpanded(false);
            sidebar.strictMode().setExpanded(false);
            sidebar.run().setExpanded(true);
            sidebar.connection().setExpanded(true);
            return sidebar;
        });
        // The Roll inbox card (Stage 4), alone.
        shots.put("roll-inbox-1-empty", () -> rollInbox(icons, RollInboxModel.builder().build()));
        shots.put("roll-inbox-2-new", () -> rollInbox(icons, RollInboxModel.builder()
            .rows(noticed(false))
            .more(2)
            .newEvents(7)
            .warnings(1)
            .build()));
        shots.put("roll-inbox-3-copied", () -> rollInbox(icons, RollInboxModel.builder()
            .rows(noticed(true))
            .more(2)
            .copied(7)
            .notice(FateLockedPlugin.copied(7))
            .build()));
        shots.put("roll-inbox-4-unlinked", () -> rollInbox(icons, RollInboxModel.builder()
            .rows(noticed(false).subList(0, 3))
            .newEvents(3)
            .character(CHARACTER)
            .build()));
        shots.put("roll-inbox-5-copy-failed", () -> rollInbox(icons, RollInboxModel.builder()
            .rows(noticed(false).subList(0, 3))
            .newEvents(3)
            .notice(FateLockedPlugin.COPY_FAILED)
            .build()));
        return shots;
    }

    /** The sidebar with only the Roll inbox card open, showing this. */
    static JComponent rollInbox(IconSource icons, RollInboxModel model) throws IOException
    {
        Sidebar sidebar = (Sidebar) sidebar(icons, upToDate(), here("vanilla-mid", 50, 50, true), active(), run(),
            connected());
        sidebar.rollInbox().apply(model);
        for (Section section : Arrays.asList(sidebar.here(), sidebar.strictMode(), sidebar.run(),
            sidebar.rollInbox(), sidebar.connection()))
        {
            section.setExpanded(section == sidebar.rollInbox());
        }
        return sidebar;
    }

    /** Five of a run's events, newest first, one of each kind of art; Slayer's is one to check. */
    static List<Row> noticed(boolean copied)
    {
        return Arrays.asList(
            new Row("1", FateEventType.SLAYER_TASK, "Abyssal demons", "Slayer", true, copied),
            new Row("2", FateEventType.COLLECTION_LOG, "Dragon warhammer", null, false, copied),
            new Row("3", FateEventType.SKILL_LEVEL, "Attack Level 71", "Attack", false, copied),
            new Row("4", FateEventType.COMBAT_ACHIEVEMENT, "Noxious Foe", null, false, copied),
            new Row("5", FateEventType.CLUE_CASKET, "Clue scroll (hard)", null, false, copied));
    }

    static JComponent sidebar(IconSource icons, StatusCardModel status, HereModel here,
        StrictModeModel strictMode, RunModel run, ConnectionModel connection)
    {
        Sidebar sidebar = new Sidebar(icons);
        sidebar.status().apply(status);
        sidebar.here().apply(here);
        sidebar.strictMode().apply(strictMode);
        if (run != null)
        {
            sidebar.run().apply(run);
        }
        else
        {
            sidebar.run().setVisible(false);
        }
        sidebar.connection().apply(connection);
        return sidebar;
    }

    static StatusCardModel upToDate()
    {
        return StatusCardModel.of(Tone.GOOD, "Rules up to date", "Synced 2 min ago for " + CHARACTER + ".");
    }

    static StrictModeModel active()
    {
        return new StrictModeModel(true, "Active", Tone.GOOD, StrictModeSectionPresenter.WHAT_IT_DOES,
            CardAction.PAUSE_STRICT_MODE, Collections.emptyList());
    }

    static RunModel run()
    {
        return new RunModel(CHARACTER + " (you)", "…a1b2", 3, 1, 0, 12, "Ritual of Clarity", null,
            "15 of 187 areas unlocked", 15 / 187.0);
    }

    static ConnectionModel connected()
    {
        return new ConnectionModel(true, "…cdef", null, CardAction.REPAIR, true, true, "Online");
    }

    static ConnectionModel disconnected()
    {
        return new ConnectionModel(false, null, null, CardAction.CONNECT, false, false, "Off");
    }

    /** Here, for one chunk of a golden run, on its own character or another. */
    static HereModel here(String scenario, int x, int y, boolean ownCharacter) throws IOException
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(scenario + ".bundle.json.gz"));
        JsonObject wire = GSON.fromJson(json, JsonObject.class);
        String account = AccountBinding.normalize(wire.getAsJsonObject("rules").get("account").getAsString());
        RulesSnapshot rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json));
        DecisionService decisions = DecisionService.create(rules, account, ownCharacter ? account : "zezima");
        return new HerePresenter().present(decisions, new CanonicalChunk(x, y));
    }
}
