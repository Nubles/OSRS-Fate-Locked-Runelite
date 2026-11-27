package com.fatelocked;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Plugin Hub notes promise that Strict Mode stops only exactly matched
 * travel, that game actions are stopped in that one place only, that the
 * banner's own Pause button takes the mouse press made on it, and that no
 * menu entry is ever removed, reordered or created. Pinned at the source
 * level so a new consume site cannot slip in unnoticed.
 */
public class PluginHubClickBoundaryTest
{
    @Test
    public void onlyTheTravelClickHandlerConsumesGameClicks() throws Exception
    {
        int consumes = 0;
        for (Map.Entry<Path, String> source : productionSources().entrySet())
        {
            // Swing key events (the keybind button) are not game clicks.
            if (!source.getValue().contains("MenuOptionClicked")) continue;
            int found = occurrences(source.getValue(), ".consume()");
            if (found > 0)
            {
                assertEquals("a game click is consumed in " + source.getKey(),
                    "StrictModeClickHandler.java",
                    source.getKey().getFileName().toString());
            }
            consumes += found;
        }
        assertEquals(1, consumes);

        String handler = sourceNamed("StrictModeClickHandler.java");
        int travel = handler.indexOf("public GuardResult handleTravel(");
        assertTrue(travel >= 0);
        assertTrue(handler.indexOf(".consume()") > travel);
    }

    /**
     * The one mouse listener that consumes anything is the block banner, and only for a left
     * press on its Pause button (and that press's release and click), after it has paused.
     */
    @Test
    public void onlyTheBannersPauseButtonConsumesMouseInput() throws Exception
    {
        for (Map.Entry<Path, String> source : productionSources().entrySet())
        {
            if (!source.getValue().contains("net.runelite.client.input.Mouse")) continue;
            if (occurrences(source.getValue(), ".consume()") > 0)
            {
                assertEquals("mouse input is consumed in " + source.getKey(),
                    "FateLockedTravelBlockOverlay.java",
                    source.getKey().getFileName().toString());
            }
        }

        String banner = sourceNamed("FateLockedTravelBlockOverlay.java");
        assertEquals(3, occurrences(banner, ".consume()"));
        int pressed = banner.indexOf("public MouseEvent mousePressed(");
        int released = banner.indexOf("public MouseEvent mouseReleased(");
        int clicked = banner.indexOf("public MouseEvent mouseClicked(");
        assertTrue(pressed >= 0 && released > pressed && clicked > released);
        int first = banner.indexOf(".consume()");
        assertTrue("the press is consumed only after the pause has run",
            first > banner.indexOf("pause.run();", pressed) && banner.indexOf("pause.run();", pressed) > pressed
                && first < released);
        assertTrue(banner.indexOf(".consume()", first + 1) > released);
        assertTrue(banner.lastIndexOf(".consume()") > clicked);
    }

    @Test
    public void menusAreNeverRemovedReorderedOrCreated() throws Exception
    {
        String all = String.join("\n", productionSources().values());
        for (String forbidden : new String[] {
            "setMenuEntries(", "createMenuEntry(", "setDeprioritized(",
            "removeMenuEntry(", "setForceLeftClick(", "setOption(" })
        {
            assertFalse(forbidden, all.contains(forbidden));
        }
        // The only change to a menu is the " (Locked)" tag on a target.
        assertEquals(1, occurrences(all, "entry.setTarget("));
        assertEquals(0, occurrences(all.replace("entry.setTarget(", "")
            .replace("point.setTarget(", ""), ".setTarget("));
    }

    private static String sourceNamed(String fileName) throws IOException
    {
        for (Map.Entry<Path, String> entry : productionSources().entrySet())
        {
            if (entry.getKey().getFileName().toString().equals(fileName))
            {
                return entry.getValue();
            }
        }
        throw new AssertionError("missing production source " + fileName);
    }

    private static Map<Path, String> productionSources() throws IOException
    {
        Map<Path, String> sources = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(Paths.get("src", "main", "java")))
        {
            paths.filter(path -> path.toString().endsWith(".java"))
                .sorted()
                .forEach(path -> sources.put(path, read(path)));
        }
        return sources;
    }

    private static String read(Path path)
    {
        try
        {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new AssertionError("could not read " + path, ex);
        }
    }

    private static int occurrences(String text, String needle)
    {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1))
        {
            count++;
        }
        return count;
    }
}
