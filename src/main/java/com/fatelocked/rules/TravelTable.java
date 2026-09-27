package com.fatelocked.rules;

import com.fatelocked.CanonicalChunk;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tracker's travel table (F1), from {@code rules.travel}. Each method is
 * matched by exactly one of a spell (its spellbook and name), item ids,
 * object ids or NPC ids. Each option, keyed by the menu option's exact text,
 * lists the chunks it can go to and the tracker's decision for the run.
 *
 * <p>Read leniently, like {@code knownMobility}: a malformed method or
 * option is dropped, never the bundle. The code sets the outer limits that
 * only a Hub release can widen: an option that is never travel is dropped
 * whatever the table says, and the table, each method and each option have
 * caps.
 */
public final class TravelTable
{
    public static final int MAX_METHODS = 1000;
    public static final int MAX_OPTIONS = 64;
    /** Ids per method: the charter ships match 144 crew ids. */
    public static final int MAX_IDS = 256;
    public static final int MAX_DESTINATIONS = 64;
    /** Labels, option texts and reasons are cut to this. */
    public static final int MAX_TEXT = 120;
    public static final List<String> SPELLBOOKS =
        Collections.unmodifiableList(Arrays.asList("standard", "ancient", "lunar", "arceuus"));
    /**
     * Options that are never travel, whatever a table says: the web's own
     * list (NON_TRAVEL_OPTIONS in data/travelMethods.ts) and a few more.
     */
    public static final Set<String> NON_TRAVEL_OPTIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "walk here", "attack", "talk-to", "trade", "bank", "collect", "deposit", "wear", "wield", "equip",
        "remove", "use", "drop", "examine", "check", "configure", "inspect", "pickpocket", "follow", "cancel",
        "destroy", "take", "eat", "drink")));
    /** A fairy ring code: three dials of four letters. */
    private static final Pattern CODE = Pattern.compile("[A-D][I-L][P-S]");
    /** A fairy ring's option for the last code it was dialled to. */
    private static final Pattern LAST_DESTINATION =
        Pattern.compile("last-destination \\(([a-z]{3})\\)", Pattern.CASE_INSENSITIVE);

    public enum Match
    {
        SPELL, ITEMS, OBJECTS, NPCS
    }

    private final Map<String, Method> methods;
    private final Map<String, List<Method>> spells = new HashMap<>();
    private final Map<Match, Map<Integer, List<Method>>> byId = new HashMap<>();

    private TravelTable(Map<String, Method> methods)
    {
        this.methods = Collections.unmodifiableMap(methods);
        for (Method method : methods.values())
        {
            if (method.match == Match.SPELL)
            {
                spells.computeIfAbsent(spellKey(method.spellbook, method.spell), key -> new ArrayList<>()).add(method);
                continue;
            }
            Map<Integer, List<Method>> index = byId.computeIfAbsent(method.match, match -> new HashMap<>());
            for (Integer id : method.ids) index.computeIfAbsent(id, key -> new ArrayList<>()).add(method);
        }
    }

    /** The table a {@code rules.travel} section holds; null when it isn't an object. */
    public static TravelTable parse(JsonElement declaration)
    {
        if (declaration == null || !declaration.isJsonObject()) return null;
        Map<String, Method> methods = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : declaration.getAsJsonObject().entrySet())
        {
            if (methods.size() == MAX_METHODS) break;
            Method method = method(entry.getKey(), entry.getValue());
            if (method != null) methods.put(method.id, method);
        }
        return new TravelTable(methods);
    }

    /** Every method, in the table's order. */
    public Collection<Method> methods()
    {
        return methods.values();
    }

    /** The method with this id; null when the table has none. */
    public Method method(String id)
    {
        return id == null ? null : methods.get(id);
    }

    /** The methods for a spell in a spellbook, its name ignoring case. */
    public List<Method> spell(String spellbook, String name)
    {
        if (spellbook == null || name == null) return Collections.emptyList();
        return Collections.unmodifiableList(spells.getOrDefault(spellKey(spellbook, name), Collections.emptyList()));
    }

    /** The methods matched by an id of this kind: items, objects or NPCs. */
    public List<Method> byId(Match match, int id)
    {
        Map<Integer, List<Method>> index = byId.get(match);
        List<Method> found = index == null ? null : index.get(id);
        return found == null ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    private static String spellKey(String spellbook, String name)
    {
        return spellbook + "|" + name.trim().toLowerCase(Locale.ROOT);
    }

    private static Method method(String id, JsonElement value)
    {
        if (isBlank(id) || id.length() > MAX_TEXT || value == null || !value.isJsonObject()) return null;
        JsonObject row = value.getAsJsonObject();
        String label = text(row.get("label"));
        if (label == null) return null;
        JsonElement matchValue = row.get("match");
        if (matchValue == null || !matchValue.isJsonObject()) return null;
        JsonObject match = matchValue.getAsJsonObject();
        List<Match> kinds = new ArrayList<>();
        if (match.has("spell")) kinds.add(Match.SPELL);
        if (match.has("items")) kinds.add(Match.ITEMS);
        if (match.has("objects")) kinds.add(Match.OBJECTS);
        if (match.has("npcs")) kinds.add(Match.NPCS);
        if (kinds.size() != 1) return null;
        Match kind = kinds.get(0);

        String spellbook = null;
        String spell = null;
        Set<Integer> ids = Collections.emptySet();
        if (kind == Match.SPELL)
        {
            JsonElement spellValue = match.get("spell");
            if (!spellValue.isJsonObject()) return null;
            spellbook = text(spellValue.getAsJsonObject().get("book"));
            spell = text(spellValue.getAsJsonObject().get("name"));
            if (spellbook == null || spell == null || !SPELLBOOKS.contains(spellbook)) return null;
        }
        else
        {
            ids = ids(match.get(kind.name().toLowerCase(Locale.ROOT)));
            if (ids == null) return null;
        }

        Map<String, Option> options = options(row.get("options"), false);
        Map<String, Option> codes = options(row.get("codes"), true);
        if (options == null || codes == null || options.isEmpty() && codes.isEmpty()) return null;
        return new Method(id, label, strings(row.get("unlocks")), kind, spellbook, spell, ids, options, codes,
            advisory(row.get("advisory")));
    }

    /** At least one id and at most {@link #MAX_IDS}, skipping any that isn't one; null otherwise. */
    private static Set<Integer> ids(JsonElement value)
    {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > MAX_IDS) return null;
        Set<Integer> ids = new LinkedHashSet<>();
        for (JsonElement item : value.getAsJsonArray())
        {
            Integer id = wholeNumber(item);
            if (id != null) ids.add(id);
        }
        return ids.isEmpty() ? null : Collections.unmodifiableSet(ids);
    }

    /**
     * Each option by its text, dropping one that is malformed or never
     * travel; for codes, only the dials' letters. Empty when there are none,
     * and null when there are too many to choose from.
     */
    private static Map<String, Option> options(JsonElement value, boolean codes)
    {
        if (value == null || !value.isJsonObject()) return Collections.emptyMap();
        if (value.getAsJsonObject().size() > MAX_OPTIONS) return null;
        Map<String, Option> options = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet())
        {
            String text = entry.getKey() == null ? "" : entry.getKey().trim();
            if (text.isEmpty() || text.length() > MAX_TEXT) continue;
            if (codes ? !CODE.matcher(text).matches() : NON_TRAVEL_OPTIONS.contains(text.toLowerCase(Locale.ROOT)))
            {
                continue;
            }
            Option option = option(codes ? "code:" + text : text, entry.getValue());
            // Texts are matched ignoring case, so only the first of two that differ in case counts.
            if (option != null && seen.add(text.toLowerCase(Locale.ROOT))) options.put(text, option);
        }
        return Collections.unmodifiableMap(options);
    }

    /**
     * An option with every destination a chunk key and a status. One bad
     * destination drops the option: skipping it could leave one of two
     * places, and one place is what Strict Mode acts on.
     */
    private static Option option(String text, JsonElement value)
    {
        if (value == null || !value.isJsonObject()) return null;
        JsonObject row = value.getAsJsonObject();
        JsonElement to = row.get("to");
        if (to == null || !to.isJsonArray() || to.getAsJsonArray().size() > MAX_DESTINATIONS) return null;
        Set<CanonicalChunk> destinations = new LinkedHashSet<>();
        for (JsonElement key : to.getAsJsonArray())
        {
            CanonicalChunk chunk = chunk(key);
            if (chunk == null) return null;
            destinations.add(chunk);
        }
        PermissionStatus status = status(row.get("status"));
        if (status == null) return null;
        return new Option(text, new ArrayList<>(destinations), status, text(row.get("reason")));
    }

    /** A "cx,cy" chunk key; null when it isn't one. */
    private static CanonicalChunk chunk(JsonElement key)
    {
        String text = string(key);
        if (text == null) return null;
        String[] xy = text.split(",", -1);
        if (xy.length != 2) return null;
        try
        {
            int cx = Integer.parseInt(xy[0].trim());
            int cy = Integer.parseInt(xy[1].trim());
            return cx < 0 || cy < 0 ? null : new CanonicalChunk(cx, cy);
        }
        catch (NumberFormatException notAKey)
        {
            return null;
        }
    }

    /** A status name; an unknown one reads as UNKNOWN, as every reader of the four names does. Null when not a string. */
    private static PermissionStatus status(JsonElement value)
    {
        String name = string(value);
        if (name == null) return null;
        try
        {
            return PermissionStatus.valueOf(name.trim());
        }
        catch (IllegalArgumentException unknown)
        {
            return PermissionStatus.UNKNOWN;
        }
    }

    /** Advisory unless the table says plainly that it isn't. */
    private static boolean advisory(JsonElement value)
    {
        if (value == null || value.isJsonNull()) return false;
        return !(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && !value.getAsBoolean());
    }

    private static List<String> strings(JsonElement value)
    {
        if (value == null || !value.isJsonArray()) return Collections.emptyList();
        List<String> strings = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray())
        {
            String text = text(item);
            if (text != null) strings.add(text);
        }
        return Collections.unmodifiableList(strings);
    }

    private static Integer wholeNumber(JsonElement value)
    {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
        double number = value.getAsDouble();
        return number < 0 || number > Integer.MAX_VALUE || number != Math.floor(number) ? null : (int) number;
    }

    /** A string trimmed and cut to {@link #MAX_TEXT}; null when blank or not a string. */
    private static String text(JsonElement value)
    {
        String text = string(value);
        if (isBlank(text)) return null;
        text = text.trim();
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT - 1) + "…";
    }

    private static String string(JsonElement value)
    {
        if (value == null || !value.isJsonPrimitive()) return null;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        return primitive.isString() ? primitive.getAsString() : null;
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    /** One way to travel, as the tracker's table has it. */
    @Getter
    public static final class Method
    {
        private final String id;
        private final String label;
        /** The unlocks it needs, as the web names them; its options' decisions already count them. */
        private final List<String> unlocks;
        private final Match match;
        /** A spell's spellbook: "standard", "ancient", "lunar" or "arceuus"; null for anything else. */
        private final String spellbook;
        /** A spell's name; null for anything else. */
        private final String spell;
        /** The item, object or NPC ids that match; empty for a spell. */
        private final Set<Integer> ids;
        /** Each option by its text, in the table's order. */
        private final Map<String, Option> options;
        /** A fairy ring's codes by code ("CKS"); empty for anything else. */
        private final Map<String, Option> codes;
        /** Tagged but never blocked: networks and boats in Stage 2. */
        private final boolean advisory;
        private final Map<String, Option> byText = new HashMap<>();

        Method(String id, String label, List<String> unlocks, Match match, String spellbook, String spell,
            Set<Integer> ids, Map<String, Option> options, Map<String, Option> codes, boolean advisory)
        {
            this.id = id;
            this.label = label;
            this.unlocks = unlocks;
            this.match = match;
            this.spellbook = spellbook;
            this.spell = spell;
            this.ids = ids;
            this.options = options;
            this.codes = codes;
            this.advisory = advisory;
            for (Map.Entry<String, Option> option : options.entrySet())
            {
                byText.put(option.getKey().toLowerCase(Locale.ROOT), option.getValue());
            }
        }

        /**
         * The option with this exact text, ignoring case. A fairy ring's
         * "Last-destination (CKS)" is its code's. Null when there is none.
         */
        public Option option(String text)
        {
            if (text == null) return null;
            String trimmed = text.trim();
            Matcher last = LAST_DESTINATION.matcher(trimmed);
            if (!codes.isEmpty() && last.matches()) return codes.get(last.group(1).toUpperCase(Locale.ROOT));
            return byText.get(trimmed.toLowerCase(Locale.ROOT));
        }
    }

    /** One menu option of a method: where it can go, and the tracker's decision for the run. */
    @Getter
    public static final class Option
    {
        /** Its text as the table keys it: "Edgeville", or "code:CKS" for a fairy ring code. */
        private final String text;
        /** Every chunk it can go to. */
        private final List<CanonicalChunk> to;
        private final PermissionStatus status;
        /** Why, unless allowed, in the tracker's words; null when not given. */
        private final String reason;

        Option(String text, List<CanonicalChunk> to, PermissionStatus status, String reason)
        {
            this.text = text;
            this.to = Collections.unmodifiableList(to);
            this.status = status;
            this.reason = reason;
        }

        /** Its one destination; null when it can go to several places, or the table doesn't say where. */
        public CanonicalChunk destination()
        {
            return to.size() == 1 ? to.get(0) : null;
        }
    }
}
