package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fatelocked.sidebar.Sidebar;
import com.fatelocked.ui.IconSource;
import com.fatelocked.ui.Section;
import com.fatelocked.ui.Terms;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

/**
 * G2 (U12): the plugin says what the web guide says. The web app's wording contract
 * (golden-bundles/runelite-wording.json, copied at the pinned commit) names the plugin's terms,
 * the words neither says, the settings and the sidebar's cards; the guide is held to the same file
 * in the web app. A change on one side fails the other's build until both agree again.
 */
public class WordingContractTest
{
    private static final Pattern LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    @Test
    public void eachTermIsTheContracts() throws Exception
    {
        Map<String, String> terms = new TreeMap<>();
        for (Field field : Terms.class.getFields())
        {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
            {
                terms.put(field.getName(), (String) field.get(null));
            }
        }
        Map<String, String> contract = new TreeMap<>();
        for (Map.Entry<String, JsonElement> term : contract().getAsJsonObject("terms").entrySet())
        {
            contract.put(term.getKey(), term.getValue().getAsString());
        }
        assertEquals(contract, terms);
    }

    @Test
    public void noTextInTheCodeSaysAnAvoidedWord() throws IOException
    {
        List<String> avoided = new ArrayList<>();
        for (JsonElement word : contract().getAsJsonArray("avoidedWords"))
        {
            avoided.add(word.getAsJsonObject().get("word").getAsString());
        }
        assertTrue(avoided.size() > 5);
        List<String> said = new ArrayList<>();
        for (Path source : sources())
        {
            for (String literal : literals(new String(Files.readAllBytes(source), StandardCharsets.UTF_8)))
            {
                for (String word : avoided)
                {
                    if (says(literal, word))
                    {
                        said.add(source.getFileName() + ": " + literal + " says " + word);
                    }
                }
            }
        }
        assertEquals(new ArrayList<String>(), said);
    }

    /** The visible settings, in the config panel's order, with their names, defaults and choices. */
    @Test
    public void theSettingsAreTheContracts() throws Exception
    {
        Map<String, ConfigSection> sections = new HashMap<>();
        for (Field field : FateLockedConfig.class.getDeclaredFields())
        {
            ConfigSection section = field.getAnnotation(ConfigSection.class);
            if (section != null)
            {
                sections.put((String) field.get(null), section);
            }
        }
        List<Method> items = new ArrayList<>();
        for (Method method : FateLockedConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null && !item.hidden())
            {
                items.add(method);
            }
        }
        items.sort(Comparator.comparingInt((Method method) -> sections.get(section(method)).position())
            .thenComparingInt(method -> method.getAnnotation(ConfigItem.class).position()));

        FateLockedConfig defaults = new FateLockedConfig() { };
        JsonArray settings = new JsonArray();
        for (Method method : items)
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            JsonObject setting = new JsonObject();
            setting.addProperty("key", item.keyName());
            setting.addProperty("section", sections.get(item.section()).name());
            setting.addProperty("name", item.name());
            setting.addProperty("defaultValue", shown(method.invoke(defaults)));
            if (method.getReturnType().isEnum())
            {
                JsonArray options = new JsonArray();
                for (Object option : method.getReturnType().getEnumConstants())
                {
                    options.add(option.toString());
                }
                setting.add("options", options);
            }
            settings.add(setting);
        }
        assertEquals(contract().getAsJsonArray("settings").toString(), settings.toString());

        JsonArray names = new JsonArray();
        sections.values().stream().sorted(Comparator.comparingInt(ConfigSection::position))
            .forEach(section -> names.add(section.name()));
        assertEquals(contract().getAsJsonArray("settingSections").toString(), names.toString());
    }

    @Test
    public void theSidebarsCardsAreTheContracts() throws Exception
    {
        List<String> titles = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> collect(new Sidebar(IconSource.NONE), titles));
        List<String> contract = new ArrayList<>();
        contract().getAsJsonArray("sidebarCards").forEach(card -> contract.add(card.getAsString()));
        assertEquals(contract, titles);
    }

    /** As the web's test matches: whole, case and all. */
    @Test
    public void aWordIsMatchedWholeCaseAndAll()
    {
        assertTrue(says("Travel Guardian", "Guardian"));
        assertFalse(says("Guardians of the Rift", "Guardian"));
        assertFalse(says("Omni-Keys", "Omni-keys"));
        assertTrue(says("spend your fate points.", "fate points"));
        assertEquals(List.of("\"a\"", "\"b \\\" c\""), literals("x = \"a\" + \"b \\\" c\"; // \"not code\""));
    }

    /** Whether the text says the word or phrase whole, not inside a longer word, with its case. */
    static boolean says(String text, String word)
    {
        return Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(word) + "(?![A-Za-z0-9])").matcher(text).find();
    }

    /** A setting's value as RuneLite's config panel shows it. */
    private static String shown(Object value)
    {
        if (value instanceof Boolean)
        {
            return (Boolean) value ? "On" : "Off";
        }
        if (value instanceof Color)
        {
            return String.format("#%08x", ((Color) value).getRGB());
        }
        return value.toString();
    }

    private static String section(Method method)
    {
        return method.getAnnotation(ConfigItem.class).section();
    }

    private static void collect(Container container, List<String> titles)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof Section)
            {
                titles.add(((Section) child).getTitle());
            }
            else if (child instanceof Container)
            {
                collect((Container) child, titles);
            }
        }
    }

    /** The string literals in code, leaving out comment lines and what follows a line comment. */
    private static List<String> literals(String source)
    {
        List<String> literals = new ArrayList<>();
        for (String line : source.split("\n"))
        {
            String code = line.trim();
            if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("//"))
            {
                continue;
            }
            Matcher literal = LITERAL.matcher(code);
            int from = 0;
            while (literal.find())
            {
                int comment = code.indexOf("//", from);
                if (comment >= 0 && comment < literal.start())
                {
                    break;
                }
                literals.add(literal.group());
                from = literal.end();
            }
        }
        return literals;
    }

    private static List<Path> sources() throws IOException
    {
        try (Stream<Path> paths = Files.walk(Paths.get("src", "main", "java")))
        {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    private static JsonObject contract() throws IOException
    {
        return GoldenBundleContractTest.json("runelite-wording.json");
    }
}
