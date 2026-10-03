# PLAN: Random Dhamma — daily random sutta or morning talk (Android)

> **Status (2026-10-03): built, and installed on the Pixel 7.** This plan has been updated to describe the app as it is now. "Changes since the original plan" below records what changed during development and why.

## Context
`Project_spec.md` asks for an Android app (Pixel 7) that sends a notification **once a day at a random time between 07:00 and 22:00**. The notification offers either a random sutta from dhammatalks.org/suttas or a random morning talk from dhammatalks.org/audio/morning, and talks play at **0.75× by default**.

**Decisions from Q&A**
- Each day a coin flip picks a sutta or a talk (the ratio can be changed in Settings).
- Suttas open in an in-app WebView.
- Talks play in an in-app Media3/ExoPlayer player with background playback, lock-screen controls and 0.75× speed by default.
- Stack: Kotlin + Jetpack Compose, Gradle, Android Studio.
- Extras: a "Give me one now" button, a History list and a Settings screen.
- Install: sideload a debug build. No Play Store work.
- Talks are drawn from every year, 2010 to the current year.
- *Added later:* the next teaching's time is never shown; it stays a surprise.
- *Added later:* a talk can be started from the lock screen without unlocking, using a compact ▶ notification and then the standard ⏸/▶ media player.
- *Added later:* the app icon is a white Bodhi leaf on bright green.

**What I verified on the website (2026-10-03)**
- `GET https://www.dhammatalks.org/suttas/random` returns **HTTP 302** with `Location: /suttas/AN/AN7_51.html`. Reading the `Location` header gives a random sutta URL. No scraping is needed.
- `https://www.dhammatalks.org/audio/morning/` links to the year pages `/audio/morning/2010/` … `/2025/` and lists the current year (2026) inline. `/audio/morning/2026/` redirects back to the index.
- Each year page lists its talks as `li.li-audio` items. Each item has the MP3 in `button[data-link]`, for example `/Archive/shorttalks/y2015/151231(short)_To_Have_a_Happy_New_Year.mp3`, and the title in `a.audio span`. There are about 250 per year.
- Sutta titles come from the page `<title>` ("AN 7:51 Undeclared | …"), with the URL as a fallback.
- So the app needs **no backend**.

## Architecture
Single-module app, package `org.randomdhamma`.
- **Build:** AGP 9.4 with its built-in Kotlin (Kotlin 2.4), Gradle 9.8, KSP 2.3 and a version catalog (`gradle/libs.versions.toml`).
  - minSdk 26; compileSdk/targetSdk 37 with build-tools 36.0.0, to match the SDK installed locally.
  - The Pixel 7 runs Android 17 (API 37).
- **Dependencies:**
  - UI: Compose BOM + Material 3 and Material icons, Navigation-Compose, lifecycle-runtime-compose.
  - Playback: `androidx.media3` (exoplayer, session), plus `androidx.media` for the compact notification style.
  - Data and background work: WorkManager, Room (KSP), DataStore Preferences.
  - Network: OkHttp and Jsoup.
- **Manual DI:** a single `AppContainer` held in `RandomDhammaApp`.

### Packages and key files (`app/src/main/java/org/randomdhamma/`)
| Area | Files | Responsibility |
|---|---|---|
| root | `RandomDhammaApp.kt`, `AppContainer.kt` | App start: creates notification channels, schedules the weekly talk-cache job, and makes sure the daily alarm is armed. |
| data/remote | `DhammaParsers.kt`, `DhammaTalksClient.kt` | Side-effect-free parsing of years, talks and titles. An OkHttp client with redirects off: `randomSutta()` reads the 302 `Location`, and `fetchYears()`/`fetchTalks()` follow redirects by hand. Sends a polite User-Agent. |
| data/local | `AppDatabase.kt`, `Entities.kt`, `Daos.kt` | Room tables: `history` (picks), `talks` (cached talk lists) and `talk_years` (when each year was fetched). |
| data | `ContentRepository.kt`, `SettingsRepository.kt`, `Models.kt` | `pick(source, type?)`: weighted coin flip (`chooseType`), then a sutta or a talk, recorded in History. Talk-cache refresh and the `isTalkListStale` rule. DataStore settings. |
| schedule | `RandomTime.kt`, `RearmAction.kt`, `DailyScheduler.kt` | `nextFireTime` picks the random time. `rearmAction` decides whether a stored time is re-armed, left alone or replaced. `DailyScheduler` sets the alarm. |
| schedule | `Receivers.kt`, `Workers.kt` | `AlarmReceiver`, `BootReceiver`, and the shared `runAsync` helper. `DailyPickWorker` resolves the pick and posts the notification. `TalkCacheWorker` refreshes the cache weekly. |
| notify | `Notifications.kt` | Channels and notifications. A talk gets a compact media-style ▶ action that starts playback directly. Visible on the lock screen. |
| playback | `PlaybackService.kt`, `PlayTalk.kt` | `MediaSessionService` with ExoPlayer: speech audio attributes, audio focus and ±10 s seek. Handles the notification's play command. `Player.playTalk()` is shared with the player screen. |
| ui | `MainActivity.kt`, `AppNav.kt`, `NavIntents.kt` | Navigation (Today, History, Settings; sutta and talk routes) and deep links from notifications and the media session. |
| ui | `HomeScreen.kt` | Today's pick, "Give me one now", and sutta-only/talk-only buttons. No next-time display. |
| ui | `SuttaScreen.kt` | WebView. dhammatalks.org links stay in-app; other links open the browser. Has a "Next random sutta" action. |
| ui | `PlayerScreen.kt` | Media3 controller UI: play/pause, ±10 s seek, progress and speed chips. |
| ui | `HistoryScreen.kt`, `SettingsScreen.kt`, `Format.kt`, `theme/Theme.kt` | History list (with clear). Settings: notifications on/off, time window (Material 3 time picker), sutta/talk mix, default speed, exact time, and a test notification. Shared formatting and speed list. Dynamic colour theme. |
| util | `SuspendRunCatching.kt` | `runCatching` for suspend calls that rethrows cancellation. |
| debug (debug builds only) | `DebugTriggerReceiver.kt` | adb commands: arm the real alarm in N seconds, send a test notification (optionally of one type), and re-roll today's time. |

### Key design details
1. **Random daily time:** a uniform random minute in `[start, end)`, in today's remaining window if today hasn't fired yet, otherwise tomorrow.
   - Inexact alarms can arrive up to an hour late (`window=+1h` in `dumpsys alarm`). To allow for that, the time is picked at least 60 minutes before the window ends. Exact alarms don't need this slack.
2. **Alarm type:** `setAndAllowWhileIdle` by default, which needs no special permission. "Exact time" in Settings uses `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` allows it.
3. **Re-arming (`rearmAction`):** AlarmManager can't report whether an alarm is pending, so:
   - a stored time in the future is re-armed;
   - a time that passed less than **2 hours** ago is left alone, because it may still be delivered or be firing right now;
   - an older time is treated as lost and replaced;
   - after a reboot, a passed time is always treated as missed.
   - Changing the window, the clock or the time zone picks a new time.
4. **When the alarm fires:** it first queues `DailyPickWorker` (expedited), then marks today done and schedules tomorrow. The worker retries up to 3 times, then posts a "Tap for today's random teaching" fallback.
5. **Picking a talk uniformly:** every year's talk list is cached in Room and refreshed by a weekly periodic job.
   - A year's list is refreshed if it was fetched before that year ended (plus 7 days' grace) and is over a week old. Lists fetched after the year ended are final.
   - Until the cache is complete, the app picks a random year, then a random talk from it.
6. **Speed:** `playTalk()` sets the default speed (0.75× unless changed in Settings) each time a talk starts. Changing speed in the player affects only that session.
7. **Lock-screen playback:**
   - The ▶ button on the notification starts the service directly; an activity would require unlocking.
   - The service becomes a foreground service *before* `play()`, because Android 16+ ignores audio requests from background apps.
   - The session is registered with `addSession()`, so Media3's lock-screen player appears even when no app screen is connected.
8. **Deep links:** navigation waits for the nav graph, which is needed on a cold start. Intents relaunched from Recents are ignored, so an old notification tap isn't replayed. The player starts its talk once per back-stack entry.
9. **Permissions:** `INTERNET`, `POST_NOTIFICATIONS` (asked for at first launch), `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and optionally `SCHEDULE_EXACT_ALARM`.
10. **App icon:** an adaptive vector icon: a white Bodhi leaf with green veins on `#34A853` (3.06:1, meeting WCAG 3:1 for graphics). There is a monochrome leaf for themed icons, and the same silhouette is the status-bar icon.

## Implementation milestones
All done. Commit history:
1. **App built** (`adfa1ea`): scaffold, parsing and tests, the sutta and audio flows, the repository and coin flip, scheduling and notifications, History and Settings, README.
2. **Surprise time** (`44bdd71`): removed every display of the next time, and added a debug re-roll.
3. **Lock-screen playback** (`46a70f6`): compact ▶ notification, foreground-before-play, session registration.
4. **App icon** (`21e21a8`, `0f14a83`, `68d1f1d`): Bodhi leaf, then a contrast fix, then the bright green version.
5. **Code review fixes** (`5002d61`): scheduling races, cold-start deep links, Recents replay, player re-entry, the talk-cache year-end rule, weekly cache job, and a clean lint.
6. **README brought up to date** (`73c5958`).

## Changes since the original plan
- **SDK 37 instead of 35:** only API 37 and build-tools 36.0.0 are installed, and nothing may be downloaded outside the project.
- **Seek is ±10 s instead of ±15 s**, to match the standard icons. 1.5× was added to the speeds.
- **Talk notifications have a ▶ button that works on the lock screen.** Tapping a notification's body still opens the app, which requires unlocking.
- **The "next teaching at …" display was removed** at your request.
- **The inexact-alarm slack and the re-arm rules were added** after on-device testing showed the one-hour delivery window. A code review then found a double-notification race and a skipped-notification case.
- **The talk cache refresh is weekly periodic work** instead of running on every app start, and the year-end rule replaced "refresh the latest year".

## Verification
- **Unit tests** (`./gradlew :app:testDebugUnitTest`, 25 tests):
  - parsing of saved HTML fixtures;
  - random-time generation, including the slack;
  - `rearmAction`;
  - `isTalkListStale`;
  - the coin-flip ratio.
- **Lint** (`./gradlew :app:lintDebug`): no issues.
- **Checked on the emulator (API 37):**
  - sutta and talk flows, and 0.75× playback in the background and with the screen off;
  - a test notification and its deep link;
  - the real alarm path, including the alarm cold-starting the app (one alarm left, for tomorrow);
  - cold-start deep links;
  - player back-navigation;
  - lock-screen ▶ and ⏸/▶ with a PIN set.
- **Checked on the Pixel 7:** install, notifications on the lock screen, and lock-screen playback.
- **Not yet tested:** a real reboot, Doze (`adb shell dumpsys deviceidle force-idle`), behaviour offline, and the Recents fix.
