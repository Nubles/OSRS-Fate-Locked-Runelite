package com.fatelocked.sidebar;

import com.fatelocked.ui.Art;
import com.fatelocked.ui.DismissButton;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.ItemRow;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.SkillArt;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.Terms;
import com.fatelocked.ui.Type;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * The Roll inbox section, drawn from a {@link RollInboxModel} (Stage 4): what RuneLite noticed this
 * run, with Copy for tracker for the tracker's Paste from RuneLite. Nothing leaves the computer
 * unless the player copies it.
 */
public class RollInboxView extends Section
{
    public static final String TITLE = "Roll inbox";
    static final String NOTE = "Copied only when you click. RuneLite doesn't upload anything.";
    static final String EMPTY = "Nothing noticed this run yet. Levels, quests, finished diary tiers, kills and"
        + " more show here as you play.";
    static final String COPY_AGAIN = "Copy again";

    private final IconSource icons;
    private final FlatButton copy = new FlatButton(Terms.COPY_FOR_TRACKER, FlatButton.Kind.PRIMARY);
    private final FlatButton copyAgain = new FlatButton(COPY_AGAIN, FlatButton.Kind.LINK);
    private final FlatButton open = new FlatButton("Open web Roll Inbox", FlatButton.Kind.SECONDARY);
    private Consumer<String> onDismiss = eventId -> { };
    private Palette palette = Palette.defaults();
    private RollInboxModel model;

    public RollInboxView(IconSource icons)
    {
        super(TITLE, false);
        this.icons = icons;
        copy.setToolTipText("Copy these events for the tracker's " + Terms.PASTE_FROM_RUNELITE);
        copyAgain.setToolTipText("Copy the events again, for another paste");
        open.setToolTipText("Open the web Roll Inbox");
    }

    /** Called when the player copies the events for the tracker. */
    public void onCopy(Runnable handler)
    {
        replace(copy, e -> handler.run());
        replace(copyAgain, e -> handler.run());
    }

    /** Called with an event's id when the player dismisses its row. */
    public void onDismiss(Consumer<String> handler)
    {
        onDismiss = handler;
    }

    /** Called when the player opens the web Roll Inbox. */
    public void onOpen(Runnable handler)
    {
        replace(open, e -> handler.run());
    }

    public void setPalette(Palette palette)
    {
        this.palette = palette;
        if (model != null)
        {
            apply(model);
        }
    }

    public void apply(RollInboxModel model)
    {
        this.model = model;
        setCount(model.getNewEvents() > 0
            ? model.getNewEvents() + " " + Terms.NEW.toLowerCase(Locale.ROOT) : null);
        JPanel body = body();
        body.removeAll();
        if (model.getCharacter() != null)
        {
            body.add(Sidebar.text("Events for " + model.getCharacter(), Type.small(), Palette.TEXT_MUTED, 0));
        }
        if (model.getQuiet() != null)
        {
            // Why nothing new will come here, rather than a promise that it will.
            body.add(Sidebar.text(model.getQuiet(), Type.small(), Palette.TEXT_MUTED, 0));
        }
        else if (model.getRows().isEmpty())
        {
            body.add(Sidebar.text(EMPTY, Type.small(), Palette.TEXT_MUTED, 0));
        }
        for (RollInboxModel.Row row : model.getRows())
        {
            body.add(row(row));
        }
        if (model.getMore() > 0)
        {
            body.add(Sidebar.text("+" + model.getMore() + " more", Type.small(), Palette.TEXT_MUTED, 0));
        }
        if (model.getNewEvents() > 0)
        {
            body.add(copy);
        }
        else if (model.getCopied() > 0)
        {
            JPanel copied = new JPanel(new BorderLayout());
            copied.setOpaque(false);
            copied.add(Sidebar.text(Terms.COPIED + " " + model.getCopied(), Type.body(), Palette.TEXT, 0),
                BorderLayout.WEST);
            copied.add(copyAgain, BorderLayout.EAST);
            body.add(copied);
        }
        if (model.getNotice() != null)
        {
            body.add(Sidebar.text(model.getNotice(), Type.small(), Palette.TEXT, 0));
        }
        body.add(Sidebar.pair("Warnings", model.getWarnings() <= 0 ? "None" : model.getWarnings() + " active",
            palette.text(model.getWarnings() <= 0 ? Palette.Tone.GOOD : Palette.Tone.BAD)));
        if (model.isSaveFailed())
        {
            body.add(Sidebar.text("Saving the local history failed.", Type.small(), palette.text(Palette.Tone.BAD),
                0));
        }
        body.add(Sidebar.text(NOTE, Type.small(), Palette.TEXT_MUTED, 0));
        body.add(open);
        body.revalidate();
        body.repaint();
    }

    public RollInboxModel model()
    {
        return model;
    }

    /** An event's row: its type's art, its name, where it stands, and a quiet cross to dismiss it. */
    private JPanel row(RollInboxModel.Row row)
    {
        ItemRow item = new ItemRow();
        item.setPalette(palette);
        if (row.isCopied())
        {
            item.show(row.getLabel(), Terms.COPIED, Palette.Tone.GOOD, null);
        }
        else if (row.isNeedsChecking())
        {
            item.show(row.getLabel(), Terms.NEEDS_CHECKING, Palette.Tone.NEUTRAL, null);
        }
        else
        {
            item.show(row.getLabel(), Terms.NEW, Palette.Tone.PENDING, null);
        }
        if (row.getSkill() != null && SkillArt.icon(row.getSkill()) != null)
        {
            item.art(SkillArt.icon(row.getSkill()));
        }
        else if (art(row) != null)
        {
            icons.load(art(row), item::art);
        }
        DismissButton dismiss = new DismissButton();
        dismiss.addActionListener(e -> onDismiss.accept(row.getEventId()));
        JPanel cross = new JPanel(new BorderLayout());
        cross.setOpaque(false);
        cross.add(dismiss, BorderLayout.NORTH);
        JPanel line = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        line.setOpaque(false);
        line.add(item, BorderLayout.CENTER);
        line.add(cross, BorderLayout.EAST);
        return line;
    }

    /** The art for an event's type, where no skill icon says it better. */
    static Art art(RollInboxModel.Row row)
    {
        if (row.getType() == null) return null;
        switch (row.getType())
        {
            case QUEST:
                return Art.QUESTS;
            case COMBAT_ACHIEVEMENT:
                return Art.COMBAT_TASK;
            case COLLECTION_LOG:
                return Art.COLLECTION_LOG;
            case CLUE_CASKET:
                return Art.CLUE;
            case BOSS_KILL:
                return Art.COMBAT;
            case RAID_COMPLETION:
                return Art.RAID;
            case DIARY_TASK:
                return Art.DIARY;
            case PET_DROP:
                return Art.PET;
            default:
                return null;
        }
    }

    private static void replace(FlatButton button, ActionListener listener)
    {
        for (ActionListener old : button.getActionListeners())
        {
            button.removeActionListener(old);
        }
        button.addActionListener(listener);
    }
}
