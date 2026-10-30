package com.fatelocked;

import com.fatelocked.rules.ItemTier;
import com.fatelocked.ui.TierMarks;
import com.fatelocked.ui.Type;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Fate in the game's own item slots (plugin vision idea 2): gear in the inventory, bank and
 * worn equipment shows its tier as a Roman numeral in the bottom-left corner, green when its
 * slot is unlocked that far and red when it's above, as the Item Charges plugin marks charges.
 * Unrated items and other characters' rules draw nothing. The over-tier gear warning stays.
 */
public class FateLockedItemTierOverlay extends WidgetItemOverlay
{
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedItemTierOverlay(FateLockedPlugin plugin, FateLockedConfig config)
    {
        this.plugin = plugin;
        this.config = config;
        showOnInventory();
        showOnBank();
        showOnEquipment();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (!config.itemTierMarks()) return;
        ItemTier tier = plugin.decisions().itemTierAnywhere(itemId);
        if (tier == null) return;
        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds == null) return;
        TierMarks.shadowed(graphics, TierMarks.roman(tier.getTier()), bounds.x, bounds.y + bounds.height - 1,
            Type.small(), tier.isOver() ? TierMarks.ABOVE : TierMarks.ALLOWED);
    }
}
