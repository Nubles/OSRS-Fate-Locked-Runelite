package com.fatelocked.sidebar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Fold;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.ItemRow;
import com.fatelocked.ui.Palette.Tone;
import com.fatelocked.ui.Section;
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
            assertFalse("the line belongs to the place the player left",
                texts(here).contains("Can't find Yew tree near you here."));
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
    public void theRollInboxCountsWarningsAndSaysWhenSavingFailed() throws Exception
    {
        onEdt(() -> {
            RollInboxView inbox = new RollInboxView();
            inbox.setExpanded(true);
            int[] opened = new int[1];
            inbox.onOpen(() -> opened[0]++);
            inbox.apply(new RollInboxModel(4, 1, 2, true));
            assertTrue(texts(inbox).contains("Saving the local history failed."));
            assertTrue(texts(inbox).contains(RollInboxView.LOCAL_ONLY));
            buttons(inbox).get(0).doClick();
            assertEquals(1, opened[0]);

            inbox.apply(new RollInboxModel(4, 1, 0, false));
            assertFalse(texts(inbox).contains("Saving the local history failed."));
        });
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
