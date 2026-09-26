package com.fatelocked;

import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.rules.Trust;
import com.google.gson.Gson;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Exports from before v4 carry no tracker decisions. The decision service
 * answers them with the old root-field rules, unchanged, and says Unknown to
 * every question those rules can't answer.
 */
public class LegacyRulesTest
{
    @Test
    public void v1ContinentsKeepTheirAnswers() throws Exception
    {
        DecisionService service = service("bundles/v1-legacy.json");

        assertTrue(service.rules().isLegacy());
        assertEquals(PermissionStatus.ALLOWED, service.chunk(new CanonicalChunk(46, 52)).getStatus());
        assertEquals("Asgarnia", service.chunk(new CanonicalChunk(46, 52)).getLabel());
        assertEquals(Decision.Source.LEGACY, service.chunk(new CanonicalChunk(46, 52)).getSource());
        assertEquals(PermissionStatus.ALLOWED, service.chunk(new CanonicalChunk(50, 50)).getStatus());

        Decision unmapped = service.chunk(new CanonicalChunk(10, 10));
        assertEquals(PermissionStatus.UNKNOWN, unmapped.getStatus());
        assertEquals(Decision.Source.UNMAPPED, unmapped.getSource());
    }

    @Test
    public void v3SubAreasAndBanksKeepTheirAnswers() throws Exception
    {
        DecisionService service = service("bundles/v3-standard.json");
        FateLockedBundle bundle = bundle("bundles/v3-standard.json");

        for (CanonicalChunk chunk : new CanonicalChunk[] {new CanonicalChunk(46, 52), new CanonicalChunk(50, 50)})
        {
            boolean unlocked = bundle.lockStateAt(chunk) == FateLockedBundle.LockState.UNLOCKED;
            assertEquals(chunk.toString(), unlocked ? PermissionStatus.ALLOWED : PermissionStatus.LOCKED,
                service.chunk(chunk).getStatus());
            assertEquals(chunk.toString(), bundle.labelAt(chunk), service.chunk(chunk).getLabel());
            assertEquals(chunk.toString(), bundle.isBankUnlocked(chunk) ? PermissionStatus.ALLOWED : PermissionStatus.LOCKED,
                service.bankAt(chunk).getStatus());
        }
        // Bank-locked run: Falador's bank is rolled, Lumbridge's is not.
        assertEquals(PermissionStatus.ALLOWED, service.bankAt(new CanonicalChunk(46, 52)).getStatus());
        assertEquals(PermissionStatus.LOCKED, service.bankAt(new CanonicalChunk(50, 50)).getStatus());
    }

    @Test
    public void chunkedExportsUnlockTheStartChunk() throws Exception
    {
        DecisionService service = service("bundles/v3-chunked-empty.json");

        assertEquals(PermissionStatus.ALLOWED, service.chunk(FateLockedBundle.CHUNKED_START).getStatus());
    }

    @Test
    public void questionsOnlyTheTrackerCanAnswerStayUnknown() throws Exception
    {
        DecisionService service = service("bundles/v3-standard.json");
        CanonicalChunk falador = new CanonicalChunk(46, 52);

        assertFalse(service.details(falador).isPresent());
        assertEquals(Decision.Source.UNMAPPED, service.target(falador, "NPC", "Guard").getSource());
        assertEquals(Decision.Source.UNMAPPED, service.mobility("Fairy Rings").getSource());
        assertEquals(Decision.Source.UNMAPPED, service.item(1333).getSource());
    }

    @Test
    public void noRulesMeansNoAnswers()
    {
        DecisionService service = DecisionService.create(RulesSnapshot.of(FateLockedBundle.empty()), null, "anyone");

        assertEquals(Trust.NO_RULES, service.trust());
        assertEquals(Decision.Source.TRUST, service.chunk(new CanonicalChunk(50, 50)).getSource());
        assertEquals(PermissionStatus.UNKNOWN, service.chunk(new CanonicalChunk(50, 50)).getStatus());
    }

    private static DecisionService service(String fixture) throws Exception
    {
        return DecisionService.create(RulesSnapshot.of(bundle(fixture)), null, "anyone");
    }

    private static FateLockedBundle bundle(String fixture) throws Exception
    {
        try (InputStream in = LegacyRulesTest.class.getClassLoader().getResourceAsStream(fixture))
        {
            assertNotNull("missing fixture " + fixture, in);
            return FateLockedBundle.loadFromJson(new Gson(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
