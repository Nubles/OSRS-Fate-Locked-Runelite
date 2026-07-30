# RuneLite Plugin Panel Legibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct clipped section headers, translucent color-preview artifacts, and unclear Run key balances in the unified RuneLite sidebar.

**Architecture:** Keep the existing seven-section Swing panel and configuration binding intact. Correct sizing where `CollapsiblePanelSection` owns header state, convert only the Swing preview background to opaque RGB inside `FateLockedConfigBinder`, and expose each existing run-state key field as its own statistics row in `FateLockedPanel`.

**Tech Stack:** Java 11, Swing, RuneLite `PluginPanel`, JUnit 4, Mockito, Gradle 8.7

## Global Constraints

- Preserve one plugin descriptor, one sidebar button, seven sections, and all 30 retained settings.
- Preserve the section order, default expansion state, and independent collapse behavior.
- Preserve each rendering color's original RGBA value for the chooser and RuneLite configuration.
- Use the exact Run labels `Keys`, `Omni Keys`, and `Chaos Keys`.
- Do not change connection, relay, rule, warning, guardian, rendering, or persistence behavior.
- Use test-first development: observe each regression test fail before changing production code.

---

### Task 1: Give collapsible headers their full text height

**Files:**
- Modify: `src/test/java/com/fatelocked/CollapsiblePanelSectionTest.java`
- Modify: `src/main/java/com/fatelocked/CollapsiblePanelSection.java`

**Interfaces:**
- Consumes: existing `setExpanded(boolean)`, `headerForTest()`, and `fullWidth(JComponent)`
- Produces: `updateHeader()` as the single owner of header text and height refresh

- [ ] **Step 1: Write the failing header-height regression**

Add this JUnit test:

```java
@Test
public void headerHeightFitsItsArrowAndTitleInBothStates()
{
    CollapsiblePanelSection section =
        new CollapsiblePanelSection("Rendering", false);

    assertTrue(section.headerForTest().getMaximumSize().height
        >= section.headerForTest().getPreferredSize().height);

    section.setExpanded(true);

    assertTrue(section.headerForTest().getMaximumSize().height
        >= section.headerForTest().getPreferredSize().height);
}
```

This fails because the constructor currently calls `fullWidth(header)` while
the button text is still empty, fixing its maximum height too early.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
gradle test --tests com.fatelocked.CollapsiblePanelSectionTest --no-daemon
```

Expected: `headerHeightFitsItsArrowAndTitleInBothStates` fails because the
maximum height is smaller than the preferred height.

- [ ] **Step 3: Refresh text before constraining the height**

Remove the constructor's early `fullWidth(header)` call. Add:

```java
private void updateHeader()
{
    header.setText((expanded ? "\u25bc " : "\u25b6 ") + title);
    fullWidth(header);
}
```

Then change `setExpanded` to:

```java
void setExpanded(boolean expanded)
{
    this.expanded = expanded;
    body.setVisible(expanded);
    updateHeader();
    revalidate();
    repaint();
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2.

Expected: all `CollapsiblePanelSectionTest` tests pass.

- [ ] **Step 5: Commit the header correction**

```powershell
git add src/main/java/com/fatelocked/CollapsiblePanelSection.java src/test/java/com/fatelocked/CollapsiblePanelSectionTest.java
git commit -m "fix: size collapsible headers after setting text"
```

---

### Task 2: Render opaque color previews while preserving RGBA

**Files:**
- Modify: `src/test/java/com/fatelocked/FateLockedConfigBinderTest.java`
- Modify: `src/main/java/com/fatelocked/FateLockedConfigBinder.java`
- Modify: `src/test/java/com/fatelocked/FateLockedPanelStatusTest.java`

**Interfaces:**
- Consumes: `colorSetting(String, String, Supplier<Color>)`
- Produces: opaque button backgrounds derived from saved colors; unchanged saved and chooser `Color` objects

- [ ] **Step 1: Write the failing translucent-preview regression**

Add a test that supplies a translucent saved color, captures the chooser's
initial color, chooses another translucent color, and asserts opaque previews:

```java
@Test
public void translucentColoursUseOpaquePreviewsButPersistOriginalRgba()
{
    Color initial = new Color(16, 185, 129, 90);
    Color chosen = new Color(239, 68, 68, 120);
    Color[] chooserInitial = new Color[1];
    FateLockedConfigBinder colorBinder = new FateLockedConfigBinder(
        configManager, statuses::add, (parent, title, supplied) -> {
            chooserInitial[0] = supplied;
            return chosen;
        });
    JButton control = (JButton) colorBinder.colorSetting(
        "unlockedColor", "Unlocked color", () -> initial);

    assertEquals(new Color(16, 185, 129), control.getBackground());
    control.doClick();

    assertSame(initial, chooserInitial[0]);
    verify(configManager).setConfiguration(
        FateLockedConfig.GROUP, "unlockedColor", chosen);
    assertEquals(new Color(239, 68, 68), control.getBackground());
    assertEquals(255, control.getBackground().getAlpha());
}
```

Update `colourChoicePersistsSelectedColour` so it asserts equality with the
opaque RGB preview rather than object identity with the stored value.

- [ ] **Step 2: Run the focused binder test and verify RED**

Run:

```powershell
gradle test --tests com.fatelocked.FateLockedConfigBinderTest --no-daemon
```

Expected: the new test fails because the button background still retains the
configured alpha.

- [ ] **Step 3: Make only the preview opaque**

Replace `applyColor` with:

```java
private static void applyColor(JButton control, Color color)
{
    Color preview = color == null
        ? Color.BLACK
        : new Color(color.getRed(), color.getGreen(), color.getBlue());
    control.setBackground(preview);
}
```

Do not alter `confirmed`, the chooser input, or the value passed to
`ConfigManager.setConfiguration`.

- [ ] **Step 4: Align the panel initialization assertion**

In `FateLockedPanelStatusTest.configSuppliersInitializeTheOwnedControls`,
assert that the frontier control has `new Color(12, 34, 56)` as its background
while the mocked supplier still returns `new Color(12, 34, 56, 78)`.

- [ ] **Step 5: Run binder and panel tests and verify GREEN**

Run:

```powershell
gradle test --tests com.fatelocked.FateLockedConfigBinderTest --tests com.fatelocked.FateLockedPanelStatusTest --no-daemon
```

Expected: both test classes pass.

- [ ] **Step 6: Commit the color-preview correction**

```powershell
git add src/main/java/com/fatelocked/FateLockedConfigBinder.java src/test/java/com/fatelocked/FateLockedConfigBinderTest.java src/test/java/com/fatelocked/FateLockedPanelStatusTest.java
git commit -m "fix: render opaque configuration color previews"
```

---

### Task 3: Give each Run key balance a named row

**Files:**
- Modify: `src/test/java/com/fatelocked/FateLockedPanelStatusTest.java`
- Modify: `src/main/java/com/fatelocked/FateLockedPanel.java`

**Interfaces:**
- Consumes: `FateLockedBundle.RunState.getKeys()`, `getSpecialKeys()`, and `getChaosKeys()`
- Produces: separate `JLabel` values beside `Keys`, `Omni Keys`, and `Chaos Keys`

- [ ] **Step 1: Write the failing Run-row regression**

Replace the compressed-key assertion in
`runValuesStillUpdateInsideRunSection` with:

```java
assertEquals("0", valueBesideLabel(run, "Keys"));
assertEquals("0", valueBesideLabel(run, "Omni Keys"));
assertEquals("0", valueBesideLabel(run, "Chaos Keys"));
```

Also assert that each value label uses the existing amber foreground.

- [ ] **Step 2: Run the focused panel test and verify RED**

Run:

```powershell
gradle test --tests com.fatelocked.FateLockedPanelStatusTest --no-daemon
```

Expected: the test fails because `Omni Keys` and `Chaos Keys` do not exist and
`Keys` still contains the abbreviated combined string.

- [ ] **Step 3: Add separate values and rows**

Replace `keysVal` with:

```java
private final JLabel keysVal = value();
private final JLabel omniKeysVal = value();
private final JLabel chaosKeysVal = value();
```

Build the Run grid with:

```java
new String[]{
    "Profile", "Account", "Run ID",
    "Keys", "Omni Keys", "Chaos Keys",
    "Fate", "Buff", "Goal"
}
```

and the corresponding value-label array. In `update`, set the three text
values independently and apply `AMBER` to all three. In the missing-state
branch, set all three to an em dash.

- [ ] **Step 4: Run the focused panel test and verify GREEN**

Run the command from Step 2.

Expected: all `FateLockedPanelStatusTest` tests pass.

- [ ] **Step 5: Commit the Run-row correction**

```powershell
git add src/main/java/com/fatelocked/FateLockedPanel.java src/test/java/com/fatelocked/FateLockedPanelStatusTest.java
git commit -m "fix: label RuneLite key balances separately"
```

---

### Task 4: Full verification and live narrow-panel check

**Files:**
- Verify: `src/main/java/**`
- Verify: `src/test/java/**`
- Verify: `build/libs/fatelocked-0.1.0.jar`

**Interfaces:**
- Consumes: the three completed UI corrections
- Produces: a verified Plugin Hub candidate and updated sideloaded test JAR

- [ ] **Step 1: Run the full clean verification**

Run:

```powershell
gradle clean check verifyPluginHubJar --no-daemon
```

Expected: all tests pass and `verifyPluginHubJar` passes.

- [ ] **Step 2: Re-run boundary scans**

Run:

```powershell
rg -n "/events|/acks|/suggest|/state|FateEventRelayClient|FateEventOutbox|RequestBody|localhost|127\.0\.0\.1" src/main/java
rg -n "@PluginDescriptor" src/main/java
rg -n "NavigationButton\.builder" src/main/java
```

Expected: no forbidden match, exactly one descriptor, and exactly one sidebar
button builder.

- [ ] **Step 3: Replace the local sideloaded test JAR**

After confirming the exact source and destination, copy
`build/libs/fatelocked-0.1.0.jar` to
`C:\Users\alexa\.runelite\sideloaded-plugins\fatelocked-0.1.0.jar` and verify
matching SHA-256 hashes.

- [ ] **Step 4: Relaunch the isolated safe-mode client**

Close only the current temporary RuneLite candidate, then use
`C:\tmp\runelite-candidate3.init.gradle` to launch `runCandidateJar`.

Expected: one `Fate Locked Ironman` plugin is sideloaded.

- [ ] **Step 5: Visually verify the three reported defects**

At the normal narrow sidebar width:

- all seven collapsed headers show complete arrow and title glyphs;
- Rendering shows one clean tooltip row and four clean color rows with no
  retained or overlapping text;
- Run shows `Keys`, `Omni Keys`, and `Chaos Keys` as separate rows.

- [ ] **Step 6: Record final status**

Confirm both feature worktrees are clean except ignored build/report output.
Do not merge, push, deploy, or submit.
