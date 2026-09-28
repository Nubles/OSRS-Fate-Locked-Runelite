package com.fatelocked;

import com.fatelocked.preview.FolderArt;
import com.fatelocked.preview.SwingSnapshot;
import com.fatelocked.ui.IconSource;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        return shots;
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
