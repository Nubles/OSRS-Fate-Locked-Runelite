package com.fatelocked;

import com.fatelocked.guardian.StrictModeStatusView;
import com.fatelocked.panel.ChunkPanelViewModel;
import com.fatelocked.panel.ChunkPanelViewModelFactory;
import com.fatelocked.preview.FolderArt;
import com.fatelocked.preview.SwingSnapshot;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.ui.IconSource;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * Renders the sidebar into PNGs in RuneLite's own theme, with no client and no
 * display: {@code gradle previews} writes them to {@code build/previews}.
 *
 * <p>The run is the golden bundles' fictional "Iron Example", so the images show no
 * real player. RuneLite's theme is global to the JVM, which is why this runs as its
 * own task and not inside {@code check}.
 */
public final class Previews
{
    private static final Gson GSON = new Gson();
    private static final Instant NOON = Instant.parse("2026-10-01T12:00:00Z");

    private Previews()
    {
    }

    public static void main(String[] args) throws Exception
    {
        Path out = Paths.get(args.length > 0 ? args[0] : "build/previews");
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
        Map<String, Callable<JComponent>> shots = shots();
        shots.putAll(SidebarShots.all(icons));
        for (Map.Entry<String, Callable<JComponent>> shot : shots.entrySet())
        {
            JComponent component = onEdt(shot.getValue());
            BufferedImage image = onEdt(() -> SwingSnapshot.paint(component, PluginPanel.PANEL_WIDTH, 0));
            Path file = out.resolve(shot.getKey() + ".png");
            ImageIO.write(image, "png", file.toFile());
            System.out.println("wrote " + file + " (" + image.getWidth() + "x" + image.getHeight() + ")");
        }
    }

    private static Map<String, Callable<JComponent>> shots()
    {
        Map<String, Callable<JComponent>> shots = new LinkedHashMap<>();
        shots.put("kit-gallery", com.fatelocked.preview.KitGallery::build);
        shots.put("sidebar-first-run", () -> new FateLockedPanel(new FateLockedConfig() { }, null));
        shots.put("sidebar-connected-lumbridge", () -> {
            FateLockedPanel panel = new FateLockedPanel(new FateLockedConfig() { }, null);
            panel.updateConnection(TrackerConnectionSnapshot.connected(NOON, "6"));
            panel.updateStrictMode(StrictModeStatusView.of(true, false, 0, null));
            panel.updateTrackerAccount("Iron Example");
            panel.renderChunkForTest(here("vanilla-mid", 50, 50));
            return panel;
        });
        return shots;
    }

    /** The Here card for one chunk of a golden run, read on its own character. */
    private static ChunkPanelViewModel here(String scenario, int x, int y) throws IOException
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(scenario + ".bundle.json.gz"));
        JsonObject wire = GSON.fromJson(json, JsonObject.class);
        String account = AccountBinding.normalize(wire.getAsJsonObject("rules").get("account").getAsString());
        RulesSnapshot rules = RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json));
        DecisionService decisions = DecisionService.create(rules, account, account);
        return new ChunkPanelViewModelFactory().create(decisions, new CanonicalChunk(x, y), null);
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
