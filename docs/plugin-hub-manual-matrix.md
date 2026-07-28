# Inbound-only Plugin Hub manual matrix

Automated evidence identifies the regression supporting each row. Manual
results below were recorded during the final same-PC validation on 2026-07-28.
`Blocked` means the scenario needs a logged-in game session or controlled fault
injection; its automated evidence still passed.

| Scenario | Automated evidence | Manual result | Notes |
|---|---|---|---|
| One sidebar and seven independent sections | `FateLockedPluginStartupContractTest`, `FateLockedPanelStatusTest`, `PluginHubNetworkBoundaryTest` | Pass | One Fate Locked panel showed Current chunk, Guardian, Roll inbox, Run, Bundle, Warnings, and Rendering. Guardian, Bundle, and Warnings were collapsed and expanded independently. |
| 30 retained settings | `FateLockedPanelStatusTest` | Pass | All retained controls were visually inspected across the seven sections. No old online-sync controls were present. |
| Connect opens the exact fragment URL | `PairingSupportTest`, startup contract | Pass | Opened `#runelite-pair=d84e09f54c4d499eafb89cf14b5d7a96`, matching 32 lowercase hexadecimal characters. |
| Companion confirmation publishes v4 | Companion pairing and driver tests | Pass | The local production build showed the requested profile confirmation and exact directional success message; RuneLite then imported 13 regions. |
| First valid GET import becomes locally Connected | `TrackerConnectionControllerTest` | Pass | RuneLite changed from Waiting to Connected after the first valid import. Connected remained RuneLite-local status. |
| Unchanged bundle returns 304 | `TrackerConnectionControllerTest` | Pass | A conditional read using the accepted version returned HTTP 304; RuneLite retained the active rules. |
| Malformed, stale, or wrong ETag keeps prior rules | Controller and relay import tests | Blocked | Requires controlled relay response injection; automated regression coverage passed. |
| Offline and 404 status | `TrackerConnectionControllerTest` | Blocked | Requires network manipulation or relay expiry; automated regression coverage passed. |
| Local event appears in `event-history.json` | `FateLockedPluginLocalHistoryTest` | Blocked | No logged-in gameplay session was available; automated regression coverage passed. |
| 251st event discards the oldest | `FateEventHistoryTest` | Blocked | No live event sequence was generated; the 250-entry boundary passed automated coverage. |
| Web Roll Inbox receives no local event | Source and jar boundary gates | Blocked | No live gameplay event was generated; source and packaged-JAR boundary gates passed. |
| Clipboard and file bundles still create local history | `FateLockedPluginLocalHistoryTest` | Blocked | Not exercised in the final live session; automated regression coverage passed. |
| History write failure status and recovery | History and plugin local-history tests | Blocked | Requires controlled filesystem failure; automated regression coverage passed. |
| Guardian exact Locked block | Guardian and travel matrices | Blocked | No logged-in gameplay session was available; automated regression coverage passed. |
| Guardian Unknown fails open | Guardian and travel matrices | Blocked | No logged-in gameplay session was available; automated regression coverage passed. |
| Guardian wrong account fails open | Travel account-binding regression | Blocked | No logged-in gameplay session was available; automated regression coverage passed. |
| Guardian stale rules fail open | Guardian freshness regressions | Blocked | No logged-in gameplay session was available; automated regression coverage passed. |
| Guardian pause and automatic resume | Guardian pause tests | Blocked | The pause control was visible, but the live 60-second timer was not exercised; automated regression coverage passed. |
| No RuneLite legacy route requests | `PluginHubNetworkBoundaryTest`, `verifyPluginHubJar` | Pass | The tested pairing flow used the fixed `/r/<code>` read; source and packaged-JAR boundary gates found no legacy route or write client. |
