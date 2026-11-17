# Seerr for Android: plan

Written 2026-11-17. A native Android client for Seerr with the same features as the iOS app (`seerr-native`, build 7). Nothing here has been started.

Repo: `git@github.com:dvillacis94/Jellyseerr-Android.git` (empty, public at the time of writing).

## Starting point

- **This Mac has no Android toolchain.** There is no Android Studio, JDK, Android SDK or emulator. Only `adb` is installed.
- **The project folder is `seerr-android`, next to the iOS project (`seerr-native`).** It holds only this plan.
- **The Android repo is public; the iOS repo is private.** Nothing personal (hostnames, IPs, keys, Firebase config, signing keys) may be committed. `.gitignore` covers these from the first commit.
- **The push relay only speaks Apple's push service.** Android needs Firebase Cloud Messaging (FCM), so the relay needs a second sender.

## Decisions to confirm before starting

| Decision | Recommendation | Why |
|---|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3 | The direct counterpart of Swift and SwiftUI; one codebase for phones and tablets |
| Minimum Android version | Android 8.0 (API 26) | Covers nearly all devices in use; avoids old-version workarounds |
| Push | FCM, through the existing relay | The only push that works reliably on stock Android. Needs a free Firebase project, created by the owner |
| Distribution | Signed APK shared directly at first; Play internal testing later | A Play developer account costs $25 once and needs identity verification. An APK needs neither |
| Repo visibility | Owner's call | Public is fine if nothing personal is committed. Private matches the iOS repo |

## Architecture

Mirrors the iOS app, so behaviour and tests can be ported one to one.

| iOS | Android |
|---|---|
| SwiftUI views | Compose screens |
| `@Observable` view models | `ViewModel` with `StateFlow` |
| `APIClient`, `APIRouter` | Retrofit on OkHttp, with kotlinx.serialization |
| `SessionCookieStore` (Keychain) | A persistent OkHttp `CookieJar`, encrypted with the Android Keystore |
| `UserDefaultsStorage` | DataStore |
| `AsyncImage` | Coil |
| `ASWebAuthenticationSession` (Plex) | Chrome Custom Tabs, with the same PIN polling |
| `PagedLoader`, `PagedList` | The same two classes, built once and shared from the start |
| Passing dependencies through initialisers | The same: one `AppContainer` built at launch, no DI framework |
| Swift Testing with `StubURLProtocol` | JUnit with OkHttp `MockWebServer` and coroutines-test |
| XcodeGen | Gradle with a version catalog |

Lessons from the iOS audit, applied from the first commit:
- A cancelled request is never shown as an error.
- Pull to refresh keeps the rows on screen until new data arrives.
- Server setup never falls back to HTTP after a certificate error, and HTTP servers are labelled "Not secure".
- A custom relay address belongs to the user and server that set it.
- No sign-in by API key. No unused scaffolding.
- API behaviour is checked against the Seerr server source (`seerr-team/seerr`, `develop`), as on iOS.

## Phases

One branch and one PR per phase, in the Android repo unless noted. Each PR has unit tests, and is checked in the emulator against the mock server before it is opened.

| # | Phase | Contents |
|---|---|---|
| 0 | Project setup | Gradle project, `.gitignore`, app icon and colours from the iOS design, empty app that builds and runs, README |
| 1 | Server and sign-in | Server address entry with the HTTPS rules, sign-in with Plex, local account and Jellyfin, session restore, sign-out, change server |
| 2 | App shell and Discover | Five tabs (Discover, Movies, Series, Requests, Profile), Discover shelves, global search, media cards with status |
| 3 | Details | Movie, series and collection pages, cast, recommendations and similar, watchlist button |
| 4 | Requesting | Request sheet: seasons, 4K, quota, advanced Radarr and Sonarr options, requesting for another user |
| 5 | Requests tab | Paged list, filter and sort, approve, decline, delete, review sheet |
| 6 | Movies and Series | Browse grids, type-limited search, filters, saved filters, last-used filters |
| 7 | Issues | Issues list in Profile, report an issue, issue page with comments and status |
| 8 | Profile | Users management, notification settings for other services, appearance |
| 9a | Relay: FCM sender | **In the iOS repo.** `platform` on device registration, an FCM sender next to the Apple one, tests, README. Redeploy the relay |
| 9b | Push on Android | Register with the relay, notification preferences, tapping a notification opens the title or issue |
| 10 | Release | Tablet layouts, release signing, a signed APK, then Play internal testing if wanted |

Phases 1 to 8 need nothing from the relay. Phase 9a can be done at any point before 9b.

## Push in more detail

- The relay's device records gain `platform: "apns" | "fcm"`. Existing records are treated as `apns`, so iOS keeps working unchanged.
- The token check accepts FCM tokens, which are not hex.
- The FCM sender uses Google's HTTP v1 API, signed with a service-account key, written without dependencies like the Apple sender.
- New relay settings: `FCM_PROJECT_ID` and `FCM_SERVICE_ACCOUNT_FILE`. Without them the relay runs as today and Android devices are refused with a clear message.
- The app needs `google-services.json` from the Firebase project. It is ignored by git because the repo is public.

## What the owner needs to do

- **Before phase 0:** install Android Studio (it brings the JDK, SDK and emulator), and confirm the decisions above.
- **Before phase 9:** create a Firebase project, add an Android app with the chosen package name, and download `google-services.json` and a service-account key.
- **Before phase 10:** decide on Play. If yes, register a Play developer account.
- **Throughout:** merge each PR, as on iOS.

## Open questions

- **Package name.** Suggested: `com.nesoi.seerr.android`. It can't be changed after release.
- **App name on the device.** Suggested: "Seerr", as on iOS.
- **Tablets.** The plan covers them in phase 10 with adaptive layouts. They can be dropped to save time.

## Not in scope

- Sharing code between iOS and Android (Kotlin Multiplatform or similar). Two small native apps are simpler to keep correct than one shared layer.
- Changes to Seerr itself.
- Wear OS, Android TV, widgets.
