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
 * run, with Send to tracker, while online sync is on, and Copy for tracker for the tracker's Paste
 * from RuneLite. Nothing leaves the computer unless the player sends or copies it.
 */
public class RollInboxView extends Section
{
    public static final String TITLE = "Roll inbox";
    /** The card's button that sends the new events to the tracker through the relay. */
    public static final String SEND_TO_TRACKER = "Send to tracker";
    /** Send to tracker's button while a send is under way. */
    static final String SENDING = "Sending\u2026";
    /** An event the relay has taken for the tracker. */
    public static final String SENT = "Sent";
    static final String NOTE = "Nothing leaves RuneLite until you click " + SEND_TO_TRACKER + " or "
        + Terms.COPY_FOR_TRACKER + ". Sent events wait on the relay for up to 30 days, until your tracker takes them.";
    static final String EMPTY = "Nothing noticed this run yet. Levels, quests, finished diary tiers, kills and"
        + " more show here as you play.";
    static final String COPY_AGAIN = "Copy again";

    private final IconSource icons;
    private final FlatButton copy = new FlatButton(Terms.COPY_FOR_TRACKER, FlatButton.Kind.PRIMARY);
    private final FlatButton send = new FlatButton(SEND_TO_TRACKER, FlatButton.Kind.PRIMARY);
    /** Copy for tracker beside Send to tracker: the fallback, so not the card's primary button. */
    private final FlatButton copyInstead = new FlatButton(Terms.COPY_FOR_TRACKER, FlatButton.Kind.SECONDARY);
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
        copyInstead.setToolTipText(copy.getToolTipText());
        send.setToolTipText("Send the new events to your tracker's Roll Inbox through the Fate Locked relay");
        copyAgain.setToolTipText("Copy the events again, for another paste");
        open.setToolTipText("Open the web Roll Inbox");
    }

    /** Called when the player copies the events for the tracker. */
    public void onCopy(Runnable handler)
    {
        replace(copy, e -> handler.run());
        replace(copyInstead, e -> handler.run());
        replace(copyAgain, e -> handler.run());
    }

    /** Called when the player sends the new events to the tracker. */
    public void onSend(Runnable handler)
    {
        replace(send, e -> handler.run());
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
        if (model.getNewEvents() > 0 && model.isCanSend())
        {
            // A disabled button ignores a second click while the first send is under way.
            send.setText(model.isSending() ? SENDING : SEND_TO_TRACKER);
            send.setEnabled(!model.isSending());
            body.add(send);
            body.add(copyInstead);
        }
        else if (model.getNewEvents() > 0)
        {
            body.add(copy);
        }
        else if (model.getCopied() > 0 || model.getSent() > 0)
        {
            JPanel copied = new JPanel(new BorderLayout());
            copied.setOpaque(false);
            String done = doneUnlessSaid(model);
            if (!done.isEmpty())
            {
                copied.add(Sidebar.text(done, Type.body(), Palette.TEXT, 0), BorderLayout.WEST);
            }
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

    /** What became of the events once none is new: "Sent 3", "Copied 5", or "Sent 3, copied 2". */
    static String done(RollInboxModel model)
    {
        if (model.getSent() == 0)
        {
            return Terms.COPIED + " " + model.getCopied();
        }
        String sent = SENT + " " + model.getSent();
        return model.getCopied() == 0 ? sent
            : sent + ", " + Terms.COPIED.toLowerCase(Locale.ROOT) + " " + model.getCopied();
    }

    /**
     * The done line's words, or "" when the notice under it already opens with them ("Sent 7"
     * over "Sent 7. They're waiting...", "Copied 5" over "Copied 5 events. ..."): Copy again then
     * stands alone, so the count isn't said twice.
     */
    static String doneUnlessSaid(RollInboxModel model)
    {
        String done = done(model);
        String notice = model.getNotice();
        boolean said = notice != null && notice.startsWith(done)
            && (notice.length() == done.length() || !Character.isDigit(notice.charAt(done.length())));
        return said ? "" : done;
    }

    /** An event's row: its type's art, its name, where it stands, and a quiet cross to dismiss it. */
    private JPanel row(RollInboxModel.Row row)
    {
        ItemRow item = new ItemRow();
        item.setPalette(palette);
        if (row.isSent())
        {
            item.show(row.getLabel(), SENT, Palette.Tone.GOOD, null);
        }
        else if (row.isCopied())
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
