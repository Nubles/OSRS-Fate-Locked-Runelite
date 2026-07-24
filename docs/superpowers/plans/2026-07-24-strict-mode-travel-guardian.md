# Strict Mode Travel Guardian Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing default-off Strict Mode stop and explain only travel clicks that fresh app-authored rules prove are locked, while suggesting a verified legal alternative and preserving Unknown-safe behaviour.

**Architecture:** A focused travel package recognises menu clicks, evaluates authoritative destination and method permissions, and ranks locally verified alternatives. The existing `StrictModeGuard` remains the sole enforcement owner; a transient interactive overlay presents the block and calls the existing shared 60-second pause controller.

**Tech Stack:** Java 11, RuneLite API/client, Swing side panel, RuneLite overlays and mouse input, Gson, Lombok, JUnit 4, Mockito, Gradle.

## Global Constraints

- Strict Mode remains one checkbox and defaults to `false`.
- Travel Guardian has no separate toggle.
- Only `PermissionStatus.LOCKED` from a fresh v4, correct-account rules context can produce `BLOCK`.
- `UNKNOWN`, `NOT_READY`, stale, legacy, malformed, missing, wrong-account, and unresolved decisions never block.
- Pausing Guardian pauses every Strict Mode category for exactly 60 seconds and resumes automatically.
- RuneLite never activates travel, moves the player, chooses an alternative, rolls, awards keys, or performs gameplay.
- Inventory, equipment, spellbook, and level checks remain local and are not persisted or uploaded.
- The bounded audit log must not contain account names, inventory contents, chat text, relay credentials, or exact routes.
- The Plugin Hub manifest is updated only after the standalone commit, mirror check, full Gradle gate, and live-client matrix pass.

## File structure

- `guardian/travel/TravelAction.java` — immutable recognised travel intent.
- `guardian/travel/TravelActionResolver.java` — converts a RuneLite menu entry into a travel intent without deciding permission.
- `guardian/travel/TravelDecision.java` — evaluation result and concise explanation.
- `guardian/travel/TravelRuleEvaluator.java` — destination and method permission evaluation.
- `guardian/travel/TravelAvailability.java` — minimal local capability interface used by alternative selection.
- `guardian/travel/RuneLiteTravelAvailability.java` — reads inventory, equipment, spellbook, and levels from `Client`.
- `guardian/travel/TravelAlternative.java` — immutable suggestion.
- `guardian/travel/TravelAlternativeCatalog.java` — checked common alternatives; no guessed routes.
- `guardian/travel/TravelAlternativeFinder.java` — filters and ranks verified legal alternatives.
- `guardian/travel/TravelBlockNotice.java` — immutable banner/chat presentation state.
- guardian/travel/TravelBlockNoticeStore.java — four-second expiry and repeat suppression.
- guardian/travel/TravelGuardianCoordinator.java — testable orchestration of recognition, evaluation, enforcement, alternatives, and notice state.
- guardian/travel/TravelGuardianResult.java — side-effect instructions returned to the plugin shell.
- `FateLockedTravelBlockOverlay.java` — viewport banner and clickable 60-second pause action.
- Existing guardian/plugin/panel/audit files — integration only; no unrelated refactor.

---

### Task 1: Recognise travel actions without enforcing them

**Files:**
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelAction.java`
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelActionResolver.java`
- Create: `src/test/java/com/fatelocked/guardian/travel/TravelActionResolverTest.java`
- Modify: `src/main/java/com/fatelocked/Teleports.java`

**Interfaces:**
- Consumes: `MenuEntry`, `Client`, `CanonicalChunk`, and the existing `Teleports` destination table.
- Produces: `TravelActionResolver.resolve(MenuEntry, Client, CanonicalChunk): TravelAction`.

- [ ] **Step 1: Write the failing table-driven resolver tests**

```java
@Test
public void resolvesNamedTeleportWalkAndCrossChunkObject()
{
    assertTravel(entry("Teleport", "Falador", MenuAction.UNKNOWN),
        TravelAction.Family.SPELL_OR_ITEM, new CanonicalChunk(46, 52), true);

    MenuEntry walk = entry("Walk here", "", MenuAction.WALK);
    when(walk.getParam0()).thenReturn(10);
    when(walk.getParam1()).thenReturn(20);
    when(client.getPlane()).thenReturn(0);
    try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
    {
        points.when(() -> WorldPoint.fromScene(client, 10, 20, 0))
            .thenReturn(new WorldPoint(3264, 3264, 0));
        assertTravel(walk, TravelAction.Family.WALK,
            new CanonicalChunk(51, 51), true);
    }

    MenuEntry door = entry("Open", "Gate", MenuAction.GAME_OBJECT_FIRST_OPTION);
    when(door.getParam0()).thenReturn(10);
    when(door.getParam1()).thenReturn(20);
    try (MockedStatic<WorldPoint> points = mockStatic(WorldPoint.class))
    {
        points.when(() -> WorldPoint.fromScene(client, 10, 20, 0))
            .thenReturn(new WorldPoint(3264, 3264, 0));
        assertTravel(door, TravelAction.Family.BOUNDARY_OBJECT,
            new CanonicalChunk(51, 51), true);
    }
}

@Test
public void sameChunkObjectAndUnknownWidgetStayUnresolved()
{
    TravelAction unknown = resolver.resolve(
        entry("Continue", "", MenuAction.UNKNOWN), client,
        new CanonicalChunk(50, 50));
    assertEquals(TravelAction.Confidence.UNKNOWN, unknown.getConfidence());
    assertNull(unknown.getDestination());
}
```

The test helper must assert family, destination, and confidence. Add recognition
fixtures for the exact keywords `fairy ring`, `spirit tree`, `gnome glider`,
`charter`, `mine cart`, `magic carpet`, `balloon`, `eagle`, `minigame
teleport`, and `quetzal`.

- [ ] **Step 2: Run the resolver test and verify the red state**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelActionResolverTest --no-daemon
```

Expected: compilation fails because `TravelAction` and
`TravelActionResolver` do not exist.

- [ ] **Step 3: Add the immutable travel contract**

```java
@Value
public class TravelAction
{
    public enum Family
    {
        WALK, BOUNDARY_OBJECT, SPELL_OR_ITEM, FAIRY_RING, SPIRIT_TREE,
        GNOME_GLIDER, CHARTER_SHIP, MINE_CART, MAGIC_CARPET, BALLOON,
        EAGLE, MINIGAME_TELEPORT, QUETZAL, OTHER_TRANSPORT, UNKNOWN
    }

    public enum Confidence { EXACT, UNKNOWN }

    Family family;
    String methodId;
    String label;
    CanonicalChunk origin;
    CanonicalChunk destination;
    String requiredUnlock;
    Confidence confidence;
}
```

`methodId` is a stable lowercase identifier such as `fairy-rings` or
`falador-teleport`. `requiredUnlock` uses the exact app-authored mobility
labels:

```text
Fairy Rings
Spirit Trees
Gnome Gliders
Charter Ships
Teleport Tablets
Jewelry Teleports
Balloon Transport
Mine Carts
Magic Carpets
Minigame Teleports
Quetzal Network
Eagle Transport
```

- [ ] **Step 4: Expose named destinations without duplicating the existing table**

Add to `Teleports`:

```java
public static Map<String, CanonicalChunk> destinations()
{
    Map<String, CanonicalChunk> result = new LinkedHashMap<>();
    for (Map.Entry<String, int[]> entry : PLACES.entrySet())
    {
        result.put(entry.getKey(),
            new CanonicalChunk(entry.getValue()[0], entry.getValue()[1]));
    }
    return Collections.unmodifiableMap(result);
}
```

Retain `destinationChunk` as the single named-destination matcher. Do not move
or copy `PLACES`.

- [ ] **Step 5: Implement recognition**

`TravelActionResolver.resolve` must:

1. normalise option/target using `Text.removeTags`;
2. resolve `MenuAction.WALK` from scene coordinates;
3. call `Teleports.destinationChunk` for named teleports/transports;
4. classify the family and exact mobility unlock using the keyword table above;
5. recognise `open`, `enter`, `climb`, `climb-up`, `climb-down`, `board`,
   `travel`, `pay-fare`, `squeeze-through`, and `cross` object options;
6. treat an object tile as an exact boundary destination only when its chunk
   differs from the supplied origin; and
7. return `Confidence.UNKNOWN` with no destination for every unresolved case.

Use this exact safe return shape:

```java
return new TravelAction(
    TravelAction.Family.UNKNOWN,
    "unknown",
    cleanLabel(option, target),
    origin,
    null,
    null,
    TravelAction.Confidence.UNKNOWN);
```

- [ ] **Step 6: Run the resolver tests**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelActionResolverTest --no-daemon
```

Expected: all resolver fixtures pass, including Unknown cases.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fatelocked/Teleports.java src/main/java/com/fatelocked/guardian/travel src/test/java/com/fatelocked/guardian/travel/TravelActionResolverTest.java
git commit -m "feat: recognize guarded travel actions"
```

---

### Task 2: Evaluate travel with app-authored rules

**Files:**
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelDecision.java`
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelRuleEvaluator.java`
- Create: `src/test/java/com/fatelocked/guardian/travel/TravelRuleEvaluatorTest.java`
- Modify: `src/main/java/com/fatelocked/rules/FateRuleEngine.java`
- Modify: `src/main/java/com/fatelocked/rules/RuneliteRulesManifest.java`
- Modify: `src/test/java/com/fatelocked/rules/FateRuleEngineTest.java`
- Modify in app: `utils/runeliteRulesManifest.ts`
- Modify in app: `utils/runeliteRulesManifest.test.ts`

**Interfaces:**
- Consumes: `TravelAction`, `FateRuleEngine.entry`, and exact mobility unlock labels.
- Produces: `TravelRuleEvaluator.evaluate(TravelAction, FateRuleEngine): TravelDecision`.

- [ ] **Step 1: Write failing rule-engine unlock tests**

Add fixtures to `FateRuleEngineTest` proving:

```java
assertEquals(PermissionStatus.ALLOWED,
    engine.mobility("Fairy Rings").getStatus());
assertEquals(PermissionStatus.LOCKED,
    engine.mobility("Spirit Trees").getStatus());
assertEquals(PermissionStatus.UNKNOWN,
    wrongAccountEngine.mobility("Fairy Rings").getStatus());
assertEquals(PermissionStatus.UNKNOWN,
    engine.mobility("Unmapped Network").getStatus());
```

The bundle fixture must include `Fairy Rings` in `rules.unlocks.mobility`,
exclude `Spirit Trees`, and include both names in a new
`rules.knownMobility` list. An absent known-name declaration stays Unknown
rather than being inferred Locked.

- [ ] **Step 2: Run the rule-engine test and verify it fails**

Run:

```bash
gradle test --tests com.fatelocked.rules.FateRuleEngineTest --no-daemon
```

Expected: FAIL because `knownMobility` and `mobility` do not exist.

- [ ] **Step 3: Extend the v4 manifest conservatively**

In app `utils/runeliteRulesManifest.ts`, import `MOBILITY_LIST` and add:

```ts
export interface RuneliteRulesManifest {
  knownMobility: string[];
}

// inside buildRuneliteRulesManifest(...)
knownMobility: sorted(MOBILITY_LIST),
```

Assert in `utils/runeliteRulesManifest.test.ts`:

```ts
expect(manifest.knownMobility).toEqual([...MOBILITY_LIST].sort());
expect(manifest.unlocks.mobility).toEqual(
  [...state.unlocks.mobility].sort(),
);
```

Run and commit the app contract first:

```bash
npm test -- utils/runeliteRulesManifest.test.ts utils/runeliteBundle.test.ts
npx tsc --noEmit
git add utils/runeliteRulesManifest.ts utils/runeliteRulesManifest.test.ts
git commit -m "feat: publish known mobility rules"
```

In standalone plugin `RuneliteRulesManifest`, parse:

```java
private List<String> knownMobility;
```

Copy it through `normalized()` using `immutableList(knownMobility)`. Malformed
or absent `knownMobility` therefore becomes an empty list and stays Unknown.

Add to `FateRuleEngine`:

```java
public RuleDecision mobility(String name)
{
    RuleDecision trust = trustDecision();
    if (trust != null) return trust;
    if (name == null || !contains(
        bundle.getRules().getKnownMobility(), name))
    {
        return unknown(name);
    }
    PermissionStatus status = contains(
        bundle.getRules().getUnlocks().getMobility(), name)
        ? PermissionStatus.ALLOWED : PermissionStatus.LOCKED;
    return new RuleDecision(status, name,
        status == PermissionStatus.LOCKED
            ? name + " is not unlocked" : null);
}
```

Comparison is case-insensitive and whitespace-normalised. Also add:

```java
public String areaLabel(CanonicalChunk chunk)
{
    if (chunk == null || trustDecision() != null) return null;
    return bundle.permissionsAt(chunk)
        .map(ChunkPermissionSnapshot::getName)
        .orElse(null);
}
```

Use this only for alternative ranking; a missing label never changes a
permission decision.

- [ ] **Step 4: Write the failing travel-evaluator truth table**

```java
@Test
public void onlyExactAuthoredLocksRemainLocked()
{
    assertEquals(PermissionStatus.LOCKED,
        evaluator.evaluate(exact(destination, null), lockedEngine).getStatus());
    assertEquals(PermissionStatus.LOCKED,
        evaluator.evaluate(exact(allowedChunk, "Fairy Rings"),
            lockedFairyRingEngine).getStatus());
    assertEquals(PermissionStatus.ALLOWED,
        evaluator.evaluate(exact(allowedChunk, "Fairy Rings"),
            allowedEngine).getStatus());
    assertEquals(PermissionStatus.UNKNOWN,
        evaluator.evaluate(unknown(), allowedEngine).getStatus());
    assertEquals(PermissionStatus.UNKNOWN,
        evaluator.evaluate(exact(allowedChunk, "Unmapped Network"),
            allowedEngine).getStatus());
}
```

- [ ] **Step 5: Implement the evaluation contract**

```java
@Value
public class TravelDecision
{
    PermissionStatus status;
    String label;
    String reason;
}
```

`TravelRuleEvaluator.evaluate` follows this order:

1. null/Unknown confidence/null destination → Unknown;
2. destination `LOCKED` → Locked with area/chunk reason;
3. destination `UNKNOWN` or `NOT_READY` → Unknown;
4. declared required mobility unlock `LOCKED` → Locked;
5. required mobility unlock `UNKNOWN` or `NOT_READY` → Unknown;
6. otherwise → Allowed.

Never combine two Unknown results into Locked.

- [ ] **Step 6: Run focused rules tests**

Run:

```bash
gradle test --tests com.fatelocked.rules.FateRuleEngineTest --tests com.fatelocked.guardian.travel.TravelRuleEvaluatorTest --no-daemon
```

Expected: both test classes pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fatelocked/rules src/main/java/com/fatelocked/guardian/travel src/test/java/com/fatelocked/rules src/test/java/com/fatelocked/guardian/travel
git commit -m "feat: evaluate authoritative travel permissions"
```

---

### Task 3: Make Strict Mode the only travel enforcement owner

**Files:**
- Modify: `src/main/java/com/fatelocked/guardian/StrictModeGuard.java`
- Modify: `src/main/java/com/fatelocked/guardian/StrictModeClickHandler.java`
- Modify: `src/test/java/com/fatelocked/guardian/StrictModeGuardTest.java`
- Modify: `src/test/java/com/fatelocked/guardian/StrictModeClickHandlerTest.java`

**Interfaces:**
- Consumes: `TravelAction`, `TravelDecision`, `GuardContext`.
- Produces: `StrictModeGuard.decideTravel(TravelAction, TravelDecision, GuardContext): GuardResult`.

- [ ] **Step 1: Write the failing Strict Mode travel invariant**

```java
@Test
public void travelBlocksOnlyFreshExactLockedDecisions()
{
    TravelAction exact = exactWalk(new CanonicalChunk(51, 51));
    TravelDecision locked = travelDecision(PermissionStatus.LOCKED);
    TravelDecision unknown = travelDecision(PermissionStatus.UNKNOWN);

    assertEquals(GuardResult.Outcome.BLOCK,
        guard.decideTravel(exact, locked, enabled()).getOutcome());
    assertEquals(GuardResult.Outcome.ALLOW,
        guard.decideTravel(exact, unknown, enabled()).getOutcome());
    assertEquals(GuardResult.Outcome.ALLOW,
        guard.decideTravel(exact, locked, disabled()).getOutcome());
    assertEquals(GuardResult.Outcome.ALLOW,
        guard.decideTravel(exact, locked, paused()).getOutcome());
    assertEquals(GuardResult.Outcome.ALLOW,
        guard.decideTravel(exact, locked, stale()).getOutcome());
    assertEquals(GuardResult.Outcome.ALLOW,
        guard.decideTravel(exact, locked, wrongAccount()).getOutcome());
}
```

Loop over every `PermissionStatus` and assert:

```java
if (result.getOutcome() == GuardResult.Outcome.BLOCK)
{
    assertEquals(PermissionStatus.LOCKED,
        result.getDecision().getStatus());
    assertEquals(TravelAction.Confidence.EXACT, action.getConfidence());
}
```

- [ ] **Step 2: Run the guard tests and verify they fail**

Run:

```bash
gradle test --tests com.fatelocked.guardian.StrictModeGuardTest --tests com.fatelocked.guardian.StrictModeClickHandlerTest --no-daemon
```

Expected: compilation fails because travel overloads do not exist.

- [ ] **Step 3: Add the travel guard overload**

Add:

```java
public GuardResult decideTravel(
    TravelAction action,
    TravelDecision decision,
    GuardContext context)
{
    if (action == null || decision == null || context == null
        || !context.isEnabled() || context.isPaused()
        || !context.isAccountMatches() || !context.isFreshRules()
        || action.getConfidence() != TravelAction.Confidence.EXACT)
    {
        return allow();
    }
    RuleDecision rule = new RuleDecision(
        decision.getStatus(), decision.getLabel(), decision.getReason());
    return decision.getStatus() == PermissionStatus.LOCKED
        ? new GuardResult(GuardResult.Outcome.BLOCK, rule)
        : allow(rule);
}
```

Do not change existing equipment/bank/NPC behaviour. Keep unresolved generic
`MOVEMENT` at `WARN_ONLY`; exact movement goes through `decideTravel`.

- [ ] **Step 4: Add a click-handler overload and consumption test**

```java
public GuardResult handleTravel(
    MenuOptionClicked event,
    TravelAction action,
    TravelDecision decision,
    GuardContext context)
{
    GuardResult result = guard.decideTravel(action, decision, context);
    if (result.getOutcome() == GuardResult.Outcome.BLOCK) event.consume();
    return result;
}
```

Verify `consume()` is called exactly once for Locked and never for Allowed,
Unknown, disabled, paused, stale, or wrong-account contexts.

- [ ] **Step 5: Run focused guard tests**

Run:

```bash
gradle test --tests com.fatelocked.guardian.StrictModeGuardTest --tests com.fatelocked.guardian.StrictModeClickHandlerTest --no-daemon
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/fatelocked/guardian src/test/java/com/fatelocked/guardian
git commit -m "feat: guard proven locked travel clicks"
```

---

### Task 4: Rank only locally verified alternatives

**Files:**
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelAvailability.java`
- Create: `src/main/java/com/fatelocked/guardian/travel/RuneLiteTravelAvailability.java`
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelAlternative.java`
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelAlternativeCatalog.java`
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelAlternativeFinder.java`
- Create: `src/test/java/com/fatelocked/guardian/travel/TravelAlternativeFinderTest.java`
- Create: `src/test/java/com/fatelocked/guardian/travel/RuneLiteTravelAvailabilityTest.java`

**Interfaces:**
- Produces: `TravelAlternativeFinder.find(TravelAction, FateRuleEngine, TravelAvailability): Optional<TravelAlternative>`.
- `TravelAvailability` exposes `hasAnyItem(Set<Integer>)`, `realLevel(Skill)`, and `spellbook()`.

- [ ] **Step 1: Write failing alternative ranking tests**

```java
@Test
public void prefersAllowedSameAreaAlternativeThatIsCarried()
{
    when(rules.entry(VARROCK)).thenReturn(allowed("Varrock"));
    when(rules.entry(FALADOR)).thenReturn(allowed("Falador"));
    when(availability.hasAnyItem(setOf(8007))).thenReturn(true);
    when(availability.hasAnyItem(setOf(8009))).thenReturn(true);

    Optional<TravelAlternative> result = finder.find(
        blockedTravelTo(VARROCK_REGION_EDGE), rules, availability);

    assertEquals("Varrock teleport tablet", result.get().getLabel());
    assertEquals(VARROCK, result.get().getDestination());
}

@Test
public void neverSuggestsLockedUnknownOrUnavailableMethods()
{
    when(rules.entry(VARROCK)).thenReturn(locked("Varrock"));
    when(rules.entry(FALADOR)).thenReturn(unknown("Falador"));
    when(availability.hasAnyItem(ArgumentMatchers.<Set<Integer>>any()))
        .thenReturn(false);
    assertFalse(finder.find(action, rules, availability).isPresent());
}
```

Also prove the selected alternative is data only: the finder has no `Client`
menu-action, interaction, invocation, or movement method.

- [ ] **Step 2: Run the tests and verify the red state**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelAlternativeFinderTest --tests com.fatelocked.guardian.travel.RuneLiteTravelAvailabilityTest --no-daemon
```

Expected: compilation fails because the alternative classes do not exist.

- [ ] **Step 3: Implement the local availability adapter**

```java
public interface TravelAvailability
{
    boolean hasAnyItem(Set<Integer> itemIds);
    int realLevel(Skill skill);
    int spellbook();
}
```

`RuneLiteTravelAvailability` reads only:

```java
client.getItemContainer(InventoryID.INVENTORY);
client.getItemContainer(InventoryID.EQUIPMENT);
client.getRealSkillLevel(skill);
client.getVarbitValue(Varbits.SPELLBOOK);
```

`hasAnyItem` scans item IDs locally and returns false for missing containers.
It exposes no serialization or network method.

- [ ] **Step 4: Add a minimal checked catalog**

Define the immutable suggestion contract:

```java
@Value
public class TravelAlternative
{
    String id;
    String label;
    CanonicalChunk destination;
    Set<Integer> requiredItemIds;
    Skill requiredSkill;
    int requiredLevel;
    Integer requiredSpellbook;
}
```

Use immutable Java definitions so item IDs and requirements are reviewed in one
place. The initial catalog contains these tablet alternatives:

```java
alternative("varrock-tablet", "Varrock teleport tablet",
    new CanonicalChunk(50, 53), setOf(8007));
alternative("lumbridge-tablet", "Lumbridge teleport tablet",
    new CanonicalChunk(50, 50), setOf(8008));
alternative("falador-tablet", "Falador teleport tablet",
    new CanonicalChunk(46, 52), setOf(8009));
alternative("camelot-tablet", "Camelot teleport tablet",
    new CanonicalChunk(43, 54), setOf(8010));
alternative("ardougne-tablet", "Ardougne teleport tablet",
    new CanonicalChunk(41, 51), setOf(8011));
alternative("watchtower-tablet", "Watchtower teleport tablet",
    new CanonicalChunk(39, 48), setOf(8012));
```

Each entry has optional required skill/level and spellbook fields, defaulting
to none. Do not add a spell alternative until rune availability can also be
verified.

- [ ] **Step 5: Implement deterministic ranking**

Filter out alternatives unless:

- `rules.entry(destination)` is `ALLOWED`;
- all declared requirements are satisfied; and
- at least one declared item is carried/equipped.

Rank by:

```text
same intended area = 0
adjacent destination chunk = 1
other allowed destination = 2
then Manhattan chunk distance
then stable alternative id
```

“Same intended area” means the normalised non-null values from
`rules.areaLabel(action.destination)` and `rules.areaLabel(candidate.destination)`
match. Missing area labels fall through to adjacency/distance and never make a
candidate eligible. Return `Optional.empty()` rather than a generic guess.

- [ ] **Step 6: Run alternative tests**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelAlternativeFinderTest --tests com.fatelocked.guardian.travel.RuneLiteTravelAvailabilityTest --no-daemon
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fatelocked/guardian/travel src/test/java/com/fatelocked/guardian/travel
git commit -m "feat: suggest verified legal travel alternatives"
```

---

### Task 5: Add the interactive block banner and chat deduplication

**Files:**
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelBlockNotice.java`
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelBlockNoticeStore.java`
- Create: `src/main/java/com/fatelocked/FateLockedTravelBlockOverlay.java`
- Create: `src/test/java/com/fatelocked/guardian/travel/TravelBlockNoticeStoreTest.java`
- Create: `src/test/java/com/fatelocked/FateLockedTravelBlockOverlayTest.java`

**Interfaces:**
- Produces: `TravelBlockNoticeStore.show(...)`, `current()`, and `shouldWriteChat(fingerprint)`.
- Overlay consumes the notice store and a `Runnable pauseGuardian`.

- [ ] **Step 1: Write failing expiry and deduplication tests**

```java
@Test
public void noticeExpiresAfterFourSecondsAndChatDeduplicatesForTen()
{
    store.show("fairy-rings:canifis", "Travel blocked — Fairy ring to Canifis",
        "Morytania is locked", "Varrock teleport tablet");
    assertTrue(store.current().isPresent());
    assertTrue(store.shouldWriteChat("fairy-rings:canifis"));
    assertFalse(store.shouldWriteChat("fairy-rings:canifis"));

    clock.advance(Duration.ofSeconds(4));
    assertFalse(store.current().isPresent());
    clock.advance(Duration.ofSeconds(6));
    assertTrue(store.shouldWriteChat("fairy-rings:canifis"));
}
```

Also verify a repeated `show` refreshes banner expiry without extending the
chat suppression window.

- [ ] **Step 2: Run the notice tests and verify the red state**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelBlockNoticeStoreTest --no-daemon
```

Expected: compilation fails because notice classes do not exist.

- [ ] **Step 3: Implement immutable notice state**

```java
@Value
public class TravelBlockNotice
{
    String fingerprint;
    String headline;
    String reason;
    String alternative;
    Instant expiresAt;
}
```

`TravelBlockNoticeStore` uses an injected `Clock`, synchronises state access,
expires at four seconds, and stores only a fingerprint→last-chat-time bounded
map capped at 32 entries.

- [ ] **Step 4: Write the failing overlay interaction test**

Render a notice, capture the generated pause-button bounds, then send a left
mouse press inside and outside those bounds:

```java
assertNull(overlay.mousePressed(insidePauseButton));
verify(pauseGuardian).run();

assertSame(outside, overlay.mousePressed(outside));
verifyNoMoreInteractions(pauseGuardian);
```

- [ ] **Step 5: Implement the overlay**

`FateLockedTravelBlockOverlay`:

- extends `Overlay`;
- implements RuneLite `MouseListener`; `mousePressed` invokes the pause callback
  and returns `null` only for a left click inside the pause bounds, while
  `mouseClicked`, `mouseReleased`, `mouseEntered`, `mouseExited`,
  `mouseDragged`, and `mouseMoved` return their input event unchanged;
- uses `OverlayPosition.TOP_CENTER` and `OverlayLayer.ABOVE_WIDGETS`;
- draws a dark translucent panel, red headline, white reason, optional amber
  alternative, and an amber `Pause Guardian for 60s` button;
- stores the last rendered button `Rectangle`;
- renders only while Strict Mode is enabled, not paused, and the notice is
  current; and
- invokes only the supplied pause callback.

- [ ] **Step 6: Run notice and overlay tests**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelBlockNoticeStoreTest --tests com.fatelocked.FateLockedTravelBlockOverlayTest --no-daemon
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/fatelocked/FateLockedTravelBlockOverlay.java src/main/java/com/fatelocked/guardian/travel src/test/java/com/fatelocked/FateLockedTravelBlockOverlayTest.java src/test/java/com/fatelocked/guardian/travel
git commit -m "feat: explain blocked travel in the viewport"
```

---

### Task 6: Wire Travel Guardian into the plugin and shared pause

**Files:**
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelGuardianCoordinator.java`
- Create: `src/main/java/com/fatelocked/guardian/travel/TravelGuardianResult.java`
- Create: `src/test/java/com/fatelocked/guardian/travel/TravelGuardianCoordinatorTest.java`
- Modify: `src/main/java/com/fatelocked/FateLockedPlugin.java`
- Modify: `src/main/java/com/fatelocked/FateLockedPanel.java`
- Modify: `src/main/java/com/fatelocked/guardian/StrictModeAuditEntry.java`
- Modify: `src/test/java/com/fatelocked/FateLockedPanelStatusTest.java`
- Modify: `src/test/java/com/fatelocked/guardian/StrictModeAuditLogTest.java`

**Interfaces:**
- Consumes all Tasks 1–5 interfaces.
- Produces `TravelGuardianCoordinator.handle(MenuOptionClicked, MenuEntry, Client, CanonicalChunk, GuardContext, FateRuleEngine, TravelAvailability): TravelGuardianResult`.
- The result tells the thin plugin shell whether to write chat/audit; the coordinator is the complete click → evaluate → block → alternative → notice flow.

- [ ] **Step 1: Write a failing integration test for the complete click path**

```java
@Test
public void provenLockedTravelIsConsumedAndExplainedOnce()
{
    MenuOptionClicked click = walkClickTo(51, 51);

    TravelGuardianResult first = coordinator.handle(
        click, click.getMenuEntry(), client, chunk(50, 51), enabled(),
        lockedRulesAt(51, 51), availability);
    TravelGuardianResult repeated = coordinator.handle(
        click, click.getMenuEntry(), client, chunk(50, 51), enabled(),
        lockedRulesAt(51, 51), availability);

    verify(click, times(2)).consume();
    assertEquals("Travel blocked — Walk here", noticeStore.current()
        .get().getHeadline());
    assertTrue(first.isWriteChat());
    assertFalse(repeated.isWriteChat());
    assertTrue(first.isWriteBlockedAudit());
}

@Test
public void pausedTravelIsAllowedAndMarkedForLocalAudit()
{
    MenuOptionClicked click = walkClickTo(51, 51);
    TravelGuardianResult result = coordinator.handle(
        click, click.getMenuEntry(), client, chunk(50, 51), paused(),
        lockedRulesAt(51, 51), availability);

    verify(click, never()).consume();
    assertFalse(noticeStore.current().isPresent());
    assertFalse(result.isWriteChat());
    assertTrue(result.isWritePausedAudit());
}
```

Add companion tests proving no consumption, banner, chat, or audit record when
Strict Mode is off, stale, wrong-account, legacy, Unknown, or `NOT_READY`.

- [ ] **Step 2: Run the integration test and verify it fails**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelGuardianCoordinatorTest --no-daemon
```

Expected: compilation fails because `TravelGuardianCoordinator` and its result contract do not exist.

- [ ] **Step 3: Implement the coordinator result contract**

```java
@Value
public class TravelGuardianResult
{
    TravelAction action;
    TravelDecision decision;
    TravelAlternative alternative;
    GuardResult guardResult;
    boolean writeChat;
    boolean writeBlockedAudit;
    boolean writePausedAudit;
}
```

`TravelGuardianCoordinator.handle` resolves and evaluates first. For a Locked
decision it safely ranks the optional alternative, stages the notice, then
calls the Task 3 click handler as the final enforcement operation. Alternative
lookup failure is caught and becomes “no suggestion”; it never cancels the
proven block. No fallible work occurs in the coordinator after `event.consume()`.
It returns an Unknown/Allow result without presentation when recognition or
trust is insufficient. When context is paused and an exact travel action is
recognised, it allows the click and sets only `writePausedAudit=true`.

- [ ] **Step 4: Initialise travel services at startup**

Create fields for resolver, evaluator, availability, finder, notice store, and
overlay. Use the already injected `Gson`, `Client`, `Clock.systemUTC()`, rules
bundle, overlay manager, and mouse manager.

At startup:

```java
overlayManager.add(travelBlockOverlay);
mouseManager.registerMouseListener(travelBlockOverlay);
travelBlockOverlay.setPauseGuardian(this::pauseStrictModeForSixtySeconds);
```

At shutdown, unregister the mouse listener before removing the overlay.

Extract the existing pause callback into:

```java
void pauseStrictModeForSixtySeconds()
{
    strictPause.pauseFor(Duration.ofSeconds(60));
    updateStrictModePanel();
}
```

Both panel and banner call this same method.

- [ ] **Step 5: Route recognised travel before generic guarding**

Inside `onMenuOptionClicked`:

1. build the existing fresh `GuardContext`;
2. resolve origin from the local player's `WorldPoint`;
3. call `TravelGuardianCoordinator.handle` once;
4. emit the requested chat and bounded audit side effects from its result;
5. return after an exact recognised travel action so the generic guard does not
   consume or explain the same click again; and
6. continue into the existing generic path only for unresolved travel, which
   can warn but not block movement.

Wrap the coordinator call in `try/catch (RuntimeException ex)` and continue
with the unconsumed action after a debug diagnostic. Because consumption is the
coordinator's final operation and no exception may escape after it, this catch
is a genuine fail-open path. Chat and audit writes are caught independently and
cannot change the already-present banner or enforcement result. The plugin shell
must not repeat resolution, rule evaluation, alternative ranking, or notice
mutation.

- [ ] **Step 6: Update banner, chat, and audit wording**

Use:

```text
Travel blocked — <label>
<reason>
Nearest legal option: <alternative>
```

Chat writes once per ten-second fingerprint:

```text
[Fate Guardian] Blocked <label>: <reason>. Suggested: <alternative>.
```

When no alternative exists, omit `Suggested` entirely.

Extend `StrictModeAuditEntry` with:

```java
String outcome;
boolean paused;
boolean alternativeAvailable;
```

For blocked travel, use `actionKind="TRAVEL"`, `outcome="BLOCKED"`, and `paused=false`. For recognised travel while paused, use `outcome="ALLOWED_PAUSED"`, `paused=true`, and no alternative. Do not
add inventory, equipment, account, chat, token, or route fields. Keep the
existing 100-entry cap and atomic persistence.

- [ ] **Step 7: Correct the Strict Mode intro copy**

Replace “Unknown actions and walking are never blocked” with:

```text
Strict Mode prevents only actions proven locked by fresh rules. Known locked
travel clicks can be stopped; uncertain movement is never blocked. Pause it
for 60 seconds here or turn it off immediately in plugin settings.
```

Assert in `FateLockedPanelStatusTest` that:

- Strict Mode off hides the pause button;
- Strict Mode on shows `Pause Strict Mode for 60 seconds`;
- paused state shows `Resume Strict Mode · 60s`; and
- no Travel Guardian checkbox exists.

- [ ] **Step 8: Run coordinator, panel, audit, and pause tests**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelGuardianCoordinatorTest --tests com.fatelocked.FateLockedPanelStatusTest --tests com.fatelocked.guardian.StrictModeAuditLogTest --tests com.fatelocked.guardian.StrictModePauseTest --no-daemon
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/fatelocked/FateLockedPlugin.java src/main/java/com/fatelocked/FateLockedPanel.java src/main/java/com/fatelocked/FateLockedTravelBlockOverlay.java src/main/java/com/fatelocked/guardian src/test/java/com/fatelocked
git commit -m "feat: integrate Strict Mode travel guardian"
```

---

### Task 7: Expand fixture coverage without broad guessing

**Files:**
- Modify: `src/test/java/com/fatelocked/guardian/travel/TravelActionResolverTest.java`
- Modify: `src/test/java/com/fatelocked/guardian/travel/TravelRuleEvaluatorTest.java`
- Modify: `src/test/java/com/fatelocked/guardian/travel/TravelGuardianCoordinatorTest.java`
- Modify: `src/main/java/com/fatelocked/Teleports.java`

**Interfaces:**
- Extends Task 1’s checked recognition table only.
- Does not change enforcement outcomes or add heuristics based on generic words such as `travel`, `enter`, or `teleport` without a resolved destination.

- [ ] **Step 1: Add one positive and two negative fixtures per family**

For every family listed in Task 1, add:

- one exact supported destination;
- one similarly worded unrelated action; and
- one supported method with an unresolved destination.

Examples:

```java
fixture("Travel", "Spirit tree — Tree Gnome Stronghold",
    SPIRIT_TREE, chunk(38, 53), EXACT);
fixture("Check", "Spirit tree health", UNKNOWN, null, UNKNOWN);
fixture("Travel", "Spirit tree — New destination", SPIRIT_TREE, null, UNKNOWN);

fixture("Pay-fare", "Magic carpet to Nardah",
    MAGIC_CARPET, chunk(53, 47), EXACT);
fixture("Talk-to", "Rug merchant", UNKNOWN, null, UNKNOWN);
fixture("Pay-fare", "Magic carpet to Unknown", MAGIC_CARPET, null, UNKNOWN);
```

- [ ] **Step 2: Run the fixtures and confirm missing mappings fail safely**

Run:

```bash
gradle test --tests com.fatelocked.guardian.travel.TravelActionResolverTest --no-daemon
```

Expected before mapping additions: positive known-destination fixtures fail as
Unknown; negative and unresolved fixtures already pass as Unknown.

- [ ] **Step 3: Add only the checked exact names to `Teleports.PLACES`**

Add canonical chunks for the positive fixture names. Keep longest-key-first
matching. Do not add substring-only completion rules or default destinations.

- [ ] **Step 4: Run the entire guardian test package**

Run:

```bash
gradle test --tests 'com.fatelocked.guardian.*' --tests com.fatelocked.guardian.travel.TravelGuardianCoordinatorTest --no-daemon
```

Expected: every fixture and safety invariant passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/fatelocked/Teleports.java src/test/java/com/fatelocked/guardian/travel src/test/java/com/fatelocked/guardian/travel/TravelGuardianCoordinatorTest.java
git commit -m "test: cover guarded travel families"
```

---

### Task 8: Documentation, full verification, mirror, and release gate

**Files:**
- Modify: `README.md`
- Modify: `CONTRIBUTING.md`
- Modify: companion app `docs/online-relay.md`
- Modify: companion app `runelite-plugin/`
- Modify after approval: Plugin Hub `plugins/fate-locked-ironman`

**Interfaces:**
- Produces one verified standalone plugin commit, one byte-identical app mirror commit, and one one-line Hub manifest PR.

- [ ] **Step 1: Document the player-facing behaviour**

README must state:

```text
Travel Guardian is part of the existing Strict Mode checkbox and is off by
default. It blocks only known locked travel clicks from fresh rules, explains
the reason in a short banner and chat record, and may suggest a locally
verified alternative. Unknown or stale travel is never blocked. RuneLite never
activates the suggestion or moves the player.
```

CONTRIBUTING must list the recognition confidence rule, the Unknown invariant,
the ten-second chat suppression window, the four-second banner lifetime, and
the privacy exclusions.

- [ ] **Step 2: Run the full standalone release gate**

Run:

```bash
gradle clean test jar --no-daemon
```

Expected: `BUILD SUCCESSFUL`; every test passes and a plugin JAR is produced.

- [ ] **Step 3: Perform the live RuneLite matrix**

With Strict Mode off, on, and paused, test:

- a named spell/item teleport;
- fairy ring;
- spirit tree;
- charter/boat;
- minecart or magic carpet;
- door/gate into an adjacent locked chunk;
- exact `Walk here` into an adjacent locked chunk;
- same actions into Allowed and Unknown destinations;
- stale bundle;
- wrong account; and
- malformed import retaining the last valid snapshot.

Record the matrix in the PR description. The release cannot advance if any
Unknown action is consumed, any allowed action is consumed, chat floods, the
pause does not automatically resume, or the suggested alternative is not
actually available.

- [ ] **Step 4: Commit standalone documentation**

```bash
git add README.md CONTRIBUTING.md
git commit -m "docs: explain Strict Mode travel guardian"
```

- [ ] **Step 5: Sync the exact standalone source into the app mirror**

Copy only:

```text
build.gradle
settings.gradle
gradle.properties
README.md
CONTRIBUTING.md
src/main/java
src/main/resources
```

Write the standalone `HEAD` SHA to `runelite-plugin/SOURCE_COMMIT`, then run:

```bash
RUNELITE_SOURCE_DIR=/absolute/path/to/RS3-Fate-Locked-Runelite npm run runelite:mirror-check
npm test
npx tsc --noEmit
npm run build
```

Expected: mirror match, all app tests pass, type-check passes, and production
build succeeds.

- [ ] **Step 6: Commit the app mirror and relay documentation**

The app manifest contract was committed in Task 2. This commit contains only
the exact standalone mirror and updated operator documentation:

```bash
git add runelite-plugin docs/online-relay.md
git commit -m "chore: sync Travel Guardian plugin mirror"
```

- [ ] **Step 7: Update the Plugin Hub pin only**

After the standalone commit is pushed and reachable, replace only the
`commit=` value in `plugins/fate-locked-ironman`. Verify:

```bash
git diff --stat
git diff -- plugins/fate-locked-ironman
```

Expected:

```text
plugins/fate-locked-ironman | 2 +-
1 file changed, 1 insertion(+), 1 deletion(-)
```

- [ ] **Step 8: Submit the focused Hub update**

```bash
git add plugins/fate-locked-ironman
git commit -m "Fate Locked Ironman: Travel Guardian update"
git push -u fork fate-locked-travel-guardian
```

The PR body must explicitly say:

- Strict Mode is still one checkbox and defaults off;
- only fresh, correct-account, proven Locked travel is prevented;
- Unknown and stale travel is never blocked;
- the 60-second pause applies to all Strict Mode categories;
- RuneLite performs no travel or gameplay; and
- the full Gradle and live-client matrices passed.

