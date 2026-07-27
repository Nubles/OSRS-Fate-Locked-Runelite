# Unified Fate Locked Plugin Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship one Plugin Hub artifact that preserves the complete Travel Guardian, adds one-click tracker pairing, and exposes every feature and retained setting in one collapsible RuneLite sidebar.

**Architecture:** Begin from the approved design branch, merge the completed `feature/strict-travel-guardian` branch as the behavioural base, then integrate pairing at the component level. Move the bundle-relay state machine into a focused `TrackerConnectionController`, keep persistent pairing data behind a non-visible `TrackerConnectionSettings` store, and rebuild `FateLockedPanel` from reusable collapsible sections backed by the existing RuneLite configuration keys.

**Tech Stack:** Java 11, RuneLite client APIs, Swing, OkHttp 3, Gson, Guice, Gradle, JUnit 4, Mockito 4, MockWebServer.

## Global Constraints

- Use one `@PluginDescriptor`, one `NavigationButton`, one `FateLockedPanel`, and one Plugin Hub artifact.
- Use `feature/strict-travel-guardian` commit `5cc1ffc` as the Guardian and relay-trust baseline.
- Use `feature/one-click-runelite-pairing` commit `ca6af2c` as a reference, not as a wholesale merge.
- Retain Java 11 source and target compatibility.
- Add no runtime dependency and do not shade RuneLite-provided dependencies.
- Keep the production tracker URL `https://nubles.github.io/OSRS-Fate-Locked/`.
- Keep the production relay URL `https://fate-relay.fatelocked.workers.dev`.
- Remove the visible and supported `onlineSync`, `syncCode`, and `relayUrl` settings.
- Preserve the other 30 configuration keys and defaults exactly.
- Require a user click on `Connect tracker` before creating the first unified pairing identity.
- Show the existing third-party relay/IP disclosure beside the connection action.
- Never block Allowed, Unknown, stale, malformed, wrong-account, or walking actions.
- Never activate travel, movement, rolling, menu choices, or any other gameplay action.
- A relay bundle becomes active only after complete v4 validation.
- A failed relay import keeps the previous bundle and receives no success acknowledgement.
- Keep all source Java-only; do not use reflection, JNI, subprocesses, browser automation, or runtime-downloaded code.

## File and responsibility map

### New production files

- `src/main/java/com/fatelocked/TrackerConnectionSettings.java`
  - Fixed production endpoints and internal pairing/token persistence.
- `src/main/java/com/fatelocked/TrackerConnectionSnapshot.java`
  - Immutable state presented to the sidebar.
- `src/main/java/com/fatelocked/TrackerConnectionController.java`
  - Pairing generation, relay polling, response serialization, version validation, transactional-import coordination, and success acknowledgement.
- `src/main/java/com/fatelocked/PairingSupport.java`
  - Pairing-code validation and tracker deep-link construction.
- `src/main/java/com/fatelocked/RepeatedValueLimiter.java`
  - Bounded duplicate status/log suppression for invalid pasted values.
- `src/main/java/com/fatelocked/CollapsiblePanelSection.java`
  - Reusable expand/collapse Swing section.
- `src/main/java/com/fatelocked/FateLockedConfigBinder.java`
  - Sidebar controls mapped to retained RuneLite configuration keys.
- `src/main/java/com/fatelocked/KeybindCaptureButton.java`
  - Focused keybind editor for the retained re-import hotkey.

### Modified production files

- `src/main/java/com/fatelocked/FateLockedConfig.java`
  - Remove the three legacy online-sync items; retain the 30 approved settings.
- `src/main/java/com/fatelocked/FateLockedPanel.java`
  - Compose the complete all-in-one sidebar and expose stable update/callback methods.
- `src/main/java/com/fatelocked/FateLockedPlugin.java`
  - Wire the connection controller and panel without weakening Guardian.
- `src/main/java/com/fatelocked/events/FateEventRelayClient.java`
  - Gate event traffic on the unified pairing state instead of the removed config toggle.
- `README.md`
  - Replace manual pairing instructions with the single-panel workflow.
- `CONTRIBUTING.md`
  - Document the unified connection and safety invariants.
- `runelite-plugin.properties`
  - Describe the unified pairing and Guardian experience.
- `.gitignore`
  - Ignore `.superpowers/` visual-companion state and local companion logs.

### New or migrated tests

- `src/test/java/com/fatelocked/TrackerConnectionSettingsTest.java`
- `src/test/java/com/fatelocked/TrackerConnectionControllerTest.java`
- `src/test/java/com/fatelocked/CollapsiblePanelSectionTest.java`
- `src/test/java/com/fatelocked/FateLockedConfigBinderTest.java`
- `src/test/java/com/fatelocked/UnifiedPluginContractTest.java`

### Modified tests

- `src/test/java/com/fatelocked/FateLockedConfigTest.java`
- `src/test/java/com/fatelocked/FateLockedPanelStatusTest.java`
- `src/test/java/com/fatelocked/FateLockedRelayImportTest.java`
- `src/test/java/com/fatelocked/events/FateEventRelayClientTest.java`
- `src/test/java/com/fatelocked/FateLockedPluginRelayTrustTest.java`
- `src/test/java/com/fatelocked/FateLockedPluginTravelAccountBindingTest.java`

---

### Task 1: Establish the Travel Guardian implementation baseline

**Files:**
- Merge: `feature/strict-travel-guardian`
- Verify: `src/main/java/com/fatelocked/guardian/**`
- Verify: `src/main/java/com/fatelocked/guardian/travel/**`
- Verify: `src/test/java/com/fatelocked/guardian/**`
- Verify: `src/test/java/com/fatelocked/guardian/travel/**`

**Interfaces:**
- Consumes: approved design branch at or after `61c2816`
- Produces: an implementation branch containing the exact Travel Guardian and hardened relay behaviour from `5cc1ffc`

- [ ] **Step 1: Create an isolated implementation worktree**

Use `superpowers:using-git-worktrees` and create a branch named
`feature/unified-plugin-hub` from the branch containing this plan. Confirm the
new worktree is clean:

```powershell
git status --short
git branch --show-current
```

Expected: no tracked changes and branch `feature/unified-plugin-hub`.

- [ ] **Step 2: Run the pre-merge baseline**

```powershell
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Merge the completed Guardian branch**

```powershell
git merge --no-ff feature/strict-travel-guardian -m "merge: establish travel guardian baseline"
```

Resolve only documentation conflicts by keeping both the existing approved
unified specification and the completed Guardian documents. Do not resolve
Java files from the pairing branch in this task.

- [ ] **Step 4: Run the full Guardian baseline**

```powershell
.\gradlew.bat clean test
```

Expected: `BUILD SUCCESSFUL`, including the travel resolver, rule evaluator,
alternative finder, overlay lifecycle, relay trust, and account-binding suites.

- [ ] **Step 5: Record the baseline evidence**

```powershell
git status --short
git log -3 --oneline
```

Expected: clean tracked state and a merge commit whose second parent reaches
`5cc1ffc`.

---

### Task 2: Add internal tracker-connection persistence without changing runtime behaviour

**Files:**
- Create: `src/main/java/com/fatelocked/TrackerConnectionSettings.java`
- Create: `src/test/java/com/fatelocked/TrackerConnectionSettingsTest.java`
- Modify: `src/main/java/com/fatelocked/events/FateEventRelayClient.java`
- Modify: `src/test/java/com/fatelocked/events/FateEventRelayClientTest.java`

**Interfaces:**
- Consumes: `ConfigManager`
- Produces:
  - `TrackerConnectionSettings.RELAY_BASE_URL`
  - `TrackerConnectionSettings.pairingCode(): String`
  - `TrackerConnectionSettings.isPaired(): boolean`
  - `TrackerConnectionSettings.replacePairingCode(String): void`
  - `TrackerConnectionSettings.clearPairing(): void`
  - `TrackerConnectionSettings.clearLegacySettings(): void`
  - `TrackerConnectionSettings.token(String, String): String`
  - `TrackerConnectionSettings.saveToken(String, String, String): void`
  - `FateEventRelayClient(OkHttpClient, Gson, ConfigManager, BooleanSupplier)`

- [ ] **Step 1: Write failing persistence tests**

Create `TrackerConnectionSettingsTest.java` with Mockito-backed
`ConfigManager` storage:

```java
@Test
public void pairingIdentityUsesANonVisibleInternalKey()
{
    TrackerConnectionSettings settings =
        new TrackerConnectionSettings(configManager);

    settings.replacePairingCode("0123456789abcdef0123456789abcdef");

    verify(configManager).setConfiguration(
        FateLockedConfig.GROUP,
        TrackerConnectionSettings.PAIRING_CODE_KEY,
        "0123456789abcdef0123456789abcdef");
}

@Test
public void successfulUnifiedPairingClearsOnlyLegacyConnectionKeys()
{
    TrackerConnectionSettings settings =
        new TrackerConnectionSettings(configManager);

    settings.clearLegacySettings();

    verify(configManager).unsetConfiguration(
        FateLockedConfig.GROUP, "onlineSync");
    verify(configManager).unsetConfiguration(
        FateLockedConfig.GROUP, "syncCode");
    verify(configManager).unsetConfiguration(
        FateLockedConfig.GROUP, "relayUrl");
    verifyNoMoreInteractions(configManager);
}
```

Add tests for blank/invalid codes returning unpaired and token keys being scoped
by both token type and pairing code.

- [ ] **Step 2: Verify the new tests fail**

```powershell
.\gradlew.bat test --tests com.fatelocked.TrackerConnectionSettingsTest
```

Expected: compilation failure because `TrackerConnectionSettings` does not yet
exist.

- [ ] **Step 3: Implement `TrackerConnectionSettings`**

Use these exact constants and public surface:

```java
final class TrackerConnectionSettings
{
    static final String RELAY_BASE_URL =
        "https://fate-relay.fatelocked.workers.dev";
    static final String PAIRING_CODE_KEY = "trackerPairingCode";
    private static final String CODE_PATTERN = "[0-9a-f]{32}";

    private final ConfigManager configManager;

    @Inject
    TrackerConnectionSettings(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    String pairingCode()
    {
        String value = configManager.getConfiguration(
            FateLockedConfig.GROUP, PAIRING_CODE_KEY);
        return value != null && value.trim().matches(CODE_PATTERN)
            ? value.trim() : "";
    }

    boolean isPaired()
    {
        return !pairingCode().isEmpty();
    }

    void replacePairingCode(String code)
    {
        if (code == null || !code.matches(CODE_PATTERN))
        {
            throw new IllegalArgumentException("Invalid pairing code");
        }
        configManager.setConfiguration(
            FateLockedConfig.GROUP, PAIRING_CODE_KEY, code);
    }

    void clearPairing()
    {
        configManager.unsetConfiguration(
            FateLockedConfig.GROUP, PAIRING_CODE_KEY);
    }

    void clearLegacySettings()
    {
        configManager.unsetConfiguration(FateLockedConfig.GROUP, "onlineSync");
        configManager.unsetConfiguration(FateLockedConfig.GROUP, "syncCode");
        configManager.unsetConfiguration(FateLockedConfig.GROUP, "relayUrl");
    }

    String token(String prefix, String code)
    {
        return configManager.getConfiguration(
            FateLockedConfig.GROUP, prefix + "." + code);
    }

    void saveToken(String prefix, String code, String token)
    {
        configManager.setConfiguration(
            FateLockedConfig.GROUP, prefix + "." + code, token);
    }
}
```

- [ ] **Step 4: Add the new event-relay consent seam**

Add a constructor that accepts a `BooleanSupplier`:

```java
public FateEventRelayClient(
    OkHttpClient client,
    Gson gson,
    ConfigManager configManager,
    BooleanSupplier enabled)
{
    this(client, gson, enabled, new ConfigTokenStore(configManager));
}
```

Keep the existing `FateLockedConfig` constructor temporarily and delegate it to
the new constructor with `config::onlineSync`. Task 6 removes that compatibility
constructor after the plugin has moved to `TrackerConnectionSettings::isPaired`.

- [ ] **Step 5: Test enabled and disabled event traffic**

Extend `FateEventRelayClientTest` so the public `BooleanSupplier` constructor is
covered:

```java
AtomicBoolean paired = new AtomicBoolean(false);
FateEventRelayClient relay = new FateEventRelayClient(
    new OkHttpClient(), gson, configManager, paired::get);

relay.flush(server.url("/").toString(), "ABCD", outbox);
assertEquals(null, server.takeRequest(200, TimeUnit.MILLISECONDS));

paired.set(true);
relay.flush(server.url("/").toString(), "ABCD", outbox);
assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
```

- [ ] **Step 6: Run the focused and full tests**

```powershell
.\gradlew.bat test --tests com.fatelocked.TrackerConnectionSettingsTest --tests com.fatelocked.events.FateEventRelayClientTest
.\gradlew.bat test
```

Expected: both commands report `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/fatelocked/TrackerConnectionSettings.java src/main/java/com/fatelocked/events/FateEventRelayClient.java src/test/java/com/fatelocked/TrackerConnectionSettingsTest.java src/test/java/com/fatelocked/events/FateEventRelayClientTest.java
git commit -m "refactor: add unified tracker connection storage"
```

---

### Task 3: Implement the one-click pairing and hardened relay controller

**Files:**
- Create: `src/main/java/com/fatelocked/PairingSupport.java`
- Create: `src/main/java/com/fatelocked/RepeatedValueLimiter.java`
- Create: `src/main/java/com/fatelocked/TrackerConnectionState.java`
- Create: `src/main/java/com/fatelocked/TrackerConnectionSnapshot.java`
- Create: `src/main/java/com/fatelocked/TrackerConnectionController.java`
- Create: `src/test/java/com/fatelocked/PairingSupportTest.java`
- Create: `src/test/java/com/fatelocked/RepeatedValueLimiterTest.java`
- Create: `src/test/java/com/fatelocked/TrackerConnectionControllerTest.java`
- Delete after migration: `src/test/java/com/fatelocked/FateLockedPluginRelayTrustTest.java`

**Interfaces:**
- Consumes:
  - `TrackerConnectionSettings`
  - `OkHttpClient`
  - `Gson`
  - `Clock`
  - `Consumer<Runnable>` client-thread dispatcher
  - `RelayBundleImporter.importBundle(String): boolean`
  - `Consumer<TrackerConnectionSnapshot>` state listener
- Produces:
  - `PairingSupport.newCode(): String`
  - `PairingSupport.trackerPairingUrl(String): String`
  - `TrackerConnectionController.beginPairing(): String`
  - `TrackerConnectionController.reportBrowserLaunchFailure(): void`
  - `TrackerConnectionController.poll(): void`
  - `TrackerConnectionController.stop(): void`
  - `TrackerConnectionController.snapshot(): TrackerConnectionSnapshot`

- [ ] **Step 1: Port the pairing utility tests first**

Use the pairing-branch expectations:

```java
@Test
public void generatedCodeIsLowercaseHexWithThirtyTwoCharacters()
{
    assertTrue(PairingSupport.newCode().matches("[0-9a-f]{32}"));
}

@Test
public void trackerUrlCarriesThePairingRequestInTheHash()
{
    assertEquals(
        "https://nubles.github.io/OSRS-Fate-Locked/#runelite-pair="
            + "0123456789abcdef0123456789abcdef",
        PairingSupport.trackerPairingUrl(
            "0123456789abcdef0123456789abcdef"));
}
```

Copy the bounded duplicate-window cases from
`feature/one-click-runelite-pairing:RepeatedValueLimiterTest.java`.

- [ ] **Step 2: Write the controller state-machine tests**

Create a MockWebServer harness with a queued client dispatcher and a recording
state listener. Start with these complete success and failure cases:

```java
@Test
public void beginPairingReplacesTheCodeAndReturnsTheBrowserUrl()
{
    String url = controller.beginPairing();

    assertTrue(settings.pairingCode().matches("[0-9a-f]{32}"));
    assertEquals(PairingSupport.trackerPairingUrl(
        settings.pairingCode()), url);
    assertEquals(TrackerConnectionState.WAITING,
        listener.last().getState());
}

@Test
public void successfulImportAdvancesVersionAndPostsOneAck() throws Exception
{
    server.enqueue(relayResponse(6, validV4Payload(), "\"6\""));
    server.enqueue(new MockResponse()
        .setResponseCode(200)
        .setBody("{\"token\":\"state-token\"}"));

    controller.poll();
    runClientTasks();

    assertEquals(TrackerConnectionState.CONNECTED,
        listener.last().getState());
    assertEquals("6", controller.snapshot().getAcceptedVersion());
    assertEquals(1, importer.acceptedPayloads().size());
    RecordedRequest ack = server.takeRequest(2, TimeUnit.SECONDS);
    assertEquals("/r/" + settings.pairingCode() + "/state",
        ack.getPath());
}

@Test
public void failedImportKeepsThePreviousVersionAndPostsNoAck()
    throws Exception
{
    importer.rejectNextPayload();
    server.enqueue(relayResponse(7, "{bad", "\"7\""));

    controller.poll();
    runClientTasks();

    assertEquals(TrackerConnectionState.IMPORT_FAILED,
        listener.last().getState());
    assertEquals(null, controller.snapshot().getAcceptedVersion());
    assertEquals(null, server.takeRequest(200, TimeUnit.MILLISECONDS));
}
```

Then add complete tests named
`onlyOneRelayPollCanBeInFlight`,
`reconnectInvalidatesAnOlderCallback`,
`stopInvalidatesAQueuedClientThreadCommit`,
`responseEtagMustMatchTheBodyVersion`,
`olderEqualAndMalformedVersionsAreRejected`,
`canonicalWeakEtagRevalidatesThrough304`, and
`networkFailurePublishesOfflineWithoutClearingPairing`,
`notFoundPublishesExpiredWithoutReplacingTheBundle`, and
`browserLaunchFailureKeepsThePairingRequestRetryable`.

Each test must use the same harness to assert the resulting snapshot, imported
payload count, queued client-task count, and acknowledgement count. The success
assertion must check all three effects together:

```java
private MockResponse relayResponse(
    int version, String payload, String etag)
{
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("ETag", etag)
        .setBody(gson.toJson(new RelayEnvelope(version, payload)));
}

private static final class RelayEnvelope
{
    private final int version;
    private final String payload;

    private RelayEnvelope(int version, String payload)
    {
        this.version = version;
        this.payload = payload;
    }
}

private String validV4Payload() throws IOException
{
    try (InputStream input = getClass().getClassLoader()
        .getResourceAsStream("bundles/v4-rules.json"))
    {
        return new String(
            input.readAllBytes(), StandardCharsets.UTF_8);
    }
}

private static final class RecordingImporter
    implements TrackerConnectionController.RelayBundleImporter
{
    private final List<String> accepted = new ArrayList<>();
    private boolean rejectNext;

    @Override
    public boolean importBundle(String payload)
    {
        if (rejectNext)
        {
            rejectNext = false;
            return false;
        }
        accepted.add(payload);
        return true;
    }

    void rejectNextPayload() { rejectNext = true; }
    List<String> acceptedPayloads() { return accepted; }
}

private static final class RecordingListener
    implements Consumer<TrackerConnectionSnapshot>
{
    private final List<TrackerConnectionSnapshot> snapshots =
        new ArrayList<>();

    @Override
    public void accept(TrackerConnectionSnapshot snapshot)
    {
        snapshots.add(snapshot);
    }

    TrackerConnectionSnapshot last()
    {
        return snapshots.get(snapshots.size() - 1);
    }
}
```

For each success case, assert the snapshot and import count, then inspect the
recorded `/state` request as shown in
`successfulImportAdvancesVersionAndPostsOneAck`.

- [ ] **Step 3: Verify all new tests fail**

```powershell
.\gradlew.bat test --tests com.fatelocked.PairingSupportTest --tests com.fatelocked.RepeatedValueLimiterTest --tests com.fatelocked.TrackerConnectionControllerTest
```

Expected: compilation failures for the new production types.

- [ ] **Step 4: Implement pairing and immutable display state**

Use this state enum:

```java
enum TrackerConnectionState
{
    DISCONNECTED,
    PREPARING,
    WAITING,
    IMPORTING,
    CONNECTED,
    EXPIRED,
    OFFLINE,
    IMPORT_FAILED
}
```

`TrackerConnectionSnapshot` must contain only:

```java
private final TrackerConnectionState state;
private final Instant lastSync;
private final String acceptedVersion;
private final String message;
```

Provide these exact getters and factories; do not expose mutable controller
fields:

```java
TrackerConnectionState getState()
Instant getLastSync()
String getAcceptedVersion()
String getMessage()

static TrackerConnectionSnapshot disconnected()
{
    return new TrackerConnectionSnapshot(
        TrackerConnectionState.DISCONNECTED, null, null, "Not connected");
}

static TrackerConnectionSnapshot waiting()
{
    return new TrackerConnectionSnapshot(
        TrackerConnectionState.WAITING, null, null, "Waiting for tracker");
}

static TrackerConnectionSnapshot connected(Instant at, String version)
{
    return new TrackerConnectionSnapshot(
        TrackerConnectionState.CONNECTED, at, version, "Connected");
}
```

Add one package-private `of(TrackerConnectionState, Instant, String, String)`
factory for the controller's remaining states.

- [ ] **Step 5: Implement the controller around the strict relay invariants**

Define the importer contract and constructor exactly:

```java
interface RelayBundleImporter
{
    boolean importBundle(String payload);
}

TrackerConnectionController(
    OkHttpClient http,
    Gson gson,
    TrackerConnectionSettings settings,
    Clock clock,
    Consumer<Runnable> clientDispatcher,
    RelayBundleImporter importer,
    Consumer<TrackerConnectionSnapshot> listener)
```

`beginPairing()` must invalidate the current generation, generate and persist a
new code, clear the accepted version and last sync, publish `WAITING`, and
return `PairingSupport.trackerPairingUrl(code)`.

`poll()` must:

```java
String code = settings.pairingCode();
if (code.isEmpty())
{
    publish(TrackerConnectionState.DISCONNECTED, null);
    return;
}
RelayPollToken token = beginPoll(
    TrackerConnectionSettings.RELAY_BASE_URL, code, acceptedVersion);
if (token == null)
{
    return;
}
```

Then preserve the `5cc1ffc` trust checks:

- only one active request;
- generation, code, base URL, and accepted-version snapshot validation;
- canonical numeric ETag parsing;
- ETag/body version equality;
- strictly increasing 200 versions;
- 304 freshness update only for the matching accepted snapshot;
- importer invocation on the client dispatcher;
- accepted version and sync time updated only after `importBundle` returns true;
- state acknowledgement only after that successful update; and
- token-specific cleanup that cannot clear a newer in-flight request.

A relay `404` for the active pairing code publishes `EXPIRED` without clearing
the previous bundle. `reportBrowserLaunchFailure()` publishes `OFFLINE` with
the message `Could not open the web tracker`; it keeps the current code so the
request remains retryable.

- [ ] **Step 6: Persist write tokens through `TrackerConnectionSettings`**

Replace the former plugin-local token helpers with:

```java
String token = settings.token("stateToken", token.code);
settings.saveToken("stateToken", token.code, response.token);
```

Call `settings.clearLegacySettings()` only after the first successfully imported
bundle for the new pairing identity.

- [ ] **Step 7: Move the relay trust tests to the controller**

Port every behavioural assertion from
`FateLockedPluginRelayTrustTest.java` into direct controller tests. Delete the
reflection-heavy plugin test only when all cases have direct equivalents in
`TrackerConnectionControllerTest`.

- [ ] **Step 8: Run the controller and full suites**

```powershell
.\gradlew.bat test --tests com.fatelocked.TrackerConnectionControllerTest --tests com.fatelocked.PairingSupportTest --tests com.fatelocked.RepeatedValueLimiterTest
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL` for both commands.

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/fatelocked/PairingSupport.java src/main/java/com/fatelocked/RepeatedValueLimiter.java src/main/java/com/fatelocked/TrackerConnectionState.java src/main/java/com/fatelocked/TrackerConnectionSnapshot.java src/main/java/com/fatelocked/TrackerConnectionController.java src/test/java/com/fatelocked/PairingSupportTest.java src/test/java/com/fatelocked/RepeatedValueLimiterTest.java src/test/java/com/fatelocked/TrackerConnectionControllerTest.java src/test/java/com/fatelocked/FateLockedPluginRelayTrustTest.java
git commit -m "feat: add hardened one-click tracker connection"
```

---

### Task 4: Build reusable collapsible sections and sidebar configuration controls

**Files:**
- Create: `src/main/java/com/fatelocked/CollapsiblePanelSection.java`
- Create: `src/main/java/com/fatelocked/FateLockedConfigBinder.java`
- Create: `src/main/java/com/fatelocked/KeybindCaptureButton.java`
- Create: `src/test/java/com/fatelocked/CollapsiblePanelSectionTest.java`
- Create: `src/test/java/com/fatelocked/FateLockedConfigBinderTest.java`

**Interfaces:**
- Produces:
  - `CollapsiblePanelSection(String, boolean)`
  - `FateLockedConfigBinder(ConfigManager, Consumer<String>)`
  - `CollapsiblePanelSection.body(): JPanel`
  - `CollapsiblePanelSection.isExpanded(): boolean`
  - `CollapsiblePanelSection.setExpanded(boolean): void`
  - `FateLockedConfigBinder.booleanSetting(String, String, BooleanSupplier): JCheckBox`
  - `FateLockedConfigBinder.keybindSetting(String, String, Supplier<Keybind>): JComponent`
  - `FateLockedConfigBinder.colorSetting(String, String, Supplier<Color>): JComponent`
  - `FateLockedConfigBinder.refresh(String): void`
  - `FateLockedConfigBinder.keys(): Set<String>`

- [ ] **Step 1: Write the collapsible-section tests**

```java
@Test
public void headerTogglesOnlyItsOwnBody()
{
    CollapsiblePanelSection section =
        new CollapsiblePanelSection("Warnings", false);

    assertFalse(section.isExpanded());
    section.headerForTest().doClick();
    assertTrue(section.isExpanded());
    assertTrue(section.body().isVisible());
}
```

Also test that `setExpanded(false)` restores the closed header text and hides
the body without removing its children.

- [ ] **Step 2: Write binder persistence and refresh tests**

Use a mocked `ConfigManager` and real Swing controls:

```java
JCheckBox control = binder.booleanSetting(
    "showHud", "Show in-game HUD", () -> true);
control.doClick();
verify(configManager).setConfiguration(
    FateLockedConfig.GROUP, "showHud", false);

when(configManager.getConfiguration(
    FateLockedConfig.GROUP, "showHud", Boolean.class))
    .thenReturn(true);
binder.refresh("showHud");
assertTrue(control.isSelected());
```

Add tests for:

- a colour choice writing a `Color`;
- Escape clearing the keybind to `Keybind.NOT_SET`;
- a captured `KeyEvent` writing `new Keybind(event)`;
- external refresh not firing a second write; and
- `keys()` returning exactly the controls created by the caller.

Also make `ConfigManager.setConfiguration` throw once and assert that the
control returns to its last confirmed value and the supplied status sink
receives exactly:

```text
couldn't save setting
```

- [ ] **Step 3: Verify the tests fail**

```powershell
.\gradlew.bat test --tests com.fatelocked.CollapsiblePanelSectionTest --tests com.fatelocked.FateLockedConfigBinderTest
```

Expected: compilation failures for the new production classes.

- [ ] **Step 4: Implement the reusable section**

Use one button, one body, and one state field:

```java
final class CollapsiblePanelSection extends JPanel
{
    private final String title;
    private final JButton header = new JButton();
    private final JPanel body = new JPanel();
    private boolean expanded;

    CollapsiblePanelSection(String title, boolean expanded)
    {
        this.title = title;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        header.addActionListener(event -> setExpanded(!this.expanded));
        add(header);
        add(body);
        setExpanded(expanded);
    }

    void setExpanded(boolean expanded)
    {
        this.expanded = expanded;
        body.setVisible(expanded);
        header.setText((expanded ? "▼ " : "▶ ") + title);
        revalidate();
        repaint();
    }
}
```

Apply the existing RuneLite panel colours, full-width sizing, and left-aligned
header styling without adding a dependency.

- [ ] **Step 5: Implement the binder**

The binder owns a map of key to refresh callback:

```java
private final Map<String, Runnable> refreshers = new LinkedHashMap<>();

JCheckBox booleanSetting(
    String key, String label, BooleanSupplier current)
{
    JCheckBox control = new JCheckBox(label, current.getAsBoolean());
    control.addActionListener(event ->
        configManager.setConfiguration(
            FateLockedConfig.GROUP, key, control.isSelected()));
    refreshers.put(key, () -> {
        Boolean value = configManager.getConfiguration(
            FateLockedConfig.GROUP, key, Boolean.class);
        if (value != null)
        {
            control.setSelected(value);
        }
    });
    return control;
}
```

Use `JColorChooser.showDialog` for colours. `KeybindCaptureButton` must request
focus when clicked, display `Press a key…`, write `new Keybind(event)` on the
next key press, and write `Keybind.NOT_SET` on Escape.

Wrap every configuration write:

```java
try
{
    configManager.setConfiguration(FateLockedConfig.GROUP, key, value);
}
catch (RuntimeException error)
{
    refresh(key);
    statusSink.accept("couldn't save setting");
}
```

- [ ] **Step 6: Run focused and full tests**

```powershell
.\gradlew.bat test --tests com.fatelocked.CollapsiblePanelSectionTest --tests com.fatelocked.FateLockedConfigBinderTest
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add src/main/java/com/fatelocked/CollapsiblePanelSection.java src/main/java/com/fatelocked/FateLockedConfigBinder.java src/main/java/com/fatelocked/KeybindCaptureButton.java src/test/java/com/fatelocked/CollapsiblePanelSectionTest.java src/test/java/com/fatelocked/FateLockedConfigBinderTest.java
git commit -m "feat: add collapsible sidebar setting controls"
```

---

### Task 5: Rebuild `FateLockedPanel` as the complete single sidebar

**Files:**
- Modify: `src/main/java/com/fatelocked/FateLockedPanel.java`
- Modify: `src/test/java/com/fatelocked/FateLockedPanelStatusTest.java`

**Interfaces:**
- Consumes:
  - `FateLockedConfigBinder`
  - `TrackerConnectionSnapshot`
  - existing `ChunkPanelViewModel`
  - existing Guardian callbacks and audit lines
- Produces:
  - `setCallbacks(Consumer<String>, Runnable, Runnable): void`
  - `updateConnection(TrackerConnectionSnapshot): void`
  - `updateTrackerAccount(String): void`
  - `updateSyncHealth(int, int, int, TrackerConnectionSnapshot): void`
  - `refreshConfig(String): void`
  - all existing bundle, chunk, Guardian, Roll Inbox, and run update methods

- [ ] **Step 1: Replace the old panel tests with the approved full-panel contract**

Instantiate the panel with mocked `FateLockedConfig` and `ConfigManager`. Add
these assertions:

```java
assertEquals(Arrays.asList(
    "Current chunk", "Guardian", "Roll inbox", "Run",
    "Bundle", "Warnings", "Rendering"),
    panel.sectionTitlesForTest());
assertTrue(panel.sectionForTest("Current chunk").isExpanded());
assertTrue(panel.sectionForTest("Guardian").isExpanded());
assertFalse(panel.sectionForTest("Roll inbox").isExpanded());
assertFalse(panel.sectionForTest("Run").isExpanded());
assertFalse(panel.sectionForTest("Bundle").isExpanded());
assertFalse(panel.sectionForTest("Warnings").isExpanded());
assertFalse(panel.sectionForTest("Rendering").isExpanded());
assertEquals(30, panel.settingKeysForTest().size());
```

Assert the exact retained key set:

```java
assertEquals(new LinkedHashSet<>(Arrays.asList(
    "autoReload", "reimportHotkey",
    "chatOnEnter", "warnOnLocked", "warnLockedBank", "flashOnLocked",
    "warnAccountMismatch", "tagLockedMenus", "tagLockedTeleports",
    "showHud", "showNearest", "showChunkContentBox", "useNotifier",
    "warnLockedSlayer", "warnOverTierGear", "showInfoBoxes", "rollNudges",
    "strictMode",
    "drawWorldMap", "drawScene", "drawMinimap",
    "highlightLockedBorders", "shadeNearbyLocked", "worldMapMarkers",
    "worldMapTooltip", "worldMapTooltipContent",
    "unlockedColor", "frontierColor", "lockedColor", "unauthoredColor")),
    panel.settingKeysForTest());
```

Add negative assertions for `onlineSync`, `syncCode`, `relayUrl`, and a second
Travel Guardian toggle.

- [ ] **Step 2: Add connection and disclosure tests**

```java
panel.updateConnection(TrackerConnectionSnapshot.waiting());
flushSwing();
assertEquals("Waiting for tracker", panel.connectionTextForTest());

panel.updateConnection(TrackerConnectionSnapshot.connected(
    Instant.parse("2026-07-27T14:05:06Z"), "6"));
flushSwing();
assertTrue(panel.connectionTextForTest().contains("Connected"));
assertTrue(panel.connectionTextForTest().contains("14:05:06 UTC"));
panel.updateTrackerAccount("Nubles");
assertEquals("Nubles", panel.trackerAccountTextForTest());
assertTrue(panel.hasTextForTest(
    "your IP address is visible to the Fate Locked relay"));
```

Verify `Connect tracker` invokes the supplied callback exactly once.

- [ ] **Step 3: Verify the tests fail**

```powershell
.\gradlew.bat test --tests com.fatelocked.FateLockedPanelStatusTest
```

Expected: failures because the old panel does not contain all seven sections or
the 30 setting controls.

- [ ] **Step 4: Compose the approved section order**

Keep the header buttons and status at the top, then add:

```java
addSection("Current chunk", true, chunkBody);
addSection("Guardian", true, guardianBody);
addSection("Roll inbox", false, rollInboxBody);
addSection("Run", false, runBody);
addSection("Bundle", false, bundleBody);
addSection("Warnings", false, warningsBody);
addSection("Rendering", false, renderingBody);
```

Move existing components into these bodies; do not recreate their behavioural
callbacks.

- [ ] **Step 5: Render all retained controls in their owning sections**

Use the exact key inventory from Step 1. Put `strictMode` in Guardian, the two
bundle settings beside the existing import/reload controls, all 15 warning
controls in Warnings, and all 12 rendering controls in Rendering.

Keep the existing Strict Mode pause button and recent prevented-action list
inside Guardian. Keep the Strict Mode introduction text, but change its final
sentence from “turn it off in plugin settings” to “turn it off above.”

- [ ] **Step 6: Preserve connection, bundle, and sizing behaviour**

Port from `ca6af2c`:

- the three-argument callback registration;
- connection state copy;
- pairing-code paste detection status;
- status placement immediately below the tracker buttons; and
- the `BorderLayout.NORTH`/preferred-height fix that prevents a blank panel.

Keep the strict branch’s Roll Inbox values, run values, current-chunk rendering,
Guardian pause state, and recent audit presentation.

- [ ] **Step 7: Run panel and full tests**

```powershell
.\gradlew.bat test --tests com.fatelocked.FateLockedPanelStatusTest --tests com.fatelocked.FateLockedConfigBinderTest --tests com.fatelocked.CollapsiblePanelSectionTest
.\gradlew.bat test
```

Expected: all tests pass and no Swing test hangs.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/fatelocked/FateLockedPanel.java src/test/java/com/fatelocked/FateLockedPanelStatusTest.java
git commit -m "feat: unify the complete plugin in one sidebar"
```

---

### Task 6: Wire the unified connection into the plugin and retire manual sync

**Files:**
- Modify: `src/main/java/com/fatelocked/FateLockedConfig.java`
- Modify: `src/main/java/com/fatelocked/FateLockedPlugin.java`
- Modify: `src/main/java/com/fatelocked/events/FateEventRelayClient.java`
- Modify: `src/test/java/com/fatelocked/FateLockedConfigTest.java`
- Create: `src/test/java/com/fatelocked/UnifiedPluginContractTest.java`
- Modify: `src/test/java/com/fatelocked/FateLockedRelayImportTest.java`
- Modify: `src/test/java/com/fatelocked/events/FateEventRelayClientTest.java`

**Interfaces:**
- Consumes:
  - `TrackerConnectionController`
  - `TrackerConnectionSettings`
  - `FateLockedPanel.updateConnection`
  - existing `acceptRelayPayload`
  - existing Guardian shell and overlay lifecycle
- Produces: one startup/shutdown lifecycle owning both pairing and Guardian

- [ ] **Step 1: Write the final configuration-surface test**

Extend `FateLockedConfigTest`:

```java
@Test
public void configHasThirtyRetainedItemsAndNoManualSyncItems()
{
    Map<String, ConfigItem> items = configItemsByKey();
    assertEquals(30, items.size());
    assertFalse(items.containsKey("onlineSync"));
    assertFalse(items.containsKey("syncCode"));
    assertFalse(items.containsKey("relayUrl"));
    assertEquals("Strict Mode", items.get("strictMode").name());
}
```

- [ ] **Step 2: Write the unified lifecycle contract test**

`UnifiedPluginContractTest` must verify:

```java
@Test
public void pluginIdentityRemainsSingular()
{
    PluginDescriptor descriptor =
        FateLockedPlugin.class.getAnnotation(PluginDescriptor.class);
    assertEquals("Fate Locked Ironman", descriptor.name());
}

@Test
public void reconnectAndGuardianCallbacksCanCoexist()
{
    FateLockedPanel panel = harness.panel();
    panel.connectButtonForTest().doClick();
    panel.guardianPauseButtonForTest().doClick();

    assertEquals(1, harness.connectCalls());
    assertEquals(1, harness.pauseCalls());
}
```

Add a startup harness assertion that the same `NavigationButton` panel instance
contains both the Connect action and Guardian section.

- [ ] **Step 3: Verify the new tests fail**

```powershell
.\gradlew.bat test --tests com.fatelocked.FateLockedConfigTest --tests com.fatelocked.UnifiedPluginContractTest
```

Expected: failure because the legacy ConfigItems still exist and the plugin has
not wired the new controller.

- [ ] **Step 4: Remove the three legacy ConfigItems**

Delete only these methods and annotations from `FateLockedConfig.java`:

```java
onlineSync()
syncCode()
relayUrl()
```

Do not rename or reorder the retained key names. Re-run
`FateLockedConfigTest` before changing plugin wiring; compilation may still fail
in `FateLockedPlugin`, which is the expected intermediate state for this task.

- [ ] **Step 5: Create and wire the controller in `startUp()`**

Construct it after event/audit persistence and before adding the navigation
button:

```java
connectionController = new TrackerConnectionController(
    okHttpClient,
    gson,
    connectionSettings,
    Clock.systemUTC(),
    runnable -> clientThread.invoke(runnable),
    this::acceptRelayPayload,
    panel::updateConnection);

panel.setCallbacks(
    json -> applyPastedBundle(json, ImportSource.PASTE),
    () -> clientThread.invoke(this::reloadBundle),
    this::beginTrackerPairing);
```

`beginTrackerPairing()` must be:

```java
private void beginTrackerPairing()
{
    String url = connectionController.beginPairing();
    try
    {
        LinkBrowser.browse(url);
    }
    catch (RuntimeException error)
    {
        connectionController.reportBrowserLaunchFailure();
        panel.flashStatus("couldn't open the web tracker", false);
    }
}
```

Show the initial snapshot before the navigation button becomes visible.

- [ ] **Step 6: Replace the plugin-local relay state machine**

Remove these plugin fields and their helper methods:

```java
lastRelayVersion
lastTrackerSync
relayOffline
relayPollLock
relayGeneration
activeRelayPoll
RelayPollToken
pollRelay()
postStateAck(...)
```

Schedule `connectionController.poll()` at the existing two-second initial delay
and four-second fixed delay. `stopRelayPoll()` must cancel the future and call
`connectionController.stop()` before the panel or bundle is cleared.

- [ ] **Step 7: Preserve transactional v4 import**

Keep strict parsing before assignment:

```java
private boolean acceptRelayPayload(String payload)
{
    FateLockedBundle parsed = FateLockedBundle.loadFromJson(gson, payload);
    if (parsed.getVersion() != 4 || parsed.isLegacyRules())
    {
        return false;
    }
    bundle = parsed;
    rulesImportedAt = Instant.now();
    refreshPanel();
    return true;
}
```

Do not update connection version, sync time, or acknowledgement in this method;
the controller performs those only after the method returns true.

- [ ] **Step 8: Gate events, suggestions, and Roll Inbox on the internal pairing**

Create `FateEventRelayClient` with:

```java
eventRelayClient = new FateEventRelayClient(
    okHttpClient, gson, configManager, connectionSettings::isPaired);
```

Replace every former `config.onlineSync()`, `config.syncCode()`, and
`config.relayUrl()` read with:

```java
connectionSettings.isPaired()
connectionSettings.pairingCode()
TrackerConnectionSettings.RELAY_BASE_URL
```

The Roll Inbox link uses `connectionSettings.pairingCode()`. Suggestions do
nothing when unpaired. Delete the temporary compatibility constructor from
`FateEventRelayClient`.

When `refreshPanel()` applies a bundle, call
`panel.updateTrackerAccount(bundle.getRules().getAccount())`; use the existing
linked-account fallback for legacy clipboard bundles.

- [ ] **Step 9: Route config changes back into the panel**

At the start of the existing `onConfigChanged` handler, after confirming the
group:

```java
panel.refreshConfig(ev.getKey());
```

Keep all existing recomputation branches for auto reload, gear, Slayer,
markers, infoboxes, and Strict Mode. Delete the old branch that watches
`onlineSync`, `syncCode`, or `relayUrl`.

- [ ] **Step 10: Keep safe manual import behaviour**

Port the pairing branch’s `ImportSource` enum and repeated-value limiter. A
32-character pairing code pasted into the JSON box or clipboard must not be
parsed as a bundle and must show:

```text
pairing code detected — use Connect tracker
```

A malformed bundle must leave `bundle` and `rulesImportedAt` unchanged.

- [ ] **Step 11: Run focused wiring tests**

```powershell
.\gradlew.bat test --tests com.fatelocked.FateLockedConfigTest --tests com.fatelocked.UnifiedPluginContractTest --tests com.fatelocked.FateLockedRelayImportTest --tests com.fatelocked.events.FateEventRelayClientTest --tests com.fatelocked.TrackerConnectionControllerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Prove the removed fields have no runtime references**

```powershell
rg -n "config\.(onlineSync|syncCode|relayUrl)\(" src/main src/test
```

Expected: no matches.

```powershell
rg -n '"onlineSync"|"syncCode"|"relayUrl"' src/main/java
```

Expected: matches only the three legacy cleanup calls in
`TrackerConnectionSettings.clearLegacySettings()`.

- [ ] **Step 13: Run the complete test suite**

```powershell
.\gradlew.bat clean test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 14: Commit**

```powershell
git add src/main/java/com/fatelocked/FateLockedConfig.java src/main/java/com/fatelocked/FateLockedPlugin.java src/main/java/com/fatelocked/events/FateEventRelayClient.java src/test/java/com/fatelocked/FateLockedConfigTest.java src/test/java/com/fatelocked/UnifiedPluginContractTest.java src/test/java/com/fatelocked/FateLockedRelayImportTest.java src/test/java/com/fatelocked/events/FateEventRelayClientTest.java
git commit -m "feat: wire one-click pairing into the guardian plugin"
```

---

### Task 7: Close Guardian regressions and Plugin Hub packaging

**Files:**
- Modify: `src/test/java/com/fatelocked/FateLockedPluginTravelAccountBindingTest.java`
- Verify: `src/test/java/com/fatelocked/TravelGuardianPluginShellTest.java`
- Verify: `src/test/java/com/fatelocked/TravelGuardianOverlayLifecycleTest.java`
- Verify: `src/test/java/com/fatelocked/guardian/travel/**`
- Modify: `README.md`
- Modify: `CONTRIBUTING.md`
- Modify: `runelite-plugin.properties`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: completed unified implementation
- Produces: review-ready source, documentation, and standard non-shaded jar

- [ ] **Step 1: Run the full Guardian regression matrix**

```powershell
.\gradlew.bat test --tests com.fatelocked.TravelGuardianPluginShellTest --tests com.fatelocked.TravelGuardianOverlayLifecycleTest --tests "com.fatelocked.guardian.travel.*" --tests com.fatelocked.FateLockedPluginTravelAccountBindingTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Update the account-binding regression for the controller**

Replace the old plugin-local relay setup with a connected
`TrackerConnectionSnapshot`, while retaining this behavioural assertion:

```java
TravelGuardianPluginShell.Route route = shell.handle(click);
assertEquals(TravelGuardianPluginShell.Route.FAIL_OPEN, route);
verify(click, never()).consume();
```

The test fixture must cover a paired and freshly synced relay bundle whose
account differs from the logged-in player. Pairing success must never override
Guardian’s account-binding fail-open rule.

- [ ] **Step 3: Update user documentation**

Replace manual online-sync instructions with:

```markdown
1. Open the Fate Locked sidebar.
2. Click **Connect tracker**.
3. Complete the confirmation in the web tracker opened by RuneLite.
4. Return to RuneLite; the panel changes to **Connected** after the first valid bundle.
```

Document the seven collapsible sections and state explicitly that there is one
Plugin Hub plugin. Remove all instructions asking users to enable online sync,
copy a code, or edit a relay URL.

- [ ] **Step 4: Update contributor and Plugin Hub copy**

In `CONTRIBUTING.md`, add the fixed-endpoint, explicit-connect, success-only
acknowledgement, and Guardian fail-open invariants.

Update `runelite-plugin.properties` so its description mentions one-click web
tracker connection and optional strict prevention without claiming automation.

Add:

```gitignore
.superpowers/
.superpowers-brainstorm-*.log
```

to `.gitignore`.

- [ ] **Step 5: Run static compliance searches**

```powershell
rg -n "@PluginDescriptor" src/main/java
rg -n "ProcessBuilder|Runtime\.getRuntime|Class\.forName|java\.lang\.reflect|JNI|localhost|127\.0\.0\.1" src/main/java
rg -n "onlineSync|Online sync code|Relay URL" README.md CONTRIBUTING.md runelite-plugin.properties src/main/java
```

Expected:

- exactly one `@PluginDescriptor`;
- no subprocess, reflection, JNI, or local-server implementation;
- legacy sync names only in the intentional cleanup constants/tests, not in
  user-facing copy.

- [ ] **Step 6: Build and inspect the standard jar**

```powershell
.\gradlew.bat clean check jar
jar tf build\libs\fatelocked-0.1.0.jar
```

Expected:

- `BUILD SUCCESSFUL`;
- one plugin implementation under `com/fatelocked`;
- no shaded RuneLite, Gson, OkHttp, Guice, or SLF4J classes.

- [ ] **Step 7: Perform the same-PC manual matrix**

Launch a sideloaded build and verify:

1. the Fate Locked toolbar icon opens one non-blank sidebar;
2. all seven sections independently expand and collapse;
3. every retained setting changes its existing RuneLite profile value;
4. `Connect tracker` opens the production GitHub web app;
5. first valid relay import changes the panel to Connected;
6. an invalid relay bundle leaves the previous rules active and receives no
   success acknowledgement;
7. reconnect invalidates an earlier pending callback;
8. a proven locked travel click is blocked and explained;
9. Allowed, Unknown, stale, wrong-account, and walking actions are not blocked;
10. Pause Guardian works for 60 seconds and resumes automatically; and
11. no suggestion or alternative performs a gameplay action.

- [ ] **Step 8: Run final verification from a clean build**

```powershell
.\gradlew.bat clean test jar
git diff --check
git status --short
```

Expected: tests and jar pass, no whitespace errors, and only the intended
documentation changes remain uncommitted.

- [ ] **Step 9: Commit**

```powershell
git add README.md CONTRIBUTING.md runelite-plugin.properties .gitignore src/test/java/com/fatelocked/FateLockedPluginTravelAccountBindingTest.java
git commit -m "docs: prepare unified plugin hub release"
```

- [ ] **Step 10: Review the complete branch**

Use `superpowers:requesting-code-review`, then address only verified findings.
After review, rerun:

```powershell
.\gradlew.bat clean test jar
git status --short
git log --oneline --decorate -10
```

Expected: a clean worktree, passing build, and a small series of intentional
commits on `feature/unified-plugin-hub`.
