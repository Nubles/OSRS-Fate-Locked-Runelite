package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.SpotMemory.Spot;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The owner's review, 28 Sept: where the Here card's things were seen, kept on this computer. */
public class SpotMemoryTest
{
    private static final Gson GSON = new Gson();
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final Spot STANDING = new Spot(3205, 3205, 0);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** A shopkeeper wanders the shop: within three tiles it's the one already known. */
    @Test
    public void aSpotWithinThreeTilesIsTheSameOne()
    {
        SpotMemory spots = new SpotMemory();

        assertTrue(spots.see(LUMBRIDGE, "SHOPS", "Bob's Brilliant Axes", new Spot(3230, 3203, 0)));
        assertFalse(spots.see(LUMBRIDGE, "SHOPS", "Bob's Brilliant Axes", new Spot(3232, 3206, 0)));
        assertTrue(spots.see(LUMBRIDGE, "SHOPS", "Bob's Brilliant Axes", new Spot(3234, 3203, 0)));
        assertTrue("upstairs is another", spots.see(LUMBRIDGE, "SHOPS", "Bob's Brilliant Axes",
            new Spot(3230, 3203, 1)));
    }

    @Test
    public void theNearestIsOnYourFloorFirst()
    {
        SpotMemory spots = new SpotMemory();
        spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3230, 3230, 0));
        spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3206, 3206, 1));
        spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3210, 3200, 0));

        assertEquals(new Spot(3210, 3200, 0), spots.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));
        assertEquals(new Spot(3206, 3206, 1),
            spots.nearest(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3205, 3205, 1)));
        assertEquals("on no floor of the player's, the nearest", new Spot(3206, 3206, 1),
            spots.nearest(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3205, 3205, 2)));
    }

    /** Each chunk and row is its own: an oak here isn't the way to one elsewhere, or to a willow. */
    @Test
    public void eachChunkAndRowIsItsOwn()
    {
        SpotMemory spots = new SpotMemory();
        spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3210, 3200, 0));

        assertNull(spots.nearest(new CanonicalChunk(51, 50), "SKILLING", "Oak tree", STANDING));
        assertNull(spots.nearest(LUMBRIDGE, "SKILLING", "Willow tree", STANDING));
        assertNull(spots.nearest(LUMBRIDGE, "FARMING", "Oak tree", STANDING));
        assertNull(spots.nearest(null, "SKILLING", "Oak tree", STANDING));
        assertNull(spots.nearest(LUMBRIDGE, "SKILLING", "Oak tree", null));
    }

    /** A row keeps the eight spots found last. */
    @Test
    public void aRowKeepsTheLastEightFound()
    {
        SpotMemory spots = new SpotMemory();
        for (int i = 0; i <= SpotMemory.PER_ROW; i++)
        {
            spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3200 + 10 * i, 3200, 0));
        }

        assertEquals("the first found has gone", new Spot(3210, 3200, 0),
            spots.nearest(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3200, 3200, 0)));
        assertEquals(new Spot(3280, 3200, 0), spots.nearest(LUMBRIDGE, "SKILLING", "Oak tree",
            new Spot(3290, 3200, 0)));
    }

    @Test
    public void whatIsSavedIsThereNextTime() throws Exception
    {
        Path file = folder.getRoot().toPath().resolve(SpotMemory.FILE);
        SpotMemory spots = new SpotMemory();
        spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3210, 3200, 0));
        spots.see(LUMBRIDGE, "BANKS", "Bank booth", new Spot(3208, 3220, 2));
        assertTrue(spots.changed());

        spots.save(GSON, file);
        assertFalse(spots.changed());

        SpotMemory next = new SpotMemory();
        next.load(GSON, file);
        assertEquals(new Spot(3210, 3200, 0), next.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));
        assertEquals(new Spot(3208, 3220, 2), next.nearest(LUMBRIDGE, "BANKS", "Bank booth", STANDING));
        assertFalse("reading isn't a change", next.changed());
    }

    /** Two RuneLites on one computer keep each other's spots. */
    @Test
    public void savingKeepsWhatAnotherRuneLiteSaved() throws Exception
    {
        Path file = folder.getRoot().toPath().resolve(SpotMemory.FILE);
        SpotMemory one = new SpotMemory();
        SpotMemory two = new SpotMemory();
        one.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3210, 3200, 0));
        two.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3240, 3240, 0));

        one.save(GSON, file);
        two.save(GSON, file);

        SpotMemory next = new SpotMemory();
        next.load(GSON, file);
        assertEquals(new Spot(3210, 3200, 0), next.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));
        assertEquals(new Spot(3240, 3240, 0), next.nearest(LUMBRIDGE, "SKILLING", "Oak tree",
            new Spot(3245, 3245, 0)));
        assertEquals("and the second knows the first's now", new Spot(3210, 3200, 0),
            two.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));
    }

    /** A spot found empty is forgotten, and stays forgotten: the file loses it at the next save. */
    @Test
    public void aForgottenSpotStaysForgotten() throws Exception
    {
        Path file = folder.getRoot().toPath().resolve(SpotMemory.FILE);
        SpotMemory spots = new SpotMemory();
        spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3210, 3200, 0));
        spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3240, 3240, 0));
        spots.save(GSON, file);

        spots.forget(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3211, 3201, 0));
        assertTrue(spots.changed());
        assertEquals(new Spot(3240, 3240, 0), spots.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));
        spots.load(GSON, file);
        assertEquals("the file doesn't bring it back", new Spot(3240, 3240, 0),
            spots.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));
        spots.save(GSON, file);

        SpotMemory next = new SpotMemory();
        next.load(GSON, file);
        assertEquals(new Spot(3240, 3240, 0), next.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));

        next.forget(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3240, 3240, 0));
        next.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3240, 3240, 0));
        next.save(GSON, file);
        SpotMemory last = new SpotMemory();
        last.load(GSON, file);
        assertEquals("seen again after, it's back", new Spot(3240, 3240, 0),
            last.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));
    }

    /** A damaged file is set aside, as the plugin's other files are, and nothing is lost by it. */
    @Test
    public void aDamagedFileIsSetAside() throws Exception
    {
        Path file = folder.getRoot().toPath().resolve(SpotMemory.FILE);
        Files.write(file, "not the spots".getBytes(StandardCharsets.UTF_8));
        SpotMemory spots = new SpotMemory();

        spots.load(GSON, file);

        assertFalse(Files.exists(file));
        try (Stream<Path> kept = Files.list(folder.getRoot().toPath()))
        {
            assertTrue(kept.anyMatch(path -> path.getFileName().toString().startsWith(SpotMemory.FILE + ".corrupt-")));
        }
        spots.see(LUMBRIDGE, "SKILLING", "Oak tree", new Spot(3210, 3200, 0));
        spots.save(GSON, file);
        SpotMemory next = new SpotMemory();
        next.load(GSON, file);
        assertEquals(new Spot(3210, 3200, 0), next.nearest(LUMBRIDGE, "SKILLING", "Oak tree", STANDING));
    }
}
