package com.fatelocked;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.fatelocked.sidebar.PointTarget;
import com.fatelocked.sidebar.ShopKeepers;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/**
 * plugin-ui.d1[0]: who runs each shop. The plugin ships the tracker's keeper table as its own
 * resource, a byte-for-byte copy that scripts/pin-web-contracts.sh keeps in step with the pinned
 * contracts, and every shop row of the golden bundles gains a keeper name from it or from what
 * its name gives.
 */
public class ShopKeepersGoldenTest
{
    private static final Gson GSON = new Gson();
    private static final String RESOURCE = "/com/fatelocked/sidebar/shop-keepers.json";
    /**
     * Shop names the pinned bundles still use from before the tracker renamed them; the table
     * holds the new names ("Arhein's General Goods"), so a re-pin of the bundles drops these.
     */
    private static final Set<String> OLD_NAMES = new TreeSet<>(Arrays.asList("Arhein Store"));

    /** The resource is the pinned copy, when the pinned contracts carry one. */
    @Test
    public void theShippedTableIsThePinnedCopy() throws Exception
    {
        JsonObject files = GoldenBundleContractTest.json("manifest.json").getAsJsonObject("files");
        assumeTrue("the pinned contracts list no shop-keepers.json yet, so there is nothing to compare",
            files != null && files.has("shop-keepers.json"));
        byte[] shipped;
        try (InputStream in = ShopKeepersGoldenTest.class.getResourceAsStream(RESOURCE))
        {
            shipped = in.readAllBytes();
        }
        assertArrayEquals("re-run scripts/pin-web-contracts.sh instead of editing the copy",
            GoldenBundleContractTest.bytes("shop-keepers.json"), shipped);
    }

    /**
     * Every SHOPS row with targetKind SHOP of every golden bundle gains a keeper name from the
     * table or from what its name gives (an owner, a general store's shop keeper, an inn's
     * bartender). A shop the table lists with no keeper is a reviewed decision, counted apart.
     */
    @Test
    public void everyGoldenShopGainsAKeeper() throws Exception
    {
        Set<String> shops = new TreeSet<>();
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            JsonObject wire = GSON.fromJson(GoldenBundleContractTest.gunzip(
                GoldenBundleContractTest.bytes(scenario[0] + ".bundle.json.gz")), JsonObject.class);
            for (Map.Entry<String, JsonElement> chunk : wire.getAsJsonObject("rules").getAsJsonObject("chunks").entrySet())
            {
                JsonObject categories = chunk.getValue().getAsJsonObject().getAsJsonObject("categories");
                if (categories == null || !categories.has("SHOPS")) continue;
                for (JsonElement row : categories.getAsJsonArray("SHOPS"))
                {
                    JsonObject shop = row.getAsJsonObject();
                    if (shop.has("targetKind") && "SHOP".equals(shop.get("targetKind").getAsString()))
                    {
                        shops.add(shop.get("name").getAsString());
                    }
                }
            }
        }
        ShopKeepers table = ShopKeepers.shared();
        List<String> none = new ArrayList<>();
        List<String> reviewed = new ArrayList<>();
        for (String shop : shops)
        {
            String label = rowName(shop);
            PointTarget target = PointTarget.of("SHOPS", label);
            boolean gains = !table.of(label).isEmpty() || target.getNames().size() > 1;
            if (gains) continue;
            if (table.lists(label)) reviewed.add(shop);
            else if (!OLD_NAMES.contains(shop)) none.add(shop);
        }
        assertEquals(450, shops.size());
        assertEquals("shop rows with no keeper name", new ArrayList<String>(), none);
        assertEquals("shops the table lists with no keeper, as reviewed", Arrays.asList("Farming Supplies"), reviewed);
    }

    /** The name the Here card shows for a row, as HerePresenter.rowName gives it. */
    private static String rowName(String name)
    {
        int hash = name.indexOf('#');
        if (hash <= 0 || hash == name.length() - 1) return name;
        return name.substring(0, hash).trim() + " (" + name.substring(hash + 1).trim() + ")";
    }
}
