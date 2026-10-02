package com.fatelocked;

import com.fatelocked.events.FateEventType;
import com.fatelocked.preview.FolderArt;
import com.fatelocked.preview.SwingSnapshot;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.PointerText;
import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.sidebar.Sidebar;
import com.fatelocked.sidebar.StatusCardModel;
import com.fatelocked.sidebar.StatusCardView;
import com.fatelocked.sidebar.StrictModeModel;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Palette.Tone;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.StatTiles;
import com.fatelocked.ui.StatusPill;
import com.fatelocked.ui.Terms;
import com.fatelocked.ui.TextBlock;
import com.fatelocked.ui.ToggleSwitch;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * Renders the sidebar the web guide shows, in RuneLite's own theme, with no client and no
 * display: {@code gradle guideScreenshots} writes the PNGs and {@code guide-screenshots.json} to
 * {@code build/guide-screenshots}.
 *
 * <p>Each shot is laid out at the sidebar's own width and drawn at {@link #SCALE} times the
 * detail, as a high-density screen shows it, then cut to the card its chapter is about, or left
 * whole for the sidebar at a glance. The json gives, for each of a shot's callouts, the outline of
 * what it names, as fractions of the image, from where the components were laid out, so a marker
 * can't drift from what it names. The run is the golden bundles' fictional "Iron Example", so the
 * images show no real player.
 */
public final class GuideScreenshots
{
    /** Image pixels to each of the sidebar's own. */
    static final int SCALE = 2;
    /** The sidebar's background shown round a card cut from it, in its own pixels. */
    static final int MARGIN = 4;

    private GuideScreenshots()
    {
    }

    public static void main(String[] args) throws Exception
    {
        Path out = Paths.get(args.length > 0 ? args[0] : "build/guide-screenshots");
        Files.createDirectories(out);
        onEdt(() -> {
            if (!RuneLiteLAF.setup())
            {
                throw new IllegalStateException("RuneLite's theme did not install");
            }
            return null;
        });
        String artFolder = System.getProperty("fatelocked.previewArt", "");
        IconSource icons = artFolder.isEmpty() ? IconSource.NONE : new FolderArt(Paths.get(artFolder));

        JsonArray entries = new JsonArray();
        for (Shot shot : shots(icons))
        {
            JComponent root = onEdt(shot.build);
            BufferedImage whole = onEdt(() -> SwingSnapshot.paint(root, PluginPanel.PANEL_WIDTH, 0, SCALE));
            Rectangle frame = onEdt(() -> shot.frame(root));
            BufferedImage image = crop(whole, frame);
            byte[] png = png(image);
            Files.write(out.resolve(shot.id + ".png"), png);

            JsonObject entry = new JsonObject();
            entry.addProperty("id", shot.id);
            entry.addProperty("file", shot.id + ".png");
            entry.addProperty("width", image.getWidth());
            entry.addProperty("height", image.getHeight());
            entry.addProperty("sha256", sha256(png));
            JsonObject anchors = new JsonObject();
            for (Anchor anchor : shot.anchors)
            {
                double[] box = onEdt(() -> anchor.locate(root, frame));
                JsonArray outline = new JsonArray();
                for (double value : box)
                {
                    outline.add(value);
                }
                anchors.add(anchor.name, outline);
            }
            entry.add("anchors", anchors);
            entries.add(entry);
            System.out.println("wrote " + out.resolve(shot.id + ".png") + " (" + image.getWidth() + "x"
                + image.getHeight() + ")");
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("runeliteVersion", RuneLiteProperties.getVersion());
        manifest.addProperty("scale", SCALE);
        manifest.add("shots", entries);
        Files.write(out.resolve("guide-screenshots.json"),
            (new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static List<Shot> shots(IconSource icons) throws IOException
    {
        List<Shot> shots = new ArrayList<>();
        shots.add(new Shot("sidebar", () -> open(upToDate(icons), null), null,
            new Anchor("open-tracker", text("Open tracker")),
            new Anchor("status", type(StatusCardView.class)),
            Anchor.all("cards", type(Section.class)),
            new Anchor("more-settings", startsWith("More settings"))));

        // The status card in each of its states, alone.
        shots.add(status("status-up-to-date", icons, SidebarShots.upToDate(), true));
        shots.add(status("status-not-connected", icons,
            StatusCardModel.of(Tone.NEUTRAL, "Not connected", "Connect the tracker to load your run's rules.")
                .withActions(CardAction.CONNECT, CardAction.USE_BACKUP), false));
        shots.add(status("status-waiting", icons,
            StatusCardModel.of(Tone.PENDING, "Waiting for confirmation",
                "Confirm this profile in the browser tab RuneLite opened. RuneLite checks every few seconds.")
                .withActions(CardAction.OPEN_PAGE_AGAIN, CardAction.CANCEL_PAIRING), false));
        shots.add(status("status-out-of-date", icons,
            StatusCardModel.of(Tone.PENDING, "Rules may be out of date",
                "Last synced 32 min ago. RuneLite couldn't reach the tracker; next check at 12:35."
                    + " Strict Mode is inactive until the rules refresh.")
                .withActions(CardAction.CHECK_NOW, null), true));
        shots.add(status("status-expired", icons,
            StatusCardModel.of(Tone.PENDING, "Tracker copy expired",
                "The tracker hasn't sent your rules in the last 24 hours. Open the web tracker to send them again.")
                .withActions(CardAction.OPEN_TRACKER, null), true));
        shots.add(status("status-backup", icons,
            StatusCardModel.of(Tone.NEUTRAL, "Using a backup",
                "Rules from the clipboard, exported at 11:58. Strict Mode works for 15 minutes after the"
                    + " tracker exports them.")
                .withActions(null, CardAction.CONNECT), false));
        shots.add(status("status-different-character", icons,
            StatusCardModel.of(Tone.BAD, "Different character",
                "This run belongs to " + SidebarShots.CHARACTER + ", and you're logged in as Zezima."
                    + " Warnings and Strict Mode are off."), true));

        HereModel tower = SidebarShots.here("vanilla-mid", 42, 53, true);
        HereModel.Subgroup firstSkill = tower.getGroups().stream()
            .filter(group -> group.getCategory().equals("SKILLING")).findFirst()
            .map(group -> group.getSubgroups().get(0)).orElseThrow(IllegalStateException::new);
        shots.add(new Shot("here", () -> {
            Sidebar sidebar = (Sidebar) SidebarShots.sidebar(icons, SidebarShots.upToDate(), tower,
                SidebarShots.active(), SidebarShots.run(), SidebarShots.connected());
            // Skilling open at its first skill; the rest closed, as they start. Its first row clicked.
            sidebar.here().setOpen(new java.util.TreeSet<>(Arrays.asList("SKILLING", firstSkill.getKey())));
            sidebar.here().showPointer(PointerText.pointing(firstSkill.getRows().get(0).getName(), true), true);
            return open(sidebar, sidebar.here());
        }, Sidebar::here,
            Anchor.row("place", pill("Locked")),
            new Anchor("reason", text("Unlock Seers' Village")),
            new Anchor("counts", inside("Here", type(StatTiles.class))),
            Anchor.row("arrow", startsWith("The arrow points")),
            Anchor.row("categories", text("Skilling")),
            Anchor.row("skills", text(firstSkill.getTitle()))));
        shots.add(new Shot("here-way", () -> {
            Sidebar sidebar = (Sidebar) SidebarShots.sidebar(icons, SidebarShots.upToDate(), tower,
                SidebarShots.active(), SidebarShots.run(), SidebarShots.connected());
            sidebar.here().showPointer(PointerText.routed(firstSkill.getRows().get(0).getName(), tower.getPlace()),
                true);
            return open(sidebar, sidebar.here());
        }, Sidebar::here,
            Anchor.row("way", startsWith("Shortest Path shows"))));
        shots.add(new Shot("strict-mode", () -> {
            Sidebar sidebar = (Sidebar) SidebarShots.sidebar(icons, SidebarShots.upToDate(),
                SidebarShots.here("vanilla-mid", 50, 50, true),
                new StrictModeModel(true, "Paused · 42s", Tone.PENDING, null, CardAction.RESUME_STRICT_MODE,
                    Arrays.asList("Varrock Teleport, 12:02", "Ring of dueling: Emir's Arena, 11:40")),
                SidebarShots.run(), SidebarShots.connected());
            return open(sidebar, sidebar.strictMode());
        }, Sidebar::strictMode,
            new Anchor("switch", inside("Strict Mode", type(ToggleSwitch.class))),
            Anchor.row("pause", pill("Paused · 42s")),
            Anchor.all("stopped", either(text("Recently stopped"), startsWith("Varrock Teleport"),
                startsWith("Ring of dueling")))));
        shots.add(new Shot("run", () -> {
            Sidebar sidebar = upToDate(icons);
            return open(sidebar, sidebar.run());
        }, Sidebar::run,
            Anchor.row("character", text("Iron Example (you)")),
            new Anchor("progress", text("15 of 187 areas unlocked")),
            new Anchor("keys", type(StatTiles.class)),
            Anchor.row("fate-points", text("Fate Points")),
            Anchor.row("ritual", text("Ritual of Clarity"))));
        shots.add(new Shot("roll-inbox", () -> {
            Sidebar sidebar = upToDate(icons);
            sidebar.rollInbox().apply(RollInboxModel.builder()
                .rows(Arrays.asList(
                    new RollInboxModel.Row("1", FateEventType.SKILL_LEVEL, "Attack Level 71", "Attack", false, false),
                    new RollInboxModel.Row("2", FateEventType.BOSS_KILL, "Vorkath", null, false, false),
                    new RollInboxModel.Row("3", FateEventType.SLAYER_TASK, "Gargoyles", "Slayer", true, false),
                    new RollInboxModel.Row("4", FateEventType.QUEST, "Cook's Assistant", null, false, true)))
                .newEvents(3).copied(1).warnings(1).build());
            return open(sidebar, sidebar.rollInbox());
        }, Sidebar::rollInbox,
            Anchor.row("events", text("Attack Level 71")),
            Anchor.row("needs-checking", pill(Terms.NEEDS_CHECKING)),
            Anchor.row("copied", pill(Terms.COPIED)),
            new Anchor("copy", text(Terms.COPY_FOR_TRACKER)),
            Anchor.row("warnings", text("Warnings")),
            new Anchor("open", text("Open web Roll Inbox"))));
        shots.add(new Shot("connection", () -> {
            Sidebar sidebar = upToDate(icons);
            return open(sidebar, sidebar.connection());
        }, Sidebar::connection,
            Anchor.row("sync", text("Online sync")),
            Anchor.row("pairing", text("Re-pair tracker…")),
            new Anchor("check", text("Check now")),
            Anchor.all("backups", either(text("Import from clipboard"), text("Load newest backup file")))));
        return shots;
    }

    /** The status card alone, in one state; the run is connected when it says so. */
    private static Shot status(String id, IconSource icons, StatusCardModel model, boolean connected)
    {
        return new Shot(id, () -> open((Sidebar) SidebarShots.sidebar(icons, model,
            HereModel.message("Log in to see the place you're standing in."), StrictModeModel.off(),
            connected ? SidebarShots.run() : null, connected ? SidebarShots.connected() : SidebarShots.disconnected()),
            null), Sidebar::status);
    }

    private static Sidebar upToDate(IconSource icons) throws Exception
    {
        return (Sidebar) SidebarShots.sidebar(icons, SidebarShots.upToDate(),
            SidebarShots.here("vanilla-mid", 50, 50, true), SidebarShots.active(), SidebarShots.run(),
            SidebarShots.connected());
    }

    /** The sidebar with only this card open, or none. */
    private static Sidebar open(Sidebar sidebar, Section card)
    {
        for (Section section : Arrays.asList(sidebar.here(), sidebar.strictMode(), sidebar.run(),
            sidebar.rollInbox(), sidebar.connection()))
        {
            section.setExpanded(section == card);
        }
        return sidebar;
    }

    private static final class Shot
    {
        final String id;
        final Callable<JComponent> build;
        /** The card the shot is cut to, or null for the whole sidebar. */
        final Function<Sidebar, Component> card;
        final List<Anchor> anchors;

        Shot(String id, Callable<JComponent> build, Function<Sidebar, Component> card, Anchor... anchors)
        {
            this.id = id;
            this.build = build;
            this.card = card;
            this.anchors = Arrays.asList(anchors);
        }

        /** Where the shot is cut from the sidebar, in its own pixels: the card and a margin, or all of it. */
        Rectangle frame(JComponent root)
        {
            Rectangle all = new Rectangle(0, 0, root.getWidth(), root.getHeight());
            if (card == null)
            {
                return all;
            }
            Component component = card.apply((Sidebar) root);
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
            bounds.grow(MARGIN, MARGIN);
            return bounds.intersection(all);
        }
    }

    /**
     * A named outline on a shot: the first shown component that matches, all of them together,
     * or the row the first sits in, label and value, so a marker's line to it crosses nothing.
     */
    private static final class Anchor
    {
        final String name;
        final Predicate<Component> match;
        final boolean every;
        final boolean row;

        Anchor(String name, Predicate<Component> match)
        {
            this(name, match, false, false);
        }

        private Anchor(String name, Predicate<Component> match, boolean every, boolean row)
        {
            this.name = name;
            this.match = match;
            this.every = every;
            this.row = row;
        }

        static Anchor all(String name, Predicate<Component> match)
        {
            return new Anchor(name, match, true, false);
        }

        static Anchor row(String name, Predicate<Component> match)
        {
            return new Anchor(name, match, false, true);
        }

        /** The outline as {x, y, width, height}, fractions of the frame the shot is cut to. */
        double[] locate(JComponent root, Rectangle frame)
        {
            List<Component> found = new ArrayList<>();
            collect(root, match, every, found);
            if (found.isEmpty())
            {
                throw new IllegalStateException("No component for the callout " + name);
            }
            Rectangle box = null;
            for (Component match : found)
            {
                Rectangle bounds = row ? rowOf(match, root)
                    : SwingUtilities.convertRectangle(match.getParent(), match.getBounds(), root);
                box = box == null ? bounds : box.union(bounds);
            }
            Rectangle inside = box.intersection(frame);
            if (inside.isEmpty())
            {
                throw new IllegalStateException("The callout " + name + " is outside its shot");
            }
            return new double[] {
                round((inside.x - frame.x) / (double) frame.width),
                round((inside.y - frame.y) / (double) frame.height),
                round(inside.width / (double) frame.width),
                round(inside.height / (double) frame.height)};
        }
    }

    /**
     * The line a component is on: it and whatever shares its line, at every level up, such as a
     * row's label and its value, or a status and its button. Nothing taller than the line joins
     * it, so a card's body never does.
     */
    private static Rectangle rowOf(Component match, JComponent root)
    {
        Rectangle row = SwingUtilities.convertRectangle(match.getParent(), match.getBounds(), root);
        Component level = match;
        while (level.getParent() != null && level.getParent() != root)
        {
            Container parent = level.getParent();
            for (Component sibling : parent.getComponents())
            {
                if (sibling == level || !sibling.isVisible() || sibling.getWidth() == 0)
                {
                    continue;
                }
                Rectangle other = SwingUtilities.convertRectangle(parent, sibling.getBounds(), root);
                boolean sameLine = other.y < row.y + row.height && row.y < other.y + other.height;
                if (sameLine && other.height <= row.height * 1.6 + 2)
                {
                    row = row.union(other);
                }
            }
            level = parent;
        }
        return row;
    }

    /** A frame of the drawn sidebar, which is {@link #SCALE} times its own size. */
    private static BufferedImage crop(BufferedImage whole, Rectangle frame)
    {
        BufferedImage image = new BufferedImage(frame.width * SCALE, frame.height * SCALE, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        try
        {
            g.drawImage(whole.getSubimage(frame.x * SCALE, frame.y * SCALE, frame.width * SCALE,
                frame.height * SCALE), 0, 0, null);
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    /** The shown components that match, depth first: the first only, or every one not inside another. */
    private static void collect(Container container, Predicate<Component> match, boolean every,
        List<Component> found)
    {
        for (Component child : container.getComponents())
        {
            if (!every && !found.isEmpty())
            {
                return;
            }
            if (!child.isVisible())
            {
                continue;
            }
            if (match.test(child))
            {
                found.add(child);
                continue;
            }
            if (child instanceof Container)
            {
                collect((Container) child, match, every, found);
            }
        }
    }

    /** The first match inside the card with this title. */
    private static Predicate<Component> inside(String title, Predicate<Component> match)
    {
        return component -> {
            Component card = component.getParent();
            while (card != null && !(card instanceof Section && title.equals(((Section) card).getTitle())))
            {
                card = card.getParent();
            }
            return card != null && match.test(component);
        };
    }

    private static Predicate<Component> text(String text)
    {
        return component -> text.equals(textOf(component));
    }

    private static Predicate<Component> startsWith(String text)
    {
        return component -> textOf(component) != null && textOf(component).startsWith(text);
    }

    @SafeVarargs
    private static Predicate<Component> either(Predicate<Component>... matches)
    {
        return component -> Arrays.stream(matches).anyMatch(match -> match.test(component));
    }

    private static Predicate<Component> pill(String word)
    {
        return component -> component instanceof StatusPill && word.equals(((StatusPill) component).getWord());
    }

    private static Predicate<Component> type(Class<? extends Component> type)
    {
        return type::isInstance;
    }

    /** A card's header: the top of the card with this title. */
    private static Predicate<Component> section(String title)
    {
        return component -> component instanceof Section && title.equals(((Section) component).getTitle());
    }

    private static String textOf(Component component)
    {
        if (component instanceof JLabel)
        {
            return ((JLabel) component).getText();
        }
        if (component instanceof AbstractButton)
        {
            return ((AbstractButton) component).getText();
        }
        if (component instanceof TextBlock)
        {
            return ((TextBlock) component).getText();
        }
        return null;
    }

    private static double round(double fraction)
    {
        return Math.round(fraction * 1000) / 1000.0;
    }

    private static byte[] png(BufferedImage image) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception
    {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
        {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static <T> T onEdt(Callable<T> task) throws Exception
    {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try
            {
                result.set(task.call());
            }
            catch (Exception e)
            {
                failure.set(e);
            }
        });
        if (failure.get() != null)
        {
            throw failure.get();
        }
        return result.get();
    }
}
