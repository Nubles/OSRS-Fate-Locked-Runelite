package com.fatelocked.sidebar;

import com.fatelocked.events.DetectedEventStore;
import com.fatelocked.events.EventConfidence;
import com.fatelocked.events.FateEvent;
import com.fatelocked.events.FateEventType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.fatelocked.events.DetectedEventStore.Status.COPIED;
import static com.fatelocked.events.DetectedEventStore.Status.NEW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RollInboxPresenterTest
{
    @Test
    public void listsTheNewestFiveAndCountsTheRest()
    {
        List<DetectedEventStore.Entry> offered = new ArrayList<>();
        for (int i = 0; i < 7; i++)
        {
            offered.add(entry("evt-" + i, FateEventType.QUEST, "Quest " + i, EventConfidence.EXACT, i < 2 ? NEW : COPIED));
        }

        RollInboxModel model = RollInboxPresenter.present(offered, 1, true, true, "Copied 5 events.");

        assertEquals(RollInboxModel.MAX_ROWS, model.getRows().size());
        assertEquals("evt-0", model.getRows().get(0).getEventId());
        assertEquals("Quest 4", model.getRows().get(4).getLabel());
        assertEquals(2, model.getMore());
        assertEquals(2, model.getNewEvents());
        assertEquals(5, model.getCopied());
        assertEquals(1, model.getWarnings());
        assertTrue(model.isSaveFailed());
        assertEquals("Copied 5 events.", model.getNotice());
        assertFalse(model.getRows().get(1).isCopied());
        assertTrue(model.getRows().get(2).isCopied());
    }

    @Test
    public void saysWhichTheTrackerWillAskAbout()
    {
        RollInboxModel model = RollInboxPresenter.present(List.of(
            entry("a", FateEventType.DIARY_TASK, "Varrock Easy", EventConfidence.UNCERTAIN, NEW),
            entry("b", FateEventType.BOSS_KILL, "Vorkath", EventConfidence.EXACT, NEW)), 0, false, true, null);

        assertTrue(model.getRows().get(0).isNeedsChecking());
        assertFalse(model.getRows().get(1).isNeedsChecking());
        assertEquals(FateEventType.DIARY_TASK, model.getRows().get(0).getType());
    }

    @Test
    public void aLevelOrSlayerTaskShowsItsSkill()
    {
        FateEvent level = event("a", FateEventType.SKILL_LEVEL, "Attack Level 71", EventConfidence.EXACT,
            Map.of("skill", "Attack"));
        FateEvent odd = event("b", FateEventType.SKILL_LEVEL, "Attack Level 72", EventConfidence.EXACT,
            Map.of("skill", 7));
        FateEvent slayer = event("c", FateEventType.SLAYER_TASK, "Abyssal demons", EventConfidence.UNCERTAIN, null);
        FateEvent quest = event("d", FateEventType.QUEST, "Cook's Assistant", EventConfidence.EXACT,
            Map.of("skill", "Cooking"));
        FateEvent bare = event("e", FateEventType.SKILL_LEVEL, "Attack Level 73", EventConfidence.EXACT, null);

        assertEquals("Attack", RollInboxPresenter.skill(level));
        assertNull(RollInboxPresenter.skill(odd));
        assertEquals("Slayer", RollInboxPresenter.skill(slayer));
        assertNull(RollInboxPresenter.skill(quest));
        assertNull(RollInboxPresenter.skill(bare));
    }

    @Test
    public void anEventWithoutANameStillHasARow()
    {
        RollInboxModel model = RollInboxPresenter.present(List.of(
            new DetectedEventStore.Entry(event("a", FateEventType.COLLECTION_LOG, " ", EventConfidence.EXACT, null), NEW),
            new DetectedEventStore.Entry(event("b", FateEventType.COLLECTION_LOG, null, EventConfidence.EXACT, null), NEW)),
            0, false, true, null);

        assertEquals(RollInboxPresenter.UNNAMED, model.getRows().get(0).getLabel());
        assertEquals(RollInboxPresenter.UNNAMED, model.getRows().get(1).getLabel());
    }

    @Test
    public void aRunLinkedToNoOneSaysWhoseEventsTheyAre()
    {
        List<DetectedEventStore.Entry> offered = List.of(
            entry("a", "Zezima"), entry("b", " Zezima "), entry("c", "Nubles"), entry("d", null), entry("e", " "));

        assertEquals("Zezima and Nubles", RollInboxPresenter.present(offered, 0, false, false, null).getCharacter());
        assertNull(RollInboxPresenter.present(offered, 0, false, true, null).getCharacter());
        assertNull(RollInboxPresenter.present(Collections.emptyList(), 0, false, false, null).getCharacter());
        assertEquals("Zezima", RollInboxPresenter.listed(List.of("Zezima")));
        assertEquals("Zezima, Nubles and Lynx Titan", RollInboxPresenter.listed(List.of("Zezima", "Nubles", "Lynx Titan")));
    }

    @Test
    public void nothingOfferedIsAnEmptyCard()
    {
        RollInboxModel model = RollInboxPresenter.present(Collections.emptyList(), 2, false, true, null);

        assertEquals(RollInboxModel.builder().warnings(2).build(), model);
    }

    private static DetectedEventStore.Entry entry(String id, String account)
    {
        FateEvent event = event(id, FateEventType.QUEST, "Quest", EventConfidence.EXACT, null).toBuilder()
            .account(account).build();
        return new DetectedEventStore.Entry(event, NEW);
    }

    private static DetectedEventStore.Entry entry(String id, FateEventType type, String label,
        EventConfidence confidence, DetectedEventStore.Status status)
    {
        return new DetectedEventStore.Entry(event(id, type, label, confidence, null), status);
    }

    private static FateEvent event(String id, FateEventType type, String label, EventConfidence confidence,
        Map<String, Object> evidence)
    {
        return FateEvent.builder()
            .protocolVersion(1)
            .eventId(id)
            .runId("run-1")
            .account("Nubles")
            .runRevision(1)
            .eventType(type)
            .canonicalLabel(label)
            .confidence(confidence)
            .occurredAt(1_000L)
            .sessionSequence(1)
            .bundleVersion(4)
            .rulesVersion("1")
            .contentVersion(1)
            .evidence(evidence)
            .build();
    }
}
