package com.fatelocked.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Stage 3's design language, held in place: colours come from {@link Palette}, fonts
 * from {@link Type} at their own size, and marks from OSRS art, never from symbols
 * RuneLite's fonts can't draw (U15, U17).
 *
 * <p>Each list names the files that still break a rule. A task that fixes a file
 * takes it off its list; a new file can't join one.
 */
public class UiBoundaryTest
{
    private static final Set<String> COLOUR_LITERALS = new TreeSet<>(List.of(
        "FateLockedConfig.java", "FateLockedPlugin.java"));
    private static final Set<String> DERIVED_FONTS = new TreeSet<>();
    private static final Set<String> SYMBOLS = new TreeSet<>();

    /** Marks and arrows RuneLite's RuneScape fonts have no glyph for. */
    private static final String UNDRAWABLE = "✓✔✕✖✗○●⚠"
        + "▶▸▼▾←→";
    private static final Pattern LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])'");

    @Test
    public void coloursComeFromThePalette() throws IOException
    {
        assertEquals(COLOUR_LITERALS, filesWhere(text -> text.contains("new Color("), "Palette.java"));
    }

    @Test
    public void fontsAreNeverDerived() throws IOException
    {
        assertEquals(DERIVED_FONTS, filesWhere(text -> text.contains("deriveFont("), null));
    }

    @Test
    public void textNeverUsesSymbolsTheGameFontsCantDraw() throws IOException
    {
        assertEquals(SYMBOLS, filesWhere(UiBoundaryTest::hasUndrawableLiteral, null));
    }

    @Test
    public void theScanFindsTheSources() throws IOException
    {
        assertTrue(sources().size() > 50);
        assertTrue(hasUndrawableLiteral("x = \"here ✓\";"));
        assertTrue(!hasUndrawableLiteral("/** Continent → chunks */"));
    }

    private static boolean hasUndrawableLiteral(String text)
    {
        for (String line : text.split("\n"))
        {
            String code = line.trim();
            if (code.startsWith("*") || code.startsWith("/*") || code.startsWith("//"))
            {
                continue;
            }
            Matcher literal = LITERAL.matcher(code);
            while (literal.find())
            {
                for (char c : literal.group().toCharArray())
                {
                    if (UNDRAWABLE.indexOf(c) >= 0)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private interface Rule
    {
        boolean broken(String text);
    }

    private static Set<String> filesWhere(Rule rule, String allowed) throws IOException
    {
        Set<String> offenders = new TreeSet<>();
        for (Path source : sources())
        {
            String name = source.getFileName().toString();
            if (name.equals(allowed))
            {
                continue;
            }
            if (rule.broken(new String(Files.readAllBytes(source), StandardCharsets.UTF_8)))
            {
                offenders.add(name);
            }
        }
        return offenders;
    }

    private static List<Path> sources() throws IOException
    {
        try (Stream<Path> paths = Files.walk(Paths.get("src", "main", "java")))
        {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }
}
