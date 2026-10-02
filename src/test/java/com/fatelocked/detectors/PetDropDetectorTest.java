package com.fatelocked.detectors;

import com.fatelocked.events.EventConfidence;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Pets stay as they are while the owner's poll decides their reward (Stage 4, plan decision 13). */
public class PetDropDetectorTest
{
    @Test
    public void aNewPetIsRecordedWithoutGuessingWhichItIs()
    {
        // The game's lines, from RuneLite's screenshot plugin.
        PetDropDetector detector = new PetDropDetector();
        Optional<DetectedEvent> followed = detector.detect(
            "You have a funny feeling like you're being followed.", 10_000);
        assertTrue(followed.isPresent());
        assertNull(followed.get().getCanonicalLabel());
        assertEquals(EventConfidence.UNCERTAIN, followed.get().getConfidence());
        assertTrue(detector.detect(
            "You feel something weird sneaking into your backpack.", 20_000).isPresent());
    }

    @Test
    public void aPetAlreadyOwnedIsNotANewPet()
    {
        PetDropDetector detector = new PetDropDetector();
        assertFalse(detector.detect(
            "You have a funny feeling like you would have been followed...", 10_000).isPresent());
        assertFalse(detector.detect("Your pet is insured.", 20_000).isPresent());
    }
}
