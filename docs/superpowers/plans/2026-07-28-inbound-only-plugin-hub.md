# Inbound-Only Plugin Hub Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship one current RuneLite Plugin Hub candidate and one current GitHub Pages companion app with one-click companion-to-RuneLite rules delivery, no RuneLite gameplay-data upload, and a bounded local Roll Inbox history.

**Architecture:** RuneLite owns a single fixed-host `GET /r/<code>` controller and imports only complete, strictly validated v4 bundles. Detected gameplay events stay in a copy-on-write local history; all event, acknowledgement, suggestion, heartbeat, and write-token paths are removed from the shipped plugin. The current companion app adopts the RuneLite-generated pairing code, publishes the bundle, reports only that the profile was sent, and tells the user to verify `Connected` inside RuneLite.

**Tech Stack:** Java 11, RuneLite API, Gradle 8.7, JUnit 4, Mockito, OkHttp MockWebServer, React 18, TypeScript, Vitest, Vite, GitHub Pages, Cloudflare Worker relay.

## Global Constraints

- Plugin implementation worktree: `C:\Users\alexa\Downloads\flitest-main\RS3-Fate-Locked-Runelite\.worktrees\unified-plugin-hub`, branch `feature/unified-plugin-hub`.
- Preserve the existing uncommitted edit in `src/test/java/com/fatelocked/FateLockedPluginTravelAccountBindingTest.java`; Task 4 validates and commits it with the Guardian regression work.
- Companion source repository: `C:\Users\alexa\Downloads\flitest-main\flitest-main`.
- Before Task 6, invoke `superpowers:using-git-worktrees`, fetch `origin`, and create `C:\Users\alexa\Downloads\flitest-main\flitest-main\.worktrees\inbound-only-runelite-pairing` on `feature/inbound-only-runelite-pairing` from the then-current `origin/main`.
- Do not modify the dirty companion main worktree (`README.md`, `.superpowers/`, and `docs/media/` belong to the user).
- Do not cherry-pick `feature/one-click-runelite-pairing`; it is 98 commits behind the current app and still contains the retired RuneLite mirror. Port only the reviewed app-side behavior described in Tasks 6 and 7.
- The shipped plugin's only constructed HTTP request is `GET https://fate-relay.fatelocked.workers.dev/r/<32-lowercase-hex-code>` with optional `If-None-Match`.
- Browser handoffs may open only the fixed GitHub Pages tracker URLs and must contain no RuneLite-observed player or gameplay data.
- Keep exactly one `@PluginDescriptor`, one `FateLockedPlugin`, one `FateLockedPanel`, one navigation button, seven sidebar sections, and 30 retained settings.
- Keep section order `Current chunk`, `Guardian`, `Roll inbox`, `Run`, `Bundle`, `Warnings`, `Rendering`; only the first two start expanded.
- Keep `strictMode` as the sole Guardian toggle and preserve the complete Travel Guardian block/fail-open/pause behavior.
- Local history path is `<RuneLite data directory>/fate-locked/event-history.json`; retain the newest 250 unique events and migrate only the newest 250 legacy `pending` entries.
- Leave `event-outbox.json` untouched after migration.
- Exact Roll Inbox disclosure: `Local only — RuneLite does not upload gameplay data.`
- Exact network disclosure: `RuneLite retrieves rules from the Fate Locked relay. Your IP address is visible to the relay, but RuneLite does not upload gameplay data.`
- Exact companion success copy: `Profile sent. Return to RuneLite; its Fate Locked panel will show Connected after the first valid import.`
- The companion may retain Worker routes for older installed clients, but new onboarding and the new plugin must not depend on `/state`, `/events`, `/acks`, or `/suggest`.
- Standard jar only: no shading, JNI, reflection in production, subprocesses, dynamic class loading, local server, `localhost`, or `127.0.0.1`.
- Do not push, deploy GitHub Pages, update the Plugin Hub pin, or submit reviewer requests until the user authorizes that external publication step.

## File Responsibility Map

### RuneLite plugin

- `TrackerConnectionSettings.java` — pairing-code persistence and one-way cleanup of obsolete settings/tokens.
- `TrackerConnectionController.java` — the sole plugin-process HTTP request, version validation, client-thread import, and local connection snapshot.
- `events/FateEventHistory.java` — bounded local event persistence, corruption recovery, and legacy outbox migration.
- `FateLockedPlugin.java` — detector wiring, local-history status, inbound poll scheduling, and unchanged Guardian lifecycle.
- `FateLockedPanel.java` — one unified sidebar, local Roll Inbox status/copy, fixed browser links, and connection display.
- `PluginHubNetworkBoundaryTest.java` — exhaustive production-source network and prohibited-runtime gate.
- `build.gradle` — standard-jar inspection gate.
- `.github/workflows/build.yml` — clean `check` plus standard jar artifact.

### Current companion app

- `utils/runelitePairing.ts` — pure strict pairing-code/fragment parsing and shared success copy.
- `components/RunelitePairingDialog.tsx` — phased profile confirmation, delivery, success, and retry surface.
- `services/relaySync.ts` — browser-owned relay session, bundle POST, and a retry signal that rebuilds the current browser-authored payload.
- `components/OnlineSyncDriver.tsx` — publish the active run after pairing replacement and state changes.
- `components/RuneLiteOnboarding.tsx` — directional sent/error guidance without a plugin heartbeat claim.
- `App.tsx` — scrub the pairing fragment and mount the phased dialog within current modal/changelog policy.
- `utils/changelogState.ts` — prevent the changelog from covering a startup pairing request.

---

### Task 1: Make the connection controller GET-only and purge obsolete tokens

**Files:**
- Modify: `src/main/java/com/fatelocked/TrackerConnectionSettings.java`
- Modify: `src/main/java/com/fatelocked/TrackerConnectionController.java`
- Modify: `src/main/java/com/fatelocked/FateLockedPlugin.java`
- Modify: `src/test/java/com/fatelocked/TrackerConnectionSettingsTest.java`
- Modify: `src/test/java/com/fatelocked/TrackerConnectionControllerTest.java`

**Interfaces:**
- Retains: `TrackerConnectionController(OkHttpClient, Gson, TrackerConnectionSettings, Clock, Consumer<Runnable>, RelayBundleImporter, Consumer<TrackerConnectionSnapshot>)`
- Retains: `String beginPairing()`, `void poll()`, `void stop()`, `TrackerConnectionSnapshot snapshot()`
- Produces: `void TrackerConnectionSettings.clearLegacySettings()` that removes visible legacy settings and every legacy relay-token key without removing `trackerPairingCode`
- Produces: exactly one request construction in production plugin source

- [ ] **Step 1: Write failing settings cleanup tests**

Replace the token round-trip test with keys from every retired family and one unrelated key:

```java
@Test
public void cleanupRemovesLegacySettingsAndAllRelayTokens()
{
    when(configManager.getConfigurationKeys(FateLockedConfig.GROUP + "."))
        .thenReturn(Arrays.asList(
            FateLockedConfig.GROUP + ".eventToken.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            FateLockedConfig.GROUP + ".stateToken.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            FateLockedConfig.GROUP + ".suggestToken.cccccccccccccccccccccccccccccccc",
            FateLockedConfig.GROUP + ".ackToken.dddddddddddddddddddddddddddddddd",
            FateLockedConfig.GROUP + ".trackerPairingCode",
            FateLockedConfig.GROUP + ".strictMode"));

    new TrackerConnectionSettings(configManager).clearLegacySettings();

    verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "onlineSync");
    verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "syncCode");
    verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "relayUrl");
    verify(configManager).unsetConfiguration(
        FateLockedConfig.GROUP,
        "eventToken.aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    verify(configManager).unsetConfiguration(
        FateLockedConfig.GROUP,
        "stateToken.bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    verify(configManager).unsetConfiguration(
        FateLockedConfig.GROUP,
        "suggestToken.cccccccccccccccccccccccccccccccc");
    verify(configManager).unsetConfiguration(
        FateLockedConfig.GROUP,
        "ackToken.dddddddddddddddddddddddddddddddd");
    verify(configManager, never()).unsetConfiguration(
        FateLockedConfig.GROUP, TrackerConnectionSettings.PAIRING_CODE_KEY);
    verify(configManager, never()).unsetConfiguration(
        FateLockedConfig.GROUP, "strictMode");
}
```

Also test `getConfigurationKeys(...) == null` and both full `fatelocked.key` and bare `key` representations so cleanup is safe across RuneLite versions and test doubles.

- [ ] **Step 2: Rewrite controller success tests around one request**

In `TrackerConnectionControllerTest`, record the original request before the existing interceptor redirects it to `MockWebServer`:

```java
private final List<Request> pluginRequests = new CopyOnWriteArrayList<>();

Interceptor redirectToServer = chain -> {
    Request original = chain.request();
    pluginRequests.add(original);
    return chain.proceed(original.newBuilder()
        .url(server.url(original.url().encodedPath()))
        .build());
};
```

Change the primary success regression to enqueue only the bundle response, include an ignored legacy token field, run the queued client import, and assert:

```java
assertEquals(1, pluginRequests.size());
Request request = pluginRequests.get(0);
assertEquals("GET", request.method());
assertEquals("https", request.url().scheme());
assertEquals("fate-relay.fatelocked.workers.dev", request.url().host());
assertEquals("/r/" + settings.pairingCode(), request.url().encodedPath());
assertNull(request.body());
assertEquals(TrackerConnectionState.CONNECTED, controller.snapshot().getState());
assertEquals("6", controller.snapshot().getAcceptedVersion());
assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS));
```

Remove acknowledgement response enqueues, `acknowledgementCount`, `takeAck()`, `assertAckVersion(...)`, and acknowledgement-only assertions while preserving every stale callback, replacement pairing, 304, ETag/body mismatch, stopped-controller, import rollback, and browser failure regression.

- [ ] **Step 3: Run the focused tests to verify RED**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.TrackerConnectionSettingsTest --tests com.fatelocked.TrackerConnectionControllerTest
```

Expected: failures show token methods still exist, a second `/state` POST is observed, and cleanup does not enumerate token keys.

- [ ] **Step 4: Implement one-way cleanup**

Keep the method name used by current callers and normalize keys returned by RuneLite:

```java
private static final String[] LEGACY_TOKEN_PREFIXES = {
    "eventToken.", "stateToken.", "suggestToken.", "ackToken."
};

void clearLegacySettings()
{
    configManager.unsetConfiguration(FateLockedConfig.GROUP, "onlineSync");
    configManager.unsetConfiguration(FateLockedConfig.GROUP, "syncCode");
    configManager.unsetConfiguration(FateLockedConfig.GROUP, "relayUrl");
    List<String> keys = configManager.getConfigurationKeys(
        FateLockedConfig.GROUP + ".");
    if (keys == null) return;
    String groupPrefix = FateLockedConfig.GROUP + ".";
    for (String stored : keys)
    {
        String key = stored != null && stored.startsWith(groupPrefix)
            ? stored.substring(groupPrefix.length()) : stored;
        if (startsWithLegacyTokenPrefix(key))
        {
            configManager.unsetConfiguration(FateLockedConfig.GROUP, key);
        }
    }
}
```

Delete `token(...)` and `saveToken(...)`. Call cleanup once during `FateLockedPlugin.startUp()`, after `replacePairingCode(...)` in `beginPairing()`, and when `poll()` detects an externally replaced pairing identity.

- [ ] **Step 5: Remove acknowledgement construction from the controller**

Remove `MediaType`, `RequestBody`, `HashMap`, `Map`, `JSON`, `legacyClearedCode`, `postStateAcknowledgement(...)`, `isActiveSession(...)`, `isSessionCurrent(...)`, and `TokenResponse`. Change:

```java
private void dispatchImport(
    RelayPollToken token, String payload, String version)
```

After a successful import, update only `acceptedVersion`, `lastSync`, and the local `Connected` snapshot. Remove `baseUrl` from `RelayPollToken` and build the request only as:

```java
Request.Builder builder = new Request.Builder()
    .url(TrackerConnectionSettings.RELAY_BASE_URL + "/r/" + token.code)
    .get();
if (token.acceptedVersion != null)
{
    builder.header("If-None-Match", token.acceptedVersion);
}
```

- [ ] **Step 6: Run focused and controller-adjacent tests**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.TrackerConnectionSettingsTest --tests com.fatelocked.TrackerConnectionControllerTest --tests com.fatelocked.PairingSupportTest --tests com.fatelocked.FateLockedRelayImportTest
```

Expected: all tests pass; each successful import produces one GET and no second request.

- [ ] **Step 7: Commit the GET-only controller**

```powershell
git add src/main/java/com/fatelocked/TrackerConnectionSettings.java src/main/java/com/fatelocked/TrackerConnectionController.java src/main/java/com/fatelocked/FateLockedPlugin.java src/test/java/com/fatelocked/TrackerConnectionSettingsTest.java src/test/java/com/fatelocked/TrackerConnectionControllerTest.java
git commit -m "refactor: make tracker connection inbound only"
```

---

### Task 2: Add bounded copy-on-write local event history

**Files:**
- Create: `src/main/java/com/fatelocked/events/FateEventHistory.java`
- Create: `src/test/java/com/fatelocked/events/FateEventHistoryTest.java`

**Interfaces:**
- Produces: `public FateEventHistory(Gson gson, Path historyPath, Path legacyOutboxPath) throws IOException`
- Produces: package-private test constructor with `FateEventHistory.Persistence`
- Produces: `public synchronized boolean record(FateEvent event) throws IOException`
- Produces: `public synchronized List<FateEvent> events()` as an immutable defensive copy

- [ ] **Step 1: Write the local-history tests**

Cover restart persistence, duplicate rejection, rollover, corruption recovery, legacy migration, and copy-on-write failure. The rollover assertion must be exact:

```java
for (int i = 1; i <= 251; i++)
{
    assertTrue(history.record(event("evt-" + i)));
}
assertEquals(250, history.events().size());
assertEquals("evt-2", history.events().get(0).getEventId());
assertEquals("evt-251",
    history.events().get(249).getEventId());
```

Migration writes the old shape and proves the old bytes are unchanged:

```java
byte[] legacyBytes = gson.toJson(Collections.singletonMap(
    "pending", legacyEvents)).getBytes(StandardCharsets.UTF_8);
Files.write(legacyPath, legacyBytes);

FateEventHistory migrated =
    new FateEventHistory(gson, historyPath, legacyPath);

assertEquals(250, migrated.events().size());
assertArrayEquals(legacyBytes, Files.readAllBytes(legacyPath));
assertTrue(Files.exists(historyPath));
```

Use a package-private persistence seam for deterministic failure:

```java
FateEventHistory.Persistence failing =
    (target, bytes) -> { throw new IOException("disk full"); };
FateEventHistory reloaded =
    new FateEventHistory(gson, historyPath, legacyPath, failing);

try
{
    reloaded.record(event("evt-2"));
    fail("expected write failure");
}
catch (IOException expected)
{
    assertEquals("disk full", expected.getMessage());
}
assertEquals(Collections.singletonList("evt-1"),
    eventIds(reloaded.events()));
assertEquals(Collections.singletonList("evt-1"),
    eventIds(new FateEventHistory(
        gson, historyPath, legacyPath).events()));
```

- [ ] **Step 2: Run the new test to verify RED**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.events.FateEventHistoryTest
```

Expected: test compilation fails because `FateEventHistory` does not exist.

- [ ] **Step 3: Implement the history component**

Use this exact boundary:

```java
public final class FateEventHistory
{
    static final int MAX_EVENTS = 250;

    interface Persistence
    {
        void write(Path target, byte[] bytes) throws IOException;
    }

    public synchronized boolean record(FateEvent event) throws IOException
    {
        if (event == null || event.getEventId() == null
            || event.getEventId().trim().isEmpty()
            || contains(event.getEventId()))
        {
            return false;
        }
        List<FateEvent> candidate = new ArrayList<>(events);
        if (candidate.size() == MAX_EVENTS)
        {
            candidate.remove(0);
        }
        candidate.add(event);
        persist(candidate);
        events.clear();
        events.addAll(candidate);
        return true;
    }
}
```

Persist `{ "events": [...] }` to a sibling `.tmp`, use `ATOMIC_MOVE` with a `REPLACE_EXISTING` fallback, and publish the candidate list only after persistence succeeds. Return an immutable defensive copy from `events()`. On malformed `event-history.json`, move it to `event-history.json.corrupt-<millis>` and begin empty. When history is absent and legacy outbox exists, parse only `pending`, keep the newest 250, persist the new history, and never modify or delete the legacy file. The new state contains no acknowledgement map, relay state, sent flag, or token.

- [ ] **Step 4: Run history and event-model tests**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.events.FateEventHistoryTest --tests com.fatelocked.events.FateEventFactoryTest
```

Expected: all tests pass.

- [ ] **Step 5: Commit local history**

```powershell
git add src/main/java/com/fatelocked/events/FateEventHistory.java src/test/java/com/fatelocked/events/FateEventHistoryTest.java
git commit -m "feat: add local detected event history"
```

---

### Task 3: Convert the unified Roll Inbox panel to local status

**Files:**
- Modify: `src/main/java/com/fatelocked/FateLockedPanel.java`
- Modify: `src/main/java/com/fatelocked/FateLockedPlugin.java`
- Modify: `src/test/java/com/fatelocked/FateLockedPanelStatusTest.java`

**Interfaces:**
- Produces: `void updateRollInboxStatus(int localEvents, int needsReview, int warnings, boolean saveFailed)`
- Retains separately: `void updateConnection(TrackerConnectionSnapshot snapshot)`
- Produces: `static String rollInboxUrl(String trackerUrl)` with no pairing-code argument

- [ ] **Step 1: Write exact-copy, fixed-link, and failure-state tests**

Update `FateLockedPanelStatusTest` to assert:

```java
assertEquals("https://tracker.example/app?open=roll-inbox",
    FateLockedPanel.rollInboxUrl("https://tracker.example/app"));
assertTrue(panel.hasTextForTest(
    "Local only — RuneLite does not upload gameplay data."));
assertTrue(panel.hasTextForTest(
    "RuneLite retrieves rules from the Fate Locked relay. "
        + "Your IP address is visible to the relay, "
        + "but RuneLite does not upload gameplay data."));
```

The button must be `Open web Roll Inbox`, its tooltip must say local history is not transferred, and its URL must not contain `code=`.

Test the separated status API:

```java
panel.updateRollInboxStatus(4, 2, 1, true);
flushSwing();
assertEquals("4", panel.localEventsTextForTest());
assertEquals("2", panel.reviewTextForTest());
assertEquals("1 active", panel.warningTextForTest());
assertTrue(panel.historyStatusVisibleForTest());
assertEquals("Local history save failed",
    panel.historyStatusTextForTest());

panel.updateRollInboxStatus(4, 2, 1, false);
flushSwing();
assertFalse(panel.historyStatusVisibleForTest());
```

Keep the existing seven-section order/default assertions and exact 30-setting assertions unchanged.

- [ ] **Step 2: Run panel tests to verify RED**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.FateLockedPanelStatusTest
```

Expected: failures show old `Queued`, old disclosure, pairing-code URL, old button text, and missing save-failure status.

- [ ] **Step 3: Implement the local Roll Inbox surface**

Use labels `Local events`, `Needs review`, `Warnings`. Add a local-only disclosure and a hidden `historyStatusVal`. Implement:

```java
void updateRollInboxStatus(
    int localEvents, int needsReview, int warnings,
    boolean saveFailed)
{
    runOnEdt(() -> {
        localEventsVal.setText(
            String.valueOf(Math.max(0, localEvents)));
        reviewVal.setText(
            String.valueOf(Math.max(0, needsReview)));
        warningsVal.setText(
            warnings <= 0 ? "None" : warnings + " active");
        warningsVal.setForeground(warnings <= 0 ? GREEN : RED);
        historyStatusVal.setText(
            saveFailed ? "Local history save failed" : "");
        historyStatusVal.setVisible(saveFailed);
    });
}
```

Do not call `applyConnection(...)` here. Keep connection updates solely in `updateConnection(...)`.

Replace URL construction with:

```java
static String rollInboxUrl(String trackerUrl)
{
    String base = trackerUrl == null || trackerUrl.trim().isEmpty()
        ? TRACKER_URL : trackerUrl.trim();
    return base + "?open=roll-inbox";
}
```

Remove `URLEncoder` and `StandardCharsets` imports. Change the plugin's temporary old-outbox status call to invoke `updateRollInboxStatus(..., false)` and `updateConnection(...)` separately so this task compiles before Task 4 swaps storage.

- [ ] **Step 4: Run panel, config, and startup contracts**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.FateLockedPanelStatusTest --tests com.fatelocked.FateLockedConfigTest --tests com.fatelocked.FateLockedPluginStartupContractTest
```

Expected: all tests pass with seven sections and 30 settings intact.

- [ ] **Step 5: Commit the local Roll Inbox UI**

```powershell
git add src/main/java/com/fatelocked/FateLockedPanel.java src/main/java/com/fatelocked/FateLockedPlugin.java src/test/java/com/fatelocked/FateLockedPanelStatusTest.java
git commit -m "feat: show detected events as local history"
```

---

### Task 4: Replace plugin relay delivery with local-history wiring

**Files:**
- Modify: `src/main/java/com/fatelocked/FateLockedPlugin.java`
- Delete: `src/main/java/com/fatelocked/events/FateEventOutbox.java`
- Delete: `src/main/java/com/fatelocked/events/FateEventRelayClient.java`
- Delete: `src/test/java/com/fatelocked/events/FateEventOutboxTest.java`
- Delete: `src/test/java/com/fatelocked/events/FateEventRelayClientTest.java`
- Modify: `src/test/java/com/fatelocked/FateLockedRelayImportTest.java`
- Create: `src/test/java/com/fatelocked/FateLockedPluginLocalHistoryTest.java`
- Modify: `src/test/java/com/fatelocked/FateLockedPluginStartupContractTest.java`
- Modify: `src/test/java/com/fatelocked/FateLockedPluginTravelAccountBindingTest.java`

**Interfaces:**
- Consumes: `FateEventHistory.record(FateEvent)` and `events()`
- Consumes: `FateLockedPanel.updateRollInboxStatus(...)`
- Produces: local detector history for paired relay, clipboard/paste, and file-loaded valid bundles
- Removes: every event flush, acknowledgement poll, suggestion request, and plugin relay write-token path

- [ ] **Step 1: Write local-history integration regressions**

Create `FateLockedPluginLocalHistoryTest` with a `TemporaryFolder`, a valid v4 fixture, a mocked local player name, and a real `FateEventHistory`. Invoke the existing private `record(DetectedEvent)` through the same narrow reflection pattern already used by `FateLockedRelayImportTest`.

Cover these exact gates:

```java
// Valid bundle + runId + account records even with no pairing.
assertFalse(connectionSettings.isPaired());
invokeRecord(plugin, exactQuest("Dragon Slayer"));
assertEquals(1, history.events().size());

// Blank runId, absent account, null detection, and duplicate eventId add nothing.
```

For source coverage, build three plugin instances:

1. call `acceptRelayPayload(v4Fixture)`;
2. call `applyPastedBundle(v4Fixture, CLIPBOARD)`;
3. write `fate-locked-bundle-test.json` under an overridden `dataDirectory()` and invoke `reloadBundle()`.

After each load, invoke one detected event and assert it appears in local history without requiring a pairing code.

- [ ] **Step 2: Add persistence-failure isolation tests**

Mock `FateEventHistory.record(...)` to throw once and then succeed. Capture panel calls and prove:

```java
FateLockedBundle before = plugin.getBundle();
TrackerConnectionSnapshot connectionBefore =
    connectionController.snapshot();

invokeRecord(plugin, firstEvent);
assertSame(before, plugin.getBundle());
assertSame(connectionBefore, connectionController.snapshot());
verify(panel).updateRollInboxStatus(
    existingCount, existingReview, existingWarnings, true);
verifyNoInteractions(travelGuardianCoordinator);

invokeRecord(plugin, secondUniqueEvent);
verify(panel).updateRollInboxStatus(
    existingCount + 1, existingReview, existingWarnings, false);
```

The `Warnings` value remains derived only from locked chunk, Slayer, and over-tier state; history persistence failure must not increment it.

- [ ] **Step 3: Update startup and Guardian contracts before production changes**

In `FateLockedPluginStartupContractTest`, make the stateful `ConfigManager` return `getConfigurationKeys(...)`, seed all legacy token prefixes, and assert startup removes them. Capture scheduled tasks and assert one four-second tracker poll exists; no event flush task exists.

Retain the current uncommitted `FateLockedPluginTravelAccountBindingTest` change:

```java
when(connectionSettings.isPaired()).thenReturn(true);
when(connectionController.snapshot()).thenReturn(
    TrackerConnectionSnapshot.connected(Instant.now(), "1"));
```

This makes the paired Guardian freshness harness use the controller snapshot that remains authoritative after heartbeat removal.

- [ ] **Step 4: Run integration tests to verify RED**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.FateLockedPluginLocalHistoryTest --tests com.fatelocked.FateLockedPluginStartupContractTest --tests com.fatelocked.FateLockedPluginTravelAccountBindingTest
```

Expected: local recording is still pairing-gated, old outbox fields exist, and failure status is not wired.

- [ ] **Step 5: Wire `FateEventHistory` into startup and detection**

Replace plugin fields with:

```java
private FateEventHistory eventHistory;
private boolean historySaveFailed;
```

Startup uses:

```java
Path dataPath = dataDirectory().toPath();
eventHistory = new FateEventHistory(
    gson,
    dataPath.resolve("event-history.json"),
    dataPath.resolve("event-outbox.json"));
historySaveFailed = false;
```

If construction throws, log once, set `eventHistory = null`, and set `historySaveFailed = true`.

Change `record(DetectedEvent)` to require only a non-null detected event, valid bundle with non-blank run ID, available logged-in account, and available history. On a successful unique write, clear the failure flag; on `IOException`, retain prior counts, set it, and log `Could not persist local Fate event history`.

Use:

```java
private void updatePanelRollInbox()
{
    List<FateEvent> events = eventHistory == null
        ? Collections.emptyList() : eventHistory.events();
    int needsReview = (int) events.stream()
        .filter(event ->
            event.getConfidence() == EventConfidence.UNCERTAIN)
        .count();
    panel.updateRollInboxStatus(
        events.size(), needsReview, activeWarningCount(),
        historySaveFailed);
}
```

- [ ] **Step 6: Delete every plugin-to-relay gameplay path**

Make `pollTrackerConnection()` call only `connectionController.poll()`. Delete:

- `flushRelayEvents()`;
- `SuggestionDto`;
- `MAX_SUGGESTIONS`;
- `loadRelayToken(...)` and `saveRelayToken(...)`;
- `pairingIsCurrent(...)`;
- `pushSuggestion(...)`;
- `postSuggestions(...)`;
- plugin-local `TokenResponse` and `RelayMessage`;
- the diary call to `pushSuggestion("Diary", name)`;
- production and tests for `FateEventRelayClient`;
- production and tests for `FateEventOutbox`.

Rename `relayPollFuture`, `startRelayPoll()`, and `stopRelayPoll()` to `trackerPollFuture`, `startTrackerPoll()`, and `stopTrackerPoll()` so the remaining schedule describes inbound bundle polling.

Remove obsolete OkHttp `Call`, `Callback`, `Request`, and `Response` imports from `FateLockedPlugin`; retain the injected `OkHttpClient` only because the controller consumes it.

- [ ] **Step 7: Run local-history, import, startup, and Guardian suites**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.events.FateEventHistoryTest --tests com.fatelocked.FateLockedPluginLocalHistoryTest --tests com.fatelocked.FateLockedRelayImportTest --tests com.fatelocked.FateLockedPluginStartupContractTest --tests com.fatelocked.FateLockedPluginTravelAccountBindingTest --tests "com.fatelocked.guardian.*" --tests "com.fatelocked.guardian.travel.*"
```

Expected: all tests pass and no deleted relay test class is selected.

- [ ] **Step 8: Scan production plugin source for retired flows**

Run:

```powershell
rg -n "/events|/acks|/suggest|/state|FateEventRelayClient|FateEventOutbox|RequestBody|\.post\(|\.put\(|\.patch\(|\.delete\(" src/main/java
```

Expected: no matches.

- [ ] **Step 9: Commit the local-only runtime**

```powershell
git add src/main/java/com/fatelocked/FateLockedPlugin.java src/main/java/com/fatelocked/events src/test/java/com/fatelocked/events src/test/java/com/fatelocked/FateLockedRelayImportTest.java src/test/java/com/fatelocked/FateLockedPluginLocalHistoryTest.java src/test/java/com/fatelocked/FateLockedPluginStartupContractTest.java src/test/java/com/fatelocked/FateLockedPluginTravelAccountBindingTest.java
git commit -m "refactor: keep detected gameplay events local"
```

---

### Task 5: Add automated Plugin Hub source and jar compliance gates

**Files:**
- Create: `src/test/java/com/fatelocked/PluginHubNetworkBoundaryTest.java`
- Modify: `src/test/java/com/fatelocked/UnifiedPluginContractTest.java`
- Modify: `build.gradle`
- Modify: `.github/workflows/build.yml`

**Interfaces:**
- Produces: source-level proof of one fixed-host GET and one descriptor
- Produces: Gradle task `verifyPluginHubJar`
- Produces: `check` dependency on the standard-jar gate

- [ ] **Step 1: Write the source boundary test**

Walk `src/main/java` and join every `.java` source. Assert:

```java
assertEquals(1, occurrences(allSource, "new Request.Builder()"));
assertTrue(controllerSource.contains(
    "TrackerConnectionSettings.RELAY_BASE_URL + \"/r/\" + token.code"));
assertTrue(controllerSource.contains(".get()"));
assertFalse(allSource.contains("/events"));
assertFalse(allSource.contains("/acks"));
assertFalse(allSource.contains("/suggest"));
assertFalse(allSource.contains("/state"));
assertFalse(allSource.contains("RequestBody"));
assertFalse(allSource.contains("FateEventRelayClient"));
assertFalse(allSource.contains("localhost"));
assertFalse(allSource.contains("127.0.0.1"));
assertEquals(1, occurrences(allSource, "@PluginDescriptor("));
```

Also reject `.post(`, `.put(`, `.patch(`, `.delete(`, `ProcessBuilder`, `Runtime.getRuntime().exec`, `Class.forName`, `java.lang.reflect`, `ServerSocket`, and embedded HTTP-server classes. Permit token-prefix string literals only inside `TrackerConnectionSettings.clearLegacySettings()` and assert there are no token getters, setters, request fields, or token response DTOs elsewhere.

- [ ] **Step 2: Run the source test**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat test --tests com.fatelocked.PluginHubNetworkBoundaryTest --tests com.fatelocked.UnifiedPluginContractTest
```

Expected: PASS after Task 4; temporarily reintroducing any forbidden route or second request builder makes the test fail.

- [ ] **Step 3: Add the standard-jar inspection task**

Add a portable Gradle task:

```groovy
tasks.register('verifyPluginHubJar') {
    dependsOn tasks.named('jar')
    doLast {
        File pluginJar = tasks.named('jar').get()
            .archiveFile.get().asFile
        def zip = new java.util.zip.ZipFile(pluginJar)
        try {
            def entries = zip.entries().toList()
            def names = entries.collect { it.name }
            [
                'net/runelite/', 'okhttp3/', 'com/google/gson/',
                'com/google/inject/', 'org/slf4j/'
            ].each { prefix ->
                if (names.any { it.startsWith(prefix) }) {
                    throw new GradleException(
                        "shaded dependency found: ${prefix}")
                }
            }
            String classText = entries
                .findAll { !it.directory && it.name.endsWith('.class') }
                .collect {
                    new String(
                        zip.getInputStream(it).bytes,
                        java.nio.charset.StandardCharsets.ISO_8859_1)
                }.join('\n')
            ['/events', '/acks', '/suggest', '/state',
             'FateEventRelayClient', 'RequestBody',
             'localhost', '127.0.0.1'].each { forbidden ->
                if (classText.contains(forbidden)) {
                    throw new GradleException(
                        "forbidden jar symbol: ${forbidden}")
                }
            }
        } finally {
            zip.close()
        }
    }
}

check.dependsOn tasks.named('verifyPluginHubJar')
```

Keep every dependency `compileOnly`; do not add Shadow or another packaging plugin.

- [ ] **Step 4: Make CI run the complete gate**

Replace separate test/jar commands in `.github/workflows/build.yml` with:

```yaml
- name: Test and verify Hub-compatible jar
  run: gradle clean check --no-daemon
```

Keep the existing artifact upload from `build/libs/*.jar`.

- [ ] **Step 5: Run the clean compliance build**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat clean check
```

Expected: full tests, source gate, jar creation, and jar inspection all pass.

- [ ] **Step 6: Commit compliance automation**

```powershell
git add src/test/java/com/fatelocked/PluginHubNetworkBoundaryTest.java src/test/java/com/fatelocked/UnifiedPluginContractTest.java build.gradle .github/workflows/build.yml
git commit -m "test: enforce Plugin Hub network boundary"
```

---

### Task 6: Port one-click pairing onto the current companion app

**Files:**
- Create in fresh companion worktree: `utils/runelitePairing.ts`
- Create: `utils/runelitePairing.test.ts`
- Create: `components/RunelitePairingDialog.tsx`
- Create: `components/RunelitePairingDialog.test.tsx`
- Create: `services/relaySync.test.ts`
- Create: `components/OnlineSyncDriver.test.tsx`
- Modify: `services/relaySync.ts`
- Modify: `components/OnlineSyncDriver.tsx`
- Modify: `utils/changelogState.ts`
- Modify: `utils/changelogState.test.ts`
- Modify: `scripts/runeliteRepositoryBoundary.test.ts`
- Modify: `App.tsx`

**Interfaces:**
- Produces: `RUNELITE_PAIR_HASH_PREFIX`
- Produces: `RUNELITE_PAIR_CODE_PATTERN`
- Produces: `RUNELITE_PAIRING_SUCCESS_COPY`
- Produces: `isRunelitePairCode(value: string): boolean`
- Produces: `parseRunelitePairFragment(hash: string): string | null`
- Produces: `RelaySyncService.adoptCode(code: string): boolean`
- Produces: `RelaySyncService.pushRequestRevision: number`
- Produces: `RelaySyncService.requestPush(): boolean`
- Produces: `RelaySyncService.reportPushFailure(error: unknown): void`
- Retains: current app `postOwnedSubresource(...)`, run IDs/revisions, custom mode, Roll Inbox driver, and centralized RuneLite repository boundary

- [ ] **Step 1: Create the fresh current-main worktree**

Invoke `superpowers:using-git-worktrees`. Fetch `origin`, verify the target commit is current, then create:

```powershell
git worktree add C:\Users\alexa\Downloads\flitest-main\flitest-main\.worktrees\inbound-only-runelite-pairing -b feature/inbound-only-runelite-pairing origin/main
```

Expected: the new worktree is clean and does not contain the retired `runelite-plugin/` mirror. Record the exact base commit in the SDD ledger. If dependencies are absent, run `npm ci` before the first test; do not change either package file.

- [ ] **Step 2: Write strict pairing utility tests**

Define validators in `utils/runelitePairing.ts` so they have no service/localStorage side effect:

```ts
export const RUNELITE_PAIR_CODE_PATTERN = /^[0-9a-f]{32}$/;
export const RUNELITE_PAIR_HASH_PREFIX = '#runelite-pair=';
export const RUNELITE_PAIRING_SUCCESS_COPY =
  'Profile sent. Return to RuneLite; its Fate Locked panel will show Connected after the first valid import.';

export const isRunelitePairCode = (value: string): boolean =>
  RUNELITE_PAIR_CODE_PATTERN.test(value);
```

Test pure parsing of a valid fragment. Malformed, uppercase, `#sync=`, and `#/overlay` hashes return null. Fragment removal belongs to `App.tsx`, where it is independently covered.

- [ ] **Step 3: Write relay adoption, retry-signal, and stale-completion tests**

Export `RelaySyncService` for direct tests. Assert an invalid code changes nothing; a valid code atomically stores `{code, token}`, sets `syncing`, emits once, and replacement creates a new token. If `localStorage.setItem` throws, `adoptCode` returns false and retains the previous session.

Also cover:

- `adoptCode(...)` advances `pushRequestRevision`, causing an immediate build;
- `requestPush()` returns false without an active session, otherwise sets `syncing`, clears the old error, advances the revision, emits once, and returns true;
- `reportPushFailure(...)` changes the current active session to `error`, while the driver suppresses a delayed build failure when its captured code no longer equals `relaySync.code`;
- `push(...)` captures the current `{code, token}` before awaiting the POST;
- resolving or rejecting a POST after `disable()` or re-pairing does not change the newer session's status, error, or `lastSyncAt`;
- legacy `enable()` and `disable()` retain their current compatibility behavior.

- [ ] **Step 4: Write phased dialog, driver, modal-policy, and ownership tests**

`RunelitePairingDialog.test.tsx` must cover:

- profile and linked account;
- `No bound account` fallback;
- replacement warning;
- `confirm`, `uploading`, `success`, and `error` phases;
- the exact `RUNELITE_PAIRING_SUCCESS_COPY` in success;
- Retry from error;
- Cancel before confirmation;
- Close after success;
- backdrop Cancel without button propagation during confirmation.

Use this component contract:

```ts
interface RunelitePairingDialogProps {
  code: string;
  replacing: boolean;
  profileName: string;
  linkedAccount: string | null;
  phase: 'confirm' | 'uploading' | 'success' | 'error';
  error?: string;
  onConfirm(): void;
  onRetry(): void;
  onClose(): void;
}
```

`OnlineSyncDriver.test.tsx` adopts code A, advances 1500 ms, observes a POST to A, adopts code B without changing run state, advances 1500 ms, and observes a second POST to B. A failed bundle build calls `reportPushFailure`; a delayed code-A build failure after adopting code B is ignored. `requestPush()` after a current-session failure triggers a fresh `buildBundlePayload(...)` using the current game state rather than replaying a stored payload. The effect dependency list must retain every current bundle input and add both `relaySync.code` and `relaySync.pushRequestRevision`.

Extend `changelogState.test.ts` so a valid `#runelite-pair=<code>` suppresses automatic changelog opening just like `#sync=`, while an invalid pairing fragment does not.

Extend `scripts/runeliteRepositoryBoundary.test.ts` so the retained integration-path list includes `components/RunelitePairingDialog.tsx` and `utils/runelitePairing.ts`. Keep its assertion that the retired `runelite-plugin/` mirror is absent.

- [ ] **Step 5: Run focused companion tests to verify RED**

Run from the fresh companion worktree:

```powershell
npm test -- utils/runelitePairing.test.ts services/relaySync.test.ts components/RunelitePairingDialog.test.tsx components/OnlineSyncDriver.test.tsx utils/changelogState.test.ts scripts/runeliteRepositoryBoundary.test.ts
```

Expected: missing utility/dialog/service interfaces fail.

- [ ] **Step 6: Implement adoption and pairing UI**

`RelaySyncService.adoptCode(...)` validates before mutation, creates a fresh private browser write token, persists the whole next session before assigning it, resets status/error/time, advances `pushRequestRevision`, and emits once. `requestPush()` advances that revision so the driver always rebuilds the current profile. Do not store a `lastPayload` or expose a `retryLastPush()` API.

In `push(...)`, copy the session before `fetch`, POST with that copied code/token, and compare it to the current session before committing success or failure state. A completion from a disabled or replaced session is stale and must be ignored.

The dialog copy must be directional:

```tsx
<p>
  Connect this tracker profile so RuneLite can retrieve its
  Fate Locked rules. RuneLite does not upload gameplay data.
</p>
```

In `App.tsx`:

- read `activeProfileName` from `useProfiles()`;
- read `linkedAccount` from `useGame()`;
- parse one valid fragment on mount and immediately scrub it with `history.replaceState`, before the user confirms;
- include `!!runelitePairCode` in the boolean `anyModalOpen`;
- clear the pairing dialog on Escape;
- render it only when `modalRenderPolicy.renderGlobalDialogOverlays` permits;
- leave the dialog open after successful `adoptCode(...)`;
- subscribe to `relaySync` and map its status to `uploading`, `success`, or `error`;
- if `adoptCode(...)` returns false, retain the confirmation state and show a local inline save error so Confirm can be retried without replacing the prior session;
- call `relaySync.requestPush()` for Retry so the driver rebuilds the active profile;
- close only on Cancel before confirmation or Close after success.

Do not copy the stale branch's removed modals, old GameContext shape, or `runelite-plugin/` mirror.

- [ ] **Step 7: Make replacement and retry requests publish current state**

Read:

```ts
const sessionCode = relaySync.code;
const pushRequestRevision = relaySync.pushRequestRevision;
```

Include both in `OnlineSyncDriver`'s effect dependency list while retaining current `runId`, `runRevision`, `customMode`, and all current bundle fields. Capture `sessionCode` for each build; a bundle-build rejection calls `relaySync.reportPushFailure(error)` only if that code is still current. A network failure remains handled inside `push(...)`.

- [ ] **Step 8: Run tests and typecheck**

Run:

```powershell
npm test -- utils/runelitePairing.test.ts services/relaySync.test.ts components/RunelitePairingDialog.test.tsx components/OnlineSyncDriver.test.tsx utils/changelogState.test.ts scripts/runeliteRepositoryBoundary.test.ts
npm run typecheck
```

Expected: focused tests and typecheck pass.

- [ ] **Step 9: Commit the current-app pairing port**

```powershell
git add utils/runelitePairing.ts utils/runelitePairing.test.ts components/RunelitePairingDialog.tsx components/RunelitePairingDialog.test.tsx services/relaySync.ts services/relaySync.test.ts components/OnlineSyncDriver.tsx components/OnlineSyncDriver.test.tsx utils/changelogState.ts utils/changelogState.test.ts scripts/runeliteRepositoryBoundary.test.ts App.tsx
git commit -m "feat: add current one-click RuneLite pairing"
```

---

### Task 7: Remove companion heartbeat claims and add retryable profile delivery

**Files:**
- Modify: `services/relaySync.ts`
- Modify: `services/relaySync.test.ts`
- Modify: `components/RuneLiteOnboarding.tsx`
- Create: `components/RuneLiteOnboarding.test.tsx`
- Modify: `components/OnlineSyncDriver.test.tsx`

**Interfaces:**
- Removes: `RelaySyncService.fetchPluginState()`
- Uses: `RelaySyncService.requestPush(): boolean`
- Retains: browser `POST /r/<code>` bundle publication and `postOwnedSubresource(...)` for temporary legacy app compatibility

- [ ] **Step 1: Write no-heartbeat and directional onboarding tests**

Mock only `enabled`, `code`, `status`, `lastError`, `lastSyncAt`, `subscribe`, `requestPush`, and `disable`. There must be no `fetchPluginState` mock.

Cover:

```ts
expect(screen.getByText(
  'Profile sent. Return to RuneLite; its Fate Locked panel will show Connected after the first valid import.',
)).toBeTruthy();
expect(screen.queryByText(/^Connected$/i)).toBeNull();
```

Also assert:

- off state says to start from RuneLite's `Connect tracker`;
- syncing says `Sending profile to RuneLite…`;
- error shows `Retry profile upload`;
- clicking Retry calls `requestPush()` once;
- no instructions mention `Enable online sync`, `Online sync code`, or copying an eight-character code;
- advanced recovery describes clipboard/file import and does not claim local history transfers to the web Roll Inbox.

- [ ] **Step 2: Verify the current tests fail for the intended heartbeat behavior**

Run:

```powershell
npm test -- services/relaySync.test.ts components/RuneLiteOnboarding.test.tsx components/OnlineSyncDriver.test.tsx
```

Expected: the service still exposes `/state` and onboarding still polls it.

- [ ] **Step 3: Remove heartbeat reads**

Delete `fetchPluginState()` and its `/state` string. Do not change the web app's bundle POST, session generation retained for legacy callers, or legacy `postOwnedSubresource`.

- [ ] **Step 4: Replace heartbeat-derived UI with delivery-derived UI**

Use:

```ts
type DeliveryStatus =
  | 'off' | 'sending' | 'sent' | 'upload-error';

const deliveryStatus: DeliveryStatus = !relaySync.enabled
  ? 'off'
  : relaySync.status === 'error'
    ? 'upload-error'
    : relaySync.status === 'synced'
      ? 'sent'
      : 'sending';
```

Delete `pluginSeen`, `POLL_MS`, the polling effect, `acknowledgedAt`, and every badge or sentence that claims the plugin is connected. The sent state imports and uses `RUNELITE_PAIRING_SUCCESS_COPY`. The collapsed state says `Profile sent`, not `Connected`. Error Retry calls `relaySync.requestPush()`.

Keep the Plugin Hub link, stream-overlay control for an active browser session, Disconnect, and clipboard/file recovery. Do not expose generation of a new legacy eight-character code in normal or advanced UI.

- [ ] **Step 5: Add no-heartbeat and compatibility regressions**

In `services/relaySync.test.ts`, read `services/relaySync.ts` and `components/RuneLiteOnboarding.tsx` with `node:fs` and assert neither contains:

```ts
expect(source).not.toContain('/state');
expect(source).not.toContain('fetchPluginState');
expect(source).not.toContain('Plugin connected');
```

Do not scan `workers/fate-relay`; legacy routes are intentionally retained for older installed clients.

Run the existing `#sync`, stream-overlay, `RollInboxDriver`, `fateEventRelay` acknowledgement, current run identity/revision, and non-pairing navigation tests unchanged. These paths remain compatibility behavior and must not be rewritten as part of the new primary pairing flow.

- [ ] **Step 6: Run focused and full companion verification**

Run:

```powershell
npm test -- services/relaySync.test.ts components/RuneLiteOnboarding.test.tsx components/OnlineSyncDriver.test.tsx components/RunelitePairingDialog.test.tsx utils/runelitePairing.test.ts utils/changelogState.test.ts
npm run typecheck
npm test
```

Expected: all tests and typecheck pass; baseline Vite deprecation warnings may remain.

- [ ] **Step 7: Commit inbound-only companion behavior**

```powershell
git add services/relaySync.ts services/relaySync.test.ts components/RuneLiteOnboarding.tsx components/RuneLiteOnboarding.test.tsx components/OnlineSyncDriver.test.tsx
git commit -m "refactor: make RuneLite onboarding inbound only"
```

---

### Task 8: Rewrite plugin release documentation for the Hub candidate

**Files:**
- Modify: `README.md`
- Modify: `CONTRIBUTING.md`
- Modify: `runelite-plugin.properties`
- Modify: `src/main/java/com/fatelocked/FateLockedPlugin.java`
- Create: `docs/plugin-hub-review-notes.md`
- Create: `docs/plugin-hub-manual-matrix.md`

**Interfaces:**
- Produces: reviewer-facing statement of the fixed GET-only boundary
- Produces: explicit Strict Mode pre-clearance request
- Produces: same-PC validation matrix without claiming Hub acceptance

- [ ] **Step 1: Write a current-doc regression scan**

Add shell verification to the manual matrix and run it against current release docs:

```powershell
rg -n "Enable online sync|Online sync code|event-outbox|/events|/acks|/suggest|heartbeat|delivered with a stable event ID" README.md CONTRIBUTING.md runelite-plugin.properties src/main/java/com/fatelocked/FateLockedPlugin.java
```

Expected before edits: current README and CONTRIBUTING matches demonstrate stale claims.

- [ ] **Step 2: Rewrite end-user setup and Roll Inbox ownership**

README setup becomes:

1. install the single `Fate Locked Ironman` Hub plugin;
2. click `Connect tracker` in its one sidebar;
3. confirm the current profile in the opened GitHub Pages tab;
4. return to RuneLite and verify `Connected`;
5. use clipboard/file import only as recovery.

Describe Roll Inbox as the newest 250 local observations, with ambiguous detections counted under `Needs review`. State that `Open web Roll Inbox` opens a separate browser view and does not transfer local history.

- [ ] **Step 3: Rewrite developer and manifest descriptions**

CONTRIBUTING must state:

- the controller's sole fixed GET;
- strict v4 full validation before replacement;
- no plugin POST, event relay, heartbeat, acknowledgement, suggestion, or relay token;
- local history schema/migration/corruption behavior;
- exact source/jar compliance commands;
- unchanged standard build and Java-only constraints.

Update `runelite-plugin.properties` and `@PluginDescriptor` descriptions without saying the plugin automatically sends detections to the app.

- [ ] **Step 4: Add reviewer notes and Strict Mode pre-clearance text**

`docs/plugin-hub-review-notes.md` must link the official rejected-features and Plugin Hub review pages, list the one allowed request, and say:

```text
Strict Mode does not remove or reorder menu entries and never performs an
action. It can consume a user-selected click only when fresh, exact,
account-bound app-authored rules prove the action Locked. Because that is
behaviorally adjacent to conditional menu-entry restrictions, we request
reviewer pre-clearance and do not claim that this behavior is already
approved.
```

List every fail-open state and the 60-second pause.

- [ ] **Step 5: Add the same-PC manual matrix**

Create a table with rows for:

- one sidebar and seven independent sections;
- 30 retained settings;
- Connect opens the exact fragment URL;
- companion confirmation publishes v4;
- first valid GET import becomes locally `Connected`;
- 304 refresh;
- malformed/stale/wrong ETag keeps prior rules;
- offline and 404 status;
- local event appears in `event-history.json`;
- 251st event discards oldest;
- web Roll Inbox receives no local event;
- clipboard and file bundles still create local history;
- history write failure status and recovery;
- Guardian exact block, Unknown fail-open, wrong account, stale rules, pause/resume;
- no RuneLite legacy route requests.

Each row has columns `Automated evidence`, `Manual result`, and `Notes`; initial manual result is `Not run` and Task 10 replaces it with the observed result.

- [ ] **Step 6: Run documentation and compliance scans**

Run:

```powershell
rg -n "Enable online sync|Online sync code|event-outbox|/events|/acks|/suggest|heartbeat|delivered with a stable event ID" README.md CONTRIBUTING.md runelite-plugin.properties src/main/java/com/fatelocked/FateLockedPlugin.java
rg -n "Local only — RuneLite does not upload gameplay data|Strict Mode|pre-clearance|GET https://fate-relay.fatelocked.workers.dev/r/" README.md CONTRIBUTING.md docs
```

Expected: first command has no matches; second command finds the new exact disclosures and review note.

- [ ] **Step 7: Run clean plugin check and commit docs**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat clean check
```

Then:

```powershell
git add README.md CONTRIBUTING.md runelite-plugin.properties src/main/java/com/fatelocked/FateLockedPlugin.java docs/plugin-hub-review-notes.md docs/plugin-hub-manual-matrix.md
git commit -m "docs: describe inbound-only Hub candidate"
```

---

### Task 9: Rewrite current companion relay documentation

**Files:**
- Modify in companion worktree: `README.md`
- Modify: `ROADMAP.md`
- Modify: `docs/online-relay.md`
- Modify: `components/StreamOverlay.tsx`

**Interfaces:**
- Documents: browser publishes v4; RuneLite retrieves v4; local events stay in RuneLite
- Preserves: legacy Worker route documentation, clearly labelled for older installed clients

- [ ] **Step 1: Replace current bidirectional claims**

README and `docs/online-relay.md` must distinguish:

```text
Current Hub candidate:
  Browser POST /r/<code>       publishes the app-authored v4 bundle
  RuneLite GET /r/<code>      retrieves and validates that bundle

Legacy compatibility only:
  /state, /events, /acks, /suggest
```

Remove instructions to enable a plugin toggle or paste a manual code. State that the companion cannot know whether RuneLite imported the bundle because RuneLite intentionally sends no receipt.

Update `ROADMAP.md` so its current architecture says browser POST plus RuneLite GET. Move `/state`, `/events`, `/acks`, and `/suggest` into an explicitly temporary legacy-compatibility note rather than describing them as the current Hub design.

- [ ] **Step 2: Update stream-overlay recovery copy**

Change the 404 guidance from `enable Online sync in the tracker` to:

```text
Nothing is published yet — connect this profile from RuneLite first.
```

The stream overlay still reads the web-authored bundle and is not part of RuneLite local event history.

- [ ] **Step 3: Scan current companion UI/docs**

Run:

```powershell
rg -n "Enable online sync|Online sync code|Plugin connected|plugin heartbeat|fetchPluginState|/state" README.md ROADMAP.md docs/online-relay.md components/RuneLiteOnboarding.tsx components/StreamOverlay.tsx services/relaySync.ts
```

Expected: `/state` appears only in explicitly labelled legacy compatibility sections in `ROADMAP.md` and `docs/online-relay.md`; no current UI/service claim remains.

- [ ] **Step 4: Run companion release verification**

Run:

```powershell
npm run release:verify
```

Expected: full tests, typecheck, content verification, and production build pass.

- [ ] **Step 5: Commit companion documentation**

```powershell
git add README.md ROADMAP.md docs/online-relay.md components/StreamOverlay.tsx
git commit -m "docs: explain directional RuneLite connection"
```

---

### Task 10: Run whole-system verification and prepare the testable artifacts

**Files:**
- Modify: `docs/plugin-hub-manual-matrix.md`
- Create: `.superpowers/sdd/2026-07-28-inbound-only-plugin-hub/final-report.md`

**Interfaces:**
- Produces: verified plugin jar, companion production build, commit IDs, jar hash, and same-PC evidence
- Does not publish: GitHub branches, GitHub Pages, Plugin Hub pins, or reviewer requests

- [ ] **Step 1: Run the complete plugin suite and parse results**

Run:

```powershell
C:\Users\alexa\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat clean check
```

Parse `build/test-results/test/*.xml` and record suites, tests, failures, errors, and skips in `final-report.md`. Expected failures/errors: zero.

- [ ] **Step 2: Inspect and hash the standard jar**

Run:

```powershell
$jar = Get-ChildItem build\libs\*.jar | Sort-Object LastWriteTime -Descending | Select-Object -First 1
jar tf $jar.FullName
Get-FileHash -Algorithm SHA256 $jar.FullName
```

Record the absolute jar path and SHA-256. Verify there are no dependency package trees and exactly one plugin class with a descriptor.

- [ ] **Step 3: Run complete companion release verification**

From the fresh companion worktree:

```powershell
npm run release:verify
```

Record test totals, typecheck, content verification, build result, and production `dist/` path.

- [ ] **Step 4: Perform the same-PC connection matrix**

With user approval for the out-of-workspace copy, place the jar in `%USERPROFILE%\.runelite\sideloaded-plugins\`, start the current companion production preview, and exercise:

1. one RuneLite sidebar;
2. seven collapsible sections and exact expansion defaults;
3. Connect opens `#runelite-pair=<32 lowercase hex>`;
4. confirm the active profile;
5. companion says the exact `Profile sent...` sentence;
6. RuneLite becomes `Connected` only after its valid import;
7. re-pair invalidates the prior code;
8. offline/recovery retains prior rules;
9. local event counts/history do not appear in the web Roll Inbox;
10. Strict Mode exact block, fail-open, and 60-second pause.

Use the controller's instrumented request test and the source/jar gate as the authoritative network evidence; do not install a localhost bridge or weaken HTTPS to inspect traffic.

- [ ] **Step 5: Update the matrix with observed results**

Replace every `Not run` cell in `docs/plugin-hub-manual-matrix.md` with `Pass`, `Fail`, or `Blocked`, include the exact evidence, and do not mark a row Pass without observing it. A blocked live-game event row retains its automated evidence and names the missing manual condition.

- [ ] **Step 6: Run final forbidden-flow and singular-plugin scans**

Run:

```powershell
rg -n "/events|/acks|/suggest|/state|FateEventRelayClient|FateEventOutbox|RequestBody|localhost|127\.0\.0\.1" src/main/java
rg -n "@PluginDescriptor" src/main/java
rg -n "NavigationButton\.builder" src/main/java
rg -n "Enable online sync|Online sync code|Plugin connected|fetchPluginState" C:\Users\alexa\Downloads\flitest-main\flitest-main\.worktrees\inbound-only-runelite-pairing\components C:\Users\alexa\Downloads\flitest-main\flitest-main\.worktrees\inbound-only-runelite-pairing\services
```

Expected: first and fourth scans have no matches; descriptor and navigation scans each have exactly one match.

- [ ] **Step 7: Request independent whole-branch review**

Review both implementation ranges against `docs/superpowers/specs/2026-07-28-inbound-only-plugin-hub-design.md`. Classify findings as Critical, Important, or Minor. Fix every Critical and Important finding with a failing regression first, rerun each repository's complete verification, and record the fix commit IDs.

- [ ] **Step 8: Commit verified matrix results**

```powershell
git add docs/plugin-hub-manual-matrix.md
git commit -m "test: record inbound-only integration matrix"
```

Finish with both worktrees clean except ignored SDD evidence. Report the two branch names, commit ranges, jar path/hash, companion build path, test totals, manual results, remaining Strict Mode reviewer pre-clearance, and the fact that no external push/deploy/submission occurred.
