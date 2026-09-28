package com.fatelocked;

import com.fatelocked.preview.FolderArt;
import com.fatelocked.preview.SwingSnapshot;
import com.fatelocked.sidebar.CardAction;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.RollInboxModel;
import com.fatelocked.sidebar.Sidebar;
import com.fatelocked.sidebar.StatusCardModel;
import com.fatelocked.sidebar.StrictModeModel;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Palette.Tone;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.StatTiles;
import com.fatelocked.ui.StatusPill;
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
 * Renders the sidebar states the web guide shows (Stage 3 G3), in RuneLite's own theme, with no
 * client and no display: {@code gradle guideScreenshots} writes the PNGs and
 * {@code guide-screenshots.json} to {@code build/guide-screenshots}. The json says where each of
 * a shot's callouts points, as fractions of the image, from where the components were laid out,
 * so a marker can't drift from what it names.
 *
 * <p>Each shot opens only the card its chapter is about. The run is the golden bundles' fictional
 * "Iron Example", so the images show no real player.
 */
public final class GuideScreenshots
{
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
            BufferedImage image = onEdt(() -> SwingSnapshot.paint(root, PluginPanel.PANEL_WIDTH, 0));
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
                double[] at = onEdt(() -> anchor.locate(root, image));
                JsonArray point = new JsonArray();
                point.add(at[0]);
                point.add(at[1]);
                anchors.add(anchor.name, point);
            }
            entry.add("anchors", anchors);
            entries.add(entry);
            System.out.println("wrote " + out.resolve(shot.id + ".png") + " (" + image.getWidth() + "x"
                + image.getHeight() + ")");
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("runeliteVersion", RuneLiteProperties.getVersion());
        manifest.add("shots", entries);
        Files.write(out.resolve("guide-screenshots.json"),
            (new GsonBuilder().setPrettyPrinting().create().toJson(manifest) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static List<Shot> shots(IconSource icons) throws IOException
    {
        String firstRun = "Log in to see the place you're standing in.";
        List<Shot> shots = new ArrayList<>();
        shots.add(new Shot("sidebar-not-connected", () -> SidebarShots.sidebar(icons,
            StatusCardModel.of(Tone.NEUTRAL, "Not connected", "Connect the tracker to load your run's rules.")
                .withActions(CardAction.CONNECT, CardAction.USE_BACKUP),
            HereModel.message(firstRun), StrictModeModel.off(), null, SidebarShots.disconnected()),
            new Anchor("status", text("Not connected")),
            new Anchor("connect", text("Connect tracker")),
            new Anchor("backup", text("Use a backup instead"))));
        shots.add(new Shot("sidebar-waiting", () -> SidebarShots.sidebar(icons,
            StatusCardModel.of(Tone.PENDING, "Waiting for confirmation",
                "Confirm this profile in the browser tab RuneLite opened. RuneLite checks every few seconds.")
                .withActions(CardAction.OPEN_PAGE_AGAIN, CardAction.CANCEL_PAIRING),
            HereModel.message(firstRun), StrictModeModel.off(), null, SidebarShots.disconnected()),
            new Anchor("status", text("Waiting for confirmation")),
            new Anchor("open-again", text("Open page again")),
            new Anchor("cancel", text("Cancel"))));
        shots.add(new Shot("sidebar-overview", () -> open(upToDate(icons), null),
            new Anchor("status", text("Rules up to date")),
            new Anchor("cards", section("Here")),
            new Anchor("more-settings", startsWith("More settings"))));
        HereModel tower = SidebarShots.here("vanilla-mid", 42, 53, true);
        HereModel.Subgroup firstSkill = tower.getGroups().stream()
            .filter(group -> group.getCategory().equals("SKILLING")).findFirst()
            .map(group -> group.getSubgroups().get(0)).orElseThrow(IllegalStateException::new);
        shots.add(new Shot("sidebar-here", () -> {
            Sidebar sidebar = (Sidebar) SidebarShots.sidebar(icons, SidebarShots.upToDate(), tower,
                SidebarShots.active(), SidebarShots.run(), SidebarShots.connected());
            // Skilling open at its first skill; the rest closed, as they start.
            sidebar.here().setOpen(new java.util.TreeSet<>(Arrays.asList("SKILLING", firstSkill.getKey())));
            return open(sidebar, sidebar.here());
        },
            new Anchor("status", pill("Locked")),
            new Anchor("reason", text("Unlock Seers' Village")),
            new Anchor("counts", inside("Here", type(StatTiles.class))),
            new Anchor("categories", text("Skilling")),
            new Anchor("skills", text(firstSkill.getTitle()))));
        shots.add(new Shot("sidebar-strict-mode", () -> {
            Sidebar sidebar = (Sidebar) SidebarShots.sidebar(icons, SidebarShots.upToDate(),
                SidebarShots.here("vanilla-mid", 50, 50, true),
                new StrictModeModel(true, "Paused · 42s", Tone.PENDING, null, CardAction.RESUME_STRICT_MODE,
                    Arrays.asList("Varrock Teleport, 12:02", "Ring of dueling: Emir's Arena, 11:40")),
                SidebarShots.run(), SidebarShots.connected());
            return open(sidebar, sidebar.strictMode());
        },
            new Anchor("switch", inside("Strict Mode", type(ToggleSwitch.class))),
            new Anchor("paused", pill("Paused · 42s")),
            new Anchor("resume", text("Resume")),
            new Anchor("stopped", text("Recently stopped"))));
        shots.add(new Shot("sidebar-run", () -> {
            Sidebar sidebar = upToDate(icons);
            return open(sidebar, sidebar.run());
        },
            new Anchor("character", text("Iron Example (you)")),
            new Anchor("progress", text("15 of 187 areas unlocked")),
            new Anchor("keys", type(StatTiles.class)),
            new Anchor("ritual", text("Ritual of Clarity"))));
        shots.add(new Shot("sidebar-roll-inbox", () -> {
            Sidebar sidebar = upToDate(icons);
            sidebar.rollInbox().apply(new RollInboxModel(12, 2, 1, false));
            return open(sidebar, sidebar.rollInbox());
        },
            new Anchor("events", text("Local events")),
            new Anchor("needs-checking", text("Needs checking")),
            new Anchor("open", text("Open web Roll Inbox"))));
        shots.add(new Shot("sidebar-connection", () -> {
            Sidebar sidebar = upToDate(icons);
            return open(sidebar, sidebar.connection());
        },
            new Anchor("sync", inside("Connection & backup", type(ToggleSwitch.class))),
            new Anchor("repair", text("Re-pair tracker…")),
            new Anchor("check", text("Check now")),
            new Anchor("clipboard", text("Import from clipboard"))));
        shots.add(new Shot("sidebar-out-of-date", () -> {
            Sidebar sidebar = (Sidebar) SidebarShots.sidebar(icons,
                StatusCardModel.of(Tone.PENDING, "Rules may be out of date",
                    "Last synced 32 min ago. RuneLite couldn't reach the tracker; next check at 12:35."
                        + " Strict Mode is inactive until the rules refresh.")
                    .withActions(CardAction.CHECK_NOW, null),
                SidebarShots.here("vanilla-mid", 50, 50, true),
                new StrictModeModel(true, "Inactive", Tone.PENDING,
                    "Not blocking anything: the rules are more than 15 minutes old.", null,
                    Collections.emptyList()), SidebarShots.run(), SidebarShots.connected());
            return open(sidebar, sidebar.strictMode());
        },
            new Anchor("status", text("Rules may be out of date")),
            new Anchor("check", text("Check now")),
            new Anchor("inactive", pill("Inactive"))));
        shots.add(new Shot("sidebar-different-character", () -> {
            Sidebar sidebar = (Sidebar) SidebarShots.sidebar(icons,
                StatusCardModel.of(Tone.BAD, "Different character",
                    "This run belongs to " + SidebarShots.CHARACTER + ", and you're logged in as Zezima."
                        + " Warnings and Strict Mode are off."),
                SidebarShots.here("vanilla-mid", 50, 50, false),
                new StrictModeModel(true, "Inactive", Tone.PENDING,
                    "Not blocking anything: the rules are for " + SidebarShots.CHARACTER + ".", null,
                    Collections.emptyList()), SidebarShots.run(), SidebarShots.connected());
            return open(sidebar, sidebar.here());
        },
            new Anchor("status", text("Different character")),
            new Anchor("here", section("Here"))));
        return shots;
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
        final List<Anchor> anchors;

        Shot(String id, Callable<JComponent> build, Anchor... anchors)
        {
            this.id = id;
            this.build = build;
            this.anchors = Arrays.asList(anchors);
        }
    }

    /** A named point on a shot: the middle of the first shown component that matches. */
    private static final class Anchor
    {
        final String name;
        final Predicate<Component> match;

        Anchor(String name, Predicate<Component> match)
        {
            this.name = name;
            this.match = match;
        }

        double[] locate(JComponent root, BufferedImage image)
        {
            Component found = find(root, match);
            if (found == null)
            {
                throw new IllegalStateException("No component for the callout " + name);
            }
            Rectangle bounds = SwingUtilities.convertRectangle(found.getParent(), found.getBounds(), root);
            return new double[] {
                round((bounds.x + bounds.width / 2.0) / image.getWidth()),
                round((bounds.y + bounds.height / 2.0) / image.getHeight())};
        }
    }

    private static Component find(Container container, Predicate<Component> match)
    {
        for (Component child : container.getComponents())
        {
            if (!child.isVisible())
            {
                continue;
            }
            if (match.test(child))
            {
                return child;
            }
            if (child instanceof Container)
            {
                Component found = find((Container) child, match);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
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
        return Math.round(fraction * 100) / 100.0;
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
