package com.fatelocked.preview;

import com.fatelocked.ui.Card;
import com.fatelocked.ui.FlatButton;
import com.fatelocked.ui.Hairline;
import com.fatelocked.ui.ItemRow;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Palette.Tone;
import com.fatelocked.ui.ProgressBar;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.Space;
import com.fatelocked.ui.StatTiles;
import com.fatelocked.ui.StatusPill;
import com.fatelocked.ui.TextBlock;
import com.fatelocked.ui.ToggleSwitch;
import com.fatelocked.ui.Type;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;

/** Every component of the kit on one sidebar, with made-up content, for design review. */
public final class KitGallery
{
    private static final int WIDTH = PluginPanel.PANEL_WIDTH - 2 * Space.EDGE - 2 * Space.PAD;

    private KitGallery()
    {
    }

    public static JComponent build()
    {
        JPanel root = new JPanel(new DynamicGridLayout(0, 1, 0, Space.GAP));
        root.setBackground(Palette.PANEL);
        root.setBorder(new EmptyBorder(Space.EDGE, Space.EDGE, Space.EDGE, Space.EDGE));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(2, 2, 2, 2));
        header.add(text("Fate Locked", Type.title(), Palette.TITLE, 1), BorderLayout.CENTER);
        header.add(new FlatButton("Open tracker", FlatButton.Kind.LINK), BorderLayout.EAST);
        root.add(header);

        Card status = new Card();
        status.setAccent(Palette.defaults().text(Tone.GOOD));
        status.add(text("Rules up to date", Type.title(), Palette.defaults().text(Tone.GOOD), 1));
        status.add(text("Synced 2 min ago for Iron Example.", Type.body(), Palette.TEXT_MUTED, 0));
        root.add(status);

        Card waiting = new Card();
        waiting.setAccent(Palette.defaults().text(Tone.PENDING));
        waiting.add(text("Waiting for confirmation", Type.title(), Palette.defaults().text(Tone.PENDING), 1));
        waiting.add(text("Confirm this profile in the browser tab RuneLite opened.", Type.body(),
            Palette.TEXT_MUTED, 0));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(new FlatButton("Open page again", FlatButton.Kind.PRIMARY));
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new java.awt.Dimension(Space.GAP, 1));
        actions.add(spacer);
        actions.add(new FlatButton("Cancel", FlatButton.Kind.SECONDARY));
        waiting.add(actions);
        root.add(waiting);

        Section here = new Section("Here", true);
        JPanel title = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        title.setOpaque(false);
        title.add(text("Falador", Type.title(), Palette.TITLE, 1), BorderLayout.CENTER);
        title.add(new StatusPill("Locked", Tone.BAD), BorderLayout.EAST);
        here.body().add(title);
        here.body().add(text("Asgarnia · 46, 52", Type.small(), Palette.TEXT_MUTED, 1));
        here.body().add(text("Unlock Falador in the tracker to use this area.", Type.body(),
            Palette.TEXT_MUTED, 2));
        StatTiles tiles = new StatTiles();
        tiles.show(new StatTiles.Tile(14, "Can do", Tone.GOOD), new StatTiles.Tile(24, "Not ready", Tone.PENDING),
            new StatTiles.Tile(35, "Locked", Tone.BAD));
        here.body().add(tiles);
        here.body().add(new Hairline());
        here.body().add(text("Quests", Type.small(), Palette.TEXT_MUTED, 1));
        here.body().add(new ItemRow().show("Cook's Assistant", "Can do", Tone.GOOD, null));
        here.body().add(new ItemRow().show("Recipe for Disaster", "Not ready", Tone.PENDING,
            "Needs Cook's Assistant, Fishing Contest, Goblin Diplomacy, Big Chompy Bird Hunting and 8 more quests"));
        here.body().add(new ItemRow().show("Black Knights' Fortress", "Locked", Tone.BAD,
            "Unlock Black Knights' Fortress in the tracker"));
        here.body().add(new ItemRow().show("The Knight's Sword", "Needs checking", Tone.NEUTRAL, null));
        here.body().add(new FlatButton("+19 more", FlatButton.Kind.LINK));
        root.add(here);

        Section strict = new Section("Strict Mode", true);
        ToggleSwitch toggle = new ToggleSwitch();
        toggle.setSelected(true);
        strict.setTrailing(toggle);
        JPanel state = new JPanel(new BorderLayout(Space.ICON_GAP, 0));
        state.setOpaque(false);
        state.add(new StatusPill("Active", Tone.GOOD), BorderLayout.WEST);
        state.add(new FlatButton("Pause 60 s", FlatButton.Kind.SECONDARY), BorderLayout.EAST);
        strict.body().add(state);
        strict.body().add(text("Stops a teleport to a place your rules lock.", Type.small(), Palette.TEXT_MUTED, 0));
        root.add(strict);

        Section run = new Section("Run", true);
        run.setCount("Iron Example");
        run.body().add(text("15 of 187 areas unlocked", Type.body(), Palette.TEXT, 1));
        ProgressBar bar = new ProgressBar();
        bar.setFraction(15 / 187.0);
        run.body().add(bar);
        root.add(run);

        Section connection = new Section("Connection & backup", false);
        connection.setCount("Online");
        root.add(connection);

        root.add(text("More settings are in RuneLite's configuration, under Fate Locked Ironman.", Type.small(),
            Palette.TEXT_MUTED, 0));
        return root;
    }

    private static TextBlock text(String value, java.awt.Font font, java.awt.Color colour, int lines)
    {
        TextBlock block = new TextBlock(font, colour, lines, WIDTH);
        block.setText(value);
        return block;
    }
}
