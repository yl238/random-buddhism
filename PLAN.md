# PLAN: Random Dhamma — daily random sutta or morning talk (Android)

## Context
`Project_spec.md` asks for an Android app (Pixel 7) that sends a notification **once a day at a random time between 07:00 and 22:00**. The notification offers either a random sutta from dhammatalks.org/suttas or a random morning talk from dhammatalks.org/audio/morning, and talks play at **0.75× by default**. The repo has no code yet: just the spec and an IntelliJ `.idea` folder.

**Decisions from Q&A**
- Each day a coin flip picks a sutta or a talk (the ratio can be changed in Settings).
- Suttas open in an in-app WebView.
- Talks play in an in-app Media3/ExoPlayer player with background playback, lock-screen controls and 0.75× speed by default.
- Stack: Kotlin + Jetpack Compose, Gradle, Android Studio.
- Extras: a "Give me one now" button, a History list and a Settings screen.
- Install: sideload a debug build from Android Studio. No Play Store work.
- Talks are drawn from every year, 2010 to the current year.

**What I verified on the website (2026-10-03)**
- `GET https://www.dhammatalks.org/suttas/random` returns **HTTP 302** with `Location: /suttas/AN/AN7_51.html`. Turning off redirect-following and reading the `Location` header gives a random sutta URL. No scraping is needed.
- `https://www.dhammatalks.org/audio/morning/` links to the year pages `/audio/morning/2010/` … `/2025/` and lists the current year (2026) inline.
- Each year page links straight to its MP3s, for example `href="/Archive/shorttalks/y2015/151231(short)_To_Have_a_Happy_New_Year.mp3"`. There are about 250 per year.
- So the app needs **no backend**. Talk titles come from the MP3 filename (strip the date and `(short)_`, replace `_` with a space) or from the link text next to it.

## Architecture
Single-module app with package `org.randomdhamma` (name can change).
- **Build:** AGP 9.4 with its built-in Kotlin (Kotlin 2.4), Gradle 9.8, KSP 2.3 and a version catalog (`gradle/libs.versions.toml`).
  - minSdk 26; compileSdk/targetSdk 37 with build-tools 36.0.0, to match the SDK installed locally.
  - The Pixel 7 runs Android 13–16.
- **Dependencies:** Compose BOM + Material 3, Navigation-Compose, `androidx.media3` (exoplayer, session, ui), WorkManager, Room, DataStore Preferences, OkHttp, Jsoup.
- **Manual DI:** a single `AppContainer` held in `Application`. Hilt isn't worth it at this size.

### Packages and key files (`app/src/main/java/org/randomdhamma/`)
| Area | Files | Responsibility |
|---|---|---|
| data/remote | `DhammaTalksClient.kt` | OkHttp with `followRedirects(false)`. `randomSuttaUrl()` reads the 302 `Location` header. `fetchYearList()` and `fetchTalks(year)` parse links with Jsoup. Sends a polite User-Agent. |
| data/local | `AppDatabase.kt`, `HistoryEntity/Dao`, `TalkCacheEntity/Dao` | History of picks (type, url, title, timestamp, opened). Cache of talk lists per year. |
| data | `ContentRepository.kt` | `pickRandom(): Pick`, which does the weighted coin flip and then picks a sutta or a talk. |
| data | `SettingsRepository.kt` | DataStore settings: window start/end (default 07:00/22:00), sutta-vs-audio ratio (default 50%), default speed (default 0.75), notifications on/off. |
| schedule | `DailyScheduler.kt` | Works out tomorrow's (or today's remaining) random time and sets the alarm. |
| schedule | `AlarmReceiver.kt`, `BootReceiver.kt` | When the alarm fires, enqueues an expedited `DailyPickWorker`. `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED` all reschedule. |
| schedule | `DailyPickWorker.kt` | Calls `pickRandom()`, saves it to History, posts the notification, then schedules the next day. |
| notify | `Notifications.kt` | Notification channel and builder. Tapping it deep-links into the reader or player. |
| playback | `PlaybackService.kt` | `MediaSessionService` with ExoPlayer. Calls `setPlaybackSpeed(settings.defaultSpeed)` every time an item loads. Provides lock-screen and notification controls. |
| ui | `MainActivity.kt`, `nav/AppNav.kt` | Navigation and the deep-link routes `sutta?url=` and `talk?url=&title=`. |
| ui | `HomeScreen.kt` | Shows today's pick, the "Give me one now" button and the next scheduled time. |
| ui | `SuttaScreen.kt` | WebView loading the sutta URL. JavaScript on, and links stay inside the app. Adds a "Next random sutta" action. |
| ui | `PlayerScreen.kt` | Media3 controller UI: play/pause, ±10 s seek, and speed chips (0.5/0.75/1.0/1.25/1.5). |
| ui | `HistoryScreen.kt`, `SettingsScreen.kt` | History lists past picks and reopens them. Settings holds the time window, ratio, speed, a test-notification button and an exact-alarm permission hint. |

### Key design details
1. **Random daily time:** pick a uniform random minute in `[start, end)`.
   - If the app starts or the device reboots after today's time has passed (or after today's notification has fired), schedule for tomorrow.
   - Store `nextFireAt` in DataStore so a reschedule after reboot keeps the same day's time and doesn't re-roll it.
2. **Alarm type:** `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)`.
   - This alarm is inexact. It can be a few minutes late in Doze, which is fine for a random time.
   - It avoids `SCHEDULE_EXACT_ALARM`, which Android 14+ denies by default.
   - Settings can offer exact alarms as an option, guarded by `canScheduleExactAlarms()`.
3. **Network work happens in WorkManager, not in the receiver.** The worker uses the `CONNECTED` constraint and retries with backoff.
   - If the fetch fails, the fallback notification just says "Tap for today's random teaching", and the pick is resolved when tapped.
4. **Picking a talk uniformly:** the year list comes from the morning index page (2010 → current year).
   - On first use, prefetch each year's talk list in the background and cache it in Room. Past years never change. The current year is refreshed if its cache is older than 7 days.
   - Then pick uniformly over all cached talks. Until the cache is complete, pick a random year and then a random talk from it.
5. **0.75× speed:** set it on the player after each `setMediaItem` and keep it as the session default. A speed changed in the player applies only to that session. The Settings default persists.
6. **Permissions:** `INTERNET`, `POST_NOTIFICATIONS` (asked for at runtime on first launch), `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
7. **Gitignore:** replace the plain `.idea` IntelliJ module with a standard Android Studio Gradle project and add an Android `.gitignore` (`.idea/`, `build/`, `local.properties`, `*.iml`).

## Implementation milestones
1. **Scaffold.** Gradle wrapper, version catalog, the `app` module, an empty Compose `MainActivity` and `.gitignore`. The debug build installs on the Pixel 7.
2. **Remote and parsing.** `DhammaTalksClient` with unit tests against saved HTML fixtures (`app/src/test/resources/`) for the 302 handling and MP3 link parsing.
3. **Sutta flow.** `SuttaScreen` WebView and Home's "Give me one now", forced to sutta.
4. **Audio flow.** `PlaybackService`, `PlayerScreen` and the 0.75× default, including background playback and lock-screen controls.
5. **Repository and coin flip.** `ContentRepository`, Room history and the talk cache.
6. **Scheduling and notifications.** Scheduler, receivers, worker, notification deep links and the boot reschedule.
7. **History and Settings screens**, connected to DataStore. Changing the window reschedules.
8. **Polish.** App icon, empty and error states, offline message, README with build and sideload steps.

## Verification
- **Unit tests** (`./gradlew test`): sutta 302 parsing, MP3 and title parsing from fixtures, random-time generation (always inside the window, and rolls to tomorrow when past), coin-flip ratio.
- **Instrumented/manual on the Pixel 7** (`./gradlew installDebug`):
  - "Give me one now" opens both a sutta and a talk. The talk starts at 0.75× (check the speed chip and by ear) and keeps playing with the screen locked.
  - Settings "Send test notification" posts one, and tapping it opens the right screen.
  - Set the window to the next 2 minutes and confirm the real alarm fires. Check with `adb shell dumpsys alarm | grep randomdhamma`.
  - Reboot the phone and check the alarm is still scheduled (`dumpsys alarm`).
  - Force Doze (`adb shell dumpsys deviceidle force-idle`) and confirm the alarm still fires.
  - With airplane mode on, check the fallback notification and the error states.
