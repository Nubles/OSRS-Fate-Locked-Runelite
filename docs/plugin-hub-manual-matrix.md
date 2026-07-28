# Inbound-only Plugin Hub manual matrix

Automated evidence identifies the regression that supports each row. Manual
results remain `Not run` until the final same-PC validation.

| Scenario | Automated evidence | Manual result | Notes |
|---|---|---|---|
| One sidebar and seven independent sections | `FateLockedPluginStartupContractTest`, `FateLockedPanelStatusTest`, `PluginHubNetworkBoundaryTest` | Not run | Verify section order and independent collapse state. |
| 30 retained settings | `FateLockedPanelStatusTest` | Not run | Verify all setting controls are present. |
| Connect opens the exact fragment URL | `PairingSupportTest`, startup contract | Not run | Expect `#runelite-pair=<32 lowercase hex>`. |
| Companion confirmation publishes v4 | Companion pairing and driver tests | Not run | Confirm the active profile on the same PC. |
| First valid GET import becomes locally Connected | `TrackerConnectionControllerTest` | Not run | Connected is RuneLite-local state only. |
| Unchanged bundle returns 304 | `TrackerConnectionControllerTest` | Not run | Prior rules remain active. |
| Malformed, stale, or wrong ETag keeps prior rules | Controller and relay import tests | Not run | Test each rejection independently. |
| Offline and 404 status | `TrackerConnectionControllerTest` | Not run | Prior valid rules must remain. |
| Local event appears in `event-history.json` | `FateLockedPluginLocalHistoryTest` | Not run | No relay event request. |
| 251st event discards the oldest | `FateEventHistoryTest` | Not run | Exactly 250 remain. |
| Web Roll Inbox receives no local event | Source and jar boundary gates | Not run | Web view is separate from local history. |
| Clipboard and file bundles still create local history | `FateLockedPluginLocalHistoryTest` | Not run | Relay, clipboard, and file share local recording. |
| History write failure status and recovery | History and plugin local-history tests | Not run | Rules and durable prior history remain. |
| Guardian exact Locked block | Guardian and travel matrices | Not run | Only a user-selected exact action is consumed. |
| Guardian Unknown fails open | Guardian and travel matrices | Not run | Include unresolved and ambiguous routes. |
| Guardian wrong account fails open | Travel account-binding regression | Not run | No click is consumed. |
| Guardian stale rules fail open | Guardian freshness regressions | Not run | No click is consumed. |
| Guardian pause and automatic resume | Guardian pause tests | Not run | Shared pause is 60 seconds. |
| No RuneLite legacy route requests | `PluginHubNetworkBoundaryTest`, `verifyPluginHubJar` | Not run | Sole request is the fixed relay GET. |
