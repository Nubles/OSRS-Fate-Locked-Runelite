package com.fatelocked;

import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SavedRulesStoreTest
{
    private static final String CODE = "0123456789abcdef0123456789abcdef";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Gson gson = new Gson();

    @Test
    public void savedRulesComeBackAsTheSameRules() throws Exception
    {
        Path path = folder.getRoot().toPath().resolve(SavedRulesStore.FILE_NAME);
        String bundle = fixture("bundles/v4-rules.json");
        Instant savedAt = Instant.parse("2026-09-25T14:05:00Z");
        new SavedRulesStore(gson, path).save(new SavedRules(bundle,
            FateLockedPlugin.RulesSource.RELAY, savedAt, "41", PairingSupport.tag(CODE)));

        SavedRules loaded = new SavedRulesStore(gson, path).load();

        assertNotNull(loaded);
        assertEquals(FateLockedPlugin.RulesSource.RELAY, loaded.getSource());
        assertEquals(savedAt, loaded.getSavedAt());
        assertEquals("41", loaded.getRelayVersion());
        assertEquals(PairingSupport.tag(CODE), loaded.getPairingTag());
        FateLockedBundle original = FateLockedBundle.loadFromJson(gson, bundle);
        FateLockedBundle restored = FateLockedBundle.loadFromJson(gson, loaded.getPayload());
        assertEquals(original.getRunId(), restored.getRunId());
        assertEquals(original.getRegionChunks(), restored.getRegionChunks());
        assertEquals(original.getUnlockedRegions(), restored.getUnlockedRegions());
    }

    @Test
    public void theFileKeepsTheBundleCompressedAndNeverTheCode() throws Exception
    {
        Path path = folder.getRoot().toPath().resolve(SavedRulesStore.FILE_NAME);
        new SavedRulesStore(gson, path).save(new SavedRules(fixture("bundles/v4-rules.json"),
            FateLockedPlugin.RulesSource.RELAY, Instant.now(), "41", PairingSupport.tag(CODE)));

        String written = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        assertTrue(written.contains("\"payload\":\"FLGZ:"));
        assertFalse(written.contains(CODE));
        assertFalse(written.contains("Lumbridge"));
    }

    @Test
    public void noFileMeansNoSavedRules()
    {
        Path path = folder.getRoot().toPath().resolve(SavedRulesStore.FILE_NAME);

        assertNull(new SavedRulesStore(gson, path).load());
    }

    @Test
    public void aDamagedFileIsMovedAsideAndNeverStopsTheStart() throws Exception
    {
        File dir = folder.newFolder("damaged");
        Path path = dir.toPath().resolve(SavedRulesStore.FILE_NAME);
        Files.write(path, "{\"format\":1,\"source\":".getBytes(StandardCharsets.UTF_8));

        assertNull(new SavedRulesStore(gson, path).load());

        assertFalse(Files.exists(path));
        File[] kept = dir.listFiles((parent, name) ->
            name.startsWith(SavedRulesStore.FILE_NAME + ".corrupt-"));
        assertEquals(1, kept == null ? 0 : kept.length);
    }

    @Test
    public void aFileFromANewerPluginIsLeftAlone() throws Exception
    {
        Path path = folder.getRoot().toPath().resolve(SavedRulesStore.FILE_NAME);
        Files.write(path, "{\"format\":2,\"somethingNew\":true}"
            .getBytes(StandardCharsets.UTF_8));

        assertNull(new SavedRulesStore(gson, path).load());
        assertTrue(Files.exists(path));
    }

    @Test
    public void compressingKeepsAnAlreadyCompressedBundle() throws Exception
    {
        String compressed = SavedRulesStore.compress(fixture("bundles/v4-rules.json"));

        assertTrue(compressed.startsWith("FLGZ:"));
        assertEquals(compressed, SavedRulesStore.compress(compressed));
    }

    @Test
    public void aPairingTagNamesThePairingWithoutRevealingIt()
    {
        String tag = PairingSupport.tag(CODE);

        assertTrue(tag.matches("[0-9a-f]{16}"));
        assertEquals(tag, PairingSupport.tag(CODE));
        assertFalse(tag.equals(PairingSupport.tag("fedcba9876543210fedcba9876543210")));
        assertFalse(CODE.contains(tag));
        assertNull(PairingSupport.tag(""));
        assertNull(PairingSupport.tag(null));
    }

    private static String fixture(String name) throws Exception
    {
        try (InputStream in = SavedRulesStoreTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
