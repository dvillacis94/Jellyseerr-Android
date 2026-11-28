# Seerr for Android: plan

Written 2026-11-17, updated 2026-11-26. A native Android client for Seerr with the same features as the iOS app (`seerr-native`, build 8).

**Status:** phases 0 and 1 are merged (#1, #2). Phase 2 is in review. Phases 3 to 10 have not been started.

Repo: `git@github.com:dvillacis94/Jellyseerr-Android.git` (public).

## Starting point

- **The toolchain is installed.** Android Studio (its bundled JDK, on the external volume), the SDK and an emulator. There are no command-line tools or `avdmanager`, so a test AVD has to be written by hand.
- **Two emulators.** `emulator-5554` is the owner's and is in use for other work: never send it input. Test on our own AVD `SeerrTest` (port 5556) and pin every `adb` call with `-s emulator-5556`. Shut it down afterwards.
- **The project folder is `seerr-android`, next to the iOS project (`seerr-native`).**
- **The Android repo is public; the iOS repo is private.** Nothing personal (hostnames, IPs, keys, Firebase config, signing keys) may be committed. `.gitignore` covers these. This plan is kept local and untracked, excluded through `.git/info/exclude`. Plans, audits and security files stay out of every repo.
- **The push relay only speaks Apple's push service.** Android needs Firebase Cloud Messaging (FCM), so the relay needs a second sender.

## Decisions

All confirmed and in use, except where a row says otherwise.

| Decision | Recommendation | Why |
|---|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3 | The direct counterpart of Swift and SwiftUI; one codebase for phones and tablets |
| Minimum Android version | Android 8.0 (API 26) | Covers nearly all devices in use; avoids old-version workarounds |
| Push | FCM, through the existing relay | The only push that works reliably on stock Android. Needs a free Firebase project, created by the owner |
| Distribution | Signed APK shared directly at first; Play internal testing later | A Play developer account costs $25 once and needs identity verification. An APK needs neither |
| Repo visibility | Public | Decided by the owner. Nothing personal may be committed |

## Architecture

Mirrors the iOS app, so behaviour and tests can be ported one to one.

| iOS | Android |
|---|---|
| SwiftUI views | Compose screens |
| `@Observable` view models | `ViewModel` with `StateFlow` |
| `APIClient`, `APIRouter` | **Plain OkHttp** with kotlinx.serialization: an `ApiClient` plus a `Routes` object. Retrofit was dropped in phase 1 because the base URL is chosen by the user at run time and there were only a few calls. Revisit if the endpoint count makes it worth it |
| `SessionCookieStore` (Keychain) | A persistent OkHttp `CookieJar`, encrypted with the Android Keystore |
| `UserDefaultsStorage` | DataStore |
| `AsyncImage` | Coil |
| `ASWebAuthenticationSession` (Plex) | Chrome Custom Tabs, with the same PIN polling |
| `PagedLoader`, `PagedList` | The same two classes, built once and shared. `PagedLoader` is built (phase 2). `PagedList` for Requests and Issues comes in phase 5, so it isn't unused scaffolding |
| Passing dependencies through initialisers | The same: one `AppContainer` built at launch, no DI framework. Screen logic sits in plain state-holder classes that take a `CoroutineScope` and expose `StateFlow`, so tests need no Android |
| Swift Testing with `StubURLProtocol` | JUnit with OkHttp `MockWebServer` and coroutines-test |
| XcodeGen | Gradle with a version catalog |

Lessons from the iOS audit, applied from the first commit:
- A cancelled request is never shown as an error.
- Pull to refresh keeps the rows on screen until new data arrives.
- Server setup never falls back to HTTP after a certificate error, and HTTP servers are labelled "Not secure".
- A custom relay address belongs to the user and server that set it.
- No sign-in by API key. No unused scaffolding.
- API behaviour is checked against the Seerr server source (`seerr-team/seerr`, `develop`), as on iOS.

Learned in phase 1:
- **Android 17 (API 37) blocks private-network hosts** for apps targeting 37 unless the runtime permission `ACCESS_LOCAL_NETWORK` is granted. Without it, requests to `192.168.x`, `10.x` and similar addresses time out with no useful error. The app asks for it up front when the address looks local, and offers it again after an unreachable error.
- **Cleartext traffic is on**, because many Seerr servers are plain `http` on a home network. The UI labels them "Not secure".
- **The Plex token is never stored.** The PIN flow runs in a Custom Tab with a 15-minute poll.
- **A session that can't be checked because the server is down** shows a retry screen. It does not sign the user out.
- **Test on the emulator through `10.0.2.2`.** A mock Seerr runs on the host. It stays in the scratchpad, not in the repo.

## Phases

One branch and one PR per phase, in the Android repo unless noted. Each PR has unit tests, and is checked in the emulator against the mock server before it is opened.

| # | Phase | Contents |
|---|---|---|
| 0 ✅ | Project setup | Gradle project, `.gitignore`, app icon and colours from the iOS design, empty app that builds and runs, README |
| 1 ✅ | Server and sign-in | Server address entry with the HTTPS rules, sign-in with Plex, local account and Jellyfin, session restore, sign-out, change server. Merged as #2. Sign-in, including Plex, was checked by the owner on an Android 17 emulator |
| 2 🔍 | App shell and Discover | Five tabs (Discover, Movies, Series, Requests, Profile), Discover shelves, global search, media cards with status. Introduces `PagedLoader`, Coil, Navigation Compose and the unauthorized handler (a 401 returns the user to sign-in). Movies, Series and Requests are placeholders; Profile only has Sign out and Change server. **Merged.** Left for later: `PagedList` (phase 5, when Requests needs it) |
| 3 🔍 | Details | Movie, series and collection pages, cast, trailer, recommendations and similar, watchlist button and shelf, tapping any card. **Merged.** Left for later: Request and issue buttons (phases 4 and 7), season availability, Play links, a page for people |
| 4 | Requesting | Split in two. **Part 1 (merged):** Request button and sheet with seasons, 4K and quota, using the user's permissions and what is already requested or available. **Part 2:** advanced Radarr and Sonarr options and requesting for another user (admin only) |
| 5 🔍 | Requests tab | Paged list (`PagedList`, offset-based), filter and sort, approve, decline, retry, delete, review sheet. **In review.** Left for later: a pending-count badge on the tab, "my requests" for managers, editing a request (seasons, server, profile), and the admin-only paths, which are unit-tested only |
| 6 | Movies and Series | Part 1 merged: browse grids and type-limited search. Part 2 merged: filters, saved filters, last-used filters. Left for later: reordering saved filters, keywords, studio or network, vote count, certification |
| 7 | Issues | Issues list in Profile, report an issue, issue page with comments and status |
| 8 | Profile | Split in three. **Part 1 🔍 in review:** the Profile screen (who is signed in, appearance light/dark/system, server details, confirmed sign-out and change server). **Part 2:** notification settings for the other channels (email, Discord, Telegram and so on). **Part 3:** users management (admin only, needs an admin account to check). The Issues row joins in phase 7 |
| 9a | Relay: FCM sender | **In the iOS repo.** `platform` on device registration, an FCM sender next to the Apple one, tests, README. Redeploy the relay |
| 9b | Push on Android | Register with the relay, notification preferences, tapping a notification opens the title or issue |
| 10 | Release | Tablet layouts, release signing, a signed APK, then Play internal testing if wanted |

Phases 1 to 8 need nothing from the relay. Phase 9b needs 9a deployed and the Firebase project from the owner. Phase 9a can be done at any point before 9b.

## Push in more detail

- The relay's device records gain `platform: "apns" | "fcm"`. Existing records are treated as `apns`, so iOS keeps working unchanged.
- The token check accepts FCM tokens, which are not hex.
- The FCM sender uses Google's HTTP v1 API, signed with a service-account key, written without dependencies like the Apple sender.
- New relay settings: `FCM_PROJECT_ID` and `FCM_SERVICE_ACCOUNT_FILE`. Without them the relay runs as today and Android devices are refused with a clear message.
- The app needs `google-services.json` from the Firebase project. It is ignored by git because the repo is public.

## What the owner needs to do

- **Done:** Android Studio installed and the decisions confirmed.
- **Before phase 9:** create a Firebase project, add an Android app with the chosen package name, and download `google-services.json` and a service-account key.
- **Before phase 10:** decide on Play. If yes, register a Play developer account.
- **Throughout:** merge each PR, as on iOS. Try a real Plex sign-in on phase 1 before relying on it.

## Open questions

- **Package name.** Settled: `com.nesoi.seerr.android`. It can't be changed after release.
- **App name on the device.** Settled: "Seerr", as on iOS.
- **Old commit on GitHub.** The history was rewritten to remove an earlier copy of this plan, but GitHub may still serve the old commit through PR #1's refs. Only GitHub Support, or deleting and recreating the repo, removes it. The plan held no secrets.
- **Tablets.** The plan covers them in phase 10 with adaptive layouts. They can be dropped to save time.

## Not in scope

- Sharing code between iOS and Android (Kotlin Multiplatform or similar). Two small native apps are simpler to keep correct than one shared layer.
- Changes to Seerr itself.
- Wear OS, Android TV, widgets.
