package com.fatelocked;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A fairy ring's dials, read as RuneLite's own Fairy Rings plugin reads them (FAIRYRING_1 to _3,
 * interface 398, its CONFIRM button), and a confirm read only once a turn has reached the client.
 */
public class FairyRingDialTest
{
    private final Client client = mock(Client.class);

    @Test
    public void threeDialValuesSpellACode()
    {
        assertEquals("AIP", FairyRingDial.code(0, 0, 0));
        assertEquals("CKS", FairyRingDial.code(2, 2, 1));
        assertEquals("DLR", FairyRingDial.code(1, 1, 2));
        assertEquals("BJQ", FairyRingDial.code(3, 3, 3));
        assertNull(FairyRingDial.code(4, 0, 0));
        assertNull(FairyRingDial.code(0, -1, 0));
        assertNull(FairyRingDial.code(0, 0, 9));
    }

    @Test
    public void everyCodeIsThreeLettersOneFromEachDial()
    {
        int codes = 0;
        for (int left = 0; left < 4; left++)
        {
            for (int middle = 0; middle < 4; middle++)
            {
                for (int right = 0; right < 4; right++)
                {
                    String code = FairyRingDial.code(left, middle, right);
                    assertTrue(code, code.matches("[A-D][I-L][P-S]"));
                    codes++;
                }
            }
        }
        assertEquals(64, codes);
    }

    @Test
    public void theDialsAreReadFromTheirVarbits()
    {
        dials(2, 2, 1);
        assertEquals("CKS", FairyRingDial.read(client));
    }

    @Test
    public void aConfirmJustAfterATurnHasNoCodeUntilTheDialsChangeOrTwoTicksPass()
    {
        FairyRingDial dial = new FairyRingDial();
        assertEquals("no turn yet", "AIP", dial.settled(10, "AIP"));
        assertNull(dial.settled(10, null));

        dial.turned(10, "AIP");
        assertNull("the same tick, the old code", dial.settled(10, "AIP"));
        assertNull("a tick later, still the old code", dial.settled(11, "AIP"));
        assertEquals("the dials changed: the turn arrived", "AIS", dial.settled(11, "AIS"));
        assertEquals("two ticks later the dials are taken as they are", "AIP", dial.settled(12, "AIP"));
        assertEquals("a tick count that went back means a new login", "AIP", dial.settled(3, "AIP"));
    }

    @Test
    public void theConfirmButtonIsTheTripAndTheRestOfTheDialsMayTurnThem()
    {
        assertTrue(FairyRingDial.isConfirm(InterfaceID.Fairyrings.CONFIRM));
        assertFalse(FairyRingDial.mayTurn(InterfaceID.Fairyrings.CONFIRM));
        assertTrue(FairyRingDial.mayTurn(InterfaceID.Fairyrings._1_CLOCKWISE));
        assertTrue(FairyRingDial.mayTurn(InterfaceID.Fairyrings._3_ANTICLOCKWISE));
        assertTrue("the travel log's Use code", FairyRingDial.mayTurn(WidgetUtil.packComponentId(InterfaceID.FAIRYRINGS_LOG, 7)));
        assertFalse(FairyRingDial.mayTurn(WidgetUtil.packComponentId(InterfaceID.INVENTORY, 0)));
        assertFalse(FairyRingDial.isConfirm(WidgetUtil.packComponentId(InterfaceID.INVENTORY, 26)));
    }

    /** The click's facts: the confirm's first op carries the dialled code; nothing else does. */
    @Test
    public void aClickReaderReadsTheCodeForTheConfirmOnly()
    {
        dials(2, 2, 1);
        when(client.getTickCount()).thenReturn(100);
        MenuFactsReader reader = new MenuFactsReader(client, new FairyRingDial());

        MenuFacts confirm = reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings.CONFIRM, 1));
        assertEquals(MenuFacts.Kind.WIDGET, confirm.getKind());
        assertEquals(InterfaceID.FAIRYRINGS, confirm.getInterfaceGroup());
        assertEquals(InterfaceID.Fairyrings.CONFIRM, confirm.getComponentId());
        assertEquals("CKS", confirm.getFairyRingCode());

        assertNull("another op on the button", reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings.CONFIRM, 2))
            .getFairyRingCode());
        assertNull(reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings._1_CLOCKWISE, 1)).getFairyRingCode());
    }

    @Test
    public void aConfirmRightAfterATurnFailsOpen()
    {
        dials(2, 2, 1);
        when(client.getTickCount()).thenReturn(100);
        MenuFactsReader reader = new MenuFactsReader(client, new FairyRingDial());

        reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings._2_CLOCKWISE, 1));
        assertNull("the turn hasn't reached the dials", reader.read(
            widget(MenuAction.CC_OP, InterfaceID.Fairyrings.CONFIRM, 1)).getFairyRingCode());

        dials(2, 3, 1);
        assertEquals("CJS", reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings.CONFIRM, 1))
            .getFairyRingCode());

        // An older kind of button on the dials counts as a turn too, but is never a trip itself.
        MenuFacts older = reader.read(widget(MenuAction.WIDGET_TYPE_1, InterfaceID.Fairyrings._3_CLOCKWISE, 0));
        assertEquals(MenuFacts.Kind.OTHER, older.getKind());
        assertNull(reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings.CONFIRM, 1)).getFairyRingCode());
        when(client.getTickCount()).thenReturn(102);
        assertEquals("CJS", reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings.CONFIRM, 1))
            .getFairyRingCode());
    }

    /** The menu tag's reader follows no dials: it never reads them. */
    @Test
    public void aReaderWithoutTheDialNeverReadsThem()
    {
        MenuFactsReader reader = new MenuFactsReader(client);

        MenuFacts confirm = reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings.CONFIRM, 1));
        assertEquals(InterfaceID.Fairyrings.CONFIRM, confirm.getComponentId());
        assertNull(confirm.getFairyRingCode());
        reader.read(widget(MenuAction.CC_OP, InterfaceID.Fairyrings._1_CLOCKWISE, 1));
        verify(client, never()).getVarbitValue(anyInt());
        verify(client, never()).getTickCount();
    }

    @Test
    public void theDialsAreOpenWhileTheConfirmButtonShows()
    {
        assertFalse(FairyRingDial.isOpen(client));
        assertNull(FairyRingDial.bounds(client));

        Widget confirm = component(InterfaceID.Fairyrings.CONFIRM, new Rectangle(240, 150, 40, 40));
        Widget layer = component(WidgetUtil.packComponentId(InterfaceID.FAIRYRINGS, 0), new Rectangle(4, 4, 512, 334));
        Widget mount = component(WidgetUtil.packComponentId(InterfaceID.TOPLEVEL, 9), new Rectangle(0, 0, 765, 503));
        when(confirm.getParent()).thenReturn(layer);
        when(layer.getParent()).thenReturn(mount);
        when(client.getWidget(InterfaceID.Fairyrings.CONFIRM)).thenReturn(confirm);

        assertTrue(FairyRingDial.isOpen(client));
        assertEquals("the dials' own outermost component, not the one they're mounted in",
            new Rectangle(4, 4, 512, 334), FairyRingDial.bounds(client));

        when(confirm.isHidden()).thenReturn(true);
        assertFalse(FairyRingDial.isOpen(client));
        assertNull(FairyRingDial.bounds(client));
    }

    private void dials(int left, int middle, int right)
    {
        when(client.getVarbitValue(VarbitID.FAIRYRING_1)).thenReturn(left);
        when(client.getVarbitValue(VarbitID.FAIRYRING_2)).thenReturn(middle);
        when(client.getVarbitValue(VarbitID.FAIRYRING_3)).thenReturn(right);
    }

    private static MenuEntry widget(MenuAction type, int component, int op)
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(type);
        when(entry.getOption()).thenReturn("Confirm");
        when(entry.getTarget()).thenReturn("");
        when(entry.getParam1()).thenReturn(component);
        when(entry.getIdentifier()).thenReturn(op);
        when(entry.getItemId()).thenReturn(-1);
        return entry;
    }

    private static Widget component(int id, Rectangle bounds)
    {
        Widget widget = mock(Widget.class);
        when(widget.getId()).thenReturn(id);
        when(widget.getBounds()).thenReturn(bounds);
        return widget;
    }
}
