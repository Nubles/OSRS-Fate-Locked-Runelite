package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Progress;
import com.fatelocked.rules.Trust;
import com.fatelocked.sidebar.RunModel;
import com.fatelocked.ui.Copy;
import com.fatelocked.ui.Terms;
import java.util.List;

/**
 * The Run section (R12, U22): whose run it is, beside who is logged in; the run id by
 * its last four characters only; keys, Fate Points and the ritual by the web app's
 * names; and progress in the unit the run counts. Pure.
 */
final class RunPresenter
{
    private RunPresenter()
    {
    }

    static RunModel present(FateLockedBundle bundle, DecisionService decisions, String loggedInAs)
    {
        FateLockedBundle.RunState state = bundle.getState();
        List<String> goals = state == null ? null : state.getPinnedGoals();
        Progress progress = decisions.progress();
        return new RunModel(
            character(AccountBinding.boundAccount(bundle), loggedInAs, decisions.trust()),
            Copy.shortId(bundle.getRunId()),
            state == null ? 0 : state.getKeys(),
            state == null ? 0 : state.getSpecialKeys(),
            state == null ? 0 : state.getChaosKeys(),
            state == null ? 0 : state.getFatePoints(),
            state == null ? null : Terms.ritual(state.getActiveBuff()),
            goals == null || goals.isEmpty() ? null : goals.get(0),
            progress == null || progress.getTotal() <= 0 ? null
                : Copy.unlocked(progress.getUnlocked(), progress.getTotal(), progress.getUnit()),
            progress == null || progress.percent() < 0 ? 0 : progress.percent() / 100.0);
    }

    /** "Nubles (you)", "Nubles, and you're on Zezima", or the run's character alone while logged out. */
    static String character(String bound, String loggedInAs, Trust trust)
    {
        if (bound == null)
        {
            return "No character named";
        }
        if (trust == Trust.WRONG_CHARACTER && loggedInAs != null)
        {
            return bound + ", and you're on " + loggedInAs;
        }
        return trust == Trust.TRUSTED ? bound + " (you)" : bound;
    }
}
