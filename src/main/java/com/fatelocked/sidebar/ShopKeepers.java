package com.fatelocked.sidebar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Who runs each shop the tracker can name in a SHOPS row, so the Here card, the (Locked) tag,
 * the outlines and the pointer find a shop whose name doesn't give its keeper ("Fine Fashions":
 * Rometti). The table is the tracker's contracts/golden-bundles/shop-keepers.json, copied
 * byte for byte into this package's resources by scripts/pin-web-contracts.sh; it is never
 * edited by hand. A missing or unreadable table leaves only the names PointTarget works out
 * itself, and logs one line.
 */
@Slf4j
public final class ShopKeepers
{
    static final String RESOURCE = "shop-keepers.json";
    private static final Pattern SECTION = Pattern.compile("^(.*\\S)\\s*\\(([^()]*)\\)$");
    /** No table: every shop falls back to what its name gives. */
    public static final ShopKeepers NONE = new ShopKeepers(Collections.emptyMap());

    private static volatile ShopKeepers shared;

    /** Keepers by the shop's name as the Here card shows it, lower case; each list lower case. */
    private final Map<String, List<String>> keepers;

    private ShopKeepers(Map<String, List<String>> keepers)
    {
        this.keepers = keepers;
    }

    /**
     * The plugin's own table, read once on first use. It is about 22 kB of JSON, a few
     * milliseconds to read, so the first Here card or menu that needs it reads it in place.
     */
    public static ShopKeepers shared()
    {
        ShopKeepers table = shared;
        if (table == null)
        {
            synchronized (ShopKeepers.class)
            {
                table = shared;
                if (table == null)
                {
                    table = load(ShopKeepers.class.getResourceAsStream(RESOURCE));
                    shared = table;
                }
            }
        }
        return table;
    }

    /** The table in a stream, or {@link #NONE} when there is none or it can't be read. */
    static ShopKeepers load(InputStream in)
    {
        if (in == null)
        {
            log.warn("Shop keeper table {} not found; shops are found by their names only", RESOURCE);
            return NONE;
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
        {
            return read(reader);
        }
        catch (Exception unreadable)
        {
            log.warn("Shop keeper table {} unreadable; shops are found by their names only", RESOURCE, unreadable);
            return NONE;
        }
    }

    /**
     * A table of the tracker's shape, {"schema": 1, "shops": {"name": ["keeper", ...]}}. A shop
     * keyed "Name#Section" is the row the card shows as "Name (Section)". Entries that aren't a
     * list of strings are skipped.
     */
    static ShopKeepers read(Reader reader)
    {
        JsonElement root = new JsonParser().parse(reader);
        JsonElement shops = root != null && root.isJsonObject() ? root.getAsJsonObject().get("shops") : null;
        if (shops == null || !shops.isJsonObject())
        {
            throw new IllegalArgumentException("no shops object");
        }
        Map<String, List<String>> keepers = new HashMap<>();
        for (Map.Entry<String, JsonElement> shop : ((JsonObject) shops).entrySet())
        {
            if (shop.getValue() == null || !shop.getValue().isJsonArray())
            {
                continue;
            }
            Set<String> names = new LinkedHashSet<>();
            for (JsonElement name : shop.getValue().getAsJsonArray())
            {
                if (name != null && name.isJsonPrimitive() && name.getAsJsonPrimitive().isString()
                    && !name.getAsString().trim().isEmpty())
                {
                    names.add(key(name.getAsString()));
                }
            }
            keepers.merge(key(HerePresenter.rowName(shop.getKey())),
                Collections.unmodifiableList(new ArrayList<>(names)), ShopKeepers::both);
        }
        return new ShopKeepers(Collections.unmodifiableMap(keepers));
    }

    /**
     * The keepers of a shop by the name the card shows, lower case; for "Name (Section)" that the
     * table doesn't list, those of "Name". Empty when the table names no one.
     */
    public List<String> of(String shop)
    {
        String listed = listed(shop);
        return listed == null ? Collections.emptyList() : keepers.get(listed);
    }

    /** Whether the table lists this shop at all, even with no keeper. */
    public boolean lists(String shop)
    {
        return listed(shop) != null;
    }

    /** The table's key for a shop, or null when it doesn't list it. */
    private String listed(String shop)
    {
        if (shop == null)
        {
            return null;
        }
        String name = key(shop);
        if (keepers.containsKey(name))
        {
            return name;
        }
        Matcher section = SECTION.matcher(name);
        return section.matches() && keepers.containsKey(section.group(1).trim()) ? section.group(1).trim() : null;
    }

    private static List<String> both(List<String> a, List<String> b)
    {
        Set<String> names = new LinkedHashSet<>(a);
        names.addAll(b);
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    private static String key(String text)
    {
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
