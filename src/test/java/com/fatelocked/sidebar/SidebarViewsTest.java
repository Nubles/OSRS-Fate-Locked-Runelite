package com.fatelocked.sidebar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fatelocked.events.FateEventType;
import com.fatelocked.ui.DismissButton;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Fold;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.ItemRow;
import com.fatelocked.ui.Palette.Tone;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.Terms;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class SidebarViewsTest
{
    @Test
    public void theSidebarLeadsWithStatusThenHereStrictModeRunAndConnection() throws Exception
    {
        onEdt(() -> {
            Sidebar sidebar = new Sidebar(IconSource.NONE);
            List<String> sections = all(sidebar, Section.class::isInstance).stream()
                .map(section -> ((Section) section).getTitle()).collect(Collectors.toList());
            assertEquals(Arrays.asList("Here", "Strict Mode", "Run", "Roll inbox", "Connection & backup"), sections);
            assertTrue(Arrays.asList(sidebar.getComponents()).indexOf(sidebar.status())
                < Arrays.asList(sidebar.getComponents()).indexOf(sidebar.here()));
        });
    }

    @Test
    public void theStatusCardShowsOnePrimaryActionAndAQuietSecond() throws Exception
    {
        onEdt(() -> {
            StatusCardView card = new StatusCardView();
            List<CardAction> pressed = new ArrayList<>();
            card.onAction(pressed::add);
            card.apply(StatusCardModel.of(Tone.NEUTRAL, "Not connected", "Connect the tracker.")
                .withActions(CardAction.CONNECT, CardAction.USE_BACKUP));

            List<FlatButton> buttons = buttons(card);
            assertEquals(2, buttons.size());
            assertEquals(FlatButton.Kind.PRIMARY, buttons.get(0).getKind());
            assertEquals("Connect tracker", buttons.get(0).getText());
            assertEquals(FlatButton.Kind.LINK, buttons.get(1).getKind());
            buttons.get(1).doClick();
            assertEquals(Collections.singletonList(CardAction.USE_BACKUP), pressed);

            card.apply(StatusCardModel.of(Tone.GOOD, "Rules up to date", "Synced just now."));
            assertTrue("no actions, no buttons", buttons(card).isEmpty());
        });
    }

    /** The owner's review, 28 Sept: categories open and close, and say what they hold while closed. */
    @Test
    public void hereStartsClosedAndShowsFiveRowsAndMoreOnRequest() throws Exception
    {
        onEdt(() -> {
            HereView here = new HereView(IconSource.NONE);
            List<Set<String>> saved = new ArrayList<>();
            here.onFold(saved::add);
            List<HereModel.Row> rows = IntStream.range(0, 8)
                .mapToObj(i -> new HereModel.Row("Shop " + i, "Locked", Tone.BAD, null))
                .collect(Collectors.toList());
            here.apply(new HereModel("Falador", "Locked", Tone.BAD, "Asgarnia", "Unlock Falador",
                Collections.emptyList(), Collections.singletonList(new HereModel.Group("SHOPS", "Shops", rows)),
                null));
            assertEquals("closed at first", 0, all(here, ItemRow.class::isInstance).size());
            assertEquals("what it holds, while closed", Collections.singletonList("8 locked"), labels(here));

            Fold shops = folds(here).get(0);
            shops.click();
            assertEquals(Collections.singletonList(Collections.singleton("SHOPS")), saved);
            assertTrue("the summary goes once it's open", labels(here).isEmpty());
            assertEquals(5, all(here, ItemRow.class::isInstance).size());
            FlatButton more = buttons(here).get(0);
            assertEquals("+3 more", more.getText());
            more.doClick();
            assertEquals(8, all(here, ItemRow.class::isInstance).size());
            assertEquals("Show fewer", buttons(here).get(0).getText());

            here.apply(new HereModel("Taverley", "Locked", Tone.BAD, null, null, Collections.emptyList(),
                Collections.singletonList(new HereModel.Group("SHOPS", "Shops", rows)), null));
            assertEquals("still open at a new place, with the first few again", 5,
                all(here, ItemRow.class::isInstance).size());

            folds(here).get(0).click();
            assertEquals(Collections.emptySet(), saved.get(1));
            assertEquals(0, all(here, ItemRow.class::isInstance).size());
        });
    }

    /**
     * The owner's review, 28 Sept: a row the card can point at answers a click, and a line
     * under the counts says what the arrow points at, with Clear while it's up.
     */
    @Test
    public void aRowPointsAndALineSaysWhere() throws Exception
    {
        onEdt(() -> {
            HereView here = new HereView(IconSource.NONE);
            List<String> points = new ArrayList<>();
            List<String> clears = new ArrayList<>();
            here.onPoint((category, row) -> points.add(category + ": " + row));
            here.onClearPoint(() -> clears.add("clear"));
            List<HereModel.Row> trees = Collections.singletonList(
                new HereModel.Row("Oak tree", "Can do", Tone.GOOD, "Level 15"));
            List<HereModel.Row> quests = Collections.singletonList(
                new HereModel.Row("Cook's Assistant", "Can do", Tone.GOOD, null));
            here.setOpen(new TreeSet<>(Arrays.asList("SKILLING", "SKILLING/Woodcutting", "QUESTS")));
            here.apply(new HereModel("Lumbridge", "Unlocked", Tone.GOOD, null, null, Collections.emptyList(),
                Arrays.asList(new HereModel.Group("SKILLING", "Skilling", trees, Collections.singletonList(
                        new HereModel.Subgroup("SKILLING/Woodcutting", "Woodcutting", "Woodcutting", "Level 15 · cap 20",
                            trees))),
                    new HereModel.Group("QUESTS", "Quests", quests)), null));

            List<ItemRow> rows = all(here, ItemRow.class::isInstance).stream().map(ItemRow.class::cast)
                .collect(Collectors.toList());
            assertTrue("Oak tree", rows.get(0).clickable());
            assertFalse("a quest has no one thing to point at", rows.get(1).clickable());
            rows.get(0).click();
            assertEquals(Collections.singletonList("SKILLING: Oak tree"), points);

            String line = "The arrow points at the nearest Oak tree.";
            here.showPointer(line, true);
            assertTrue(texts(here).contains(line));
            FlatButton clear = buttons(here).stream().filter(button -> button.getText().equals("Clear")).findFirst()
                .orElseThrow(AssertionError::new);
            clear.doClick();
            assertEquals(Collections.singletonList("clear"), clears);

            here.showPointer("Can't find Yew tree near you here.", false);
            assertTrue(texts(here).contains("Can't find Yew tree near you here."));
            assertFalse("nothing to clear", buttons(here).stream().anyMatch(button -> button.getText().equals("Clear")));

            here.apply(new HereModel("Draynor", "Unlocked", Tone.GOOD, null, null, Collections.emptyList(),
                Collections.emptyList(), null));
            assertFalse("the note belongs to the place the player left",
                texts(here).contains("Can't find Yew tree near you here."));

            String way = "Shortest Path shows the way to the nearest Oak tree you've seen here.";
            here.showPointer(way, true);
            here.apply(new HereModel("Lumbridge", "Unlocked", Tone.GOOD, null, null, Collections.emptyList(),
                Collections.emptyList(), null));
            assertTrue("while it's up, the arrow's line goes from place to place, as the way may",
                texts(here).contains(way));
            assertTrue(buttons(here).stream().anyMatch(button -> button.getText().equals("Clear")));
        });
    }

    /** Skilling splits by skill, each with the game's own icon and the player's level and cap. */
    @Test
    public void skillingOpensSkillBySkill() throws Exception
    {
        onEdt(() -> {
            HereView here = new HereView(IconSource.NONE);
            List<HereModel.Row> trees = Arrays.asList(new HereModel.Row("Tree", "Can do", Tone.GOOD, "Level 1"),
                new HereModel.Row("Yew tree", "Not ready", Tone.PENDING, "Level 60"));
            List<HereModel.Row> spots = Collections.singletonList(
                new HereModel.Row("Fishing spot", "Not ready", Tone.PENDING, "Level 1; The Lost Tribe started"));
            List<HereModel.Row> all = new ArrayList<>(spots);
            all.addAll(trees);
            here.apply(new HereModel("Lumbridge", "Unlocked", Tone.GOOD, null, null, Collections.emptyList(),
                Collections.singletonList(new HereModel.Group("SKILLING", "Skilling", all, Arrays.asList(
                    new HereModel.Subgroup("SKILLING/Fishing", "Fishing", "Fishing", "Level 1 · cap 10", spots),
                    new HereModel.Subgroup("SKILLING/Woodcutting", "Woodcutting", "Woodcutting", "Level 15 · cap 20",
                        trees)))), null));

            folds(here).get(0).click();
            List<Fold> skills = folds(here).subList(1, 3);
            assertTrue("each skill has its icon", skills.stream().allMatch(fold -> fold.art().getImage() != null));
            assertEquals(0, all(here, ItemRow.class::isInstance).size());
            assertTrue(texts(here).containsAll(Arrays.asList("Fishing", "Woodcutting")));
            assertTrue(all(here, JLabel.class::isInstance).stream().map(label -> ((JLabel) label).getText())
                .collect(Collectors.toList()).containsAll(Arrays.asList("Level 1 · cap 10", "Level 15 · cap 20")));

            skills.get(1).click();
            assertEquals(2, all(here, ItemRow.class::isInstance).size());
            assertEquals(new TreeSet<>(Arrays.asList("SKILLING", "SKILLING/Woodcutting")), here.open());

            HereView again = new HereView(IconSource.NONE);
            again.setOpen(here.open());
            again.apply(here.model());
            assertEquals("remembered from last time", 2, all(again, ItemRow.class::isInstance).size());
        });
    }

    @Test
    public void strictModeIsOpenWhileOnAndItsSwitchSpeaksForThePlayer() throws Exception
    {
        onEdt(() -> {
            StrictModeView strict = new StrictModeView(IconSource.NONE);
            List<Boolean> flipped = new ArrayList<>();
            strict.onToggle(flipped::add);
            strict.apply(StrictModeModel.off());
            assertFalse(strict.isExpanded());
            assertFalse(strict.toggle().isSelected());

            strict.apply(new StrictModeModel(true, "Active", Tone.GOOD, null, CardAction.PAUSE_STRICT_MODE,
                Collections.emptyList()));
            assertTrue(strict.isExpanded());
            assertTrue(strict.toggle().isSelected());
            assertEquals("Pause 60s", buttons(strict).get(0).getText());

            strict.toggle().doClick();
            assertEquals(Collections.singletonList(false), flipped);
        });
    }

    @Test
    public void aNoticeShowsUnderTheStatusCardAndClearsItself() throws Exception
    {
        onEdt(() -> {
            Sidebar sidebar = new Sidebar(IconSource.NONE);
            assertEquals(null, sidebar.noticeText());
            sidebar.notice("Imported from the clipboard", Tone.GOOD);
            assertEquals("Imported from the clipboard", sidebar.noticeText());
            assertEquals(8000, Sidebar.NOTICE_MILLIS);
            sidebar.clearNotice();
            assertEquals(null, sidebar.noticeText());
        });
    }

    @Test
    public void anEmptyRollInboxSaysSoAndOffersNoCopy() throws Exception
    {
        onEdt(() -> {
            RollInboxView inbox = new RollInboxView(IconSource.NONE);
            inbox.setExpanded(true);
            int[] opened = new int[1];
            inbox.onOpen(() -> opened[0]++);
            inbox.apply(RollInboxModel.builder().warnings(2).saveFailed(true).build());

            assertEquals(null, inbox.getCount());
            assertTrue(texts(inbox).contains(RollInboxView.EMPTY));
            assertTrue(texts(inbox).contains("Saving the local history failed."));
            assertTrue(texts(inbox).contains(RollInboxView.NOTE));
            assertEquals(List.of("Open web Roll Inbox"), buttonTexts(inbox));
            buttons(inbox).get(0).doClick();
            assertEquals(1, opened[0]);

            inbox.apply(RollInboxModel.builder().build());
            assertFalse(texts(inbox).contains("Saving the local history failed."));
        });
    }

    @Test
    public void theRollInboxListsWhatItNoticedToCopyOrDismiss() throws Exception
    {
        onEdt(() -> {
            RollInboxView inbox = new RollInboxView(IconSource.NONE);
            inbox.setExpanded(true);
            int[] copies = new int[1];
            List<String> dismissed = new ArrayList<>();
            inbox.onCopy(() -> copies[0]++);
            inbox.onDismiss(dismissed::add);
            RollInboxModel.Row copiedRow = new RollInboxModel.Row("c", FateEventType.QUEST, "Cook's Assistant", null,
                false, true);
            inbox.apply(RollInboxModel.builder()
                .rows(List.of(
                    new RollInboxModel.Row("a", FateEventType.SKILL_LEVEL, "Attack Level 71", "Attack", false, false),
                    new RollInboxModel.Row("b", FateEventType.DIARY_TASK, "Varrock Easy", null, true, false),
                    copiedRow))
                .more(4).newEvents(2).copied(5).character("Zezima").notice("Copied 5 events.").build());

            assertEquals("2 new", inbox.getCount());
            List<ItemRow> rows = all(inbox, ItemRow.class::isInstance).stream().map(ItemRow.class::cast)
                .collect(Collectors.toList());
            assertEquals(List.of("Attack Level 71", "Varrock Easy", "Cook's Assistant"),
                rows.stream().map(ItemRow::title).collect(Collectors.toList()));
            assertEquals(List.of(Terms.NEW, Terms.NEEDS_CHECKING, Terms.COPIED),
                rows.stream().map(row -> row.pill().getWord()).collect(Collectors.toList()));
            assertEquals(List.of(Tone.PENDING, Tone.NEUTRAL, Tone.GOOD),
                rows.stream().map(row -> row.pill().getTone()).collect(Collectors.toList()));
            assertTrue(texts(inbox).containsAll(List.of("Events for Zezima", "+4 more", "Copied 5 events.")));
            assertFalse(texts(inbox).contains(RollInboxView.EMPTY));
            assertEquals(List.of(Terms.COPY_FOR_TRACKER, "Open web Roll Inbox"), buttonTexts(inbox));
            buttons(inbox).get(0).doClick();
            assertEquals(1, copies[0]);

            List<Component> crosses = all(inbox, DismissButton.class::isInstance);
            assertEquals(3, crosses.size());
            assertEquals(DismissButton.LABEL, ((DismissButton) crosses.get(1)).getToolTipText());
            ((DismissButton) crosses.get(1)).doClick();
            assertEquals(List.of("b"), dismissed);

            // All copied: no count, and Copy again copies them again.
            inbox.apply(RollInboxModel.builder().rows(List.of(copiedRow)).copied(5).build());
            assertEquals(null, inbox.getCount());
            assertTrue(texts(inbox).contains(Terms.COPIED + " 5"));
            assertFalse(texts(inbox).contains("Events for Zezima"));
            assertFalse(texts(inbox).stream().anyMatch(text -> text.startsWith("+")));
            assertEquals(List.of(RollInboxView.COPY_AGAIN, "Open web Roll Inbox"), buttonTexts(inbox));
            buttons(inbox).get(0).doClick();
            assertEquals(2, copies[0]);
        });
    }

    @Test
    public void eachRowShowsItsTypesArt()
    {
        assertEquals(com.fatelocked.ui.Art.QUESTS, RollInboxView.art(row(FateEventType.QUEST)));
        assertEquals(com.fatelocked.ui.Art.COMBAT_TASK, RollInboxView.art(row(FateEventType.COMBAT_ACHIEVEMENT)));
        assertEquals(com.fatelocked.ui.Art.COLLECTION_LOG, RollInboxView.art(row(FateEventType.COLLECTION_LOG)));
        assertEquals(com.fatelocked.ui.Art.CLUE, RollInboxView.art(row(FateEventType.CLUE_CASKET)));
        assertEquals(com.fatelocked.ui.Art.COMBAT, RollInboxView.art(row(FateEventType.BOSS_KILL)));
        assertEquals(com.fatelocked.ui.Art.RAID, RollInboxView.art(row(FateEventType.RAID_COMPLETION)));
        assertEquals(com.fatelocked.ui.Art.DIARY, RollInboxView.art(row(FateEventType.DIARY_TASK)));
        assertEquals(null, RollInboxView.art(row(FateEventType.SKILL_LEVEL)));
        assertEquals(null, RollInboxView.art(row(null)));
    }

    private static RollInboxModel.Row row(FateEventType type)
    {
        return new RollInboxModel.Row("id", type, "Label", null, false, false);
    }

    private static List<String> buttonTexts(Container root)
    {
        return buttons(root).stream().map(FlatButton::getText).collect(Collectors.toList());
    }

    @Test
    public void strictModeExplainsItselfOnceUntilDismissed() throws Exception
    {
        onEdt(() -> {
            StrictModeView strict = new StrictModeView(IconSource.NONE);
            int[] dismissed = new int[1];
            strict.onIntroDismiss(() -> dismissed[0]++);
            strict.apply(StrictModeModel.off());
            strict.showIntro();
            assertTrue(strict.isExpanded());
            assertTrue(texts(strict).contains(StrictModeView.INTRO));

            buttons(strict).stream().filter(b -> b.getText().equals("Got it")).findFirst().get().doClick();
            assertEquals(1, dismissed[0]);
            assertFalse(strict.isIntroShown());
            assertFalse(texts(strict).contains(StrictModeView.INTRO));
        });
    }

    /**
     * The owner's call T6 in the accuracy review: the intro and the card's line say what Strict
     * Mode stops, as its setting does (StrictModeGoldenTest): a teleport of a kind the run hasn't
     * unlocked, and a worn item's teleport.
     */
    @Test
    public void strictModeSaysItStopsATeleportOfAKindNotUnlockedAndAWornItems()
    {
        for (String says : new String[]{StrictModeView.INTRO, StrictModeSectionPresenter.WHAT_IT_DOES,
            StrictModeModel.off().getDetail()})
        {
            assertTrue(says, says.contains("you haven't unlocked"));
            assertTrue(says, says.contains("Teleport Tablets"));
            assertFalse(says, says.contains("equipment"));
        }
        assertTrue(StrictModeView.INTRO, StrictModeView.INTRO.contains("A worn item's teleport"));
    }

    @Test
    public void theConnectionSwitchAsksBeforeItChangesAnything() throws Exception
    {
        onEdt(() -> {
            ConnectionView connection = new ConnectionView();
            assertFalse("closed until the player opens it", connection.isExpanded());
            connection.setExpanded(true);
            List<Boolean> asked = new ArrayList<>();
            connection.onSyncToggle(asked::add);
            connection.apply(new ConnectionModel(false, null, null, CardAction.CONNECT, false, false, "Off"));
            connection.syncSwitch().doClick();
            assertEquals(Collections.singletonList(true), asked);
            assertTrue(buttons(connection).stream().noneMatch(b -> b.getText().equals("Disconnect")));

            connection.apply(new ConnectionModel(true, "…cdef", null, CardAction.REPAIR, true, true, "Online"));
            assertTrue(buttons(connection).stream().anyMatch(b -> b.getText().equals("Disconnect")));
            assertTrue(buttons(connection).stream().anyMatch(b -> b.getText().equals("Check now")));
        });
    }

    private static List<String> texts(Container root)
    {
        return all(root, com.fatelocked.ui.TextBlock.class::isInstance).stream()
            .map(block -> ((com.fatelocked.ui.TextBlock) block).getText()).collect(Collectors.toList());
    }

    private static List<Fold> folds(Container root)
    {
        return all(root, Fold.class::isInstance).stream().map(Fold.class::cast).collect(Collectors.toList());
    }

    /** The small labels shown, such as a closed category's summary, leaving out the " · " between. */
    private static List<String> labels(Container root)
    {
        List<String> shown = new ArrayList<>();
        for (Fold fold : folds(root))
        {
            for (Component label : all(fold, JLabel.class::isInstance))
            {
                String text = ((JLabel) label).getText();
                if (!text.isEmpty() && !text.trim().equals("·") && !text.matches("\\d+"))
                {
                    shown.add(text);
                }
            }
        }
        return shown;
    }

    private static List<FlatButton> buttons(Container root)
    {
        return all(root, FlatButton.class::isInstance).stream().map(FlatButton.class::cast)
            .collect(Collectors.toList());
    }

    private static List<Component> all(Container root, Predicate<Component> match)
    {
        List<Component> found = new ArrayList<>();
        for (Component child : root.getComponents())
        {
            if (match.test(child) && child.isVisible())
            {
                found.add(child);
            }
            if (child instanceof Container && child.isVisible())
            {
                found.addAll(all((Container) child, match));
            }
        }
        return found;
    }

    private static void onEdt(Runnable task) throws Exception
    {
        SwingUtilities.invokeAndWait(task);
    }
}
