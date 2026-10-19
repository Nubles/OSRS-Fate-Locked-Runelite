package com.fatelocked;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;

/**
 * A fairy ring's dials (interface 398), read as RuneLite's own Fairy Rings plugin reads them: three
 * varbits, one per dial, each 0 to 3, give the code the confirm button goes to. The interface only
 * says which click is the trip; where each code goes is the tracker's travel table.
 *
 * <p>A dial turns on the server: the click that turns it reaches the client's varbits a tick or so
 * later. A confirm clicked before then would read the code the dials showed before the turn, so
 * an instance remembers the last click that may have turned them, and {@link #settled} gives no
 * code (Strict Mode then lets the click through) until the dials have changed or two ticks have
 * passed.
 */
final class FairyRingDial
{
    /** Each dial's letters, by its varbit's value, as RuneLite's Fairy Rings plugin has them. */
    private static final String[] LEFT = {"A", "D", "C", "B"};
    private static final String[] MIDDLE = {"I", "L", "K", "J"};
    private static final String[] RIGHT = {"P", "S", "R", "Q"};
    /** Ticks after a turning click before the dials' varbits are taken as current. */
    static final int SETTLE_TICKS = 2;
    private static final int NEVER = Integer.MIN_VALUE;

    private int turnedAt = NEVER;
    private String codeWhenTurned;

    /** The code three dial values spell, such as "CKS"; null when one is out of range. */
    static String code(int left, int middle, int right)
    {
        if (!dial(left) || !dial(middle) || !dial(right)) return null;
        return LEFT[left] + MIDDLE[middle] + RIGHT[right];
    }

    private static boolean dial(int value)
    {
        return value >= 0 && value < 4;
    }

    /** The code the dials show now, from their varbits; on the client thread. */
    static String read(Client client)
    {
        return code(client.getVarbitValue(VarbitID.FAIRYRING_1), client.getVarbitValue(VarbitID.FAIRYRING_2),
            client.getVarbitValue(VarbitID.FAIRYRING_3));
    }

    /** Whether the dials are open: their confirm button is loaded and shown, as RuneLite's plugin checks. */
    static boolean isOpen(Client client)
    {
        Widget confirm = client.getWidget(InterfaceID.Fairyrings.CONFIRM);
        return confirm != null && !confirm.isHidden();
    }

    /**
     * Where the dials' interface is on the canvas, this frame: the outermost component of interface
     * 398 above the confirm button. Null while the dials aren't open.
     */
    static Rectangle bounds(Client client)
    {
        Widget widget = client.getWidget(InterfaceID.Fairyrings.CONFIRM);
        if (widget == null || widget.isHidden()) return null;
        for (int depth = 0; depth < 16; depth++)
        {
            Widget parent = widget.getParent();
            if (parent == null || WidgetUtil.componentToInterface(parent.getId()) != InterfaceID.FAIRYRINGS) break;
            widget = parent;
        }
        Rectangle bounds = widget.getBounds();
        return bounds == null || bounds.isEmpty() ? null : bounds;
    }

    /** Whether a clicked component is the dials' confirm button. */
    static boolean isConfirm(int componentId)
    {
        return componentId == InterfaceID.Fairyrings.CONFIRM;
    }

    /** Whether a clicked component may turn the dials: anything else on the dials or in their travel log. */
    static boolean mayTurn(int componentId)
    {
        int group = WidgetUtil.componentToInterface(componentId);
        return !isConfirm(componentId) && (group == InterfaceID.FAIRYRINGS || group == InterfaceID.FAIRYRINGS_LOG);
    }

    /** A click that may turn the dials, at this tick, while they showed this code. */
    void turned(int tick, String code)
    {
        turnedAt = tick;
        codeWhenTurned = code;
    }

    /**
     * The code a confirm at this tick goes to, given what the dials show: null when they may still
     * be turning (a turning click less than {@link #SETTLE_TICKS} ago, and they still show the code
     * they showed then), or when there is no code.
     */
    String settled(int tick, String code)
    {
        if (code == null) return null;
        if (turnedAt == NEVER) return code;
        long since = (long) tick - turnedAt;
        // A tick count that went back (a new login) means the turn is long past.
        boolean recent = since >= 0 && since < SETTLE_TICKS;
        return recent && code.equals(codeWhenTurned) ? null : code;
    }
}
